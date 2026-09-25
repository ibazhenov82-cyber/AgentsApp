package com.example.agentsapp.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
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
import java.util.Locale

/*
 * Бейджи настроек агента/чата.
 *
 * Бейджи компактные (метки, а не кнопки-чипы Material 3 высотой 32dp):
 * высота ~20dp, шрифт labelSmall, отступы 6×2dp. По умолчанию показываются в
 * ОДНУ строку — сколько поместится, остальные сворачиваются в «+N»; нажатие
 * на «+N» раскрывает все бейджи с переносом, «Свернуть» — обратно.
 */

private val BadgeSpacing: Dp = 4.dp
private val BadgeShape = RoundedCornerShape(6.dp)

/**
 * Тексты бейджей настроек в том же порядке, что и на экране настроек:
 * температура+top_p → seed → потоковые ответы → рассуждения → уровень
 * рассуждений → максимум токенов → JSON → стоп-последовательности →
 * автосуммаризация → стратегия контекста → память → задачи → инварианты →
 * инструменты → профиль.
 *
 * Два режима:
 * - [baseline] == null (экран чата) — всё, что задано/включено в [settings];
 * - [baseline] != null — только настройки, отличающиеся от [baseline]
 *   (строка чата — от настроек агента, карточка агента — от настроек, которые
 *   получил бы новый агент). Если настройка выключена/сброшена относительно
 *   базы, это показывается явно («Потоковые ответы (Выкл.)»).
 */
fun settingsBadgeTexts(
    settings: Settings,
    baseline: Settings? = null,
    // Инварианты выбираются отдельным списком (не поле Settings) и не
    // наследуются, поэтому показываются, если у владельца выбран хотя бы один.
    hasInvariants: Boolean = false,
    profileName: String? = null,
): List<String> = buildList {
    val hasBaseline = baseline != null

    diffBadgeText(
        settings.temperature to settings.top_p,
        baseline?.let { it.temperature to it.top_p },
        hasBaseline,
        { (temperature, topP) ->
            "Температура: ${formatSettingsNumber(temperature)} · top_p: ${formatSettingsNumber(topP)}"
        },
        null,
    )?.let { add(it) }

    diffBadgeText(
        settings.seed, baseline?.seed, hasBaseline,
        { seed -> seed?.let { "Seed: $it" } },
        "Seed: не задан",
    )?.let { add(it) }

    diffBadgeText(
        settings.stream, baseline?.stream, hasBaseline,
        { if (it) "Потоковые ответы" else null },
        "Потоковые ответы (Выкл.)",
    )?.let { add(it) }

    diffBadgeText(
        settings.thinking_enabled, baseline?.thinking_enabled, hasBaseline,
        { if (it) "Рассуждения" else null },
        "Рассуждения (Выкл.)",
    )?.let { add(it) }

    diffBadgeText(
        settings.reasoning_effort, baseline?.reasoning_effort, hasBaseline,
        { effort -> effort?.let { "Уровень рассуждений: $it" } },
        "Уровень рассуждений: не задан",
    )?.let { add(it) }

    diffBadgeText(
        settings.max_tokens, baseline?.max_tokens, hasBaseline,
        { max -> max?.let { "Макс. токенов: $it" } },
        "Макс. токенов: не задано",
    )?.let { add(it) }

    diffBadgeText(
        settings.json_mode, baseline?.json_mode, hasBaseline,
        { if (it) "Ответ в JSON" else null },
        "Ответ в JSON (Выкл.)",
    )?.let { add(it) }

    diffBadgeText(
        settings.stop_sequences, baseline?.stop_sequences, hasBaseline,
        { seqs -> if (seqs.isNotEmpty()) "Стоп-последовательности: ${seqs.joinToString(", ")}" else null },
        "Стоп-последовательности: нет",
    )?.let { add(it) }

    diffBadgeText(
        Triple(settings.autosummary, settings.autosummary_by_messages, settings.autosummary_by_tokens),
        baseline?.let { Triple(it.autosummary, it.autosummary_by_messages, it.autosummary_by_tokens) },
        hasBaseline,
        { (mode, byMessages, byTokens) ->
            if (mode == "off") {
                null
            } else {
                val (count, unit) = if (mode == "tokens") byTokens to "токенов" else byMessages to "сообщений"
                "Автосуммаризация ($count $unit)"
            }
        },
        "Автосуммаризация выключена",
    )?.let { add(it) }

    diffBadgeText(
        settings.context_strategy to settings.context_strategy_limit,
        baseline?.let { it.context_strategy to it.context_strategy_limit },
        hasBaseline,
        { (strategy, limit) ->
            strategy?.let {
                val name = CONTEXT_STRATEGY_OPTION_LABELS[it] ?: it
                if (limit != null) "Стратегия $name ($limit сообщений)" else "Стратегия $name"
            }
        },
        "Стратегия контекста: не задана",
    )?.let { add(it) }

    diffBadgeText(
        memoryFlags(settings),
        baseline?.let { memoryFlags(it) },
        hasBaseline,
        { flags -> if (flags[0]) memoryBadgeText(settings) else null },
        "Память: выключена",
    )?.let { add(it) }

    diffBadgeText(
        settings.task_tracking_enabled, baseline?.task_tracking_enabled, hasBaseline,
        { if (it) "Задачи" else null },
        "Задачи (Выкл.)",
    )?.let { add(it) }

    if (hasInvariants) add("Инварианты")

    // Сравнивается набор имён функций, а не сырой JSON (иначе разное
    // форматирование одного выбора считалось бы отличием).
    diffBadgeText(
        toolsKey(settings.tools_json),
        baseline?.let { toolsKey(it.tools_json) },
        hasBaseline,
        { if (hasConfiguredTools(settings.tools_json)) "Инструменты" else null },
        "Инструменты (Выкл.)",
    )?.let { add(it) }

    if (profileName != null) add("Профиль: $profileName")
}

/**
 * Сводка настроек во всю ширину (экран чата): одна строка бейджей с «+N»,
 * по нажатию — все бейджи с переносом.
 */
@Composable
fun SettingsSummary(
    settings: Settings,
    baselineSettings: Settings? = null,
    hasInvariants: Boolean = false,
    profileName: String? = null,
    modifier: Modifier = Modifier,
) {
    val badges = settingsBadgeTexts(settings, baselineSettings, hasInvariants, profileName)
    if (badges.isEmpty()) return
    var expanded by rememberSaveable { mutableStateOf(false) }
    if (expanded) {
        SettingsBadgeFlow(badges, onCollapse = { expanded = false }, modifier = modifier)
    } else {
        SettingsBadgeLine(badges, onExpand = { expanded = true }, modifier = modifier)
    }
}

/** Бейджи в одну строку: сколько поместится, остальные — «+N» (нажатие
 * вызывает [onExpand]). Если не помещается даже первый бейдж целиком, он
 * обрезается многоточием. */
@Composable
fun SettingsBadgeLine(badges: List<String>, onExpand: () -> Unit, modifier: Modifier = Modifier) {
    if (badges.isEmpty()) return
    SubcomposeLayout(modifier) { constraints ->
        val maxWidth = constraints.maxWidth
        val spacing = BadgeSpacing.roundToPx()
        val loose = Constraints(maxWidth = maxWidth)
        val measured = badges.mapIndexed { i, text ->
            subcompose("badge$i") { SettingsBadge(text) }.first().measure(loose)
        }
        val totalWidth = measured.sumOf { it.width } + spacing * (measured.size - 1)
        val placeables = if (totalWidth <= maxWidth) {
            measured
        } else {
            // Ширина «+N» с запасом — по общему числу бейджей.
            val moreWidth = subcompose("moreProbe") { MoreBadge(badges.size) {} }.first().measure(loose).width
            var used = 0
            var fit = 0
            for (p in measured) {
                val next = used + p.width + spacing
                if (next + moreWidth > maxWidth) break
                used = next
                fit++
            }
            val shown = if (fit > 0) {
                measured.take(fit)
            } else {
                // Не влезает даже первый — сжимаем его до оставшегося места.
                val room = maxWidth - moreWidth - spacing
                if (room > 24.dp.roundToPx()) {
                    listOf(subcompose("first") { SettingsBadge(badges[0]) }.first().measure(Constraints(maxWidth = room)))
                } else {
                    emptyList()
                }
            }
            val hidden = badges.size - shown.size
            shown + subcompose("more") { MoreBadge(hidden, onExpand) }.first().measure(loose)
        }
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val width = (placeables.sumOf { it.width } + spacing * (placeables.size - 1))
            .coerceIn(constraints.minWidth, maxWidth)
        layout(width, height) {
            var x = 0
            placeables.forEach { p ->
                p.placeRelative(x, (height - p.height) / 2)
                x += p.width + spacing
            }
        }
    }
}

/** Все бейджи с переносом строк и «Свернуть» в конце. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsBadgeFlow(badges: List<String>, onCollapse: () -> Unit, modifier: Modifier = Modifier) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(BadgeSpacing),
        verticalArrangement = Arrangement.spacedBy(BadgeSpacing),
    ) {
        badges.forEach { SettingsBadge(it) }
        ActionBadge("Свернуть", onCollapse)
    }
}

/**
 * Строка «название + бейджи»: название занимает сколько нужно, но не больше
 * [maxNameFraction] ширины, бейджи ([SettingsBadgeLine]) — всё остальное.
 */
@Composable
fun NameWithBadges(
    name: @Composable () -> Unit,
    badges: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    maxNameFraction: Float = 0.6f,
) {
    Layout(contents = listOf<@Composable () -> Unit>({ Box { name() } }, { Box { badges() } }), modifier = modifier) { (nameM, badgesM), constraints ->
        val gap = 8.dp.roundToPx()
        val maxWidth = constraints.maxWidth
        val namePlaceable = nameM.first().measure(Constraints(maxWidth = (maxWidth * maxNameFraction).toInt()))
        val room = (maxWidth - namePlaceable.width - gap).coerceAtLeast(0)
        val badgesPlaceable = badgesM.first().measure(Constraints(maxWidth = room))
        val height = maxOf(namePlaceable.height, badgesPlaceable.height)
        val width = if (constraints.hasBoundedWidth) maxWidth else namePlaceable.width + gap + badgesPlaceable.width
        layout(width.coerceAtLeast(constraints.minWidth), height) {
            namePlaceable.placeRelative(0, (height - namePlaceable.height) / 2)
            if (badgesPlaceable.width > 0) {
                badgesPlaceable.placeRelative(namePlaceable.width + gap, (height - badgesPlaceable.height) / 2)
            }
        }
    }
}

@Composable
fun SettingsBadge(text: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = BadgeShape,
        color = BadgeChatFill,
        contentColor = BadgeChatBorderAndText,
        border = BorderStroke(0.5.dp, BadgeChatBorder),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun MoreBadge(count: Int, onClick: () -> Unit) = ActionBadge("+$count", onClick)

/** Бейдж-действие («+N», «Свернуть») — выделен цветом темы. */
@Composable
private fun ActionBadge(text: String, onClick: () -> Unit) {
    Surface(
        shape = BadgeShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.clip(BadgeShape).clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

private val toolsJsonParser = Json { ignoreUnknownKeys = true }

/** Выбран ли в `tools_json` хотя бы один инструмент: непустой JSON-массив.
 * "[]" и пустая строка — нет. Невалидный, но непустой текст считаем
 * настроенным: сервер всё равно попытается его использовать. */
fun hasConfiguredTools(toolsJson: String): Boolean {
    if (toolsJson.isBlank()) return false
    val parsed = runCatching { toolsJsonParser.parseToJsonElement(toolsJson) }.getOrNull() ?: return true
    return (parsed as? JsonArray)?.isNotEmpty() ?: true
}

/** Ключ сравнения выбора инструментов: множество имён функций из
 * `tools_json`; невалидный JSON — сам текст; пусто/"[]" — пустое множество. */
private fun toolsKey(toolsJson: String): Any {
    if (!hasConfiguredTools(toolsJson)) return emptySet<String>()
    val array = runCatching { toolsJsonParser.parseToJsonElement(toolsJson) }.getOrNull() as? JsonArray
        ?: return toolsJson.trim()
    return array.mapNotNull { tool ->
        val function = (tool as? JsonObject)?.get("function") as? JsonObject
        (function?.get("name") as? JsonPrimitive)?.contentOrNull ?: tool.toString()
    }.toSet()
}

/** Флаги памяти одним списком — одним сравнением решаем, отличается ли набор. */
private fun memoryFlags(settings: Settings): List<Boolean> = listOf(
    settings.memory_tools_enabled,
    settings.working_memory_enabled,
    settings.long_term_memory_enabled,
    settings.episodic_memory_enabled,
    settings.semantic_memory_enabled,
    settings.procedural_memory_enabled,
)

/** Текст бейджа с базой сравнения: без базы — по [currentValue]; с базой —
 * только если значение отличается, а выключение настройки показывается явным
 * [textWhenUnset]. */
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

/** «Память: Рабочая, Долговременная» — включённые типы в порядке экрана настроек. */
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

/** Число без лишних нулей: `1` вместо `1.0`, `0.7` вместо `0.70`. */
private fun formatSettingsNumber(value: Double): String {
    if (value == value.toLong().toDouble()) return value.toLong().toString()
    return String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')
}
