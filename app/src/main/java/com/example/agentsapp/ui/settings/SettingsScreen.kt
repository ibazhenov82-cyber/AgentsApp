package com.example.agentsapp.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.agentsapp.data.remote.FieldType
import com.example.agentsapp.data.remote.INVARIANT_KIND_LABELS
import com.example.agentsapp.data.remote.Invariant
import com.example.agentsapp.data.remote.ModelInfo
import com.example.agentsapp.data.remote.SETTINGS_GROUP_ORDER
import com.example.agentsapp.data.remote.SettingsFieldDef
import com.example.agentsapp.ui.common.toolTitle

/** Строка внутри списка настроек — либо заголовок группы, либо конкретное поле. */
private sealed class SettingsRow {
    data class GroupHeader(val title: String) : SettingsRow()
    data class Field(val def: SettingsFieldDef) : SettingsRow()
}

/** Переключатели типов/слоёв памяти — недоступны (см. [FieldEditor]), пока
 * не включена "Разрешить агенту сохранять память" (memory_tools_enabled);
 * по замечанию пользователя. */
private val MEMORY_TYPE_FIELD_NAMES = setOf(
    "working_memory_enabled",
    "long_term_memory_enabled",
    "episodic_memory_enabled",
    "semantic_memory_enabled",
    "procedural_memory_enabled",
)

/** Переключатель (BOOLEAN) — единственный тип поля, для которого
 * floating-label в принципе не применим (это не поле ввода и не список), он
 * рисуется как обычно: лейбл слева, Switch справа, в одну строку. Все
 * остальные типы — и текстовые/числовые поля, и выпадающие списки — с этого
 * момента используют встроенный floating-label самого `OutlinedTextField`
 * (тот же приём, что уже был у поля "Название" и "Адрес сервера"), а не
 * отдельный статичный `Text` рядом с полем — раньше это и создавало
 * несогласованный внешний вид формы. */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showResetConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            title = { Text("Сбросить настройки?") },
            text = { Text("Все настройки по умолчанию будут заменены встроенными значениями сервера.") },
            confirmButton = {
                TextButton(onClick = {
                    showResetConfirm = false
                    viewModel.resetToDefaults()
                }) { Text("Сбросить") }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) { Text("Отмена") }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (state.showResetButton) {
                        IconButton(onClick = { showResetConfirm = true }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Сбросить к встроенным значениям")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val rows = remember(state.fields, state.values) { buildRows(state.fields, state.values) }

        // Важно: contentPadding центрирует только внутренние отступы списка,
        // а не отступ под TopAppBar — без .padding(padding) первый элемент
        // (блок "Соединение с сервером") оказывался под шапкой экрана и был
        // недоступен для прокрутки к нему (это и была причина "не добраться
        // до настроек сервера").
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.entityName != null) {
                item {
                    OutlinedTextField(
                        value = state.entityName.orEmpty(),
                        onValueChange = viewModel::onEntityNameChange,
                        label = { Text("Название") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
                item { HorizontalDivider() }
            }

            if (state.showConnectionBlock) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GroupHeaderText(SETTINGS_GROUP_ORDER.first())
                        ConnectionField(state = state, viewModel = viewModel)
                        // Адрес отдельного MCP-сервера (новое ТЗ, третий компонент) —
                        // второе, независимое поле в том же блоке: свой сервис, свой
                        // адрес (см. McpConnectionSettings), без проверки соединения —
                        // статус MCP-сервера виден прямо на экране "MCP".
                        OutlinedTextField(
                            value = state.mcpConnectionUrl,
                            onValueChange = viewModel::onMcpConnectionUrlChange,
                            label = { Text("Адрес MCP-сервера") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                item { HorizontalDivider() }
            }

            // Профиль-пайплайн персонализации (замечание 1: "профиль
            // выбирается в настройках агента/чата") — не часть generic-полей
            // Settings, отдельная связь с общим справочником профилей.
            if (state.showProfilePicker) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GroupHeaderText("Профиль")
                        ProfilePickerField(state = state, onSelected = viewModel::onProfileChange)
                    }
                }
                item { HorizontalDivider() }
            }

            // Инварианты ("День 14") — общий справочник (экран "Инварианты" с
            // главного экрана), здесь только МНОЖЕСТВЕННЫЙ выбор того, какие
            // из них действуют для этого агента/чата (не generic-поле
            // Settings, как и профиль выше). Отдельного тумблера "Разрешить
            // инварианты" больше нет (см. новое ТЗ) — как только здесь выбран
            // хотя бы один инвариант, они считаются разрешёнными сами по
            // себе; пустой выбор — инъекции не будет.
            if (state.showInvariantsPicker) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        GroupHeaderText("Инварианты")
                        InvariantsPickerField(state = state, onChanged = viewModel::onInvariantsChange)
                    }
                }
                item { HorizontalDivider() }
            }

            items(rows) { row ->
                when (row) {
                    is SettingsRow.GroupHeader -> GroupHeaderText(row.title)
                    is SettingsRow.Field -> {
                        // Переключатели типов памяти недоступны, пока не включена
                        // "Разрешить агенту сохранять память" (см. MEMORY_TYPE_FIELD_NAMES).
                        val fieldEnabled = if (row.def.sysName in MEMORY_TYPE_FIELD_NAMES) {
                            state.values["memory_tools_enabled"] as? Boolean ?: false
                        } else {
                            true
                        }
                        // Поле "Список функций в формате OpenAI" (tools_json) — отдельный
                        // редактор с переключателем "JSON"/"Выбрать из доступных"
                        // (новое ТЗ, интеграция с MCP-сервером), остальные типы полей —
                        // как раньше, через generic FieldEditor.
                        if (row.def.sysName == "tools_json") {
                            ToolsJsonFieldEditor(def = row.def, state = state, viewModel = viewModel)
                        } else {
                            FieldEditor(
                                def = row.def,
                                value = state.values[row.def.sysName],
                                enabled = fieldEnabled,
                                modelEditable = state.modelEditable,
                                models = state.models,
                                onImmediateChange = { v -> viewModel.onImmediateChange(row.def.sysName, v) },
                                onDebouncedChange = { v -> viewModel.onDebouncedChange(row.def.sysName, v) },
                                onSliderDrag = { v -> viewModel.onSliderDrag(row.def.sysName, v) },
                                onSliderChangeFinished = { viewModel.onSliderChangeFinished(row.def.sysName) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeaderText(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
}

/** Блок "Соединение с сервером": адрес сервера и кнопка проверки соединения
 * (иконка) справа от поля ввода — единственная настройка уровня самого
 * приложения, показывается только на экране "Настройки" (режим Default). */
@Composable
private fun ConnectionField(state: SettingsUiState, viewModel: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.connectionUrl,
                onValueChange = viewModel::onConnectionUrlChange,
                label = { Text("Адрес сервера") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            if (state.isCheckingConnection) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                IconButton(onClick = viewModel::checkConnection) {
                    Icon(Icons.Filled.Wifi, contentDescription = "Проверить соединение")
                }
            }
        }
        state.connectionCheckResult?.let { result ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(
                    imageVector = if (state.connectionCheckSucceeded) Icons.Filled.CheckCircle else Icons.Filled.Error,
                    contentDescription = null,
                    tint = if (state.connectionCheckSucceeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.width(16.dp),
                )
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.connectionCheckSucceeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/** Редактор поля "Список функций в формате OpenAI" (tools_json) — переключатель
 * "JSON" / "Выбрать из доступных" (новое ТЗ, интеграция с MCP-сервером):
 * в режиме JSON — прежний, ничем не ограниченный текстовый ввод; в режиме
 * списка — чекбоксы по инструментам MCP-сервера (`GET /api/tools`), tools_json
 * при этом ПОЛНОСТЬЮ перестраивается из отмеченного набора (см. докстринг
 * [SettingsUiState.toolsPickerMode] — переключение туда и обратно не
 * объединяет изменения). */
@Composable
private fun ToolsJsonFieldEditor(def: SettingsFieldDef, state: SettingsUiState, viewModel: SettingsViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(def.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text(
                text = if (state.toolsPickerMode) "Выбрать из доступных" else "JSON",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(checked = state.toolsPickerMode, onCheckedChange = viewModel::onToolsPickerModeChange)
        }

        if (!state.toolsPickerMode) {
            OutlinedTextField(
                value = state.values[def.sysName] as? String ?: "",
                onValueChange = { viewModel.onDebouncedChange(def.sysName, it) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                maxLines = 16,
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        } else {
            Text(
                "Переключение между режимами не объединяет изменения: список ниже построен по текущему " +
                    "содержимому JSON, а при отметке/снятии функции JSON перестраивается заново целиком.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.availableMcpTools.isEmpty()) {
                Text(
                    "Нет доступных инструментов — проверьте адрес MCP-сервера в блоке \"Соединение с сервером\" выше.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    state.availableMcpTools.forEach { tool ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.onMcpToolToggle(tool) },
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = tool.name in state.selectedMcpToolNames,
                                onCheckedChange = { viewModel.onMcpToolToggle(tool) },
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    toolTitle(tool.name, tool.title) ?: tool.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                Text(
                                    tool.name,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Строит плоский список строк (заголовки групп + поля) в порядке
 * [SETTINGS_GROUP_ORDER], пропуская поля, скрытые из-за
 * [SettingsFieldDef.visibleWhenAutosummary]/[SettingsFieldDef.visibleWhenContextStrategySet]/[SettingsFieldDef.visibleWhenContextStrategy]. */
private fun buildRows(fields: List<SettingsFieldDef>, values: Map<String, Any?>): List<SettingsRow> {
    val currentAutosummary = values["autosummary"] as? String
    val currentContextStrategy = values["context_strategy"] as? String
    val byGroup = fields.groupBy { it.group }
    val rows = mutableListOf<SettingsRow>()
    for (group in SETTINGS_GROUP_ORDER) {
        val groupFields = byGroup[group] ?: continue
        val visibleFields = groupFields.filter { field ->
            (field.visibleWhenAutosummary == null || field.visibleWhenAutosummary == currentAutosummary) &&
                (!field.visibleWhenContextStrategySet || currentContextStrategy != null) &&
                (field.visibleWhenContextStrategy == null || field.visibleWhenContextStrategy == currentContextStrategy)
        }
        if (visibleFields.isEmpty()) continue
        rows += SettingsRow.GroupHeader(group)
        visibleFields.forEach { rows += SettingsRow.Field(it) }
    }
    return rows
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FieldEditor(
    def: SettingsFieldDef,
    value: Any?,
    modelEditable: Boolean,
    models: List<ModelInfo>,
    onImmediateChange: (Any?) -> Unit,
    onDebouncedChange: (Any?) -> Unit,
    onSliderDrag: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    // Только для BOOLEAN-полей типов памяти: false, пока не включена
    // "Разрешить агенту сохранять память" (см. MEMORY_TYPE_FIELD_NAMES) — на
    // остальные типы полей не влияет.
    enabled: Boolean = true,
) {
    when (def.type) {
        FieldType.BOOLEAN -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                def.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Switch(checked = value as? Boolean ?: false, onCheckedChange = onImmediateChange, enabled = enabled)
        }

        FieldType.MODEL_PICKER -> ModelPickerField(def.title, value as? String, modelEditable, models, onImmediateChange)

        FieldType.ENUM -> EnumDropdown(
            label = def.title,
            current = value as? String ?: def.options.firstOrNull().orEmpty(),
            options = def.options,
            labelOf = { def.optionLabels[it] ?: it },
            onSelected = onImmediateChange,
        )

        FieldType.NULLABLE_ENUM -> EnumDropdown(
            label = def.title,
            current = value as? String,
            options = listOf<String?>(null) + def.options.map { it as String? },
            labelOf = { it?.let { v -> def.optionLabels[v] ?: v } ?: "не задано" },
            onSelected = onImmediateChange,
        )

        FieldType.NULLABLE_INT -> OutlinedTextField(
            value = (value as? Int)?.toString() ?: "",
            onValueChange = { text ->
                val filtered = text.filter { it.isDigit() || it == '-' }
                onDebouncedChange(if (filtered.isBlank()) null else filtered.toIntOrNull())
            },
            label = { Text(def.title) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            placeholder = { Text("не задано") },
        )

        FieldType.NULLABLE_FLOAT -> OutlinedTextField(
            value = (value as? Number)?.toString() ?: "",
            onValueChange = { text ->
                val filtered = text.filter { it.isDigit() || it == '.' || it == '-' }
                onDebouncedChange(if (filtered.isBlank()) null else filtered.toDoubleOrNull())
            },
            label = { Text(def.title) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            placeholder = { Text("не задано") },
        )

        FieldType.INT -> OutlinedTextField(
            value = (value as? Int)?.toString() ?: "0",
            onValueChange = { text ->
                val filtered = text.filter { it.isDigit() }
                onDebouncedChange(filtered.toIntOrNull() ?: 0)
            },
            label = { Text(def.title) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        )

        FieldType.MULTILINE_STRING -> OutlinedTextField(
            value = value as? String ?: "",
            onValueChange = onDebouncedChange,
            label = { Text(def.title) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 8,
        )

        FieldType.STOP_SEQUENCES -> {
            @Suppress("UNCHECKED_CAST")
            val list = value as? List<String> ?: emptyList()
            OutlinedTextField(
                value = list.joinToString(", "),
                onValueChange = { text ->
                    val parsed = text.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    onDebouncedChange(parsed)
                },
                label = { Text(def.title) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                placeholder = { Text("значение1, значение2, ...") },
            )
        }

        FieldType.TOOLS_JSON -> OutlinedTextField(
            value = value as? String ?: "",
            onValueChange = onDebouncedChange,
            label = { Text(def.title) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            maxLines = 16,
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        )

        FieldType.FLOAT_SLIDER -> {
            // Слайдер — не поле ввода и не список, floating-label к нему не
            // применим (сохраняется прежний вид: лейбл сверху, значение снизу).
            val floatValue = ((value as? Number)?.toFloat() ?: def.sliderRange.start)
                .coerceIn(def.sliderRange.start, def.sliderRange.endInclusive)
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(def.title, style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = floatValue,
                    onValueChange = onSliderDrag,
                    onValueChangeFinished = onSliderChangeFinished,
                    valueRange = def.sliderRange,
                )
                Text(
                    "%.2f".format(floatValue),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Выпадающий список с floating-label — тем же приёмом, что и у обычных
 * текстовых полей (`label`, а не отдельный статичный `Text` рядом). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    current: T,
    options: List<T>,
    labelOf: (T) -> String,
    onSelected: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = labelOf(current),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            textStyle = MaterialTheme.typography.bodyMedium,
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(labelOf(option)) },
                    onClick = {
                        expanded = false
                        onSelected(option)
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerField(
    label: String,
    modelId: String?,
    editable: Boolean,
    models: List<ModelInfo>,
    onSelected: (String) -> Unit,
) {
    if (!editable) {
        // Настройки чата: модель зафиксирована на уровне агента и здесь
        // только отображается — это не поле ввода, floating-label не нужен,
        // подпись остаётся статичным текстом сверху для контекста.
        val modelText = models.firstOrNull { it.id == modelId }?.let { modelLabel(it) } ?: modelId.orEmpty()
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(modelText, style = MaterialTheme.typography.bodyLarge)
        }
        return
    }

    var expanded by remember { mutableStateOf(false) }
    val currentLabel = models.firstOrNull { it.id == modelId }?.let { modelLabel(it) } ?: modelId.orEmpty()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = { Text(modelLabel(model)) },
                    onClick = {
                        expanded = false
                        onSelected(model.id)
                    },
                )
            }
        }
    }
}

/** Название модели с суффиксом "(локальная)" вместо отдельного бейджа
 * (замечания 7/8). */
private fun modelLabel(model: ModelInfo): String {
    val name = model.display_name.ifBlank { model.model_id }
    return if (model.is_local) "$name (локальная)" else name
}

/** Выбор профиля из общего справочника (`GET /profiles`, см.
 * ProfilesScreen) — null означает "без профиля". В режиме настроек агента
 * это default_profile_id (действует только на новые чаты, см. комментарий
 * у `SettingsViewModel.onProfileChange`), в режиме настроек чата —
 * active_profile_id именно этого чата. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilePickerField(state: SettingsUiState, onSelected: (String?) -> Unit) {
    if (state.availableProfiles.isEmpty()) {
        Text(
            "Справочник профилей пуст — добавьте профиль на экране \"Профили\" (доступен с главного экрана).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val selected = state.availableProfiles.firstOrNull { it.id == state.selectedProfileId }
    val currentLabel = selected?.name ?: "Без профиля"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = currentLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Профиль") },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Без профиля") },
                onClick = { expanded = false; onSelected(null) },
            )
            state.availableProfiles.forEach { profile ->
                DropdownMenuItem(
                    text = { Text(profile.name) },
                    onClick = { expanded = false; onSelected(profile.id) },
                )
            }
        }
    }
}

/** Множественный выбор инвариантов из общего справочника (`GET /invariants`,
 * см. InvariantsScreen) — тем же приёмом, что и выбор скиллов профиля
 * (SkillsMultiSelectField в ProfilesScreen.kt): выпадающий список, который не
 * закрывается после выбора пункта (можно отметить несколько подряд), плюс
 * чипы текущего выбора под полем, по которым можно снять выбор одним
 * нажатием. Пустой список (`onChanged(emptyList())`) — валидное состояние
 * "ни один инвариант не выбран", а не "не изменено". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InvariantsPickerField(state: SettingsUiState, onChanged: (List<String>) -> Unit) {
    if (state.availableInvariants.isEmpty()) {
        Text(
            "Справочник инвариантов пуст — добавьте инвариант на экране \"Инварианты\" (доступен с главного экрана).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    var expanded by remember { mutableStateOf(false) }
    val selectedIds = state.selectedInvariantIds
    val selectedInvariants = state.availableInvariants.filter { it.id in selectedIds }
    val fieldValue = if (selectedInvariants.isEmpty()) "Не выбрано" else selectedInvariants.joinToString(", ") { it.title }

    fun toggle(invariantId: String) {
        val updated = if (invariantId in selectedIds) selectedIds - invariantId else selectedIds + invariantId
        onChanged(updated)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = fieldValue,
                onValueChange = {},
                readOnly = true,
                label = { Text("Инварианты (общий справочник)") },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                state.availableInvariants.forEach { invariant ->
                    val checked = invariant.id in selectedIds
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(invariant.title, style = MaterialTheme.typography.bodyMedium)
                                val kindLabel = invariant.kind?.let { INVARIANT_KIND_LABELS[it] ?: it }
                                val subtitle = listOfNotNull(
                                    kindLabel,
                                    "выключен".takeIf { !invariant.is_active },
                                ).joinToString(" · ")
                                if (subtitle.isNotEmpty()) {
                                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        },
                        leadingIcon = if (checked) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else null,
                        // Пункт НЕ закрывает список — можно отметить сразу
                        // несколько инвариантов, не открывая список заново.
                        onClick = { toggle(invariant.id) },
                    )
                }
            }
        }

        if (selectedInvariants.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                selectedInvariants.forEach { invariant ->
                    FilterChip(
                        selected = true,
                        onClick = { toggle(invariant.id) },
                        label = { Text(invariant.title) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Убрать «${invariant.title}»", modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }
    }
}
