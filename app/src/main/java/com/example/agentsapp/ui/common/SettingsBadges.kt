package com.example.agentsapp.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.agentsapp.data.remote.CONTEXT_STRATEGY_OPTION_LABELS
import com.example.agentsapp.data.remote.Settings
import com.example.agentsapp.ui.theme.BadgeChatBorder
import com.example.agentsapp.ui.theme.BadgeChatBorderAndText
import com.example.agentsapp.ui.theme.BadgeChatFill
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Единая сводка настроек ([Settings]) агента или чата — один ряд бейджей, в
 * том же порядке, что и на экране настроек (см. `AGENT_SETTINGS_FIELDS` в
 * `SettingsFieldMeta.kt`): температура+top_p (общим бейджем) → seed →
 * потоковые ответы → рассуждения → уровень рассуждений → максимальное число
 * токенов → ответ в JSON → стоп-последовательности → автоматическая
 * суммаризация → стратегия управления контекстом → сохранение памяти
 * агентом (с перечислением включённых типов памяти) → задачи → инварианты →
 * инструменты → профиль (в этом же ряду, а не отдельным блоком). Все бейджи —
 * одного вида (по замечанию пользователя): заливка — цвет пузыря сообщения
 * пользователя в чате ([BadgeChatFill]), обводка и текст — тёмный, близкий к
 * чёрному вариант того же тона ([BadgeChatBorderAndText]).
 *
 * Два режима:
 * - [baselineSettings] == null (карточка агента, экран чата) — показывается
 *   всё, что реально задано/включено в [settings], как и раньше; температура/
 *   top_p — всегда первым бейджем, безусловно.
 * - [baselineSettings] != null (строка чата в списке агентов на главном
 *   экране — настройки агента-владельца, по замечанию пользователя) —
 *   бейдж настройки показывается, только если её значение в [settings]
 *   отличается от [baselineSettings]; если чат явно ВЫКЛЮЧИЛ/сбросил
 *   настройку, которая у агента включена/задана, это показывается явно
 *   (например "Потоковые ответы (Выкл.)"), а не молчаливым исчезновением
 *   бейджа — иначе расхождение с агентом было бы не видно.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSummary(
    settings: Settings,
    baselineSettings: Settings? = null,
    // Инварианты (ТЗ "Инварианты" — тумблер `invariants_enabled` убран):
    // считаются разрешёнными сами по себе, если для этого агента/чата
    // выбран хотя бы один инвариант (`Agent.invariant_ids`/`Chat.invariant_ids`,
    // само поле не входит в Settings) — bool передаёт вызывающий экран.
    hasInvariants: Boolean = false,
    profileName: String? = null,
    modifier: Modifier = Modifier,
) {
    // Один цвет на все бейджи — заливка как у пузыря сообщения пользователя
    // в чате, обводка и текст — тёмный вариант того же тона (по замечанию
    // пользователя); фиксированный, не зависит от темы приложения и не
    // завязан на MaterialTheme.colorScheme, чтобы не задевать другие
    // элементы (FAB, кнопки), использующие роли primary/secondary/tertiary.
    val badgeColor = BadgeChatFill
    val onBadgeColor = BadgeChatBorderAndText
    val primary = badgeColor
    val onPrimary = onBadgeColor
    val secondary = badgeColor
    val onSecondary = onBadgeColor
    val tertiary = badgeColor
    val onTertiary = onBadgeColor
    val neutral = badgeColor
    val onNeutral = onBadgeColor
    val hasBaseline = baselineSettings != null

    val badges = buildList<@Composable () -> Unit> {
        diffBadgeText(
            settings.temperature to settings.top_p,
            baselineSettings?.let { it.temperature to it.top_p },
            hasBaseline,
            { (temperature, topP) ->
                "Температура: ${formatSettingsNumber(temperature)} · top_p: ${formatSettingsNumber(topP)}"
            },
            null,
        )?.let { text -> add { SettingsBadgeChip(text, primary, onPrimary) } }

        diffBadgeText(
            settings.seed, baselineSettings?.seed, hasBaseline,
            { seed -> seed?.let { "Начальное число (seed): $it" } },
            "Начальное число (seed): не задано",
        )?.let { text -> add { SettingsBadgeChip(text, neutral, onNeutral) } }

        diffBadgeText(
            settings.stream, baselineSettings?.stream, hasBaseline,
            { if (it) "Потоковые ответы" else null },
            "Потоковые ответы (Выкл.)",
        )?.let { text -> add { SettingsBadgeChip(text, neutral, onNeutral) } }

        diffBadgeText(
            settings.thinking_enabled, baselineSettings?.thinking_enabled, hasBaseline,
            { if (it) "Рассуждения" else null },
            "Рассуждения (Выкл.)",
        )?.let { text -> add { SettingsBadgeChip(text, neutral, onNeutral) } }

        diffBadgeText(
            settings.reasoning_effort, baselineSettings?.reasoning_effort, hasBaseline,
            { effort -> effort?.let { "Уровень рассуждений: $it" } },
            "Уровень рассуждений: не задан",
        )?.let { text -> add { SettingsBadgeChip(text, neutral, onNeutral) } }

        diffBadgeText(
            settings.max_tokens, baselineSettings?.max_tokens, hasBaseline,
            { max -> max?.let { "Максимальное число токенов: $it" } },
            "Максимальное число токенов: не задано",
        )?.let { text -> add { SettingsBadgeChip(text, neutral, onNeutral) } }

        diffBadgeText(
            settings.json_mode, baselineSettings?.json_mode, hasBaseline,
            { if (it) "Ответ в JSON" else null },
            "Ответ в JSON (Выкл.)",
        )?.let { text -> add { SettingsBadgeChip(text, neutral, onNeutral) } }

        diffBadgeText(
            settings.stop_sequences, baselineSettings?.stop_sequences, hasBaseline,
            { seqs -> if (seqs.isNotEmpty()) "Стоп-последовательности: ${seqs.joinToString(", ")}" else null },
            "Стоп-последовательности: нет",
        )?.let { text -> add { SettingsBadgeChip(text, neutral, onNeutral) } }

        diffBadgeText(
            Triple(settings.autosummary, settings.autosummary_by_messages, settings.autosummary_by_tokens),
            baselineSettings?.let { Triple(it.autosummary, it.autosummary_by_messages, it.autosummary_by_tokens) },
            hasBaseline,
            { (mode, byMessages, byTokens) ->
                if (mode == "off") {
                    null
                } else {
                    val (count, unit) = if (mode == "tokens") byTokens to "токенов" else byMessages to "сообщений"
                    "Автоматическая суммаризация ($count $unit)"
                }
            },
            "Автоматическая суммаризация выключена",
        )?.let { text -> add { SettingsBadgeChip(text, tertiary, onTertiary) } }

        diffBadgeText(
            settings.context_strategy to settings.context_strategy_limit,
            baselineSettings?.let { it.context_strategy to it.context_strategy_limit },
            hasBaseline,
            { (strategy, limit) ->
                strategy?.let {
                    val name = CONTEXT_STRATEGY_OPTION_LABELS[it] ?: it
                    if (limit != null) "Стратегия $name ($limit сообщений)" else "Стратегия $name"
                }
            },
            "Стратегия управления контекстом: не задана",
        )?.let { text -> add { SettingsBadgeChip(text, secondary, onSecondary) } }

        diffBadgeText(
            memoryFlags(settings),
            baselineSettings?.let { memoryFlags(it) },
            hasBaseline,
            { flags -> if (flags[0]) memoryBadgeText(settings) else null },
            "Память: выключена",
        )?.let { text -> add { SettingsBadgeChip(text, tertiary, onTertiary) } }

        // "Задачи" и "Инварианты" — по аналогии с остальными настройками
        // (замечание пользователя: на главном экране не было бейджа по
        // настройке "Задачи" в списке агентов/чатов, по аналогии с другими
        // настройками); идут сразу после бейджа памяти, перед бейджем профиля.
        diffBadgeText(
            settings.task_tracking_enabled, baselineSettings?.task_tracking_enabled, hasBaseline,
            { if (it) "Задачи" else null },
            "Задачи (Выкл.)",
        )?.let { text -> add { SettingsBadgeChip(text, tertiary, onTertiary) } }

        // Не через diffBadgeText: в отличие от прежнего булева тумблера,
        // выбор инвариантов чата — НЕ копия/переопределение выбора агента
        // (независимые списки, объединяются на лету), так что "изменилось
        // относительно агента" здесь не имеет смысла — просто показываем,
        // если у ЭТОГО владельца (агента или чата) выбран хотя бы один.
        if (hasInvariants) {
            add { SettingsBadgeChip("Инварианты", tertiary, onTertiary) }
        }

        // "Инструменты" (по замечанию пользователя) — если в настройках
        // выбран хотя бы один инструмент (`tools_json`: выбор в «Выбрать из
        // доступных» или ручной JSON). В строке чата в списке агентов —
        // по той же логике, что и остальные настройки: только если набор
        // инструментов чата отличается от набора агента; если чат их убрал,
        // а у агента они есть — "Инструменты (Выкл.)". Сравнивается набор
        // имён функций, а не сырой текст JSON (иначе разное форматирование
        // одного и того же выбора считалось бы отличием).
        diffBadgeText(
            toolsKey(settings.tools_json),
            baselineSettings?.let { toolsKey(it.tools_json) },
            hasBaseline,
            { if (hasConfiguredTools(settings.tools_json)) "Инструменты" else null },
            "Инструменты (Выкл.)",
        )?.let { text -> add { SettingsBadgeChip(text, tertiary, onTertiary) } }

        // Профиль — сразу после бейджа памяти, справа от него, в том же ряду
        // (по замечанию пользователя), а не отдельным блоком выше/ниже; не
        // участвует в diff-сравнении с агентом.
        if (profileName != null) add { ProfileBadge(name = profileName) }
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        badges.forEach { badge -> badge() }
    }
}

private val toolsJsonParser = Json { ignoreUnknownKeys = true }

/** Выбран ли в `tools_json` хотя бы один инструмент: непустой JSON-массив.
 * "[]" (так «Выбрать из доступных» кодирует "ничего не выбрано") и пустая
 * строка — нет. Невалидный, но непустой текст считаем настроенным: сервер
 * всё равно попытается его использовать (и вернёт ошибку), скрывать это
 * бейджем было бы неверно. */
fun hasConfiguredTools(toolsJson: String): Boolean {
    if (toolsJson.isBlank()) return false
    val parsed = runCatching { toolsJsonParser.parseToJsonElement(toolsJson) }.getOrNull() ?: return true
    return (parsed as? JsonArray)?.isNotEmpty() ?: true
}

/** Ключ сравнения выбора инструментов чата и агента: множество имён
 * функций из `tools_json`; невалидный JSON — сам текст (без пробелов по
 * краям); пусто/"[]" — пустое множество. */
private fun toolsKey(toolsJson: String): Any {
    if (!hasConfiguredTools(toolsJson)) return emptySet<String>()
    val array = runCatching { toolsJsonParser.parseToJsonElement(toolsJson) }.getOrNull() as? JsonArray
        ?: return toolsJson.trim()
    return array.mapNotNull { tool ->
        val function = (tool as? JsonObject)?.get("function") as? JsonObject
        (function?.get("name") as? JsonPrimitive)?.contentOrNull ?: tool.toString()
    }.toSet()
}

/** Значения пяти флагов памяти (родительский тумблер + пять типов/слоёв) —
 * одним списком, чтобы одним сравнением `==` решить, отличается ли весь
 * набор памяти чата от набора агента (см. [diffBadgeText]). */
private fun memoryFlags(settings: Settings): List<Boolean> = listOf(
    settings.memory_tools_enabled,
    settings.working_memory_enabled,
    settings.long_term_memory_enabled,
    settings.episodic_memory_enabled,
    settings.semantic_memory_enabled,
    settings.procedural_memory_enabled,
)

/** Текст бейджа-настройки с поддержкой обычного и "diff"-режима:
 * - [hasBaseline] == false — бейдж строится как раньше, только по
 *   [currentValue] (через [textWhenSet]; `null` — не показывать).
 * - [hasBaseline] == true — бейдж показывается, только если [currentValue]
 *   отличается от [baselineValue]; если при этом [textWhenSet] всё равно
 *   вернул `null` (настройка отличается тем, что чат её выключил/сбросил),
 *   используется явный [textWhenUnset] вместо того, чтобы бейдж просто не
 *   появился — иначе расхождение с настройками агента было бы не видно. */
private fun <T> diffBadgeText(
    currentValue: T,
    baselineValue: T?,
    hasBaseline: Boolean,
    textWhenSet: (T) -> String?,
    textWhenUnset: String?,
): String? {
    if (!hasBaseline) return textWhenSet(currentValue)
    if (currentValue == baselineValue) return null
    return textWhenSet(currentValue) ?: textWhenUnset
}

/** Текст бейджа "Память: ..." — перечисляет включённые типы/слои памяти в
 * том же порядке, что и на экране настроек (группа "Память" в
 * `SettingsFieldMeta.kt`): рабочая → долговременная → эпизодическая →
 * семантическая → процедурная. Показывается, только когда
 * `memory_tools_enabled == true` (см. вызов в [SettingsSummary]); если сама
 * настройка включена, а ни один тип почему-то не выбран (старые данные с
 * сервера) — короткая заглушка вместо пустого перечисления. */
private fun memoryBadgeText(settings: Settings): String {
    val enabledTypes = buildList {
        if (settings.working_memory_enabled) add("Рабочая")
        if (settings.long_term_memory_enabled) add("Долговременная")
        if (settings.episodic_memory_enabled) add("Эпизодическая")
        if (settings.semantic_memory_enabled) add("Семантическая")
        if (settings.procedural_memory_enabled) add("Процедурная")
    }
    return if (enabledTypes.isEmpty()) "Память: включена" else "Память: ${enabledTypes.joinToString(", ")}"
}

/** Бейдж подключённого профиля-пайплайна персонализации — тот же вид, что и
 * у остальных бейджей настроек (см. [BadgeChatFill]/[BadgeChatBorderAndText]
 * в Color.kt), чтобы все бейджи выглядели единообразно. Рисуется внутри
 * общего ряда бейджей [SettingsSummary] (сразу после бейджа памяти);
 * решение, показывать ли бейдж вообще (профиль подключён или нет),
 * принимает [SettingsSummary]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileBadge(name: String, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = {},
        modifier = modifier,
        label = { Text("Профиль: $name", style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = BadgeChatFill,
            labelColor = BadgeChatBorderAndText,
        ),
        border = BorderStroke(1.dp, BadgeChatBorder),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsBadgeChip(text: String, color: Color, labelColor: Color) {
    AssistChip(
        onClick = {},
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(containerColor = color, labelColor = labelColor),
        // Обводка того же тона, что и текст, но вдвое светлее (по замечанию
        // пользователя) — а не стандартная AssistChip-обводка и не тот же
        // тёмный цвет, что у текста.
        border = BorderStroke(1.dp, BadgeChatBorder),
    )
}

/** Компактное форматирование числа с плавающей точкой для бейджей/текста
 * настроек: целые значения — без дробной части (`1` вместо `1.0`), иначе —
 * не более двух знаков после запятой без хвостовых нулей (`0.7`, не `0.70`). */
private fun formatSettingsNumber(value: Double): String {
    if (value == value.toLong().toDouble()) return value.toLong().toString()
    return "%.2f".format(value).trimEnd('0').trimEnd('.')
}
