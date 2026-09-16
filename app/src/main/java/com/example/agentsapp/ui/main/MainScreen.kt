package com.example.agentsapp.ui.main

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.example.agentsapp.R
import com.example.agentsapp.data.remote.Agent
import com.example.agentsapp.data.remote.AgentWithChats
import com.example.agentsapp.data.remote.Chat
import com.example.agentsapp.data.remote.ModelInfo
import com.example.agentsapp.data.remote.Profile
import com.example.agentsapp.data.remote.Settings
import com.example.agentsapp.ui.common.SettingsSummary

/**
 * Главный экран приложения: сводный список агентов вместе с их чатами.
 * Работает только через переданный [MainViewModel] — весь доступ к сети
 * спрятан внутри него. Адрес сервера и проверка соединения больше не
 * показываются здесь — это часть экрана "Настройки" (замечание 4).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    onOpenChat: (chatId: String) -> Unit,
    onOpenAgentSettings: (agentId: String) -> Unit,
    onOpenChatSettings: (chatId: String) -> Unit,
    onOpenModels: () -> Unit,
    onOpenDefaultSettings: () -> Unit,
    onOpenProfiles: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Обновляем статистику по токенам при каждом возврате на главный экран
    // (например, после отправки сообщений в чате) — замечание 6.
    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    // Состояния диалогов — всё локально для экрана, ViewModel о них не знает.
    // Создание агента/чата показывает диалог с именем, предзаполненным по
    // тому же шаблону, что применяет сервер ("Агент N"/"Чат N"), но имя
    // можно скорректировать перед подтверждением.
    var deleteAgentTarget by remember { mutableStateOf<Agent?>(null) }
    var deleteChatTarget by remember { mutableStateOf<Chat?>(null) }
    var copyChatTarget by remember { mutableStateOf<Chat?>(null) }
    var showCreateAgentDialog by remember { mutableStateOf(false) }
    var createChatFor by remember { mutableStateOf<AgentWithChats?>(null) }

    // Сетевые ошибки показываем снэкбаром.
    LaunchedEffect(uiState.errorMessage) {
        val message = uiState.errorMessage
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Агенты") },
                actions = {
                    IconButton(onClick = onOpenModels) {
                        // Иконка "Linked services" (Material Symbols, fonts.google.com/icons) —
                        // её нет в классическом наборе Material Icons (material-icons-extended),
                        // подключённом в проекте, поэтому используется точный SVG, экспортированный
                        // как Android Vector Drawable — см. res/drawable/ic_linked_services.xml.
                        Icon(painterResource(R.drawable.ic_linked_services), contentDescription = "Модели")
                    }
                    IconButton(onClick = onOpenProfiles) {
                        // Справочник профилей-пайплайнов персонализации, общий для
                        // всех агентов — доступ с главного экрана (замечание 1).
                        Icon(Icons.Filled.Badge, contentDescription = "Профили")
                    }
                    IconButton(onClick = onOpenDefaultSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Настройки")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) } },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateAgentDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Добавить агента")
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (uiState.isLoading && uiState.agentsWithChats.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.agentsWithChats.isEmpty()) {
                Text(
                    text = "Пока нет ни одного агента. Добавьте первого кнопкой \"+\".",
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.agentsWithChats, key = { it.agent.id }) { agentWithChats ->
                        AgentCard(
                            agentWithChats = agentWithChats,
                            models = uiState.models,
                            profiles = uiState.profiles,
                            onOpenChat = onOpenChat,
                            onOpenAgentSettings = onOpenAgentSettings,
                            onOpenChatSettings = onOpenChatSettings,
                            onDeleteAgent = { deleteAgentTarget = agentWithChats.agent },
                            onAddChat = { createChatFor = agentWithChats },
                            onCopyChat = { copyChatTarget = it },
                            onDeleteChat = { deleteChatTarget = it },
                        )
                    }
                }
            }
        }
    }

    deleteAgentTarget?.let { agent ->
        AlertDialog(
            onDismissRequest = { deleteAgentTarget = null },
            title = { Text("Удалить агента?") },
            text = {
                Text(
                    "Агент \"${agent.name}\" будет удалён вместе со всеми его чатами и сообщениями. " +
                        "Это действие нельзя отменить.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteAgent(agent.id)
                    deleteAgentTarget = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { deleteAgentTarget = null }) { Text("Отмена") }
            },
        )
    }

    deleteChatTarget?.let { chat ->
        AlertDialog(
            onDismissRequest = { deleteChatTarget = null },
            title = { Text("Удалить чат?") },
            text = { Text("Удалить чат «${chat.title}»?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteChat(chat.id)
                    deleteChatTarget = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { deleteChatTarget = null }) { Text("Отмена") }
            },
        )
    }

    copyChatTarget?.let { chat ->
        TextInputDialog(
            title = "Копировать чат",
            label = "Название копии",
            confirmText = "Копировать",
            initialValue = "${chat.title} (копия)",
            onConfirm = { title ->
                viewModel.copyChat(chat.id, title.trim())
                copyChatTarget = null
            },
            onDismiss = { copyChatTarget = null },
        )
    }

    if (showCreateAgentDialog) {
        TextInputDialog(
            title = "Новый агент",
            label = "Имя агента",
            confirmText = "Добавить",
            dismissText = "Отменить",
            initialValue = "Агент ${uiState.agentsWithChats.size + 1}",
            onConfirm = { name ->
                viewModel.createAgent(name.trim())
                showCreateAgentDialog = false
            },
            onDismiss = { showCreateAgentDialog = false },
        )
    }

    createChatFor?.let { target ->
        TextInputDialog(
            title = "Новый чат",
            label = "Название чата",
            confirmText = "Добавить",
            dismissText = "Отменить",
            initialValue = "Чат ${target.chats.size + 1}",
            onConfirm = { title ->
                viewModel.createChat(target.agent.id, title.trim())
                createChatFor = null
            },
            onDismiss = { createChatFor = null },
        )
    }
}

/** Карточка одного агента: заголовок с действиями + список его чатов. */
@Composable
private fun AgentCard(
    agentWithChats: AgentWithChats,
    models: List<ModelInfo>,
    profiles: List<Profile>,
    onOpenChat: (String) -> Unit,
    onOpenAgentSettings: (String) -> Unit,
    onOpenChatSettings: (String) -> Unit,
    onDeleteAgent: () -> Unit,
    onAddChat: () -> Unit,
    onCopyChat: (Chat) -> Unit,
    onDeleteChat: (Chat) -> Unit,
) {
    val agent = agentWithChats.agent
    val model = models.find { it.id == agent.settings.model }
    // Суффикс "(локальная)" вместо отдельного бейджа (замечание 7).
    val modelLabel = model?.let { if (it.is_local) "${it.display_name} (локальная)" else it.display_name } ?: agent.settings.model
    // default_profile_id — профиль ПО УМОЛЧАНИЮ для новых чатов этого агента
    // (см. Agent.default_profile_id), не то же самое, что активный профиль
    // конкретного чата (ChatRow ниже показывает свой бейдж по active_profile_id).
    val agentProfileName = profileNameOf(profiles, agent.default_profile_id)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = agent.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = modelLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Сводка настроек агента (бейджи ограничений + температура/top_p
                    // текстом) — в том же виде и порядке, что и на экране настроек;
                    // бейдж "Профиль" — в этом же ряду, справа от "Память: ..."
                    // (по замечанию пользователя), а не отдельным блоком.
                    SettingsSummary(
                        settings = agent.settings,
                        profileName = agentProfileName,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                IconButton(onClick = onAddChat) {
                    Icon(Icons.Filled.Add, contentDescription = "Добавить чат")
                }
                IconButton(onClick = { onOpenAgentSettings(agent.id) }) {
                    Icon(Icons.Filled.Settings, contentDescription = "Настройка агента")
                }
                IconButton(onClick = onDeleteAgent) {
                    Icon(Icons.Filled.Delete, contentDescription = "Удалить агента")
                }
            }

            if (agentWithChats.chats.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    agentWithChats.chats.forEach { chat ->
                        ChatRow(
                            chat = chat,
                            agentSettings = agent.settings,
                            profiles = profiles,
                            onOpenChat = { onOpenChat(chat.id) },
                            onOpenChatSettings = { onOpenChatSettings(chat.id) },
                            onCopyChat = { onCopyChat(chat) },
                            onDeleteChat = { onDeleteChat(chat) },
                        )
                    }
                }
            } else {
                Text(
                    text = "У этого агента пока нет чатов.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Строка одного чата: заголовок, счётчики токенов, индикатор заполнения
 * контекста и три действия — порядок "Копировать, Настройка, Удалить"
 * (замечание 10). */
@Composable
private fun ChatRow(
    chat: Chat,
    // Настройки агента-владельца — база для сравнения (замечание
    // пользователя: в списке агентов бейджи чата показывают только то, чем
    // он отличается от агента, см. SettingsSummary(baselineSettings = ...)).
    agentSettings: Settings,
    profiles: List<Profile>,
    onOpenChat: () -> Unit,
    onOpenChatSettings: () -> Unit,
    onCopyChat: () -> Unit,
    onDeleteChat: () -> Unit,
) {
    val chatProfileName = profileNameOf(profiles, chat.active_profile_id)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenChat)
            .padding(vertical = 6.dp, horizontal = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = chat.title, style = MaterialTheme.typography.bodyLarge)
                // Формат приведён к тому же виду, что и на странице чата
                // (ContextInfoBar: "Токены — вход:.."); размер шрифта — на
                // ~15% меньше обычного bodySmall (по замечанию пользователя).
                Text(
                    text = "Токены — вход: ${chat.stats.prompt_tokens}, выход: ${chat.stats.completion_tokens}, " +
                        "всего: ${chat.stats.total_tokens}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = MaterialTheme.typography.bodySmall.fontSize * 0.85f,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Сводка настроек ЭТОГО чата — здесь, в списке агентов,
                // показываем только то, чем настройки чата отличаются от
                // настроек агента-владельца (baselineSettings = agentSettings,
                // по замечанию пользователя): если, например, "Потоковые
                // ответы" совпадают — бейдж не показываем, если чат их
                // выключил — показываем "Потоковые ответы (Выкл.)". На
                // экране самого чата (ChatScreen) сравнения нет — там всегда
                // видны все включённые настройки этого чата целиком.
                SettingsSummary(
                    settings = chat.settings,
                    baselineSettings = agentSettings,
                    profileName = chatProfileName,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            IconButton(onClick = onCopyChat) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "Копировать чат")
            }
            IconButton(onClick = onOpenChatSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Настройка чата")
            }
            IconButton(onClick = onDeleteChat) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить чат")
            }
        }
        val fillRatio = chat.stats.context_fill_ratio
        if (fillRatio != null) {
            val isOverflow = fillRatio > 1.0
            LinearProgressIndicator(
                progress = { fillRatio.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .padding(top = 4.dp),
                color = if (isOverflow) MaterialTheme.colorScheme.error else ProgressIndicatorDefaults.linearColor,
            )
        }
    }
}

/** Универсальный диалог ввода одной текстовой строки (название агента,
 * название чата, название копии чата). */
@Composable
private fun TextInputDialog(
    title: String,
    label: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    initialValue: String = "",
    allowBlank: Boolean = false,
    dismissText: String = "Отмена",
) {
    var text by remember { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                enabled = allowBlank || text.isNotBlank(),
                onClick = { onConfirm(text) },
            ) { Text(confirmText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(dismissText) }
        },
    )
}

/** Имя профиля по id (для бейджа "Профиль" — [com.example.agentsapp.ui.common.ProfileBadge],
 * рисуется внутри [com.example.agentsapp.ui.common.SettingsSummary]) — null,
 * если профиль не подключён (id == null) или не нашёлся в справочнике. */
private fun profileNameOf(profiles: List<Profile>, profileId: String?): String? =
    profileId?.let { id -> profiles.firstOrNull { it.id == id }?.name }
