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

private val LightColors = lightColorScheme(
    primary = AgentsBlue,
    background = SurfaceLight,
    surface = SurfaceLight,
    error = ErrorRed,
)

private val DarkColors = darkColorScheme(
    primary = AgentsBlueDark,
    background = SurfaceDark,
    surface = SurfaceDark,
    error = ErrorRed,
)

@Composable
fun AgentsAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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

/** Цвет бейджа "Ветка N" для номера ветки [number] (1, 2, 3, ...) — свой
 * цвет на каждую ветку, палитра переиспользуется по кругу (Доработка 9). */
@Composable
fun branchBadgeColor(number: Int): androidx.compose.ui.graphics.Color {
    val palette = if (isSystemInDarkTheme()) BranchBadgePaletteDark else BranchBadgePaletteLight
    val index = ((number - 1).coerceAtLeast(0)) % palette.size
    return palette[index]
}
