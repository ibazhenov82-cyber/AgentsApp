package com.example.agentsapp.ui.common

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

/**
 * Единая сводка настроек ([Settings]) агента или чата — один ряд бейджей, в
 * том же порядке, что и на экране настроек (см. `AGENT_SETTINGS_FIELDS` в
 * `SettingsFieldMeta.kt`): температура+top_p (общим бейджем, всегда первым
 * слева) → seed → потоковые ответы → рассуждения → уровень рассуждений →
 * максимальное число токенов → ответ в JSON → стоп-последовательности →
 * автоматическая суммаризация → стратегия управления контекстом. Дальше
 * температуры/top_p показывается только то, что реально задано/включено —
 * используется одинаково на главном экране (карточка агента, строка чата) и
 * на экране чата, чтобы информация не расходилась.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsSummary(settings: Settings, modifier: Modifier = Modifier) {
    val primary = MaterialTheme.colorScheme.primaryContainer
    val secondary = MaterialTheme.colorScheme.secondaryContainer
    val tertiary = MaterialTheme.colorScheme.tertiaryContainer
    val neutral = MaterialTheme.colorScheme.surfaceVariant

    val badges = buildList {
        // Температура и top_p — всегда первым, общим бейджем (по замечанию
        // пользователя), а не отдельной строкой текста.
        add(
            BadgeSpec(
                "Температура: ${formatSettingsNumber(settings.temperature)} · " +
                    "top_p: ${formatSettingsNumber(settings.top_p)}",
                primary,
            )
        )
        settings.seed?.let { add(BadgeSpec("Начальное число (seed): $it", neutral)) }
        if (settings.stream) add(BadgeSpec("Потоковые ответы", neutral))
        if (settings.thinking_enabled) add(BadgeSpec("Рассуждения", neutral))
        settings.reasoning_effort?.let { add(BadgeSpec("Уровень рассуждений: $it", neutral)) }
        settings.max_tokens?.let { add(BadgeSpec("Максимальное число токенов: $it", neutral)) }
        if (settings.json_mode) add(BadgeSpec("Ответ в JSON", neutral))
        if (settings.stop_sequences.isNotEmpty()) {
            add(BadgeSpec("Стоп-последовательности: ${settings.stop_sequences.joinToString(", ")}", neutral))
        }
        if (settings.autosummary != "off") {
            val (count, unit) = if (settings.autosummary == "tokens") {
                settings.autosummary_by_tokens to "токенов"
            } else {
                settings.autosummary_by_messages to "сообщений"
            }
            add(BadgeSpec("Автоматическая суммаризация ($count $unit)", tertiary))
        }
        settings.context_strategy?.let { strategy ->
            val name = CONTEXT_STRATEGY_OPTION_LABELS[strategy] ?: strategy
            val limit = settings.context_strategy_limit
            val text = if (limit != null) "Стратегия $name ($limit сообщений)" else "Стратегия $name"
            add(BadgeSpec(text, secondary))
        }
    }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        badges.forEach { badge -> SettingsBadgeChip(text = badge.text, color = badge.color) }
    }
}

private data class BadgeSpec(val text: String, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsBadgeChip(text: String, color: Color) {
    AssistChip(
        onClick = {},
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(containerColor = color),
    )
}

/** Компактное форматирование числа с плавающей точкой для бейджей/текста
 * настроек: целые значения — без дробной части (`1` вместо `1.0`), иначе —
 * не более двух знаков после запятой без хвостовых нулей (`0.7`, не `0.70`). */
private fun formatSettingsNumber(value: Double): String {
    if (value == value.toLong().toDouble()) return value.toLong().toString()
    return "%.2f".format(value).trimEnd('0').trimEnd('.')
}
