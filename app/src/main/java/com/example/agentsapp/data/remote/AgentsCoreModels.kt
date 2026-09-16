package com.example.agentsapp.data.remote

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

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
    // Тумблер "Разрешить агенту сохранять память" — см. GROUP_MEMORY в
    // SettingsFieldMeta.kt. Значение по умолчанию — на случай ответа от ещё
    // не обновлённого сервера, как и у остальных новых полей ниже.
    val memory_tools_enabled: Boolean = false,
    // Какие слои/типы памяти сейчас включены — обычные поля настроек
    // (группа GROUP_MEMORY в SettingsFieldMeta.kt, экран настроек агента/
    // чата), а не отдельный экран. Все пять по умолчанию false — совпадает
    // с сервером (см. Settings.working_memory_enabled в agents_core/models.py);
    // значение по умолчанию здесь — только на случай ответа от ещё не
    // обновлённого сервера.
    val working_memory_enabled: Boolean = false,
    val long_term_memory_enabled: Boolean = false,
    val episodic_memory_enabled: Boolean = false,
    val semantic_memory_enabled: Boolean = false,
    val procedural_memory_enabled: Boolean = false,
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
    // Профиль по умолчанию для НОВЫХ чатов этого агента (см. PUT
    // /agents/{id}/default-profile) — выбирается в настройках агента из
    // общего справочника профилей (GET /profiles). На уже существующие
    // чаты не влияет, только на момент их создания.
    val default_profile_id: String? = null,
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
    // Id подключённого профиля-пайплайна персонализации, если есть — см.
    // ActiveProfileSetRequest / MemoryScreen.
    val active_profile_id: String? = null,
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

// ---- память и профили-пайплайны (см. agents_core/schemas.py) -------------

@Serializable
data class WorkingMemoryEntry(
    val id: Long,
    val chat_id: String,
    val key: String,
    val value: String,
    val source: String, // "manual" | "agent"
    val created_at: Long,
    val updated_at: Long,
)

@Serializable
data class LongTermMemoryEntry(
    val id: Long,
    val agent_id: String,
    val category: String, // "profile" | "decision" | "knowledge"
    val key: String,
    val value: String,
    val source: String, // "manual" | "agent"
    val created_at: Long,
    val updated_at: Long,
)

// Профили — общий справочник для ВСЕХ агентов (не привязаны к agent_id):
// добавляются/редактируются на отдельном экране "Профили" (доступном с
// главного экрана), а подключаются в настройках агента/чата.
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val style: String? = null,
    val format: String? = null,
    val constraints: String? = null,
    val skills_json: String = "",
    val orchestration_prompt: String? = null,
    val is_default: Boolean = false,
    val created_at: Long,
    val updated_at: Long,
)

@Serializable
data class MemorySnapshotMessage(val role: String, val content: String)

@Serializable
data class MemorySnapshotShortTerm(
    val message_count: Int,
    val messages: List<MemorySnapshotMessage>,
)

@Serializable
data class MemorySnapshot(
    val short_term: MemorySnapshotShortTerm,
    val working_memory: List<WorkingMemoryEntry>,
    val long_term_memory: List<LongTermMemoryEntry>,
    val active_profile: Profile? = null,
    val available_tools: List<JsonObject>,
    val memory_tools_enabled: Boolean,
)

@Serializable
data class WorkingMemorySaveRequest(val key: String, val value: String)

@Serializable
data class LongTermMemorySaveRequest(val category: String, val key: String, val value: String)

@Serializable
data class ProfileCreateRequest(
    val name: String,
    val style: String? = null,
    val format: String? = null,
    val constraints: String? = null,
    val skills_json: String = "",
    // Имена скиллов из GET /skills — сервер сам соберёт skills_json из
    // реестра (см. AGENT_REGISTERED_SKILLS в .env.example AgentsCore).
    // Взаимоисключимо с skills_json — заполняется ровно одно из двух.
    val skill_names: List<String>? = null,
    val orchestration_prompt: String? = null,
)

@Serializable
data class ActiveProfileSetRequest(val profile_id: String? = null)

/** Один скилл из реестра сервиса (`GET /skills`, переменная окружения
 * AGENT_REGISTERED_SKILLS на бэкенде) — из таких пользователь выбирает
 * набор для профиля по имени, не переписывая вручную JSON-схему функции. */
@Serializable
data class RegisteredSkill(
    val name: String,
    val description: String = "",
    val parameters: JsonObject = JsonObject(emptyMap()),
)

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
