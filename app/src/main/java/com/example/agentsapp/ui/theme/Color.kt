package com.example.agentsapp.ui.theme

import androidx.compose.ui.graphics.Color

val AgentsBlue = Color(0xFF3B6FED)
val AgentsBlueDark = Color(0xFF9DB8FF)
val SurfaceLight = Color(0xFFF7F8FA)
val SurfaceDark = Color(0xFF121317)
val AssistantBubbleLight = Color(0xFFEDEFF4)
val AssistantBubbleDark = Color(0xFF23252B)
val UserBubbleLight = Color(0xFFDCE7FF)
val UserBubbleDark = Color(0xFF2A3B63)
val ErrorRed = Color(0xFFD32F2F)

// Сообщение-суммаризация (isSummary = true) — выделяется отдельным цветом,
// отличным от обычного пользовательского сообщения.
val SummaryBubbleLight = Color(0xFFCFF3E6)
val SummaryBubbleDark = Color(0xFF1F4438)

// Палитра для бейджа "Ветка N" — свой цвет на каждый номер ветки (Доработка 9),
// циклически переиспользуется, если веток больше, чем цветов в палитре.
val BranchBadgePaletteLight = listOf(
    Color(0xFFFFE0B2), // ветка 1 — оранжевый
    Color(0xFFE1BEE7), // ветка 2 — фиолетовый
    Color(0xFFB3E5FC), // ветка 3 — голубой
    Color(0xFFC8E6C9), // ветка 4 — зелёный
    Color(0xFFF8BBD0), // ветка 5 — розовый
)
val BranchBadgePaletteDark = listOf(
    Color(0xFF6B4A1F),
    Color(0xFF4A2E52),
    Color(0xFF1F4A5C),
    Color(0xFF2C4A2E),
    Color(0xFF5C2C3C),
)
