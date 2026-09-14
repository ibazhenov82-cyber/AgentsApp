package com.example.agentsapp.data.remote

/**
 * Метаданные полей настроек — единый источник правды для формы настроек:
 * системное имя (совпадает с именем поля [Settings]/[DefaultSettings] и с
 * ключом в JSON), заголовок для интерфейса, группа для визуальной
 * компоновки и тип поля ввода. Порядок списков — как в техническом задании.
 */

enum class FieldType {
    MODEL_PICKER,
    MULTILINE_STRING,
    FLOAT_SLIDER,
    NULLABLE_INT,
    NULLABLE_FLOAT,
    INT,
    BOOLEAN,
    ENUM,
    NULLABLE_ENUM,
    STOP_SEQUENCES,
    TOOLS_JSON,
}

data class SettingsFieldDef(
    val sysName: String,
    val title: String,
    val group: String,
    val type: FieldType,
    val options: List<String> = emptyList(),
    val sliderRange: ClosedFloatingPointRange<Float> = 0f..1f,
    /** Поле показывается только если [autosummary] в текущих настройках равно этому значению; null — показывать всегда. */
    val visibleWhenAutosummary: String? = null,
    /** Поле показывается только если `context_strategy` в текущих настройках не null (используется для `context_strategy_limit`). */
    val visibleWhenContextStrategySet: Boolean = false,
    /** Поле показывается только если `context_strategy` равно этому значению (используется для `extraction_system_prompt`). */
    val visibleWhenContextStrategy: String? = null,
    /** Человекочитаемые подписи для значений [options] (по-русски) — если не задано, используется само значение. */
    val optionLabels: Map<String, String> = emptyMap(),
)

val THINKING_EFFORT_OPTIONS = listOf("low", "high", "max")
val TOOL_CHOICE_OPTIONS = listOf("auto", "none", "required")
val AUTOSUMMARY_OPTIONS = listOf("off", "messages", "tokens")
val CONTEXT_STRATEGY_OPTIONS = listOf("sliding_window", "sticky_facts")

/** Русские подписи для выпадающего списка "Автоматическая суммаризация чата". */
val AUTOSUMMARY_OPTION_LABELS = mapOf(
    "off" to "не задано",
    "messages" to "по сообщениям",
    "tokens" to "по токенам",
)

/** Русские подписи для выпадающего списка "Стратегия" управления контекстом. */
val CONTEXT_STRATEGY_OPTION_LABELS = mapOf(
    "sliding_window" to "Sliding Window",
    "sticky_facts" to "Sticky Facts",
)

const val GROUP_CONNECTION = "Соединение с сервером"
const val GROUP_MODEL = "Модель"
const val GROUP_RESPONSE = "Параметры ответа"
const val GROUP_TOOLS = "Инструменты"
const val GROUP_SUMMARIZATION = "Суммаризация запросов"
const val GROUP_CONTEXT_STRATEGY = "Стратегии управления контекстом"
const val GROUP_EXTRA = "Дополнительно"

val DEFAULT_SETTINGS_FIELDS: List<SettingsFieldDef> = listOf(
    SettingsFieldDef("model", "Модель", GROUP_MODEL, FieldType.MODEL_PICKER),
    SettingsFieldDef("system_prompt", "Системный prompt", GROUP_MODEL, FieldType.MULTILINE_STRING),
    SettingsFieldDef("temperature", "Температура", GROUP_MODEL, FieldType.FLOAT_SLIDER, sliderRange = 0f..2f),
    SettingsFieldDef("top_p", "top_p", GROUP_MODEL, FieldType.FLOAT_SLIDER, sliderRange = 0f..1f),
    SettingsFieldDef("seed", "Начальное число (seed)", GROUP_MODEL, FieldType.NULLABLE_INT),
    SettingsFieldDef("stream", "Потоковые ответы", GROUP_RESPONSE, FieldType.BOOLEAN),
    SettingsFieldDef("thinking_enabled", "Включить рассуждения", GROUP_RESPONSE, FieldType.BOOLEAN),
    SettingsFieldDef("reasoning_effort", "Уровень рассуждений", GROUP_RESPONSE, FieldType.NULLABLE_ENUM, options = THINKING_EFFORT_OPTIONS),
    SettingsFieldDef("summary_system_prompt", "Системный prompt для суммаризации", GROUP_SUMMARIZATION, FieldType.MULTILINE_STRING),
    SettingsFieldDef("summary_prompt", "Шаблон prompt пользователя", GROUP_SUMMARIZATION, FieldType.MULTILINE_STRING),
    SettingsFieldDef("include_usage_in_stream", "Показ токенов при потоковых ответах", GROUP_EXTRA, FieldType.BOOLEAN),
)

/** Используется и для настроек агента, и для настроек чата — на экране
 * настроек чата поле `model` рендерится как нередактируемое (см. параметр
 * `modelEditable` экрана настроек). */
val AGENT_SETTINGS_FIELDS: List<SettingsFieldDef> = listOf(
    SettingsFieldDef("model", "Модель", GROUP_MODEL, FieldType.MODEL_PICKER),
    SettingsFieldDef("system_prompt", "Системный prompt", GROUP_MODEL, FieldType.MULTILINE_STRING),
    SettingsFieldDef("temperature", "Температура", GROUP_MODEL, FieldType.FLOAT_SLIDER, sliderRange = 0f..2f),
    SettingsFieldDef("top_p", "top_p", GROUP_MODEL, FieldType.FLOAT_SLIDER, sliderRange = 0f..1f),
    SettingsFieldDef("seed", "Начальное число (seed)", GROUP_MODEL, FieldType.NULLABLE_INT),
    SettingsFieldDef("stream", "Потоковые ответы", GROUP_RESPONSE, FieldType.BOOLEAN),
    SettingsFieldDef("thinking_enabled", "Включить рассуждения", GROUP_RESPONSE, FieldType.BOOLEAN),
    SettingsFieldDef("reasoning_effort", "Уровень рассуждений", GROUP_RESPONSE, FieldType.NULLABLE_ENUM, options = THINKING_EFFORT_OPTIONS),
    SettingsFieldDef("max_tokens", "Максимальное число токенов", GROUP_RESPONSE, FieldType.NULLABLE_INT),
    SettingsFieldDef("json_mode", "Ответ в JSON", GROUP_RESPONSE, FieldType.BOOLEAN),
    SettingsFieldDef("stop_sequences", "Стоп-последовательности (через запятую)", GROUP_RESPONSE, FieldType.STOP_SEQUENCES),
    SettingsFieldDef("tool_choice", "Выбор функции", GROUP_TOOLS, FieldType.ENUM, options = TOOL_CHOICE_OPTIONS),
    SettingsFieldDef("tools_json", "Список функций в формате OpenAI", GROUP_TOOLS, FieldType.TOOLS_JSON),
    SettingsFieldDef("summary_system_prompt", "Системный prompt для суммаризации", GROUP_SUMMARIZATION, FieldType.MULTILINE_STRING),
    SettingsFieldDef("summary_prompt", "Шаблон prompt пользователя", GROUP_SUMMARIZATION, FieldType.MULTILINE_STRING),
    SettingsFieldDef(
        "autosummary", "Автоматическая суммаризация чата", GROUP_SUMMARIZATION, FieldType.ENUM,
        options = AUTOSUMMARY_OPTIONS, optionLabels = AUTOSUMMARY_OPTION_LABELS,
    ),
    SettingsFieldDef("autosummary_by_messages", "Предел числа сообщений", GROUP_SUMMARIZATION, FieldType.INT, visibleWhenAutosummary = "messages"),
    SettingsFieldDef("autosummary_by_tokens", "Предел числа токенов", GROUP_SUMMARIZATION, FieldType.INT, visibleWhenAutosummary = "tokens"),
    SettingsFieldDef(
        "context_strategy", "Стратегия", GROUP_CONTEXT_STRATEGY, FieldType.NULLABLE_ENUM,
        options = CONTEXT_STRATEGY_OPTIONS, optionLabels = CONTEXT_STRATEGY_OPTION_LABELS,
    ),
    SettingsFieldDef("context_strategy_limit", "Предел числа сообщений", GROUP_CONTEXT_STRATEGY, FieldType.NULLABLE_INT, visibleWhenContextStrategySet = true),
    SettingsFieldDef(
        "extraction_system_prompt", "Системный prompt для извлечения фактов", GROUP_CONTEXT_STRATEGY, FieldType.MULTILINE_STRING,
        visibleWhenContextStrategy = "sticky_facts",
    ),
    SettingsFieldDef("include_usage_in_stream", "Показывать токены при потоковых ответах", GROUP_EXTRA, FieldType.BOOLEAN),
    SettingsFieldDef("logprobs", "Показывать вероятности появления токенов", GROUP_EXTRA, FieldType.BOOLEAN),
    SettingsFieldDef("frequency_penalty", "Штраф за частоту", GROUP_EXTRA, FieldType.NULLABLE_FLOAT),
    SettingsFieldDef("presence_penalty", "Штраф за присутствие", GROUP_EXTRA, FieldType.NULLABLE_FLOAT),
)

/** Порядок групп на экране настроек. [GROUP_CONNECTION] используется только на
 * экране настроек по умолчанию (единственном экране уровня приложения, а не
 * сервера) и должен идти самым первым — раньше блока "Модель". */
val SETTINGS_GROUP_ORDER = listOf(
    GROUP_CONNECTION, GROUP_MODEL, GROUP_RESPONSE, GROUP_TOOLS,
    GROUP_SUMMARIZATION, GROUP_CONTEXT_STRATEGY, GROUP_EXTRA,
)

fun readSettingsField(settings: Settings, sysName: String): Any? = when (sysName) {
    "model" -> settings.model
    "system_prompt" -> settings.system_prompt
    "temperature" -> settings.temperature
    "top_p" -> settings.top_p
    "seed" -> settings.seed
    "stream" -> settings.stream
    "thinking_enabled" -> settings.thinking_enabled
    "reasoning_effort" -> settings.reasoning_effort
    "max_tokens" -> settings.max_tokens
    "json_mode" -> settings.json_mode
    "stop_sequences" -> settings.stop_sequences
    "tool_choice" -> settings.tool_choice
    "tools_json" -> settings.tools_json
    "summary_prompt" -> settings.summary_prompt
    "summary_system_prompt" -> settings.summary_system_prompt
    "autosummary" -> settings.autosummary
    "autosummary_by_messages" -> settings.autosummary_by_messages
    "autosummary_by_tokens" -> settings.autosummary_by_tokens
    "context_strategy" -> settings.context_strategy
    "context_strategy_limit" -> settings.context_strategy_limit
    "extraction_system_prompt" -> settings.extraction_system_prompt
    "include_usage_in_stream" -> settings.include_usage_in_stream
    "logprobs" -> settings.logprobs
    "frequency_penalty" -> settings.frequency_penalty
    "presence_penalty" -> settings.presence_penalty
    else -> null
}

fun readDefaultSettingsField(settings: DefaultSettings, sysName: String): Any? = when (sysName) {
    "model" -> settings.model
    "system_prompt" -> settings.system_prompt
    "temperature" -> settings.temperature
    "top_p" -> settings.top_p
    "seed" -> settings.seed
    "stream" -> settings.stream
    "thinking_enabled" -> settings.thinking_enabled
    "reasoning_effort" -> settings.reasoning_effort
    "summary_prompt" -> settings.summary_prompt
    "summary_system_prompt" -> settings.summary_system_prompt
    "include_usage_in_stream" -> settings.include_usage_in_stream
    else -> null
}
