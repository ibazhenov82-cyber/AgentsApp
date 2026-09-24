package com.example.agentsapp.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.agentsapp.data.remote.Branch
import com.example.agentsapp.data.remote.ChatStats
import com.example.agentsapp.data.remote.McpCallEvent
import com.example.agentsapp.data.remote.Message
import com.example.agentsapp.data.remote.TaskEvent
import com.example.agentsapp.data.remote.TaskSummary
import com.example.agentsapp.ui.common.SettingsSummary
import com.example.agentsapp.ui.common.toolTitle
import com.example.agentsapp.ui.theme.branchBadgeColor
import com.example.agentsapp.ui.theme.summaryBubbleColor
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.math.roundToInt

/**
 * Экран одного чата. Показывает агента-владельца и модель в одну строку
 * (замечание 8), ленту сообщений (с выделением сообщений вне эффективного
 * контекста — после суммаризации и/или обрезки стратегией управления
 * контекстом — более бледным цветом), панель фактов Sticky Facts, поле
 * ввода со сведениями о токенах прямо над ним (замечание 12) и кебаб-меню
 * с суммаризацией и ветками диалога (замечание 13, Доработка 8).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenChatSettings: () -> Unit,
    onOpenMemory: () -> Unit,
    // Пункт 4 замечаний: текущая задача на экране чата — НЕ бэдж, а отдельная
    // кликабельная строка-ссылка перед перепиской. Одна открытая задача — сразу
    // на её детали; несколько (пункт 7: в чате может быть несколько
    // параллельных открытых задач) — на список задач чата (вкладка "Задачи"
    // экрана "Память"). Значения по умолчанию — на случай навигационных
    // графов, ещё не прокинувших эти колбэки.
    onOpenTask: (taskId: String) -> Unit = {},
    onOpenTaskList: () -> Unit = {},
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var showBranchPickerDialog by remember { mutableStateOf(false) }
    var showDeleteBranchConfirm by remember { mutableStateOf(false) }

    // Обновляем производные от настроек чата поля (стратегия контекста,
    // автосуммаризация, полный Settings для бейджей) при каждом входе на этот
    // экран — в частности, при возврате с экрана настроек чата (Composable
    // пересоздаётся при навигации туда и обратно, а ChatViewModel — нет).
    // Без этого бейджи оставались устаревшими, пока пользователь не выходил
    // на главный экран и не открывал чат заново.
    LaunchedEffect(Unit) {
        viewModel.refreshSettings()
    }

    // Разовое сообщение об ошибке — показываем в Snackbar и сразу сбрасываем.
    LaunchedEffect(state.errorMessage) {
        val message = state.errorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeErrorMessage()
        }
    }

    // Карточки-подтверждения задач (редизайн "Менеджера задач") — под
    // последним сообщением, по одной на каждую открытую (активную/на паузе)
    // задачу чата (пункт 4 замечаний пользователя: обычно она одна, но
    // список чата может отслеживать несколько задач одновременно, пункт 7).
    val taskCards = state.openTasks

    // Автоскролл вниз (замечание 11, доработка "в самый низ при появлении
    // любой новой информации, включая системные сообщения"): единый расчёт
    // числа элементов списка и единая точка скролла, чтобы не было гонки
    // между несколькими независимыми эффектами.
    val itemCount = state.messages.size + (if (state.streamingDraft != null) 1 else 0) +
        (if (state.isSummarizing) 1 else 0) + taskCards.size

    // Мгновенный переход вниз при открытии чата (без анимации).
    LaunchedEffect(state.isLoading) {
        if (!state.isLoading) {
            listState.scrollToTrueBottom(itemCount, animate = false)
        }
    }

    // Плавный скролл при любом новом содержимом: новое/удалённое сообщение
    // (включая сообщения с ролью "error", которые сервер мог сохранить как
    // системные), появление/исчезновение индикатора суммаризации. Ключ —
    // весь список `state.messages` (а не только его размер), поэтому
    // эффект перезапускается и тогда, когда состав сообщений поменялся, а
    // их количество осталось тем же (например, замена локальной заглушки
    // отправленного сообщения на подтверждённое сервером).
    LaunchedEffect(state.messages, state.isSummarizing) {
        listState.scrollToTrueBottom(itemCount, animate = true)
    }

    // Замечание 15: во время стриминга автоскролл начинается только когда
    // начинает заполняться блок ОТВЕТА — эта корутина не перезапускается,
    // пока `content` остаётся пустым (ключ не меняется во время рассуждений).
    LaunchedEffect(state.streamingDraft?.content) {
        if (!state.streamingDraft?.content.isNullOrEmpty()) {
            listState.scrollToTrueBottom(itemCount, animate = true)
        }
    }

    Scaffold(
        topBar = {
            if (state.isSelectionMode) {
                TopAppBar(
                    title = { Text("Выбрано: ${state.selectedIds.size}") },
                    navigationIcon = {
                        IconButton(onClick = viewModel::clearSelection) {
                            Icon(Icons.Filled.Close, contentDescription = "Отменить выбор")
                        }
                    },
                    actions = {
                        IconButton(onClick = viewModel::requestDeleteSelected) {
                            Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                        }
                    },
                )
            } else {
                TopAppBar(
                    title = {
                        EditableChatTitle(
                            title = state.chatTitle,
                            messageCount = state.messageCount,
                            onTitleChange = viewModel::renameChat,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        // Все действия экрана — в одном кебаб-меню (по замечанию
                        // пользователя): "Настройки чата" и "Память и профиль" больше
                        // не отдельные кнопки на тулбаре, а первые пункты меню.
                        ChatOverflowMenu(
                            state = state,
                            viewModel = viewModel,
                            onOpenChatSettings = onOpenChatSettings,
                            onOpenMemory = onOpenMemory,
                            onShowBranchPicker = { showBranchPickerDialog = true },
                            onShowDeleteBranchConfirm = { showDeleteBranchConfirm = true },
                        )
                    },
                )
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
        },
        bottomBar = {
            Column {
                ContextInfoBar(stats = state.stats)
                MessageInputBar(
                    text = state.inputText,
                    onTextChange = viewModel::onInputTextChange,
                    onSend = viewModel::sendMessage,
                    enabled = !state.isBusy,
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!state.isLoading) {
                AgentModelBar(state = state)
                // Бейдж "Профиль" — в общем ряду с остальными бейджами настроек
                // (справа от бейджа "Память: ..."), а не отдельным блоком (по
                // замечанию пользователя) — см. SettingsSummary.
                state.settings?.let { settings ->
                    SettingsSummary(
                        settings = settings,
                        hasInvariants = state.hasInvariants,
                        profileName = state.activeProfileName,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                // Ссылка на текущую задачу(и) — отдельной строкой, НЕ бэдж
                // (пункт 4 замечаний), сразу перед лентой сообщений.
                if (state.openTasks.isNotEmpty()) {
                    CurrentTaskLinkRow(
                        openTasks = state.openTasks,
                        taskManagerActiveTaskId = state.taskManagerActiveTaskId,
                        onOpenTask = onOpenTask,
                        onOpenTaskList = onOpenTaskList,
                        onPauseTask = viewModel::pauseTask,
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(if (state.showFactsPanel) 0.6f else 1f),
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        MessagesList(
                            state = state,
                            viewModel = viewModel,
                            listState = listState,
                            onOpenTask = onOpenTask,
                            taskCards = taskCards,
                        )
                        if (itemCount > 0) {
                            ScrollShortcutButtons(
                                itemCount = itemCount,
                                listState = listState,
                                coroutineScope = coroutineScope,
                                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
                            )
                        }
                    }
                }
                if (state.showFactsPanel) {
                    HorizontalDivider(modifier = Modifier.fillMaxHeight().width(1.dp))
                    FactsPanel(
                        factsJson = state.latestFacts,
                        modifier = Modifier.fillMaxHeight().weight(0.4f),
                    )
                }
            }
        }
    }

    if (state.showDeleteDialog) {
        val count = state.selectedIds.size
        AlertDialog(
            onDismissRequest = viewModel::dismissDeleteDialog,
            title = {
                Text(if (count == 1) "Удалить выбранное сообщение" else "Удалить выбранные сообщения")
            },
            text = { Text("Это действие необратимо.") },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteSelected) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDeleteDialog) { Text("Отмена") }
            },
        )
    }

    if (showDeleteBranchConfirm) {
        val number = state.selectedBranch
        val branchName = state.branches.firstOrNull { it.number == number }?.name ?: "Ветка $number"
        AlertDialog(
            onDismissRequest = { showDeleteBranchConfirm = false },
            title = { Text("Удалить ветку \"$branchName\"?") },
            text = { Text("Ветка и все её сообщения будут удалены безвозвратно.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteBranch(number)
                    showDeleteBranchConfirm = false
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteBranchConfirm = false }) { Text("Отмена") }
            },
        )
    }

    if (showBranchPickerDialog) {
        BranchPickerDialog(
            branches = state.branches,
            selected = state.selectedBranch,
            onSelect = { number ->
                viewModel.selectBranch(number)
                showBranchPickerDialog = false
            },
            onDismiss = { showBranchPickerDialog = false },
        )
    }
}

/** Докручивает список до НАСТОЯЩЕГО конца содержимого. Наивный приём
 * `animateScrollToItem(last, scrollOffset = Int.MAX_VALUE)` ненадёжен:
 * Compose оценивает дистанцию анимации ДО того, как измерит реальный размер
 * ещё не отрисованных последних элементов, поэтому анимация иногда
 * останавливалась на несколько сообщений раньше конца, а повторное
 * нажатие уже ничего не докручивало (список считал себя "у той же цели").
 * Здесь вместо этого: сначала подводим последний элемент во область
 * видимости (обычным `scrollToItem`/`animateScrollToItem`, без магического
 * смещения), а затем добираем остаток точными шагами `scrollBy` — они
 * работают по уже измеренным, реальным размерам и останавливаются сами,
 * когда `canScrollForward` становится false, то есть список действительно
 * упёрся в конец. */
private suspend fun LazyListState.scrollToTrueBottom(itemCount: Int, animate: Boolean) {
    if (itemCount <= 0) return
    val lastIndex = itemCount - 1
    if (animate) animateScrollToItem(lastIndex) else scrollToItem(lastIndex)
    var guard = 0
    while (canScrollForward && guard < 30) {
        val consumed = scrollBy(4000f)
        guard++
        if (consumed == 0f) break
    }
}

/** Кнопки быстрого перемещения по ленте сообщений — наверх (к первому
 * сообщению) и вниз (в самый низ, см. [scrollToTrueBottom]). Показываются
 * поверх ленты, у правого нижнего края; каждая кнопка недоступна (потушена),
 * если прокрутка в её сторону уже некуда — при ручной прокрутке до упора
 * вверх кнопка "наверх" гаснет, аналогично для "вниз". */
@Composable
private fun ScrollShortcutButtons(
    itemCount: Int,
    listState: LazyListState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalIconButton(
            enabled = listState.canScrollBackward,
            onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } },
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Наверх")
        }
        FilledTonalIconButton(
            enabled = listState.canScrollForward,
            onClick = { coroutineScope.launch { listState.scrollToTrueBottom(itemCount, animate = true) } },
        ) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Вниз")
        }
    }
}

/** Inline-редактирование названия чата прямо в шапке экрана (там же, где
 * кнопка "назад") — по тапу заголовок превращается в текстовое поле;
 * подтверждение — по уходу фокуса или по кнопке "Готово" на клавиатуре.
 * Название сохраняется через [onTitleChange] тем же способом (debounce),
 * что и остальные текстовые поля настроек в приложении. Рядом с названием (в
 * режиме просмотра, не редактирования) — число сообщений в чате, например
 * "Чат 1 (19 сообщений)". */
@Composable
private fun EditableChatTitle(title: String, messageCount: Int, onTitleChange: (String) -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf(title) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    if (isEditing) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }
        BasicTextField(
            value = draft,
            onValueChange = { draft = it },
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(color = LocalContentColor.current),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(LocalContentColor.current),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            interactionSource = remember { MutableInteractionSource() },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onFocusChanged { focusState ->
                    if (!focusState.isFocused) {
                        isEditing = false
                        val trimmed = draft.trim()
                        if (trimmed.isNotEmpty() && trimmed != title) {
                            onTitleChange(trimmed)
                        } else {
                            draft = title
                        }
                    }
                },
        )
    } else {
        Text(
            text = "${title.ifBlank { "Чат" }} ($messageCount ${messageCountNoun(messageCount)})",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable {
                draft = title
                isEditing = true
            },
        )
    }
}

/** Правильное склонение слова "сообщение" под число (1 сообщение,
 * 2 сообщения, 5 сообщений, 11 сообщений, 21 сообщение и т.д.). */
private fun messageCountNoun(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "сообщений"
        mod10 == 1 -> "сообщение"
        mod10 in 2..4 -> "сообщения"
        else -> "сообщений"
    }
}

/** Кебаб-меню (замечание 13): настройки чата и память/профиль (по замечанию
 * пользователя — перенесены сюда с тулбара, первыми пунктами), затем
 * суммаризация, ветки диалога и — при активной
 * стратегии Sticky Facts — переключатель панели фактов. Полностью
 * заблокировано во время отправки/суммаризации (замечание 14). Все пункты
 * снабжены иконками; "Ветки диалога" не показывается, если в чате ещё нет ни
 * одной ветки, а "Удалить ветку N" появляется под ним только когда выбрана
 * (не основная) ветка. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatOverflowMenu(
    state: ChatUiState,
    viewModel: ChatViewModel,
    onOpenChatSettings: () -> Unit,
    onOpenMemory: () -> Unit,
    onShowBranchPicker: () -> Unit,
    onShowDeleteBranchConfirm: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }, enabled = !state.isBusy) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Ещё")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Настройки чата") },
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenChatSettings()
                },
            )
            // Память и профили-пайплайны (модель памяти + персонализация) —
            // отдельный пункт от настроек чата: отдельная сущность с
            // собственным CRUD и снимком памяти для проверки.
            DropdownMenuItem(
                text = { Text("Память и профиль") },
                leadingIcon = { Icon(Icons.Filled.Memory, contentDescription = null) },
                onClick = {
                    expanded = false
                    onOpenMemory()
                },
            )
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Подготовить суммарный запрос") },
                leadingIcon = { Icon(Icons.Filled.Summarize, contentDescription = null) },
                enabled = state.canSummarize,
                onClick = {
                    expanded = false
                    viewModel.summarize()
                },
            )
            DropdownMenuItem(
                text = { Text("Добавить ветку диалога") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    expanded = false
                    viewModel.createBranch()
                },
            )
            if (state.branches.isNotEmpty()) {
                DropdownMenuItem(
                    text = { Text("Ветки диалога") },
                    leadingIcon = { Icon(Icons.Filled.AccountTree, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onShowBranchPicker()
                    },
                )
                if (state.selectedBranch != 0) {
                    val branchName = state.branches.firstOrNull { it.number == state.selectedBranch }?.name
                        ?: "Ветка ${state.selectedBranch}"
                    DropdownMenuItem(
                        text = { Text("Удалить \"$branchName\"") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            expanded = false
                            onShowDeleteBranchConfirm()
                        },
                    )
                }
            }
            if (state.contextStrategy == "sticky_facts") {
                DropdownMenuItem(
                    text = { Text(if (state.showFactsPanel) "Скрыть факты" else "Показать факты") },
                    leadingIcon = {
                        Icon(
                            if (state.showFactsPanel) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        expanded = false
                        viewModel.toggleFactsPanel()
                    },
                )
            }
        }
    }
}

@Composable
private fun BranchPickerDialog(
    branches: List<Branch>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ветки диалога") },
        text = {
            Column {
                BranchOptionRow("Основная ветка", selected == 0) { onSelect(0) }
                branches.forEach { branch ->
                    BranchOptionRow(branch.name, selected == branch.number) { onSelect(branch.number) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Закрыть") }
        },
    )
}

@Composable
private fun BranchOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/** Агент и модель в одну строку, названия — жирным (замечание 8); суффикс
 * "(локальная)" вместо отдельного бейджа (замечания 7/8). */
@Composable
private fun AgentModelBar(state: ChatUiState) {
    val modelText = state.modelDisplayName + if (state.isModelLocal) " (локальная)" else ""
    Text(
        text = buildAnnotatedString {
            append("Агент: ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(state.agentName) }
            append("   ·   Модель: ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(modelText) }
        },
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    )
}

/** Строка-ссылка на текущую задачу(и) этого чата — НЕ бэдж, отдельная
 * кликабельная строка перед перепиской (пункт 4 замечаний пользователя).
 * Одна открытая задача — заголовок сразу с её названием/статусом, переход
 * прямо на детали. Основное управление задачей (редизайн "Менеджера задач") —
 * теперь карточка-подтверждение под последним сообщением (см.
 * [TaskConfirmationCard]), а не эта строка: здесь остаётся только быстрый
 * доступ к паузе, пока для задачи прямо сейчас идёт шаг/цикл ("Продолжить"/
 * "Выполнить") — по замечанию пользователя, кнопка "Пауза" должна быть
 * доступна в любой момент, не только прокрутив ленту вниз до карточки.
 * Несколько параллельных открытых задач (пункт 7 — в чате их может быть
 * несколько одновременно) — обобщённая ссылка "Задачи (N)" на список задач
 * этого чата (там уже понятно, к какой из них что относится). */
@Composable
private fun CurrentTaskLinkRow(
    openTasks: List<TaskSummary>,
    taskManagerActiveTaskId: String?,
    onOpenTask: (String) -> Unit,
    onOpenTaskList: () -> Unit,
    onPauseTask: (String) -> Unit,
) {
    val singleTask = openTasks.singleOrNull()
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (singleTask != null) {
            val running = taskManagerActiveTaskId == singleTask.id
            val label = if (running) {
                "Задача: ${singleTask.title} — Менеджер задач выполняет задачу…"
            } else {
                "Задача: ${singleTask.title} — ${singleTask.status_display}"
            }
            TextButton(
                onClick = { onOpenTask(singleTask.id) },
                modifier = Modifier.weight(1f),
            ) {
                Text(label, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
            }
            if (running) {
                IconButton(onClick = { onPauseTask(singleTask.id) }) {
                    Icon(Icons.Filled.Pause, contentDescription = "Поставить на паузу")
                }
            }
        } else {
            TextButton(
                onClick = onOpenTaskList,
                modifier = Modifier.weight(1f),
            ) {
                Text("Задачи (${openTasks.size})", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Start)
            }
        }
    }
}

/** Сведения о токенах и заполнении контекста — прямо над полем ввода, в
 * одну строку (замечание 12), а не в топ-баре. */
@Composable
private fun ContextInfoBar(stats: ChatStats?) {
    if (stats == null) return
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ratio = stats.context_fill_ratio
            val isOverflow = ratio != null && ratio > 1.0
            val displayPercent = if (ratio != null) minOf((ratio * 100).roundToInt(), 100) else null
            Text(
                text = if (displayPercent != null) "Заполнение контекста: $displayPercent%" else "",
                style = MaterialTheme.typography.labelSmall,
                color = if (isOverflow) MaterialTheme.colorScheme.error else Color.Unspecified,
            )
            Text(
                text = "Токены — вход: ${stats.prompt_tokens}, выход: ${stats.completion_tokens}, всего: ${stats.total_tokens}",
                style = MaterialTheme.typography.labelSmall,
            )
        }
        val ratio = stats.context_fill_ratio
        if (ratio != null) {
            val isOverflow = ratio > 1.0
            LinearProgressIndicator(
                progress = { ratio.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(3.dp).padding(top = 2.dp),
                color = if (isOverflow) MaterialTheme.colorScheme.error else ProgressIndicatorDefaults.linearColor,
            )
        }
    }
}

/** Панель фактов Sticky Facts — скрываемая, ~40% ширины экрана (Доработка 7). */
@Composable
private fun FactsPanel(factsJson: String?, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(12.dp)) {
        Text("Факты", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (factsJson.isNullOrBlank()) {
            Text(
                "Пока нет сохранённых фактов.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Box(modifier = Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
                JsonTreeView(rawJson = factsJson)
            }
        }
    }
}

/** Лента сообщений. Сообщения раньше `active_context_start_id` (то есть вне
 * эффективного контекста — до границы суммаризации и/или обрезанные
 * стратегией Sliding Window/Sticky Facts) отрисовываются с уменьшенной
 * непрозрачностью — единая логика вместо двух разных (замечание 6 доработки). */
@Composable
private fun MessagesList(
    state: ChatUiState,
    viewModel: ChatViewModel,
    listState: LazyListState,
    onOpenTask: (String) -> Unit,
    taskCards: List<TaskSummary>,
) {
    val messages = state.messages
    val activeStartId = state.stats?.active_context_start_id
    val firstActiveIndex = if (activeStartId != null) messages.indexOfFirst { it.id >= activeStartId } else -1
    // Разделитель "Начало контекста" — только если реально есть хотя бы одно
    // более бледное (вне эффективного контекста) сообщение ПЕРЕД первым
    // "активным"; если весь чат и так помещается в контекст, разделитель не
    // нужен (firstActiveIndex == 0 или -1).
    val showContextStartDivider = firstActiveIndex > 0

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(messages, key = { _, message -> message.id }) { index, message ->
            if (showContextStartDivider && index == firstActiveIndex) {
                ContextStartDivider()
            }
            val faded = activeStartId != null && message.id < activeStartId
            MessageItem(
                message = message,
                faded = faded,
                selected = message.id in state.selectedIds,
                isSelectionMode = state.isSelectionMode,
                reasoningExpanded = message.id in state.expandedReasoningIds,
                factsExpanded = message.id in state.expandedFactsIds,
                onLongClick = { viewModel.onMessageLongPress(message.id) },
                onClick = {
                    if (state.isSelectionMode) {
                        viewModel.onMessageTap(message.id)
                    } else if (message.role == "assistant" && !message.reasoning_content.isNullOrBlank()) {
                        viewModel.toggleReasoningExpanded(message.id)
                    }
                },
                onToggleFacts = { viewModel.toggleMessageFactsExpanded(message.id) },
                onOpenTask = onOpenTask,
            )
        }

        val draft = state.streamingDraft
        if (draft != null) {
            item(key = "streaming-draft") {
                DraftBubble(draft)
            }
        }

        if (state.isSummarizing) {
            item(key = "summarizing-indicator") {
                SummarizingIndicator()
            }
        }

        // Карточки-подтверждения задач — под последним сообщением (пункт 2
        // замечаний пользователя: "непосредственно в чате под последним
        // сообщением выводить запрос"), после черновика/индикатора
        // суммаризации, если они сейчас есть.
        items(taskCards, key = { "task-card-${it.id}" }) { task ->
            TaskConfirmationCard(
                task = task,
                isRunning = state.taskManagerActiveTaskId == task.id,
                runIsAutoPause = state.taskManagerAutoPause,
                onOpenTask = onOpenTask,
                onContinueStep = viewModel::continueTaskStep,
                onExecute = viewModel::executeTaskUntilDone,
                onPause = viewModel::pauseTask,
            )
        }
    }
}

/** Карточка-подтверждение задачи под последним сообщением чата — редизайн
 * "Менеджера задач" (замечание пользователя): прежде чем модель продолжит
 * планирование/выполнение/проверку задачи, она останавливается (задача на
 * паузе), и здесь показывается "Задача: …, следующий этап: …. Продолжить
 * выполнение?" с двумя кнопками — "Продолжить" (один этап, потом снова
 * пауза) и "Выполнить" (без остановок до done); явного действия "Отклонить"
 * в системе больше нет. Пока для этой задачи прямо сейчас идёт шаг/цикл —
 * вместо кнопок показывается индикатор и кнопка "Пауза" (по дополнению
 * пользователя — прервать "Выполнить" можно в любой момент). Та же карточка
 * обслуживает и задачу, оставшуюся активной без паузы (paused = false) —
 * тогда просто не показывается пометка "на паузе", а кнопки остаются
 * доступны. */
@Composable
private fun TaskConfirmationCard(
    task: TaskSummary,
    isRunning: Boolean,
    runIsAutoPause: Boolean,
    onOpenTask: (String) -> Unit,
    onContinueStep: (String) -> Unit,
    onExecute: (String) -> Unit,
    onPause: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = "Задача: ${task.title}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onOpenTask(task.id) },
            )
            Spacer(Modifier.height(4.dp))
            if (isRunning) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (runIsAutoPause) "Выполняется следующий этап…" else "Менеджер задач выполняет задачу…",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { onPause(task.id) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Пауза")
                }
            } else {
                val nextStage = task.next_state_display_name
                Text(
                    text = if (nextStage != null) {
                        "Следующий этап: $nextStage. Продолжить выполнение?"
                    } else {
                        "Дальнейшее продвижение недоступно — этап: ${task.state_display_name}."
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { onContinueStep(task.id) },
                        enabled = nextStage != null,
                        modifier = Modifier.weight(1f),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) { Text("Продолжить") }
                    Button(
                        onClick = { onExecute(task.id) },
                        enabled = nextStage != null,
                        modifier = Modifier.weight(1f),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) { Text("Выполнить") }
                }
            }
        }
    }
}

/** Разделитель перед первым сообщением, попадающим в эффективный контекст
 * (после суммаризации и/или обрезки стратегией Sliding Window/Sticky Facts) —
 * визуально отделяет более бледные (вне контекста) сообщения выше от тех,
 * что реально уйдут в следующий запрос модели. */
@Composable
private fun ContextStartDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f))
        Text(
            text = "Начало контекста",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HorizontalDivider(modifier = Modifier.weight(1f))
    }
}

/** Индикатор суммаризации под последним сообщением (замечание 14). */
@Composable
private fun SummarizingIndicator() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        // Тот же текст статуса, что и при автосуммаризации в потоковом режиме
        // (событие status "Выполняется суммаризация чата") — по замечанию
        // пользователя индикация должна быть идентичной независимо от того,
        // вызвана ли суммаризация вручную (кнопкой) или автоматически.
        Text("Выполняется суммаризация чата", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MessageItem(
    message: Message,
    faded: Boolean,
    selected: Boolean,
    isSelectionMode: Boolean,
    reasoningExpanded: Boolean,
    factsExpanded: Boolean,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
    onToggleFacts: () -> Unit,
    onOpenTask: (String) -> Unit,
) {
    val alignment = when (message.role) {
        "user" -> Alignment.CenterEnd
        "error" -> Alignment.Center
        else -> Alignment.CenterStart
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (faded) 0.5f else 1f),
        contentAlignment = alignment,
    ) {
        when (message.role) {
            "user" -> {
                Column(horizontalAlignment = Alignment.End) {
                    MessageBadges(message, onToggleFacts)
                    val bubbleColor = if (message.is_summary) summaryBubbleColor() else MaterialTheme.colorScheme.primaryContainer
                    Bubble(
                        color = bubbleColor,
                        selected = selected,
                        fullWidth = false,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ) {
                        MessageContent(message)
                    }
                    // Токены НА ВХОД для этого конкретного обмена — то же
                    // значение prompt_tokens, что и в usage ответа провайдера
                    // (например, DeepSeek API), а не оценка длины текста.
                    message.prompt_tokens?.let { tokens ->
                        Text(
                            text = "$tokens токенов (вход)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp, top = 2.dp),
                        )
                    }
                    if (factsExpanded && !message.facts.isNullOrBlank()) {
                        FactsBlock(message.facts)
                    }
                }
            }
            "assistant" -> {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start,
                ) {
                    MessageBadges(message, onToggleFacts)
                    // Переходы состояний задач, применённые за этот ответ —
                    // с явным указанием, к какой именно задаче они относятся
                    // (пункт 7 замечаний: в чате может быть несколько
                    // параллельных открытых задач, неоднозначность недопустима).
                    TaskEventChips(message, onOpenTask)
                    // Инструменты, вызванные через MCP-сервер за этот ответ
                    // (новое ТЗ) — тот же принцип, что и переходы задач выше.
                    McpCallChips(message)
                    Bubble(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        selected = selected,
                        fullWidth = true,
                        onClick = onClick,
                        onLongClick = onLongClick,
                    ) {
                        Column {
                            MessageContent(message)
                            if (!message.reasoning_content.isNullOrBlank()) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp),
                                ) {
                                    Icon(
                                        imageVector = if (reasoningExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = null,
                                        modifier = Modifier.height(16.dp),
                                    )
                                    Text(
                                        text = "Показать рассуждения",
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                                if (reasoningExpanded) {
                                    Text(
                                        text = message.reasoning_content,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp),
                                    )
                                }
                            }
                        }
                    }
                    // Токены НА ВЫХОД (completion_tokens) — сколько сгенерировал
                    // сам ответ, отдельно от токенов на вход, показанных под
                    // сообщением пользователя (см. ветку "user" выше).
                    val telemetry = buildList {
                        message.duration_ms?.let { add("%.1f с".format(it / 1000.0)) }
                        message.completion_tokens?.let { add("$it токенов (выход)") }
                    }
                    if (telemetry.isNotEmpty()) {
                        Text(
                            text = telemetry.joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                        )
                    }
                    if (factsExpanded && !message.facts.isNullOrBlank()) {
                        FactsBlock(message.facts)
                    }
                }
            }
            "error" -> {
                Column(
                    modifier = Modifier
                        .widthIn(max = 320.dp)
                        .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Ошибка",
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Text(
                        text = message.content,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            else -> {
                Bubble(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    selected = selected,
                    fullWidth = false,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ) {
                    MessageContent(message)
                }
            }
        }
    }
}

/** Ряд бейджей над сообщением: "Менеджер задач" (обновление "Дня 13" — см.
 * `Message.is_task_manager_step`, чтобы не создавалось впечатление, будто
 * что-то потерялось из истории — перед таким сообщением нет реплики
 * пользователя), "Summary" (Доработка 9), "Ветка N" (свой цвет на ветку) и
 * "Факты" (кликабелен — раскрывает/сворачивает блок фактов под сообщением,
 * аналогично блоку рассуждений). */
@Composable
private fun MessageBadges(message: Message, onToggleFacts: () -> Unit) {
    val hasBadges = message.is_task_manager_step || message.is_summary || message.branch > 0 || !message.facts.isNullOrBlank()
    if (!hasBadges) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 2.dp)) {
        if (message.is_task_manager_step) {
            SmallBadge(text = "Менеджер задач", color = MaterialTheme.colorScheme.tertiaryContainer)
        }
        if (message.is_summary) {
            SmallBadge(text = "Summary", color = MaterialTheme.colorScheme.tertiaryContainer)
        }
        if (message.branch > 0) {
            SmallBadge(text = "Ветка ${message.branch}", color = branchBadgeColor(message.branch))
        }
        if (!message.facts.isNullOrBlank()) {
            SmallBadge(text = "Факты", color = MaterialTheme.colorScheme.secondaryContainer, onClick = onToggleFacts)
        }
    }
}

/** JSON-декодер `Message.task_events` — отдельный от общего клиента
 * AgentsCoreApiClient, т.к. это чисто UI-парсинг уже полученного поля, а не
 * сетевой вызов. */
private val taskEventsJson = Json { ignoreUnknownKeys = true }

/** Переходы состояний задач за ЭТОТ ответ (пункт 7 замечаний) — по одному
 * чипу на переход, с явным указанием названия задачи, чтобы не путать
 * несколько параллельных открытых задач одного чата. Клик — переход на
 * детали именно этой задачи. */
@Composable
private fun TaskEventChips(message: Message, onOpenTask: (String) -> Unit) {
    val raw = message.task_events
    if (raw.isNullOrBlank()) return
    val events = remember(raw) {
        runCatching { taskEventsJson.decodeFromString(ListSerializer(TaskEvent.serializer()), raw) }.getOrDefault(emptyList())
    }
    if (events.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(bottom = 2.dp)) {
        events.forEach { event ->
            val transition = if (event.from_state_display_name != null) {
                "${event.from_state_display_name} → ${event.to_state_display_name}"
            } else {
                "начало → ${event.to_state_display_name}"
            }
            SmallBadge(
                text = "«${event.task_title}»: $transition",
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { onOpenTask(event.task_id) },
            )
        }
    }
}

/** Инструменты, вызванные через отдельный MCP-сервер за этот ответ (новое
 * ТЗ) — по одной строке-действию на КАЖДЫЙ вызов, столбиком: «Использую
 * инструмент git_host_get_repo — Получение информации о репозитории» (по
 * замечанию пользователя: раньше это был ряд чипов в одну строку, который
 * при нескольких вызовах вылезал за ширину экрана и ломал вёрстку).
 * События started/finished одного вызова схлопываются в одну строку (см.
 * [collapseMcpCalls]); статус — значком в начале строки. */
@Composable
private fun McpCallChips(message: Message) {
    val raw = message.mcp_events
    if (raw.isNullOrBlank()) return
    val events = remember(raw) {
        runCatching { taskEventsJson.decodeFromString(ListSerializer(McpCallEvent.serializer()), raw) }.getOrDefault(emptyList())
    }
    McpCallLines(events)
}

/** Один вызов инструмента для отображения: `ok == null` — ещё выполняется. */
private data class McpCallLine(val name: String, val finished: Boolean, val ok: Boolean?, val error: String?)

/** Схлопывает поток событий `started`/`finished` в по одной записи на вызов:
 * `finished` закрывает последний ещё открытый вызов с тем же именем (вызовы
 * выполняются сервером последовательно, так что это однозначно); одиночный
 * `finished` без `started` (не должен случаться, но не теряем) — отдельной
 * записью. */
private fun collapseMcpCalls(events: List<McpCallEvent>): List<McpCallLine> {
    val lines = mutableListOf<McpCallLine>()
    for (event in events) {
        if (event.status == "finished") {
            val openIndex = lines.indexOfLast { it.name == event.name && !it.finished }
            val closed = McpCallLine(event.name, finished = true, ok = event.ok, error = event.error)
            if (openIndex >= 0) lines[openIndex] = closed else lines.add(closed)
        } else {
            lines.add(McpCallLine(event.name, finished = false, ok = null, error = null))
        }
    }
    return lines
}

@Composable
private fun McpCallLines(events: List<McpCallEvent>) {
    val lines = remember(events) { collapseMcpCalls(events) }
    if (lines.isEmpty()) return
    Column(
        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        lines.forEach { line -> McpCallLineRow(line) }
    }
}

@Composable
private fun McpCallLineRow(line: McpCallLine) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.padding(top = 2.dp).size(14.dp), contentAlignment = Alignment.Center) {
            when {
                !line.finished -> CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                line.ok == false -> Icon(
                    Icons.Filled.ErrorOutline, contentDescription = "Ошибка",
                    tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp),
                )
                else -> Icon(
                    Icons.Filled.Build, contentDescription = null,
                    tint = muted, modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            val title = toolTitle(line.name)
            Text(
                text = buildAnnotatedString {
                    append("Использую инструмент ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(line.name) }
                    if (title != null) append(" — $title")
                },
                style = MaterialTheme.typography.labelMedium,
                color = muted,
            )
            if (line.ok == false && !line.error.isNullOrBlank()) {
                Text(
                    text = "Ошибка: ${line.error}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmallBadge(text: String, color: Color, onClick: (() -> Unit)? = null) {
    AssistChip(
        onClick = onClick ?: {},
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(containerColor = color),
    )
}

/** Раскрываемый блок фактов под сообщением (аналогично блоку рассуждений). */
@Composable
private fun FactsBlock(factsJson: String) {
    Box(modifier = Modifier.padding(top = 4.dp)) {
        JsonTreeView(rawJson = factsJson)
    }
}

/** Содержимое сообщения в зависимости от определённого при сохранении
 * формата (Доработка 1): обычный текст, markdown (multiplatform-markdown-renderer)
 * или JSON (собственный JsonTreeView — надёжной опубликованной библиотеки
 * "JsonViewer-Compose" не существует, см. README). */
@Composable
private fun MessageContent(message: Message) {
    when (message.format) {
        "markdown" -> Markdown(content = message.content)
        "json" -> JsonTreeView(rawJson = message.content)
        else -> Text(message.content)
    }
}

/** Пузырь сообщения общего вида — с рамкой выделения при выборе и поддержкой
 * тапа/долгого нажатия. [fullWidth] снимает ограничение ширины (замечание 9 —
 * ответ ассистента должен отображаться на всю ширину). */
@Composable
private fun Bubble(
    color: Color,
    selected: Boolean,
    fullWidth: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = (if (fullWidth) Modifier.fillMaxWidth() else Modifier.widthIn(max = 320.dp))
            .clip(shape)
            .background(color, shape)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                },
            )
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(10.dp),
    ) {
        content()
    }
}

/** Временное сообщение ассистента, пока идёт потоковая генерация ответа.
 * Блок рассуждений во время стриминга не показывается (замечание 15) — пока
 * `content` пуст, вместо него показывается статус текущей фазы вызова,
 * присланный сервером событиями `status` ("Выполняется запрос к модели",
 * при Sticky Facts затем "Обновление фактов" и т.п.) — раньше здесь был
 * зашитый нейтральный индикатор "Модель рассуждает…". */
@Composable
private fun DraftBubble(draft: StreamingDraft) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Инструменты MCP-сервера, вызванные по ходу ЭТОЙ генерации (новое
        // ТЗ) — показываются сразу, ещё до появления текста ответа, теми же
        // строками-действиями, что и в уже сохранённом сообщении (см. McpCallChips).
        if (draft.mcpCalls.isNotEmpty()) {
            McpCallLines(draft.mcpCalls)
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
            if (draft.content.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(draft.status, style = MaterialTheme.typography.labelSmall)
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(10.dp),
                    ) {
                        Text(draft.content)
                    }
                    Text(
                        text = "печатает…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                    )
                }
            }
        }
    }
}

/** Поле ввода внизу экрана — многострочный текст и кнопка отправки.
 * Заблокировано целиком (вместе с кебаб-меню и настройками — см. [ChatScreen])
 * во время отправки сообщения ИЛИ суммаризации (замечание 14). */
@Composable
private fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Сообщение…") },
            enabled = enabled,
            maxLines = 5,
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = onSend,
            enabled = text.isNotBlank() && enabled,
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Отправить")
        }
    }
}
