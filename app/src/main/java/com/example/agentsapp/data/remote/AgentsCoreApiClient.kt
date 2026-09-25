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

    /** Клиент для долгих SSE-подписок: сервер шлёт пинги каждые 15 секунд,
     * поэтому таймаут чтения — с запасом, но не бесконечный (обрыв сети
     * обнаруживается и подписка переподключается). */
    private val streamingClient: OkHttpClient = client.newBuilder()
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun url(path: String): String = "${baseUrlProvider()}$path"

    private fun requestBuilder(path: String): Request.Builder =
        Request.Builder().url(url(path)).header("Accept", "application/json")

    // ---- Состояние / модели -------------------------------------------------

    suspend fun health(): HealthResponse = get("health", HealthResponse.serializer())

    suspend fun listModels(): List<ModelInfo> = get("models", ListSerializer(ModelInfo.serializer()))

    /** Инструменты всех MCP-серверов, подключённых к AgentsCore (`GET /mcp/tools`) —
     * для выбора инструментов в настройках агента/чата. Имена — ровно те, под
     * которыми AgentsCore предлагает инструменты модели. */
    suspend fun listMcpTools(): List<McpToolDescription> = get("mcp/tools", ListSerializer(McpToolDescription.serializer()))

    suspend fun modelHealth(modelId: String): ModelHealth =
        get("model/$modelId/health", ModelHealth.serializer())

    // ---- Настройки по умолчанию ----------------------------------------------

    suspend fun getDefaultSettings(): DefaultSettings = get("settings/default", DefaultSettings.serializer())

    /** Полные настройки, которые получит новый агент, — база сравнения для
     * бейджей агентов на главном экране. */
    suspend fun getNewAgentSettings(): Settings = get("settings/default/agent", Settings.serializer())

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

    // ---- Рабочая память (чат) -----------------------------------------------

    suspend fun listWorkingMemory(chatId: String): List<WorkingMemoryEntry> =
        get("chats/$chatId/working-memory", ListSerializer(WorkingMemoryEntry.serializer()))

    suspend fun saveWorkingMemory(chatId: String, key: String, value: String): WorkingMemoryEntry =
        post("chats/$chatId/working-memory", WorkingMemorySaveRequest(key, value), WorkingMemoryEntry.serializer())

    suspend fun deleteWorkingMemory(chatId: String, key: String) {
        executeNoContent("chats/$chatId/working-memory/$key", "DELETE")
    }

    // ---- Долговременная память (агент) ---------------------------------------

    suspend fun listLongTermMemory(agentId: String, category: String? = null): List<LongTermMemoryEntry> {
        val path = if (category != null) "agents/$agentId/long-term-memory?category=$category" else "agents/$agentId/long-term-memory"
        return get(path, ListSerializer(LongTermMemoryEntry.serializer()))
    }

    suspend fun saveLongTermMemory(agentId: String, category: String, key: String, value: String): LongTermMemoryEntry =
        post(
            "agents/$agentId/long-term-memory",
            LongTermMemorySaveRequest(category, key, value),
            LongTermMemoryEntry.serializer(),
        )

    suspend fun deleteLongTermMemory(agentId: String, category: String, key: String) {
        executeNoContent("agents/$agentId/long-term-memory/$category/$key", "DELETE")
    }

    // ---- Профили-пайплайны (общий справочник, см. Profile в AgentsCoreModels.kt) ---

    suspend fun listProfiles(): List<Profile> =
        get("profiles", ListSerializer(Profile.serializer()))

    suspend fun listRegisteredSkills(): List<RegisteredSkill> =
        get("skills", ListSerializer(RegisteredSkill.serializer()))

    suspend fun createProfile(
        name: String,
        style: String? = null,
        format: String? = null,
        constraints: String? = null,
        skillsJson: String = "",
        skillNames: List<String>? = null,
        orchestrationPrompt: String? = null,
    ): Profile =
        post(
            "profiles",
            ProfileCreateRequest(name, style, format, constraints, skillsJson, skillNames, orchestrationPrompt),
            Profile.serializer(),
        )

    suspend fun getProfile(profileId: String): Profile = get("profiles/$profileId", Profile.serializer())

    suspend fun updateProfile(profileId: String, patch: Map<String, JsonElement>): Profile =
        putJson("profiles/$profileId", patch, Profile.serializer())

    suspend fun deleteProfile(profileId: String) {
        executeNoContent("profiles/$profileId", "DELETE")
    }

    suspend fun setChatActiveProfile(chatId: String, profileId: String?): Chat =
        put("chats/$chatId/active-profile", ActiveProfileSetRequest(profileId), Chat.serializer())

    /** Профиль по умолчанию для НОВЫХ чатов этого агента (см. комментарий у
     * `Agent.default_profile_id`) — не влияет на уже существующие чаты. */
    suspend fun setAgentDefaultProfile(agentId: String, profileId: String?): Agent =
        put("agents/$agentId/default-profile", ActiveProfileSetRequest(profileId), Agent.serializer())

    // ---- День 14. Инварианты (общий справочник, см. Invariant в AgentsCoreModels.kt) ---

    suspend fun listInvariants(): List<Invariant> =
        get("invariants", ListSerializer(Invariant.serializer()))

    suspend fun createInvariant(
        title: String, ruleText: String, kind: String? = null, isActive: Boolean = true,
    ): Invariant =
        post("invariants", InvariantCreateRequest(title, ruleText, kind, isActive), Invariant.serializer())

    suspend fun getInvariant(invariantId: String): Invariant = get("invariants/$invariantId", Invariant.serializer())

    suspend fun updateInvariant(invariantId: String, patch: Map<String, JsonElement>): Invariant =
        putJson("invariants/$invariantId", patch, Invariant.serializer())

    suspend fun deleteInvariant(invariantId: String) {
        executeNoContent("invariants/$invariantId", "DELETE")
    }

    /** Множественный выбор инвариантов для агента — действует во всех его
     * чатах (см. комментарий у `Agent.invariant_ids`). */
    suspend fun setAgentInvariants(agentId: String, invariantIds: List<String>): Agent =
        put("agents/$agentId/invariants", InvariantIdsSetRequest(invariantIds), Agent.serializer())

    /** То же самое, но уровня чата — итоговый набор объединяется с выбором агента. */
    suspend fun setChatInvariants(chatId: String, invariantIds: List<String>): Chat =
        put("chats/$chatId/invariants", InvariantIdsSetRequest(invariantIds), Chat.serializer())

    // ---- Снимок памяти ---------------------------------------------------------

    suspend fun getMemorySnapshot(chatId: String): MemorySnapshot =
        get("chats/$chatId/memory-snapshot", MemorySnapshot.serializer())

    // ---- День 13/15. Состояние задачи (Task State Machine) --------------------
    //
    // Стейт-машина сама зашита на сервере (см. комментарий у TaskStateInfo в
    // AgentsCoreModels.kt) — единственное, что здесь читается/пишется: список
    // из 4 фиксированных состояний (только для чтения) и набор прикреплённых
    // к машине инвариантов (полная замена, PUT).

    suspend fun getTaskStateMachine(): TaskStateMachineInfo =
        get("task-state-machine", TaskStateMachineInfo.serializer())

    suspend fun setTaskStateMachineInvariants(invariantIds: List<String>): TaskStateMachineInfo =
        put(
            "task-state-machine/invariants",
            TaskMachineInvariantIdsSetRequest(invariantIds),
            TaskStateMachineInfo.serializer(),
        )

    // -- задачи: списки, детали, ручное вмешательство --

    /** [includeCompleted] — по умолчанию false, только текущие (активные и на
     * паузе); true добавляет и завершённые (по замечанию пользователя:
     * "опционально можно показать и выполненные задачи"). */
    suspend fun listChatTasks(chatId: String, includeCompleted: Boolean = false): List<TaskSummary> =
        get("chats/$chatId/tasks?include_completed=$includeCompleted", ListSerializer(TaskSummary.serializer()))

    /** Агрегированный список задач по ВСЕМ чатам агента — для блока "Задачи"
     * на карточке агента (см. AgentCard в MainScreen.kt); каждая строка несёт
     * свой родительский чат (`TaskSummary.chat_title`). */
    suspend fun listAgentTasks(agentId: String, includeCompleted: Boolean = false): List<TaskSummary> =
        get("agents/$agentId/tasks?include_completed=$includeCompleted", ListSerializer(TaskSummary.serializer()))

    suspend fun getTask(taskId: String): TaskDetail = get("tasks/$taskId", TaskDetail.serializer())

    /** Ручное вмешательство человека — кнопки "Поставить на паузу"/"Продолжить"
     * на экране задачи (подтверждено пользователем, пункт 7.3). */
    suspend fun applyTaskActionManually(taskId: String, action: String, note: String? = null): TaskDetail =
        post("tasks/$taskId/actions", TaskManualActionRequest(action, note), TaskDetail.serializer())

    suspend fun deleteTask(taskId: String) {
        executeNoContent("tasks/$taskId", "DELETE")
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

    // ---- Асинхронные запуски, непрочитанные, общая лента (ТЗ «асинхронные ответы») ----

    /** Отправить сообщение: сервер сразу возвращает запуск, сообщение
     * пользователя и черновик ответа; сама генерация идёт в фоне. `409` —
     * в чате уже формируется ответ. [clientRequestId] — ключ
     * идемпотентности (повтор после сбоя сети не создаёт второй запрос). */
    suspend fun createMessageRun(
        chatId: String,
        text: String,
        getFacts: Boolean = false,
        slidingWindow: Boolean = false,
        autosummary: String = "off",
        branch: Int? = null,
        clientRequestId: String? = null,
    ): RunCreated =
        post(
            "chats/$chatId/runs",
            RunCreateRequest(
                text = text, get_facts = getFacts, sliding_window = slidingWindow,
                autosummary = autosummary, branch = branch, client_request_id = clientRequestId,
            ),
            RunCreated.serializer(),
        )

    /** Менеджер задач: [autoPause] = true — один шаг («Продолжить»), false —
     * шаги подряд до завершения задачи на сервере («Выполнить»). */
    suspend fun createTaskRun(chatId: String, taskId: String, autoPause: Boolean, clientRequestId: String? = null): RunCreated =
        post("chats/$chatId/tasks/$taskId/runs", TaskRunCreateRequest(autoPause, clientRequestId), RunCreated.serializer())

    suspend fun getRun(runId: String): RunSnapshot = get("runs/$runId", RunSnapshot.serializer())

    suspend fun cancelRun(runId: String): Run = post("runs/$runId/cancel", null, Run.serializer())

    suspend fun listActiveRuns(): List<Run> = get("runs?active=true", ListSerializer(Run.serializer()))

    suspend fun markChatRead(chatId: String, messageId: Long): ChatActivity =
        post("chats/$chatId/read", ChatReadRequest(messageId), ChatActivity.serializer())

    suspend fun eventsCursor(): EventsCursor = get("events/cursor", EventsCursor.serializer())

    /** События запуска с номера [after] (SSE): сначала пропущенные, потом
     * вживую; поток закрывается после итогового события. `410` (лента уже
     * удалена на сервере) приходит как [RunEvent.Gone]. */
    fun runEvents(runId: String, after: Long): Flow<RunEvent> =
        sseFlow("runs/$runId/events?after=$after", onGone = { RunEvent.Gone }) { parseRunEvent(it) }

    /** Общая лента изменений (SSE) с номера [after]; `410` — [GlobalEvent.Gone]. */
    fun globalEvents(after: Long): Flow<GlobalEvent> =
        sseFlow("events?after=$after", onGone = { GlobalEvent.Gone }) { parseGlobalEvent(it) }

    /** Общий читатель SSE (GET): строки `data: {...}` → [parse]; служебные
     * пинги сервера (строки-комментарии) пропускаются. */
    private fun <T : Any> sseFlow(path: String, onGone: () -> T, parse: (JsonObject) -> T?): Flow<T> = callbackFlow {
        val httpRequest = requestBuilder(path)
            .header("Accept", "text/event-stream")
            .get()
            .build()
        val call = streamingClient.newCall(httpRequest)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(AgentUnreachableException(e))
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { resp ->
                    if (resp.code == 410) {
                        trySend(onGone())
                        close()
                        return
                    }
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
                            val obj = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull() ?: continue
                            parse(obj)?.let { trySend(it) }
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

    private fun JsonObject.seq(): Long = (this["seq"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L

    private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.content?.toBooleanStrictOrNull()

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.content?.toLongOrNull()

    private fun decodeMessage(element: JsonElement?): Message? {
        if (element == null || element is JsonNull || element !is JsonObject) return null
        return runCatching { json.decodeFromJsonElement(Message.serializer(), element) }.getOrNull()
    }

    private fun parseRunEvent(obj: JsonObject): RunEvent? {
        val seq = obj.seq()
        return when (obj["type"].stringOrNull()) {
            "run_started" -> RunEvent.Started(seq, decodeMessage(obj["assistant_message"]))
            "status" -> RunEvent.Status(seq, obj["status"].stringOrNull().orEmpty())
            "delta" -> RunEvent.Delta(
                seq,
                content = obj["content"].stringOrNull().orEmpty(),
                reasoningContent = obj["reasoning_content"].stringOrNull().orEmpty(),
            )
            "tool_call" -> RunEvent.ToolCall(
                seq,
                ToolCallEvent(
                    name = obj["name"].stringOrNull().orEmpty(),
                    source = obj["source"].stringOrNull() ?: "unknown",
                    group = obj["group"].stringOrNull(),
                    status = obj["status"].stringOrNull().orEmpty(),
                    ok = obj.bool("ok"),
                    error = obj["error"].stringOrNull(),
                ),
            )
            "task_event" -> runCatching { json.decodeFromJsonElement(TaskEvent.serializer(), obj) }.getOrNull()
                ?.let { RunEvent.Task(seq, it) }
            "message_saved" -> RunEvent.MessageSaved(seq, decodeMessage(obj["message"]))
            "message_started" -> RunEvent.MessageStarted(seq, decodeMessage(obj["assistant_message"]))
            "task_step_done" -> RunEvent.TaskStepDone(
                seq, taskStatus = obj["task_status"].stringOrNull(), shouldContinue = obj.bool("should_continue") ?: false,
            )
            "done" -> RunEvent.Done(seq, decodeMessage(obj["message"]))
            "cancelled" -> RunEvent.Cancelled(seq, decodeMessage(obj["message"]))
            "error" -> RunEvent.Error(
                seq,
                message = obj["message"].stringOrNull() ?: "Ошибка",
                assistantMessage = decodeMessage(obj["assistant_message"]),
            )
            "gone" -> RunEvent.Gone
            else -> RunEvent.Other(seq)
        }
    }

    private fun parseGlobalEvent(obj: JsonObject): GlobalEvent? {
        val seq = obj.seq()
        val chatId = obj["chat_id"].stringOrNull().orEmpty()
        fun chat(): Chat? = (obj["chat"] as? JsonObject)?.let {
            runCatching { json.decodeFromJsonElement(Chat.serializer(), it) }.getOrNull()
        }
        return when (obj["type"].stringOrNull()) {
            "chat_created" -> GlobalEvent.ChatCreated(seq, chat(), chatId)
            "chat_updated" -> GlobalEvent.ChatUpdated(seq, chat(), chatId)
            "chat_deleted" -> GlobalEvent.ChatDeleted(seq, chatId)
            "run_started" -> GlobalEvent.RunStarted(
                seq, chatId,
                (obj["run"] as? JsonObject)?.let { runCatching { json.decodeFromJsonElement(RunBrief.serializer(), it) }.getOrNull() },
            )
            "run_status" -> GlobalEvent.RunStatus(
                seq, chatId, obj["run_id"].stringOrNull().orEmpty(), obj["current_status"].stringOrNull(),
            )
            "run_finished" -> GlobalEvent.RunFinished(
                seq, chatId, obj["run_id"].stringOrNull().orEmpty(), obj["status"].stringOrNull().orEmpty(),
            )
            "unread_changed" -> GlobalEvent.UnreadChanged(
                seq, chatId,
                unreadCount = obj.long("unread_count")?.toInt() ?: 0,
                firstUnreadMessageId = obj.long("first_unread_message_id"),
                lastMessageAt = obj.long("last_message_at"),
                preview = obj["preview"].stringOrNull(),
            )
            "gone" -> GlobalEvent.Gone
            else -> GlobalEvent.Other(seq)
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

    /** PUT с типизированным (не частичным) телом — в отличие от [putJson],
     * используется там, где сервер ожидает объект целиком, а не карту
     * "изменённое поле -> значение" (например, `PUT .../active-profile`). */
    private suspend fun <B : Any, T> put(path: String, body: B, responseSerializer: KSerializer<T>): T {
        val bodyJson = json.encodeToString(bodySerializerFor(body), body)
        val request = requestBuilder(path)
            .put(bodyJson.toRequestBody(JSON_MEDIA_TYPE.toMediaType()))
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
        is RunCreateRequest -> RunCreateRequest.serializer()
        is TaskRunCreateRequest -> TaskRunCreateRequest.serializer()
        is ChatReadRequest -> ChatReadRequest.serializer()
        is BulkDeleteRequest -> BulkDeleteRequest.serializer()
        is BranchCreateRequest -> BranchCreateRequest.serializer()
        is WorkingMemorySaveRequest -> WorkingMemorySaveRequest.serializer()
        is LongTermMemorySaveRequest -> LongTermMemorySaveRequest.serializer()
        is ProfileCreateRequest -> ProfileCreateRequest.serializer()
        is ActiveProfileSetRequest -> ActiveProfileSetRequest.serializer()
        is InvariantCreateRequest -> InvariantCreateRequest.serializer()
        is InvariantIdsSetRequest -> InvariantIdsSetRequest.serializer()
        is TaskMachineInvariantIdsSetRequest -> TaskMachineInvariantIdsSetRequest.serializer()
        is TaskManualActionRequest -> TaskManualActionRequest.serializer()
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
