package com.example.agentsapp.ui.memory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import com.example.agentsapp.data.remote.LongTermMemoryEntry
import com.example.agentsapp.data.remote.TaskSummary
import com.example.agentsapp.data.remote.WorkingMemoryEntry
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Экран "Память" одного чата: рабочая память (данные текущей задачи этого
 * чата), долговременная память агента (видна во всех его чатах) и снимок
 * памяти — то, что реально попадёт в следующий запрос модели. Профили-
 * пайплайны персонализации живут в отдельном общем справочнике
 * (`ProfilesScreen`, доступен с главного экрана) и подключаются к
 * агенту/чату в настройках, а не здесь. Основной инструмент для демонстрации
 * требований ТЗ ("явно выбирали, что и куда сохраняется", "проверьте, что
 * попадает в каждый слой и как это влияет на ответы").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemoryScreen(viewModel: MemoryViewModel, onBack: () -> Unit, onOpenTask: (taskId: String) -> Unit = {}) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showAddWorkingDialog by remember { mutableStateOf(false) }
    var showAddLongTermDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Типы памяти включаются/выключаются в настройках агента/чата (группа
    // "Память" на общем экране настроек), а не здесь — количество видимых
    // вкладок подстраивается под текущий выбор: вкладки "Рабочая" и
    // "Долговременная" скрываются, если соответствующий тип целиком
    // выключен (у долговременной — если выключены вообще все категории).
    // "Снимок" не гейтится и виден всегда.
    val visibleTabs = MemoryTab.entries.filter { tab ->
        when (tab) {
            // Видна только при включённой настройке "Отслеживать задачи"
            // (пункт 4/5 замечаний) — первая в списке (см. порядок enum MemoryTab).
            MemoryTab.TASKS -> state.taskTrackingEnabled
            MemoryTab.WORKING -> state.workingMemoryEnabled
            MemoryTab.LONG_TERM -> state.enabledLongTermCategories.isNotEmpty()
            MemoryTab.SNAPSHOT -> true
        }
    }
    val effectiveTab = if (state.selectedTab in visibleTabs) state.selectedTab else visibleTabs.firstOrNull() ?: MemoryTab.SNAPSHOT

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Память") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            when (effectiveTab) {
                MemoryTab.TASKS -> FloatingActionButton(onClick = viewModel::loadTasks) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Обновить список задач")
                }
                MemoryTab.WORKING -> FloatingActionButton(onClick = { showAddWorkingDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Сохранить в рабочую память")
                }
                MemoryTab.LONG_TERM -> FloatingActionButton(onClick = { showAddLongTermDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "Сохранить в долговременную память")
                }
                MemoryTab.SNAPSHOT -> FloatingActionButton(onClick = viewModel::loadSnapshot) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Обновить снимок")
                }
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = visibleTabs.indexOf(effectiveTab)) {
                visibleTabs.forEach { tab ->
                    Tab(
                        selected = effectiveTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = { Text(tabTitle(tab)) },
                    )
                }
            }

            when (effectiveTab) {
                MemoryTab.TASKS -> TasksTab(
                    state = state,
                    onOpenTask = onOpenTask,
                    onToggleIncludeCompleted = viewModel::toggleTasksIncludeCompleted,
                    onPauseTask = viewModel::pauseTaskInline,
                    onContinueTask = viewModel::continueTaskInline,
                    onExecuteTask = viewModel::executeTaskInline,
                )
                MemoryTab.WORKING -> WorkingMemoryTab(
                    entries = state.workingMemory,
                    onDelete = viewModel::deleteWorkingMemory,
                )
                MemoryTab.LONG_TERM -> LongTermMemoryTab(
                    entries = state.longTermMemory,
                    enabledCategories = state.enabledLongTermCategories,
                    onDelete = viewModel::deleteLongTermMemory,
                )
                MemoryTab.SNAPSHOT -> SnapshotTab(state = state)
            }
        }
    }

    if (showAddWorkingDialog) {
        KeyValueDialog(
            title = "Сохранить в рабочую память",
            keyLabel = "Ключ (например, \"текущая_задача\")",
            valueLabel = "Значение",
            onConfirm = { key, value ->
                viewModel.saveWorkingMemory(key, value)
                showAddWorkingDialog = false
            },
            onDismiss = { showAddWorkingDialog = false },
        )
    }

    if (showAddLongTermDialog) {
        LongTermMemoryDialog(
            enabledCategories = state.enabledLongTermCategories,
            onConfirm = { category, key, value ->
                viewModel.saveLongTermMemory(category, key, value)
                showAddLongTermDialog = false
            },
            onDismiss = { showAddLongTermDialog = false },
        )
    }

}

private fun tabTitle(tab: MemoryTab): String = when (tab) {
    MemoryTab.TASKS -> "Задачи"
    MemoryTab.WORKING -> "Рабочая"
    MemoryTab.LONG_TERM -> "Долговременная"
    MemoryTab.SNAPSHOT -> "Снимок"
}

// ---- вкладка "Задачи" (День 13) --------------------------------------------

@Composable
private fun TasksTab(
    state: MemoryUiState,
    onOpenTask: (String) -> Unit,
    onToggleIncludeCompleted: () -> Unit,
    onPauseTask: (String) -> Unit,
    onContinueTask: (String) -> Unit,
    onExecuteTask: (String) -> Unit,
) {
    if (state.isTasksLoading && state.tasks.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        TextButton(onClick = onToggleIncludeCompleted, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(if (state.tasksIncludeCompleted) "Скрыть выполненные" else "Показать выполненные")
        }
        if (state.tasks.isEmpty()) {
            EmptyHint("У этого чата пока нет задач.")
            return
        }
        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.tasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    isRunning = task.id in state.runningTaskIds,
                    runIsAutoPause = state.runningTaskAutoPause[task.id] ?: true,
                    onOpenTask = { onOpenTask(task.id) },
                    onPauseTask = { onPauseTask(task.id) },
                    onContinueTask = { onContinueTask(task.id) },
                    onExecuteTask = { onExecuteTask(task.id) },
                )
            }
        }
    }
}

/** Карточка задачи во вкладке "Задачи" — редизайн "Менеджера задач"
 * (замечание пользователя): "Продолжить"/"Выполнить" обращаются к модели (см.
 * `MemoryViewModel.continueTaskInline`/`executeTaskInline`), "Пауза" —
 * единственное оставшееся ручное действие; дублирует по функционалу кнопку
 * карточки-подтверждения на экране чата. Пока идёт шаг/цикл — вместо кнопок
 * показывается индикатор и кнопка "Пауза" (дополнение пользователя: прервать
 * "Выполнить" можно в любой момент). */
@Composable
private fun TaskCard(
    task: TaskSummary,
    isRunning: Boolean,
    runIsAutoPause: Boolean,
    onOpenTask: () -> Unit,
    onPauseTask: () -> Unit,
    onContinueTask: () -> Unit,
    onExecuteTask: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).clickable(onClick = onOpenTask)) {
                Text(task.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(
                    "${task.status_display} · ${task.state_display_name}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isRunning) {
                    Text(
                        text = if (runIsAutoPause) "Выполняется следующий этап…" else "Менеджер задач выполняет задачу…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (task.status != "done") {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp).padding(end = 4.dp))
                    IconButton(onClick = onPauseTask) {
                        Icon(Icons.Filled.Pause, contentDescription = "Поставить на паузу")
                    }
                } else {
                    if (task.status == "active") {
                        IconButton(onClick = onPauseTask) {
                            Icon(Icons.Filled.Pause, contentDescription = "Поставить на паузу")
                        }
                    }
                    IconButton(onClick = onContinueTask, enabled = task.next_state_display_name != null) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Продолжить")
                    }
                    IconButton(onClick = onExecuteTask, enabled = task.next_state_display_name != null) {
                        Icon(Icons.Filled.SkipNext, contentDescription = "Выполнить")
                    }
                }
            }
        }
    }
}

// ---- вкладка "Рабочая память" ----------------------------------------------

@Composable
private fun WorkingMemoryTab(entries: List<WorkingMemoryEntry>, onDelete: (String) -> Unit) {
    if (entries.isEmpty()) {
        EmptyHint("Рабочая память этого чата пуста — сохраните факт вручную кнопкой ниже, или включите тумблер \"Разрешить агенту сохранять память\" в настройках чата, чтобы агент мог сохранять их сам.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(entries, key = { it.id }) { entry ->
            MemoryEntryCard(
                title = entry.key,
                value = entry.value,
                source = entry.source,
                onDelete = { onDelete(entry.key) },
            )
        }
    }
}

// ---- вкладка "Долговременная память" ---------------------------------------

@Composable
private fun LongTermMemoryTab(
    entries: List<LongTermMemoryEntry>,
    enabledCategories: List<String>,
    onDelete: (category: String, key: String) -> Unit,
) {
    val visibleEntries = entries.filter { it.category in enabledCategories }
    if (visibleEntries.isEmpty()) {
        EmptyHint("Долговременная память этого агента пуста для включённых типов — она видна во всех его чатах, в отличие от рабочей памяти (только этот чат). Какие типы включены — настраивается в настройках агента/чата, группа \"Память\".")
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(visibleEntries, key = { it.id }) { entry ->
            MemoryEntryCard(
                title = "[${LONG_TERM_MEMORY_CATEGORY_LABELS[entry.category] ?: entry.category}] ${entry.key}",
                value = entry.value,
                source = entry.source,
                onDelete = { onDelete(entry.category, entry.key) },
            )
        }
    }
}

@Composable
private fun MemoryEntryCard(title: String, value: String, source: String, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Text(value, style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (source == "agent") "сохранено агентом" else "сохранено вручную",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить")
            }
        }
    }
}

// ---- вкладка "Снимок" -------------------------------------------------------

@Composable
private fun SnapshotTab(state: MemoryUiState) {
    if (state.isSnapshotLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val snapshot = state.snapshot
    if (snapshot == null) {
        EmptyHint("Нажмите кнопку обновления, чтобы увидеть, что реально уйдёт в следующий запрос модели.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SnapshotSection(
                "Кратковременная память (диалог)",
                "${snapshot.short_term.message_count} сообщений в эффективном контексте.",
            )
        }
        item {
            val profileText = snapshot.active_profile?.let {
                "«${it.name}»" + (it.style?.let { s -> ", стиль: $s" } ?: "")
            } ?: "не подключён"
            SnapshotSection("Активный профиль", profileText)
        }
        item {
            SnapshotSection(
                "Долговременная память",
                if (snapshot.long_term_memory.isEmpty()) "пусто" else
                    snapshot.long_term_memory.joinToString("\n") { "[${it.category}] ${it.key}: ${it.value}" },
            )
        }
        item {
            SnapshotSection(
                "Рабочая память",
                if (snapshot.working_memory.isEmpty()) "пусто" else
                    snapshot.working_memory.joinToString("\n") { "${it.key}: ${it.value}" },
            )
        }
        item {
            val toolNames = snapshot.available_tools.mapNotNull {
                (it["function"] as? JsonObject)?.get("name")
                    ?.let { n -> (n as? JsonPrimitive)?.content }
            }
            SnapshotSection(
                "Доступные функции (${if (snapshot.memory_tools_enabled) "память включена" else "память выключена"})",
                if (toolNames.isEmpty()) "нет" else toolNames.joinToString(", "),
            )
        }
    }
}

@Composable
private fun SnapshotSection(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun EmptyHint(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- диалоги -----------------------------------------------------------------

@Composable
private fun KeyValueDialog(
    title: String,
    keyLabel: String,
    valueLabel: String,
    onConfirm: (key: String, value: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(keyLabel) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(valueLabel) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = key.isNotBlank(), onClick = { onConfirm(key.trim(), value) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LongTermMemoryDialog(
    enabledCategories: List<String>,
    onConfirm: (category: String, key: String, value: String) -> Unit,
    onDismiss: () -> Unit,
) {
    // Список категорий, доступных для выбора, ограничен включёнными на
    // экране "Память и профиль" типами (сохранение вручную сервер всё равно
    // разрешает для любой категории, но предлагать в интерфейсе выключенные
    // типы было бы запутывающим — пользователь их не увидит ни в снимке
    // памяти, ни в промпте модели).
    val options = enabledCategories.ifEmpty { LONG_TERM_MEMORY_CATEGORIES }
    var key by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(options.first()) }
    var expanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Сохранить в долговременную память") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (enabledCategories.isEmpty()) {
                    Text(
                        "Все типы долговременной памяти сейчас выключены на этом экране — запись всё равно сохранится, но не будет использоваться моделью, пока вы не включите соответствующий тип.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        value = LONG_TERM_MEMORY_CATEGORY_LABELS[category] ?: category,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Категория") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                    )
                    ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        options.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(LONG_TERM_MEMORY_CATEGORY_LABELS[option] ?: option) },
                                onClick = { category = option; expanded = false },
                            )
                        }
                    }
                }
                OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text("Ключ") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Значение") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(enabled = key.isNotBlank(), onClick = { onConfirm(category, key.trim(), value) }) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
