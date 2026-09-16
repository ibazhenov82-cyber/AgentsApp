package com.example.agentsapp.ui.theme

import androidx.compose.ui.graphics.Color

val AgentsBlue = Color(0xFF3B6FED)
val AgentsBlueDark = Color(0xFF9DB8FF)
val SurfaceLight = Color(0xFFF7F8FA)
val SurfaceDark = Color(0xFF121317)

// Полная сине-зелёная схема (замечание: "вернул стандартный голубой для
// бейджей/сообщений/кнопок, я просил убрать только розовый"). Причина
// регресса: lightColorScheme()/darkColorScheme() переопределяли ТОЛЬКО
// primary/tertiary, а остальные роли (primaryContainer, secondary,
// secondaryContainer и их on*-пары) молча оставались на базовой лиловой
// палитре Material 3 — раньше это было не видно, потому что поверх всё
// равно использовалась динамическая (из обоев) палитра Android 12+; как
// только динамическая палитра была выключена (чтобы убрать розовый), эта
// лиловая "заглушка" стала видна везде, где раньше был синий фон бейджа/
// пузыря сообщения/кнопки. Ниже — полный набор ролей на основе того же
// синего оттенка, чтобы больше НИ ОДНА роль не падала на лиловый дефолт.
val AgentsOnPrimaryLight = Color(0xFFFFFFFF)
val AgentsPrimaryContainerLight = Color(0xFFDCE7FF) // = UserBubbleLight
val AgentsOnPrimaryContainerLight = Color(0xFF001B41)
val AgentsSecondaryLight = Color(0xFF56607C)
val AgentsOnSecondaryLight = Color(0xFFFFFFFF)
val AgentsSecondaryContainerLight = Color(0xFFDAE2F9)
val AgentsOnSecondaryContainerLight = Color(0xFF131C2C)

val AgentsOnPrimaryDark = Color(0xFF002E69)
val AgentsPrimaryContainerDark = Color(0xFF1E4494)
val AgentsOnPrimaryContainerDark = Color(0xFFDCE7FF)
val AgentsSecondaryDark = Color(0xFFBEC6DC)
val AgentsOnSecondaryDark = Color(0xFF283041)
val AgentsSecondaryContainerDark = Color(0xFF3E4759)
val AgentsOnSecondaryContainerDark = Color(0xFFDAE2F9)
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
// Розовый (ветка 5) заменён на более тёмный зелёный (Greenery), чтобы не
// совпадать по тону с основным цветом бейджей ниже — см. замечание про
// "однородный светло-розовый цвет" (тот же тон, что был у tertiaryContainer).
val BranchBadgePaletteLight = listOf(
    Color(0xFFFFE0B2), // ветка 1 — оранжевый
    Color(0xFFE1BEE7), // ветка 2 — фиолетовый
    Color(0xFFB3E5FC), // ветка 3 — голубой
    Color(0xFFC8E6C9), // ветка 4 — зелёный (светлый)
    Color(0xFFA9C97D), // ветка 5 — зелёный (Greenery, тёмный оттенок)
)
val BranchBadgePaletteDark = listOf(
    Color(0xFF6B4A1F),
    Color(0xFF4A2E52),
    Color(0xFF1F4A5C),
    Color(0xFF2C4A2E),
    Color(0xFF4C5E2A),
)

// Тон "Greenery" (Pantone 15-0343, жёлто-зелёный) — заменяет стандартный
// светло-розовый tertiary/tertiaryContainer из базовой палитры Material 3
// (см. замечание пользователя "исключи однородный светло-розовый цвет, в
// частности для бейджей, замени на greenery"). Используется в Theme.kt для
// ролей tertiary/onTertiary/tertiaryContainer/onTertiaryContainer — то есть
// везде, где бейджи ("Суммаризация", "Агент сохраняет память", "Summary" в
// чате) раньше получали розовый цвет из дефолтной M3-схемы.
val GreeneryTertiaryLight = Color(0xFF3F6A21)
val GreeneryOnTertiaryLight = Color(0xFFFFFFFF)
val GreeneryTertiaryContainerLight = Color(0xFFD7ECB4)
val GreeneryOnTertiaryContainerLight = Color(0xFF122000)

val GreeneryTertiaryDark = Color(0xFFB0D383)
val GreeneryOnTertiaryDark = Color(0xFF1D3700)
val GreeneryTertiaryContainerDark = Color(0xFF2E4E12)
val GreeneryOnTertiaryContainerDark = Color(0xFFD3EAAB)

// "Niagara" (Pantone 17-4123 TCX, #5587A4 — сине-стальной тон) — цвет бейджа
// подключённого профиля-пайплайна персонализации (список агентов/чатов на
// главном экране + экран чата), по замечанию пользователя. Как и у
// остальных бейджей в приложении, в самом бейдже используется не насыщенный
// исходный тон, а его пастельная Container/onContainer-пара — так бейдж
// вписывается в общий стиль остальных бейджей (см. SettingsBadges.kt), но
// заметно отличается по оттенку от них (синие/зелёные бейджи настроек), не
// путаясь с ними по смыслу.
val NiagaraContainerLight = Color(0xFFD3E6EF)
val OnNiagaraContainerLight = Color(0xFF0B3A4D)
val NiagaraContainerDark = Color(0xFF264956)
val OnNiagaraContainerDark = Color(0xFFC3E2ED)

// Подсветка синтаксиса JSON в JsonTreeView.kt — общепринятая схема цветов
// (та же логика раскраски по типу токена, что в большинстве кодовых
// редакторов и просмотрщиков JSON): ключи — синим, строковые значения —
// оранжевым/красным, числа — зелёным, true/false/null — отдельным синим
// акцентом, скобки/двоеточия — нейтральным серым. Отдельная светлая/тёмная
// пара на каждый тип токена, по тому же принципу, что и Niagara/Greenery.
val JsonKeyLight = Color(0xFF0451A5)
val JsonKeyDark = Color(0xFF9CDCFE)
val JsonStringLight = Color(0xFFA31515)
val JsonStringDark = Color(0xFFCE9178)
val JsonNumberLight = Color(0xFF098658)
val JsonNumberDark = Color(0xFFB5CEA8)
val JsonBooleanLight = Color(0xFF0000FF)
val JsonBooleanDark = Color(0xFF569CD6)
val JsonPunctuationLight = Color(0xFF6E6E6E)
val JsonPunctuationDark = Color(0xFFAEAEAE)

// Единый цвет всех бейджей (настроек и профиля) — по замечанию
// пользователя: заливка — тот же синий, что у пузыря сообщения пользователя
// в чате (UserBubbleLight/AgentsPrimaryContainerLight, #DCE7FF), обводка и
// текст — тёмный, близкий к чёрному вариант того же тона
// (AgentsOnPrimaryContainerLight, #001B41). Фиксированный, не зависит от
// темы приложения (светлая/тёмная) — чтобы бейджи всегда выглядели
// одинаково узнаваемо, вместо адаптации под системную тему.
val BadgeChatFill = Color(0xFFDCE7FF)
val BadgeChatBorderAndText = Color(0xFF001B41)
// Обводка — того же тона, что и текст, но вдвое светлее (по HSL-светлоте:
// 0.127 → 0.255), по замечанию пользователя — чтобы обводка не сливалась с
// текстом и читалась отдельным, более лёгким контуром.
val BadgeChatBorder = Color(0xFF003682)

// Единая палитра бейджей настроек — по замечанию пользователя: цвет бейджа
// "Профиль" (Niagara, ниже) понравился больше всего, остальные бейджи
// (температура/top_p, переключатели вроде "Потоковые ответы"/"Рассуждения",
// стратегия контекста, автосуммаризация/память) приведены к тому же тону
// (hue ≈198°, сине-бирюзовый), только на 1-2 ступени светлее/темнее — вместо
// разных цветов (голубой primary, зелёный Greenery tertiary, серый/бирюзовый
// neutral). Профиль (Niagara) не меняется — он и есть эталон. Порядок от
// самого светлого к самому тёмному: Neutral (самые частые бейджи-флаги) →
// Secondary (стратегия контекста) → Niagara (профиль, эталон) → Primary
// (температура/top_p) → Tertiary (автосуммаризация/память) — самый глубокий,
// чтобы выделять более "весомые" настройки. У каждого — свой контрастный
// текстовый цвет того же тона (по образцу Niagara: тёмный текст на светлом
// фоне в светлой теме, светлый текст на тёмном фоне в тёмной).
val BadgeToneNeutralLight = Color(0xFFEEF4F6)
val OnBadgeToneNeutralLight = Color(0xFF134053)
val BadgeToneNeutralDark = Color(0xFF24353D)
val OnBadgeToneNeutralDark = Color(0xFFDCE9EF)

val BadgeToneSecondaryLight = Color(0xFFDFECF1)
val OnBadgeToneSecondaryLight = Color(0xFF0E3749)
val BadgeToneSecondaryDark = Color(0xFF2A4551)
val OnBadgeToneSecondaryDark = Color(0xFFD3E6EE)

val BadgeTonePrimaryLight = Color(0xFFAAD3E4)
val OnBadgeTonePrimaryLight = Color(0xFF092E3E)
val BadgeTonePrimaryDark = Color(0xFF2C5668)
val OnBadgeTonePrimaryDark = Color(0xFFC9E2ED)

val BadgeToneTertiaryLight = Color(0xFF68B7D9)
val OnBadgeToneTertiaryLight = Color(0xFF062532)
val BadgeToneTertiaryDark = Color(0xFF2D647B)
val OnBadgeToneTertiaryDark = Color(0xFFBFDFED)
