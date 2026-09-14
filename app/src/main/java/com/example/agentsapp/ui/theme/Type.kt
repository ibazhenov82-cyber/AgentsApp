package com.example.agentsapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    // Второстепенный текст по всему приложению (строка "Агент/Модель",
    // текст рассуждений, подписи вроде "Пока нет сохранённых фактов" и т.п.)
    // использует bodySmall — стандартный размер M3 (12.sp) увеличен на 25%,
    // чтобы такой текст было проще читать.
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 20.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    // Мелкие служебные подписи (телеметрия "N токенов"/время ответа, "Токены —
    // вход/выход/всего", "Заполнение контекста", "Показать рассуждения",
    // бейджи Summary/Ветка/Факты, "печатает…" и т.п.) используют labelSmall —
    // стандартный размер M3 (11.sp) увеличен примерно на 23%.
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 13.5.sp,
        lineHeight = 20.sp,
    ),
)
