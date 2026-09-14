package com.example.agentsapp.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Выбрасывается, когда AgentsCore возвращает ответ, отличный от 2xx — это
 * собственная форма ошибки сервиса, `{"error": "..."}`. */
class AgentApiException(
    val statusCode: Int,
    val apiMessage: String?,
) : IOException(apiMessage ?: "Ошибка API сервера ($statusCode)")

/** Выбрасывается, когда сам сервер AgentsCore вообще недостижим (неверный
 * адрес, сервис не запущен, нет сети). */
class AgentUnreachableException(cause: Throwable) :
    IOException("Не удалось подключиться к серверу: ${cause.message}", cause)

private const val JSON_MEDIA_TYPE = "application/json; charset=utf-8"

/**
 * Тонкий клиент на базе OkHttp для HTTP API **AgentsCore** (точный контракт —
 * см. `agents_core/api/` и `agents_core/schemas.py` в Python-пакете).
 * Приложение никогда не обращается к DeepSeek/Ollama напрямую и никогда не
 * видит ключи API — все вызовы идут к AgentsCore, который владеет базой
 * данных, каталогом моделей и оркестрацией LLM.
 */
class AgentsCoreApiClient(
    private val baseUrlProvider: () -> String,
    private val client: OkHttpClient = defaultHttpClient(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = true
    }

    companion object {
        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS) // локальные модели и режим рассуждений могут отвечать долго
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    private fun url(path: String): String = "${baseUrlProvider()}$path"

    private fun requestBuilder(path: String): Request.Builder =
        Request.Builder().url(url(path)).header("Accept", "application/json")

    // ---- Состояние / модели -------------------------------------------------

    suspend fun health(): HealthResponse = get("health", HealthResponse.serializer())

    suspend fun listModels(): List<ModelInfo> = get("models", ListSerializer(ModelInfo.serializer()))

    suspend fun modelHealth(modelId: String): ModelHealth =
        get("model/$modelId/health", ModelHealth.serializer())

    // ---- Настройки по умолчанию ----------------------------------------------

    suspend fun getDefaultSettings(): DefaultSettings = get("settings/default", DefaultSettings.serializer())

    suspend fun updateDefaultSettings(patch: Map<String, JsonElement>): DefaultSettings =
        putJson("settings/default", patch, DefaultSettings.serializer())

    suspend fun resetDefaultSettings(): DefaultSettings =
        post("settings/default/reset", null, DefaultSettings.serializer())

    // ---- Агенты ------------------------------------------------------------

    suspend fun listAgentsWithChats(): List<AgentWithChats> =
        get("agents", ListSerializer(AgentWithChats.serializer()))

    suspend fun createAgent(name: String?, model: String?): Agent =
        post("agent", AgentCreateRequest(name, model), Agent.serializer())

    suspend fun getAgent(agentId: String): Agent = get("agents/$agentId", Agent.serializer())

    suspend fun renameAgent(agentId: String, name: String): Agent =
        patch("agents/$agentId", AgentRenameRequest(name), Agent.serializer())

    suspend fun deleteAgent(agentId: String) {
        executeNoContent("agents/$agentId", "DELETE")
    }

    suspend fun getAgentSettings(agentId: String): Settings = get("agents/$agentId/settings", Settings.serializer())

    suspend fun updateAgentSettings(agentId: String, patch: Map<String, JsonElement>): Settings =
        putJson("agents/$agentId/settings", patch, Settings.serializer())

    suspend fun createChat(agentId: String, title: String?): Chat =
        post("agents/$agentId/chat", ChatCreateRequest(title), Chat.serializer())

    // ---- Чаты ---------------------------------------------------------------

    suspend fun listChats(agentId: String? = null): List<Chat> {
        val path = if (agentId != null) "chats?agent_id=$agentId" else "chats"
        return get(path, ListSerializer(Chat.serializer()))
    }

    /** [branch] пересчитывает статистику токенов чата под конкретную ветку
     * диалога — 0 (или не задано) — только основная ветка, N>0 — основная
     * ветка + ветка N. По умолчанию сервер использует конвенцию branch=1
     * (ветки 0 и 1, если ветка 1 существует). */
    suspend fun getChat(chatId: String, branch: Int? = null): Chat {
        val path = if (branch != null) "chats/$chatId?branch=$branch" else "chats/$chatId"
        return get(path, Chat.serializer())
    }

    suspend fun renameChat(chatId: String, title: String): Chat =
        patch("chats/$chatId", ChatRenameRequest(title), Chat.serializer())

    suspend fun deleteChat(chatId: String) {
        executeNoContent("chats/$chatId", "DELETE")
    }

    suspend fun copyChat(chatId: String, title: String): Chat =
        post("chats/$chatId/copy", ChatCopyRequest(title), Chat.serializer())

    suspend fun getChatSettings(chatId: String): Settings = get("chats/$chatId/settings", Settings.serializer())

    suspend fun updateChatSettings(chatId: String, patch: Map<String, JsonElement>): Settings =
        putJson("chats/$chatId/settings", patch, Settings.serializer())

    suspend fun summarizeChat(chatId: String): Message = post("chats/$chatId/summarize", null, Message.serializer())

    // ---- Ветки диалога ----------------------------------------------------

    suspend fun listBranches(chatId: String): List<Branch> =
        get("chats/$chatId/branches", ListSerializer(Branch.serializer()))

    suspend fun createBranch(chatId: String, name: String? = null): Branch =
        post("chats/$chatId/branches", BranchCreateRequest(name), Branch.serializer())

    suspend fun deleteBranch(chatId: String, number: Int) {
        executeNoContent("chats/$chatId/branches/$number", "DELETE")
    }

    // ---- Сообщения ------------------------------------------------------

    suspend fun listMessages(chatId: String): List<Message> =
        get("chats/$chatId/messages", ListSerializer(Message.serializer()))

    suspend fun clearMessages(chatId: String) {
        executeNoContent("chats/$chatId/messages", "DELETE")
    }

    suspend fun deleteMessage(chatId: String, messageId: Long) {
        executeNoContent("chats/$chatId/messages/$messageId", "DELETE")
    }

    suspend fun bulkDeleteMessages(chatId: String, ids: List<Long>) {
        val bodyJson = json.encodeToString(BulkDeleteRequest.serializer(), BulkDeleteRequest(ids))
        val request = requestBuilder("chats/$chatId/messages/bulk-delete")
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()
        executeNoContentRequest(request)
    }

    suspend fun sendMessage(
        chatId: String,
        text: String,
        getFacts: Boolean = false,
        slidingWindow: Boolean = false,
        autosummary: String = "off",
        branch: Int? = null,
    ): SendMessageResponse =
        post(
            "chats/$chatId/messages",
            SendMessageRequest(
                text = text,
                get_facts = getFacts,
                sliding_window = slidingWindow,
                autosummary = autosummary,
                branch = branch,
            ),
            SendMessageResponse.serializer(),
        )

    /** Передаёт тело `text/event-stream` эндпоинта
     * `POST /chats/{id}/messages/stream` в виде потока [AgentStreamEvent].
     * AgentsCore сохраняет сообщение пользователя и итоговый ответ ассистента
     * на своей стороне; здесь только ретранслируются кадры SSE. `getFacts` и
     * `autosummary` поддерживаются и в потоковом режиме — обновление фактов и
     * (если нужно) автосуммаризация запускаются сервером только после того,
     * как потоковая генерация полностью завершена (см. события `status`). */
    fun streamMessage(
        chatId: String,
        text: String,
        getFacts: Boolean = false,
        slidingWindow: Boolean = false,
        autosummary: String = "off",
        branch: Int? = null,
    ): Flow<AgentStreamEvent> = callbackFlow {
        val body = json.encodeToString(
            StreamSendMessageRequest.serializer(),
            StreamSendMessageRequest(
                text = text,
                get_facts = getFacts,
                sliding_window = slidingWindow,
                autosummary = autosummary,
                branch = branch,
            ),
        )
        val httpRequest = requestBuilder("chats/$chatId/messages/stream")
            .header("Accept", "text/event-stream")
            .post(body.toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()

        val call = client.newCall(httpRequest)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(AgentUnreachableException(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (!resp.isSuccessful) {
                        val raw = resp.body?.string()
                        close(AgentApiException(resp.code, extractErrorMessage(raw)))
                        return
                    }
                    val source = resp.body?.source() ?: run {
                        close()
                        return
                    }
                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue
                            val data = line.removePrefix("data:").trim()
                            if (data.isEmpty()) continue
                            parseStreamEvent(data)?.let { trySend(it) }
                        }
                    } catch (e: IOException) {
                        close(e)
                        return
                    }
                    close()
                }
            }
        })

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    private fun parseStreamEvent(data: String): AgentStreamEvent? {
        val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: return null
        return when (obj["type"]?.jsonPrimitive?.content) {
            "status" -> AgentStreamEvent.Status(status = obj["status"].stringOrNull().orEmpty())
            "delta" -> AgentStreamEvent.Delta(
                content = obj["content"].stringOrNull().orEmpty(),
                reasoningContent = obj["reasoning_content"].stringOrNull().orEmpty(),
            )
            "done" -> obj["message"]?.let {
                runCatching { json.decodeFromJsonElement(Message.serializer(), it) }.getOrNull()
            }?.let { AgentStreamEvent.Done(it) }
            "error" -> AgentStreamEvent.Error(obj["message"].stringOrNull() ?: "unknown error")
            else -> null
        }
    }

    /** Null-безопасное извлечение JSON-строки: и отсутствующий ключ, и явный
     * JSON `null` считаются отсутствием значения. */
    private fun JsonElement?.stringOrNull(): String? {
        if (this == null || this is JsonNull) return null
        return (this as? JsonPrimitive)?.content
    }

    // ---- Низкоуровневые вспомогательные методы запросов -----------------------------------------

    private suspend fun <T> get(path: String, responseSerializer: KSerializer<T>): T =
        execute(requestBuilder(path).get().build(), responseSerializer)

    private suspend fun <B : Any, T> post(path: String, body: B?, responseSerializer: KSerializer<T>): T {
        val bodyJson = if (body == null) "{}" else json.encodeToString(bodySerializerFor(body), body)
        val request = requestBuilder(path)
            .post(bodyJson.toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()
        return execute(request, responseSerializer)
    }

    private suspend fun <B : Any, T> patch(path: String, body: B, responseSerializer: KSerializer<T>): T {
        val bodyJson = json.encodeToString(bodySerializerFor(body), body)
        val request = requestBuilder(path)
            .patch(bodyJson.toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()
        return execute(request, responseSerializer)
    }

    /** PUT с частичным телом настроек: только изменённые поля, ключ ->
     * [JsonElement] (см. [jsonValueOf]) — сервер трактует такое тело как
     * частичное обновление (не переданные поля сохраняют текущее значение). */
    private suspend fun <T> putJson(path: String, patch: Map<String, JsonElement>, responseSerializer: KSerializer<T>): T {
        val bodyJson = json.encodeToString(JsonObject.serializer(), JsonObject(patch))
        val request = requestBuilder(path)
            .put(bodyJson.toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
            .build()
        return execute(request, responseSerializer)
    }

    @Suppress("UNCHECKED_CAST")
    private fun bodySerializerFor(body: Any): KSerializer<Any> = when (body) {
        is AgentCreateRequest -> AgentCreateRequest.serializer()
        is AgentRenameRequest -> AgentRenameRequest.serializer()
        is ChatCreateRequest -> ChatCreateRequest.serializer()
        is ChatCopyRequest -> ChatCopyRequest.serializer()
        is ChatRenameRequest -> ChatRenameRequest.serializer()
        is SendMessageRequest -> SendMessageRequest.serializer()
        is StreamSendMessageRequest -> StreamSendMessageRequest.serializer()
        is BulkDeleteRequest -> BulkDeleteRequest.serializer()
        is BranchCreateRequest -> BranchCreateRequest.serializer()
        else -> error("no serializer registered for ${body::class}")
    } as KSerializer<Any>

    private suspend fun executeNoContent(path: String, method: String) {
        executeNoContentRequest(Request.Builder().url(url(path)).method(method, null).build())
    }

    private suspend fun executeNoContentRequest(request: Request) {
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine<Unit> { cont ->
                val call = client.newCall(request)
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resumeWithException(AgentUnreachableException(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use { resp ->
                            if (!resp.isSuccessful) {
                                val raw = resp.body?.string()
                                if (cont.isActive) {
                                    cont.resumeWithException(AgentApiException(resp.code, extractErrorMessage(raw)))
                                }
                                return
                            }
                            if (cont.isActive) cont.resume(Unit)
                        }
                    }
                })
            }
        }
    }

    private suspend fun <T> execute(request: Request, responseSerializer: KSerializer<T>): T =
        withContext(Dispatchers.IO) {
            suspendCancellableCoroutine { cont ->
                val call = client.newCall(request)
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resumeWithException(AgentUnreachableException(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        response.use { resp ->
                            val raw = resp.body?.string()
                            if (!resp.isSuccessful) {
                                if (cont.isActive) {
                                    cont.resumeWithException(AgentApiException(resp.code, extractErrorMessage(raw)))
                                }
                                return
                            }
                            if (cont.isActive) {
                                runCatching { json.decodeFromString(responseSerializer, raw.orEmpty()) }
                                    .onSuccess { cont.resume(it) }
                                    .onFailure { cont.resumeWithException(it) }
                            }
                        }
                    }
                })
            }
        }

    private fun extractErrorMessage(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return runCatching { json.decodeFromString(AgentErrorBody.serializer(), raw).error }.getOrNull()
    }
}

@Serializable
private data class AgentErrorBody(val error: String)
