package com.example.agentsapp.data.remote

import kotlinx.serialization.Serializable

/**
 * JSON-модели данных, в точности соответствующие схемам HTTP API AgentsCore
 * (`agents_core/schemas.py`). Имена полей совпадают с JSON-ключами сервера —
 * менять их нельзя, это часть сетевого контракта.
 */

@Serializable
data class Settings(
    val model: String,
    val system_prompt: String,
    val temperature: Double,
    val top_p: Double,
    val seed: Int? = null,
    val stream: Boolean,
    val thinking_enabled: Boolean,
    val reasoning_effort: String? = null,
    val max_tokens: Int? = null,
    val json_mode: Boolean,
    val stop_sequences: List<String> = emptyList(),
    val tool_choice: String,
    val tools_json: String = "",
    val summary_prompt: String,
    // На случай ответа от ещё не обновлённого сервера (без этих полей в
    // схеме) — значения по умолчанию, чтобы kotlinx.serialization не падал
    // с "Fields [...] are required", а просто подставлял пустую строку.
    val summary_system_prompt: String = "",
    val autosummary: String,
    val autosummary_by_messages: Int,
    val autosummary_by_tokens: Int,
    val context_strategy: String? = null,
    val context_strategy_limit: Int? = null,
    val extraction_system_prompt: String = "",
    val include_usage_in_stream: Boolean,
    val logprobs: Boolean,
    val frequency_penalty: Double? = null,
    val presence_penalty: Double? = null,
)

@Serializable
data class DefaultSettings(
    val model: String,
    val system_prompt: String,
    val temperature: Double,
    val top_p: Double,
    val seed: Int? = null,
    val stream: Boolean,
    val thinking_enabled: Boolean,
    val reasoning_effort: String? = null,
    val summary_prompt: String,
    // См. комментарий у Settings.summary_system_prompt выше — то же самое
    // защитное значение по умолчанию.
    val summary_system_prompt: String = "",
    val include_usage_in_stream: Boolean,
)

@Serializable
data class ModelInfo(
    val id: String,
    val provider: String,
    val model_id: String,
    val display_name: String,
    val is_local: Boolean,
    val context_window: Int? = null,
    val max_input_tokens: Int? = null,
    val max_output_tokens: Int? = null,
    val max_reasoning_tokens: Int? = null,
    val supports_thinking: Boolean,
    val supports_tools: Boolean,
    val supports_json_mode: Boolean,
    val supports_logprobs: Boolean,
)

@Serializable
data class ModelHealth(val model: String, val healthy: Boolean)

@Serializable
data class HealthResponse(val status: String, val version: String = "")

@Serializable
data class ChatStats(
    val prompt_tokens: Int,
    val completion_tokens: Int,
    val total_tokens: Int,
    val current_context_tokens: Int,
    val max_input_tokens: Int? = null,
    val context_window: Int? = null,
    val context_fill_ratio: Double? = null,
    val active_context_start_id: Long? = null,
    val can_summarize: Boolean,
)

@Serializable
data class Agent(
    val id: String,
    val name: String,
    val created_at: Long,
    val updated_at: Long,
    val settings: Settings,
)

@Serializable
data class Chat(
    val id: String,
    val agent_id: String,
    val title: String,
    val created_at: Long,
    val updated_at: Long,
    val settings: Settings,
    val stats: ChatStats,
)

@Serializable
data class AgentWithChats(
    val agent: Agent,
    val chats: List<Chat>,
)

@Serializable
data class Message(
    val id: Long,
    val chat_id: String,
    val role: String, // "user" | "assistant" | "error"
    val content: String,
    val created_at: Long,
    val reasoning_content: String? = null,
    val is_summary: Boolean = false,
    val duration_ms: Int? = null,
    val total_tokens: Int? = null,
    val prompt_tokens: Int? = null,
    val completion_tokens: Int? = null,
    val format: String = "text", // "text" | "markdown" | "json"
    val branch: Int = 0,
    val facts: String? = null,
)

@Serializable
data class SendMessageResponse(
    val user_message: Message,
    val assistant_message: Message,
)

@Serializable
data class Branch(
    val number: Int,
    val name: String,
    val created_at: Long,
)

// ---- тела запросов ---------------------------------------------------------

@Serializable
data class AgentCreateRequest(val name: String? = null, val model: String? = null)

@Serializable
data class AgentRenameRequest(val name: String)

@Serializable
data class ChatCreateRequest(val title: String? = null)

@Serializable
data class ChatCopyRequest(val title: String)

@Serializable
data class ChatRenameRequest(val title: String)

@Serializable
data class SendMessageRequest(
    val text: String,
    val get_facts: Boolean = false,
    val sliding_window: Boolean = false,
    val autosummary: String = "off",
    val branch: Int? = null,
)

@Serializable
data class StreamSendMessageRequest(
    val text: String,
    val get_facts: Boolean = false,
    val sliding_window: Boolean = false,
    val autosummary: String = "off",
    val branch: Int? = null,
)

@Serializable
data class BulkDeleteRequest(val ids: List<Long>)

@Serializable
data class BranchCreateRequest(val name: String? = null)

/** Один разобранный элемент потока `POST .../messages/stream` (SSE). */
sealed class AgentStreamEvent {
    /** Статус фазы выполнения запроса ("Выполняется запрос к модели",
     * "Обновление фактов" и т.п.) — показывается в чате вместо нейтрального
     * "Модель рассуждает…". */
    data class Status(val status: String) : AgentStreamEvent()
    data class Delta(val content: String, val reasoningContent: String) : AgentStreamEvent()
    data class Done(val message: Message) : AgentStreamEvent()
    data class Error(val message: String) : AgentStreamEvent()
}
