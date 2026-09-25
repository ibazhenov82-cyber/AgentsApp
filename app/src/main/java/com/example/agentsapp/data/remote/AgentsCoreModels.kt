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
    // "Отслеживать задачи" (День 13) — см. GROUP_TASKS в SettingsFieldMeta.kt.
    // По умолчанию ВЫКЛЮЧЕНО: пока false, задачи не создаются и не
    // сохраняются, а клиент не должен показывать НИКАКИЕ элементы интерфейса
    // задач (агрегированный блок на карточке агента, ссылка на экране чата,
    // вкладка "Задачи" на экране памяти) — это единственный флаг, которым
    // руководствуется UI, отдельного признака "видно/не видно" сервер не шлёт.
    val task_tracking_enabled: Boolean = false,
    // "Менеджер задач" (обновление "Дня 13") — лимит автономных шагов подряд
    // без участия пользователя, считается на уровне чата в целом (см.
    // комментарий у одноимённого поля в agents_core/models.py). `0` — лимит
    // отключён.
    val task_manager_max_steps: Int = 20,
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
    // Новое ТЗ (интеграция с MCP-сервером) — СПРАВОЧНОЕ поле, только для
    // интерфейса: какие источники инструментов реально дают эффект в этом
    // чате/агенте прямо сейчас — "own"/"memory"/"task"/"skills"/"mcp". НЕ
    // влияет на сам запрос к модели (это уже решено на сервере, tools_json
    // выше — просто как есть). Используется, чтобы не показывать раздел
    // "Инструменты MCP" активным, если "mcp" нет в списке (MCP выключен на
    // уровне сервиса AgentsCore целиком, не только этого чата).
    val tools_sources: List<String> = emptyList(),
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
data class HealthResponse(val status: String)

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
    // Id инвариантов из общего справочника (GET /invariants), выбранных для
    // ЭТОГО агента (PUT /agents/{id}/invariants, множественный выбор) —
    // действуют во ВСЕХ его чатах. В отличие от default_profile_id, НЕ
    // копируется в новые чаты при создании (живая настройка уровня агента).
    val invariant_ids: List<String> = emptyList(),
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
    // То же самое, что Agent.invariant_ids, но уровня ЭТОГО чата (PUT
    // /chats/{id}/invariants) — итоговый набор, который увидит модель,
    // это объединение инвариантов агента и инвариантов самого чата.
    val invariant_ids: List<String> = emptyList(),
    /** Кто создал чат: "app" | "scheduler" (планировщик) — пометка «по расписанию». */
    val source: String = "app",
    /** Идущий сейчас запуск в этом чате (ответ модели или Менеджер задач), если есть. */
    val active_run: RunBrief? = null,
    /** Непрочитанные: ответы ассистента и запросы планировщика новее отметки прочтения. */
    val unread_count: Int = 0,
    /** К нему экран чата прокручивает при открытии (разделитель «Новые сообщения»). */
    val first_unread_message_id: Long? = null,
    val last_read_message_id: Long = 0,
    /** Время последнего сообщения — для сортировки «непрочитанные выше». */
    val last_message_at: Long? = null,
    /** Начало последнего сообщения (до 120 символов) — строка чата на главном экране. */
    val preview: String? = null,
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
    val role: String, // "user" | "assistant"
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
    // JSON-массив переходов состояний задач, применённых моделью ЗА ЭТОТ
    // конкретный ответ (см. Repository.task_events на сервере) — по замечанию
    // пользователя, при отображении смены состояния задачи в чате нужно явно
    // показать, к какой именно задаче она относится (в чате может быть
    // несколько параллельных открытых задач). Каждый элемент десериализуется
    // как TaskEvent (см. ниже). null/пусто — за этот ответ ни одна задача не
    // менялась.
    val task_events: String? = null,
    // "Менеджер задач" — `true`, если это сообщение ассистента
    // сгенерировано автономным шагом (запуск `POST
    // /chats/{chat_id}/tasks/{task_id}/runs`), а не ответом на
    // реальное сообщение пользователя. Клиент показывает такие сообщения с
    // пометкой "Менеджер задач" (см. MessageBadges в ChatScreen.kt).
    val is_task_manager_step: Boolean = false,
    /** "complete" — обычное сообщение; "streaming" — черновик идущего ответа
     * (его показывает DraftBubble по событиям запуска, в списке он скрыт);
     * "cancelled" | "interrupted" | "failed" — остановлен / прерван
     * перезапуском сервера / ошибка (текст в [error]). */
    val status: String = "complete",
    val run_id: String? = null,
    /** JSON-массив вызовов ВСЕХ инструментов за этот ответ ([ToolCallEvent]):
     * MCP, память, задачи, скиллы. null/пусто — инструменты не вызывались. */
    val tool_events: String? = null,
    val error: String? = null,
    /** Кто отправил сообщение пользователя: "app" | "scheduler". */
    val source: String = "app",
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

// ---- День 14. Инварианты и ограничения состояния --------------------------
//
// Инварианты — общий справочник для ВСЕХ агентов/чатов (не привязаны к
// agent_id/chat_id), по аналогии с Profile выше: добавляются/редактируются
// на отдельном экране "Инварианты" (доступном с главного экрана, там же,
// где "Профили"), а подключаются МНОЖЕСТВЕННЫМ выбором в настройках
// агента/чата (Agent.invariant_ids/Chat.invariant_ids выше).

/** Метки kind — прямое отражение примеров из ТЗ + отдельная категория для
 * правил самой стейт-машины задач. Необязательны, влияют только на подпись
 * в списке/форме, не на логику соблюдения. */
val INVARIANT_KIND_OPTIONS = listOf("architecture", "tech_decision", "stack_constraint", "business_rule", "state_machine_rule")

val INVARIANT_KIND_LABELS = mapOf(
    "architecture" to "Архитектура",
    "tech_decision" to "Техническое решение",
    "stack_constraint" to "Ограничение по стеку",
    "business_rule" to "Бизнес-правило",
    "state_machine_rule" to "Правило стейт-машины",
)

@Serializable
data class Invariant(
    val id: String,
    val title: String,
    val rule_text: String,
    val kind: String? = null,
    val is_active: Boolean = true,
    val created_at: Long,
    val updated_at: Long,
)

@Serializable
data class InvariantCreateRequest(
    val title: String,
    val rule_text: String,
    val kind: String? = null,
    val is_active: Boolean = true,
)

@Serializable
data class InvariantIdsSetRequest(val invariant_ids: List<String> = emptyList())

/** Один скилл из реестра сервиса (`GET /skills`, переменная окружения
 * AGENT_REGISTERED_SKILLS на бэкенде) — из таких пользователь выбирает
 * набор для профиля по имени, не переписывая вручную JSON-схему функции. */
@Serializable
data class RegisteredSkill(
    val name: String,
    val description: String = "",
    val parameters: JsonObject = JsonObject(emptyMap()),
)

// ---- День 13/15. Состояние задачи (Task State Machine) ---------------------
//
// Стейт-машина задач теперь ЖЁСТКО зашита на сервере (4 состояния: planning →
// execution → validation → done, planning только в execution, execution в
// validation или обратно в planning, validation в done или обратно в
// execution, done — финальное) — никакого редактируемого справочника
// состояний/действий/машин больше нет. Экран "Модели состояний задач"
// (см. ui/taskmachines/) стал ПРОСТЫМ READ-ONLY просмотром состояний плюс
// управлением набором прикреплённых к машине инвариантов (единственное, что
// реально настраивается). Явное действие "Отклонить" убрано из системы
// целиком — единственное ручное действие человека, оставшееся в принципе,
// это "Пауза"; продвижение задачи всегда делает модель через tool call. Сама
// задача (Task) владеется чатом (chat_id) — в одном чате может быть несколько
// задач.

/** Один элемент `GET /task-state-machine` — фиксированное описание одного из
 * 4 состояний (planning/execution/validation/done) и того, куда из него можно
 * перейти. */
@Serializable
data class TaskStateInfo(
    val position: Int,
    val state: String,
    val display_name: String,
    val target_states: List<String> = emptyList(),
    val target_state_display_names: List<String> = emptyList(),
)

/** Ответ `GET /task-state-machine` и `PUT /task-state-machine/invariants` —
 * состояния машины (только для чтения) + текущий набор прикреплённых
 * инвариантов вида `kind == "state_machine_rule"` (единственное, что здесь
 * реально редактируется). */
@Serializable
data class TaskStateMachineInfo(
    val states: List<TaskStateInfo> = emptyList(),
    val invariants: List<Invariant> = emptyList(),
)

@Serializable
data class TaskMachineInvariantIdsSetRequest(val invariant_ids: List<String> = emptyList())

/** Одна строка в списке задач чата/агента — в т.ч. в агрегированном блоке
 * "Задачи" на карточке агента (см. AgentCard в MainScreen.kt). */
@Serializable
data class TaskSummary(
    val id: String,
    val chat_id: String,
    val title: String,
    val state: String,
    val state_display_name: String,
    val paused: Boolean = false,
    val status: String, // "active" | "paused" | "done" — вычисляемое, никогда не хранится
    val status_display: String, // "активна" | "на паузе" | "завершена"
    // Отображаемое имя состояния, в которое продвинет задачу модель из
    // текущего состояния — для карточки-подтверждения в чате ("Задача: …,
    // следующий этап: …"); null, если задача уже завершена (done).
    val next_state_display_name: String? = null,
    val current_step: String? = null,
    val created_at: Long,
    val updated_at: Long,
    // Родительский чат — заполнен в списке по агенту (GET /agents/{id}/tasks);
    // по замечанию пользователя обязателен при нескольких чатах у агента.
    val chat_title: String? = null,
)

/** Один этап на степпере экрана задачи (в порядке позиции в списке — см.
 * `TaskDetail.stages`). */
@Serializable
data class TaskStage(
    val state: String,
    val display_name: String,
    val is_current: Boolean,
    val is_final: Boolean,
    // "check" — этап уже пройден; "pause" — текущий этап, и задача сейчас на
    // паузе; "none" — этап ещё не достигнут.
    val icon: String,
)

/** Один из действий, доступных из текущего состояния — "advance" (продвижение
 * моделью через tool call, показывается только для контекста/отладки — кнопки
 * "Продолжить"/"Выполнить" не выбирают конкретное целевое состояние, это
 * решает модель) или "pause" (единственное оставшееся ручное действие). */
@Serializable
data class TaskAvailableAction(
    val kind: String, // "advance" | "pause"
    val to_state: String,
    val to_state_display_name: String,
)

@Serializable
data class TaskHistoryEntry(
    val id: Long,
    val from_state: String,
    val from_state_display_name: String,
    val to_state: String,
    val to_state_display_name: String,
    val kind: String, // "advance" | "pause" | "resume" (resume практически не встречается)
    val applied_by: String, // "system" | "agent" | "manual"
    val note: String? = null,
    val created_at: Long,
)

/** Полные детали задачи для экрана "Задача" — везде отображаемые имена, а не
 * системные. */
@Serializable
data class TaskDetail(
    val id: String,
    val chat_id: String,
    val title: String,
    val state: String,
    val state_display_name: String,
    val paused: Boolean = false,
    val status: String,
    val status_display: String,
    val next_state_display_name: String? = null,
    val plan: List<String> = emptyList(),
    val done: List<String> = emptyList(),
    val current: String? = null,
    // step/total — позиция текущего состояния в машине состояний (1..4) и
    // общее число состояний (всегда 4), СМ. ТЗ по шаблону system prompt
    // "Менеджера задач" (task/state/step/total/plan/done/current) — это уже
    // НЕ "сколько шагов плана пройдено", как было раньше.
    val step: Int = 0,
    val total: Int = 0,
    val created_at: Long,
    val updated_at: Long,
    val stages: List<TaskStage>,
    val available_actions: List<TaskAvailableAction>,
    val history: List<TaskHistoryEntry>,
)

/** Один элемент `Message.task_events` (см. поле выше) — привязывает переход
 * состояния, показанный в чате, к конкретной задаче. Раньше здесь были поля
 * `action_display_name`/`action_kind` (справочник действий) — новая
 * (жёстко зашитая) машина состояний отдаёт вместо них только `kind`
 * ("advance" — этап реально сменился, "update" — обновились только
 * current_step/done_steps без смены этапа); ни то, ни другое поле сейчас в
 * UI не используется (см. `TaskEventChips` в ChatScreen.kt), но само имя
 * поля должно совпадать с JSON сервера, иначе декодирование падает
 * целиком (см. `Repository._handle_apply_task_action`/`_handle_start_task`
 * на сервере — единственный источник этого JSON). */
@Serializable
data class TaskEvent(
    val task_id: String,
    val task_title: String,
    val from_state_display_name: String? = null,
    val to_state_display_name: String,
    val kind: String? = null,
)

// ---- тела запросов ----------------------------------------------------------

/** Ручное вмешательство человека, БЕЗ обращения к модели — единственное
 * оставшееся ручное действие: "Пауза" (`action` == "pause", любое другое
 * значение сервер отвечает ошибкой 400). Кнопки "Продолжить"/"Выполнить"
 * ВСЕГДА обращаются к модели — это запуск Менеджера задач
 * (`POST /chats/{chat_id}/tasks/{task_id}/runs`). */
@Serializable
data class TaskManualActionRequest(val action: String, val note: String? = null)
