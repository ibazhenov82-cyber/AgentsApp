package com.example.agentsapp.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

// Полностью расписанная схема (primary+secondary+tertiary и все их on*/
// *Container пары) — ни одна роль не остаётся на лиловом дефолте Material 3.
// Раньше lightColorScheme()/darkColorScheme() переопределяли только primary/
// tertiary, а secondary/primaryContainer/secondaryContainer молча оставались
// лиловыми из базовой M3-палитры — это было не видно, пока поверх всё равно
// подставлялась динамическая (из обоев) палитра Android 12+; как только её
// выключили (чтобы убрать розовый), лиловая "заглушка" стала видна везде,
// где раньше был синий фон бейджа/пузыря сообщения/кнопки (FAB и
// FilledTonalButton используют именно *Container-роли). Единственное
// намеренное отличие от исходного вида — tertiary теперь зелёный (Greenery)
// вместо розового, как и просил пользователь изначально.
private val LightColors = lightColorScheme(
    primary = AgentsBlue,
    onPrimary = AgentsOnPrimaryLight,
    primaryContainer = AgentsPrimaryContainerLight,
    onPrimaryContainer = AgentsOnPrimaryContainerLight,
    secondary = AgentsSecondaryLight,
    onSecondary = AgentsOnSecondaryLight,
    secondaryContainer = AgentsSecondaryContainerLight,
    onSecondaryContainer = AgentsOnSecondaryContainerLight,
    background = SurfaceLight,
    surface = SurfaceLight,
    error = ErrorRed,
    tertiary = GreeneryTertiaryLight,
    onTertiary = GreeneryOnTertiaryLight,
    tertiaryContainer = GreeneryTertiaryContainerLight,
    onTertiaryContainer = GreeneryOnTertiaryContainerLight,
)

private val DarkColors = darkColorScheme(
    primary = AgentsBlueDark,
    onPrimary = AgentsOnPrimaryDark,
    primaryContainer = AgentsPrimaryContainerDark,
    onPrimaryContainer = AgentsOnPrimaryContainerDark,
    secondary = AgentsSecondaryDark,
    onSecondary = AgentsOnSecondaryDark,
    secondaryContainer = AgentsSecondaryContainerDark,
    onSecondaryContainer = AgentsOnSecondaryContainerDark,
    background = SurfaceDark,
    surface = SurfaceDark,
    error = ErrorRed,
    tertiary = GreeneryTertiaryDark,
    onTertiary = GreeneryOnTertiaryDark,
    tertiaryContainer = GreeneryTertiaryContainerDark,
    onTertiaryContainer = GreeneryOnTertiaryContainerDark,
)

@Composable
fun AgentsAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // По умолчанию выключено (было true): динамическая палитра Android 12+
    // берёт цвета из обоев пользователя, а конкретно у части пользователей
    // это давало "однородный светло-розовый" на tertiary/secondary бейджах.
    // Собственная палитра (синий + greenery) гарантирует одинаковый вид на
    // любом устройстве и не зависит от обоев.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}

/** Цвет пузыря сообщения-суммаризации (isSummary = true), не зависящий от
 * того, какая цветовая схема (светлая/тёмная) сейчас активна. */
@Composable
fun summaryBubbleColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) SummaryBubbleDark else SummaryBubbleLight

/** Фон/цвет текста бейджа "Профиль" (см. [com.example.agentsapp.ui.common.ProfileBadge]) —
 * своя пара "niagara", не завязанная на роли [MaterialTheme.colorScheme], по
 * тому же принципу, что и [summaryBubbleColor]. */
@Composable
fun niagaraBadgeContainerColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) NiagaraContainerDark else NiagaraContainerLight

@Composable
fun onNiagaraBadgeContainerColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) OnNiagaraContainerDark else OnNiagaraContainerLight

/** Единая палитра бейджей настроек (см. Color.kt) — все пять оттенков
 * (Neutral/Secondary/Niagara/Primary/Tertiary) одного тона, что и бейдж
 * "Профиль" (Niagara), только светлее/темнее, по замечанию пользователя. */
@Composable
fun badgeToneNeutralContainerColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) BadgeToneNeutralDark else BadgeToneNeutralLight

@Composable
fun onBadgeToneNeutralContainerColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) OnBadgeToneNeutralDark else OnBadgeToneNeutralLight

@Composable
fun badgeToneSecondaryContainerColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) BadgeToneSecondaryDark else BadgeToneSecondaryLight

@Composable
fun onBadgeToneSecondaryContainerColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) OnBadgeToneSecondaryDark else OnBadgeToneSecondaryLight

@Composable
fun badgeTonePrimaryContainerColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) BadgeTonePrimaryDark else BadgeTonePrimaryLight

@Composable
fun onBadgeTonePrimaryContainerColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) OnBadgeTonePrimaryDark else OnBadgeTonePrimaryLight

@Composable
fun badgeToneTertiaryContainerColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) BadgeToneTertiaryDark else BadgeToneTertiaryLight

@Composable
fun onBadgeToneTertiaryContainerColor(): androidx.compose.ui.graphics.Color =
    if (isSystemInDarkTheme()) OnBadgeToneTertiaryDark else OnBadgeToneTertiaryLight

/** Цвета подсветки синтаксиса JSON в [com.example.agentsapp.ui.chat.JsonTreeView] —
 * по одному цвету на тип токена (ключ / строка / число / boolean-null /
 * пунктуация), общепринятая для JSON-подсветки раскраска (см. Color.kt). */
data class JsonSyntaxColors(
    val key: androidx.compose.ui.graphics.Color,
    val string: androidx.compose.ui.graphics.Color,
    val number: androidx.compose.ui.graphics.Color,
    val boolean: androidx.compose.ui.graphics.Color,
    val punctuation: androidx.compose.ui.graphics.Color,
)

@Composable
fun jsonSyntaxColors(): JsonSyntaxColors = if (isSystemInDarkTheme()) {
    JsonSyntaxColors(
        key = JsonKeyDark,
        string = JsonStringDark,
        number = JsonNumberDark,
        boolean = JsonBooleanDark,
        punctuation = JsonPunctuationDark,
    )
} else {
    JsonSyntaxColors(
        key = JsonKeyLight,
        string = JsonStringLight,
        number = JsonNumberLight,
        boolean = JsonBooleanLight,
        punctuation = JsonPunctuationLight,
    )
}

/** Цвет бейджа "Ветка N" для номера ветки [number] (1, 2, 3, ...) — свой
 * цвет на каждую ветку, палитра переиспользуется по кругу (Доработка 9). */
@Composable
fun branchBadgeColor(number: Int): androidx.compose.ui.graphics.Color {
    val palette = if (isSystemInDarkTheme()) BranchBadgePaletteDark else BranchBadgePaletteLight
    val index = ((number - 1).coerceAtLeast(0)) % palette.size
    return palette[index]
}
