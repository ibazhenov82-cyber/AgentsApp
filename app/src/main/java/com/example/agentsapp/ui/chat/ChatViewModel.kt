package com.example.agentsapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agentsapp.data.remote.AgentApiException
import com.example.agentsapp.data.remote.AgentUnreachableException
import com.example.agentsapp.data.remote.Branch
import com.example.agentsapp.data.remote.Chat
import com.example.agentsapp.data.remote.ChatStats
import com.example.agentsapp.data.remote.Message
import com.example.agentsapp.data.remote.Profile
import com.example.agentsapp.data.remote.Settings
import com.example.agentsapp.data.remote.TaskEvent
import com.example.agentsapp.data.remote.TaskSummary
import com.example.agentsapp.data.remote.ToolCallEvent
import com.example.agentsapp.data.repository.AgentsCoreRepository
import com.example.agentsapp.data.repository.ChatRunState
import com.example.agentsapp.data.repository.RunsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Задержка перед отправкой нового названия чата на сервер после последнего
 * изменения пользователем (то же значение и тот же приём debounce, что и на
 * экране настроек — см. `SettingsViewModel`). */
private const val RENAME_DEBOUNCE_MS = 500L

/** Задержка отметки прочтения после показа новых сообщений. */
private const val MARK_READ_DELAY_MS = 1_000L

/** Черновик ответа ассистента, накапливаемый по кадрам потока (SSE), пока
 * не пришёл финальный кадр `done` с уже сохранённым на сервере сообщением.
 * Блок рассуждений во время стриминга не показывается (замечание 15) —
 * `reasoningContent` копится только для внутреннего состояния "печатает
 * рассуждения" (пока `content` ещё пуст), сам текст рассуждений не рисуется. */
data class StreamingDraft(
    val content: String = "",
    val reasoningContent: String = "",
    /** Статус текущей фазы вызова ("Выполняется запрос к модели",
     * "Обновление фактов" и т.п.), приходит событиями `status` от сервера —
     * показывается вместо контента, пока `content` ещё пуст (см. [DraftBubble]). */
    val status: String = "Выполняется запрос к модели",
    /** Вызовы ВСЕХ инструментов по ходу этого ответа (started, затем finished
     * того же вызова) — строки «Использую инструмент …» в черновике. */
    val toolCalls: List<ToolCallEvent> = emptyList(),
    val taskEvents: List<TaskEvent> = emptyList(),
)

/** Черновик ответа из состояния запуска (см. [RunsRepository]). */
private fun ChatRunState.toDraft(): StreamingDraft = StreamingDraft(
    content = content,
    reasoningContent = reasoning,
    status = currentStatus ?: if (status == "queued") "Запрос в очереди" else "Выполняется запрос к модели",
    toolCalls = toolCalls,
    taskEvents = taskEvents,
)

/** Id-заглушка для оптимистично показанного сообщения пользователя — до
 * ответа сервера у него ещё нет настоящего id (замечание "сообщение должно
 * отображаться в чате сразу"). Отрицательный и вне диапазона реальных id
 * (автоинкремент БД начинается с 1), поэтому не может совпасть с реальным. */
private const val PENDING_USER_MESSAGE_ID = -1L

/** Состояние экрана чата. */
data class ChatUiState(
    val isLoading: Boolean = true,
    val chatTitle: String = "",
    val agentName: String = "",
    val modelDisplayName: String = "",
    val isModelLocal: Boolean = false,
    val contextStrategy: String? = null,
    /** Предел (в сообщениях) для стратегии управления контекстом — вместе с
     * [contextStrategy] используется для бейджа "Стратегия ..." над чатом. */
    val contextStrategyLimit: Int? = null,
    /** "off" | "messages" | "tokens" — для бейджа "Автоматическая суммаризация ...". */
    val autosummary: String = "off",
    val autosummaryByMessages: Int = 0,
    val autosummaryByTokens: Int = 0,
    /** Полные настройки чата — источник данных для бейджей ограничений
     * (JSON-режим, стоп-последовательности, уровень рассуждений, макс. число
     * токенов, seed, потоковые ответы, рассуждения, температура, top_p) на
     * экране чата и в списке чатов на главном экране. */
    val settings: Settings? = null,
    /** Имя подключённого к чату профиля-пайплайна персонализации
     * (`Chat.active_profile_id`, ищется по каталогу профилей) — null, если
     * профиль не подключён. Для бейджа "Профиль" на экране чата. */
    val activeProfileName: String? = null,
    /** Выбран ли для этого чата хотя бы один инвариант (`Chat.invariant_ids`)
     * — отдельного тумблера-настройки больше нет, см. `SettingsSummary`. */
    val hasInvariants: Boolean = false,
    val stats: ChatStats? = null,
    /** Общее число сообщений в чате (все ветки, а не только видимая) — для
     * заголовка "Чат N (M сообщений)" в шапке экрана. */
    val messageCount: Int = 0,
    /** Сообщения чата, уже отфильтрованные под выбранную ветку диалога
     * (основная ветка (0) + выбранная, если не основная) — см. [selectedBranch]. */
    val messages: List<Message> = emptyList(),
    val branches: List<Branch> = emptyList(),
    /** 0 — основная ветка. */
    val selectedBranch: Int = 0,
    /** Черновик ответа ассистента из идущего запуска — показывается как
     * временное сообщение в самом низу списка, пока ответ формируется (в
     * том числе если экран открыли уже посреди ответа). */
    val streamingDraft: StreamingDraft? = null,
    /** Идущий запуск в этом чате (ответ или Менеджер задач), null — нет. */
    val activeRunId: String? = null,
    /** Первое непрочитанное на момент открытия чата — перед ним рисуется
     * разделитель «Новые сообщения»; остаётся до ухода с экрана. */
    val firstUnreadMessageId: Long? = null,
    /** Разовая прокрутка к сообщению (первому непрочитанному) при открытии;
     * экран сбрасывает её через [ChatViewModel.consumeScrollTarget]. */
    val scrollToMessageId: Long? = null,
    val inputText: String = "",
    val isSending: Boolean = false,
    /** Отдельный флаг для суммаризации (замечание 14) — блокирует поле
     * ввода/отправку/кебаб-меню и показывает индикатор под последним
     * сообщением, независимо от [isSending]. */
    val isSummarizing: Boolean = false,
    /** Id выбранных (долгим нажатием / тапом в режиме выбора) сообщений. */
    val selectedIds: Set<Long> = emptySet(),
    /** Id сообщений ассистента, у которых блок "рассуждения" сейчас раскрыт. */
    val expandedReasoningIds: Set<Long> = emptySet(),
    /** Id сообщений, у которых блок "факты" сейчас раскрыт (Доработка 9). */
    val expandedFactsIds: Set<Long> = emptySet(),
    val showDeleteDialog: Boolean = false,
    /** Панель фактов (Sticky Facts) справа от чата — скрыта по умолчанию (Доработка 7). */
    val showFactsPanel: Boolean = false,
    /** JSON фактов из последнего сообщения, под которым они сохранены. */
    val latestFacts: String? = null,
    /** Открытые (активные + на паузе) задачи этого чата — для ссылки-строки
     * перед перепиской (пункт 4 замечаний: НЕ бэдж, отдельная кликабельная
     * строка). Пусто, если `task_tracking_enabled` выключена у чата — тогда
     * строка вообще не рисуется (см. ChatScreen). */
    val openTasks: List<TaskSummary> = emptyList(),
    /** Id задачи, для которой сейчас выполняется шаг(и) "Менеджера задач"
     * (см. [ChatViewModel.startTaskManagerRun]) — не null, пока идёт вызов
     * модели; используется, чтобы показать индикатор в карточке
     * подтверждения задачи (см. [TaskConfirmationCard] в ChatScreen.kt) и
     * заблокировать ввод (как при обычной отправке). */
    val taskManagerActiveTaskId: String? = null,
    /** true — идёт именно одиночный шаг ("Продолжить", после которого сервер
     * сам снова ставит задачу на паузу), false — непрерывное выполнение
     * ("Выполнить", до состояния done); используется только вместе с
     * [taskManagerActiveTaskId] != null, чтобы решить, показывать ли кнопку
     * "Пауза" для прерывания (по дополнению пользователя: прервать можно в
     * любой момент, пока "Выполнить" ещё работает). */
    val taskManagerAutoPause: Boolean = true,
    /** Разовое сообщение об ошибке для показа в Snackbar; сбрасывается после показа. */
    val errorMessage: String? = null,
) {
    val isSelectionMode: Boolean get() = selectedIds.isNotEmpty()
    val isBusy: Boolean get() = isSending || isSummarizing || activeRunId != null

    /** В чате формируется ответ — вместо «Отправить» показывается «Остановить». */
    val isGenerating: Boolean get() = activeRunId != null

    /** Кнопка суммаризации недоступна: нет сообщений, сервер сам считает
     * суммаризацию невозможной, или прямо сейчас идёт отправка/получение/суммаризация. */
    val canSummarize: Boolean
        get() = messages.isNotEmpty() && stats?.can_summarize != false && !isBusy
}

/**
 * ViewModel экрана чата. Владеет всей логикой обращения к [AgentsCoreRepository]
 * для одного конкретного чата: загрузка чата/агента/каталога моделей/сообщений,
 * отправка (потоковая или блокирующая — по `chat.settings.stream`),
 * суммаризация, ветки диалога, стратегия Sticky Facts и удаление сообщений
 * (одиночное и множественное).
 */
class ChatViewModel(
    private val chatId: String,
    private val repository: AgentsCoreRepository,
    private val runsRepository: RunsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    /** Последний загруженный чат — нужен, чтобы знать `agent_id` и
     * `settings.stream`/`settings.model`/`settings.context_strategy` без
     * повторного похода на сервер. */
    private var chat: Chat? = null

    /** Полный (неотфильтрованный по ветке) список сообщений с сервера —
     * `GET /chats/{id}/messages` не принимает параметр ветки, фильтрация по
     * ветке для отображения выполняется на клиенте (Доработка 8). */
    private var allMessages: List<Message> = emptyList()

    /** Debounce-корутина для переименования чата (замечание "inline
     * редактирование наименования чата"). */
    private var renameJob: Job? = null

    /** Отложенная отметка прочтения (не чаще раза в секунду). */
    private var markReadJob: Job? = null

    /** Экран чата сейчас виден (между onStart и onStop) — только тогда
     * сообщения отмечаются прочитанными. */
    private var screenVisible = false

    /** Прокрутка к первому непрочитанному — только при первом открытии. */
    private var initialScrollDone = false

    init {
        load()
        // Черновик идущего ответа — из запуска, который держит RunsRepository
        // (подписка живёт дольше этого экрана).
        viewModelScope.launch {
            runsRepository.observe(chatId).collect { run -> applyRunState(run) }
        }
        // Сообщения чата изменились на сервере (ответ закончился, шаг
        // Менеджера задач готов, пришёл запрос планировщика).
        viewModelScope.launch {
            runsRepository.chatRefresh.collect { changedChatId ->
                if (changedChatId == chatId) refreshChatAndMessages()
            }
        }
    }

    private fun applyRunState(run: ChatRunState?) {
        _state.update {
            val isTaskRun = run != null && (run.kind == "task_step" || run.kind == "task_run")
            it.copy(
                activeRunId = run?.runId,
                streamingDraft = run?.toDraft(),
                taskManagerActiveTaskId = if (isTaskRun) run?.taskId else null,
                taskManagerAutoPause = run?.kind != "task_run",
            )
        }
    }

    /** Экран чата стал виден/скрыт — для отметки прочтения и чтобы
     * «Новый ответ» для этого чата не показывался всплывающим сообщением. */
    fun onScreenVisible(visible: Boolean) {
        screenVisible = visible
        if (visible) {
            runsRepository.openChatId = chatId
            viewModelScope.launch { refreshChatAndMessages() }
        } else if (runsRepository.openChatId == chatId) {
            runsRepository.openChatId = null
        }
    }

    override fun onCleared() {
        if (runsRepository.openChatId == chatId) runsRepository.openChatId = null
        super.onCleared()
    }

    /** Отметка «прочитано» до последнего показанного финального сообщения —
     * с задержкой около секунды, чтобы не слать запрос на каждое сообщение. */
    private fun scheduleMarkRead() {
        if (!screenVisible) return
        val lastShown = allMessages.filter { it.status != "streaming" && it.id > 0 }.maxOfOrNull { it.id } ?: return
        val alreadyRead = chat?.last_read_message_id ?: 0L
        if (lastShown <= alreadyRead) return
        markReadJob?.cancel()
        markReadJob = viewModelScope.launch {
            delay(MARK_READ_DELAY_MS)
            runCatching { runsRepository.markRead(chatId, lastShown) }
                .onSuccess { chat = chat?.copy(last_read_message_id = lastShown) }
        }
    }

    /** Полная перезагрузка экрана: чат, владеющий агент, каталог моделей,
     * список сообщений и список веток. Вызывается при открытии экрана. */
    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                fetchAndApplySettingsDerivedState(includeBranches = true)
                _state.update { it.copy(isLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, errorMessage = errorText(e)) }
            }
        }
    }

    /** Обновляет все поля состояния, производные от НАСТРОЕК чата (модель,
     * стратегия управления контекстом, автосуммаризация, полный объект
     * [Settings] для бейджей ограничений), без показа индикатора загрузки —
     * вызывается при каждом возврате на экран чата (в том числе из экрана
     * настроек). Раньше эти поля обновлялись только в [load] (то есть один
     * раз при первом открытии экрана), из-за чего после изменения стратегии
     * в настройках чата и нажатия "назад" бейдж стратегии оставался старым
     * до полного выхода на главный экран и повторного входа в чат. */
    fun refreshSettings() {
        viewModelScope.launch {
            try {
                fetchAndApplySettingsDerivedState(includeBranches = false)
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = errorText(e)) }
            }
        }
    }

    /** Общая часть [load] и [refreshSettings]: запрашивает чат/агента/каталог
     * моделей/сообщения (и, опционально, ветки) и записывает их в состояние. */
    private suspend fun fetchAndApplySettingsDerivedState(includeBranches: Boolean) {
        // Экран чата, в отличие от главного списка, всегда запрашивает явную
        // ветку (по умолчанию 0 — основная), а не полагается на конвенцию
        // сервера "branch=1 по умолчанию" (та предназначена для компактного
        // отображения в списке чатов).
        val loadedChat = repository.getChat(chatId, branch = _state.value.selectedBranch)
        chat = loadedChat
        loadedChat.active_run?.let { runsRepository.attach(chatId, it.id) }
        val agent = repository.getAgent(loadedChat.agent_id)
        val models = repository.listModels()
        // Не блокирует загрузку экрана, если каталог профилей недоступен —
        // бейдж "Профиль" в этом случае просто не покажется (как и на
        // главном экране, см. MainViewModel.loadData).
        val profiles = runCatching { repository.listProfiles() }.getOrDefault(emptyList())
        allMessages = repository.listMessages(chatId)
        val branches = if (includeBranches) {
            runCatching { repository.listBranches(chatId) }.getOrDefault(emptyList())
        } else {
            null
        }
        val modelInfo = models.find { it.id == loadedChat.settings.model }
        val openTasks = loadOpenTasksIfEnabled(loadedChat.settings)
        _state.update {
            it.copy(
                chatTitle = loadedChat.title,
                agentName = agent.name,
                modelDisplayName = modelInfo?.display_name ?: loadedChat.settings.model,
                isModelLocal = modelInfo?.is_local ?: false,
                contextStrategy = loadedChat.settings.context_strategy,
                contextStrategyLimit = loadedChat.settings.context_strategy_limit,
                autosummary = loadedChat.settings.autosummary,
                autosummaryByMessages = loadedChat.settings.autosummary_by_messages,
                autosummaryByTokens = loadedChat.settings.autosummary_by_tokens,
                settings = loadedChat.settings,
                activeProfileName = profileNameOf(profiles, loadedChat.active_profile_id),
                hasInvariants = loadedChat.invariant_ids.isNotEmpty(),
                stats = loadedChat.stats,
                messageCount = allMessages.size,
                messages = visibleMessages(allMessages, it.selectedBranch),
                branches = branches ?: it.branches,
                latestFacts = latestFactsFrom(allMessages) ?: it.latestFacts,
                openTasks = openTasks,
                firstUnreadMessageId = if (initialScrollDone) it.firstUnreadMessageId else loadedChat.first_unread_message_id,
                scrollToMessageId = if (initialScrollDone) it.scrollToMessageId else loadedChat.first_unread_message_id,
            )
        }
        initialScrollDone = true
        scheduleMarkRead()
    }

    fun consumeScrollTarget() {
        _state.update { it.copy(scrollToMessageId = null) }
    }

    /** Задачи этого чата подгружаются, только если у чата включена настройка
     * "Отслеживать задачи" — иначе задачи не существуют вовсе (пункт 4). Не
     * прерывает загрузку экрана, если запрос списка задач не удался. */
    private suspend fun loadOpenTasksIfEnabled(settings: Settings): List<TaskSummary> =
        if (settings.task_tracking_enabled) {
            runCatching { repository.listChatTasks(chatId) }.getOrDefault(emptyList())
        } else {
            emptyList()
        }

    /** Заново запрашивает чат (для актуальной статистики токенов, под текущую
     * выбранную ветку) и список сообщений — вызывается после любого
     * изменяющего действия. */
    private suspend fun refreshChatAndMessages() {
        try {
            val branch = _state.value.selectedBranch
            val loadedChat = repository.getChat(chatId, branch = branch)
            chat = loadedChat
            val profiles = runCatching { repository.listProfiles() }.getOrDefault(emptyList())
            allMessages = repository.listMessages(chatId)
            val openTasks = loadOpenTasksIfEnabled(loadedChat.settings)
            _state.update {
                it.copy(
                    contextStrategy = loadedChat.settings.context_strategy,
                    contextStrategyLimit = loadedChat.settings.context_strategy_limit,
                    autosummary = loadedChat.settings.autosummary,
                    autosummaryByMessages = loadedChat.settings.autosummary_by_messages,
                    autosummaryByTokens = loadedChat.settings.autosummary_by_tokens,
                    settings = loadedChat.settings,
                    activeProfileName = profileNameOf(profiles, loadedChat.active_profile_id),
                    hasInvariants = loadedChat.invariant_ids.isNotEmpty(),
                    stats = loadedChat.stats,
                    messageCount = allMessages.size,
                    messages = visibleMessages(allMessages, it.selectedBranch),
                    latestFacts = latestFactsFrom(allMessages) ?: it.latestFacts,
                    openTasks = openTasks,
                )
            }
            scheduleMarkRead()
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = errorText(e)) }
        }
    }

    /** Сообщения основной ветки (0) + выбранной ветки (если не основная) —
     * то же правило, что и на сервере при отправке сообщений (`_filter_by_branch`). */
    private fun visibleMessages(all: List<Message>, selectedBranch: Int): List<Message> {
        // Черновик идущего ответа (status="streaming") в списке не показывается —
        // его рисует DraftBubble по событиям запуска.
        val finished = all.filter { it.status != "streaming" }
        return if (selectedBranch == 0) finished.filter { it.branch == 0 } else finished.filter { it.branch == 0 || it.branch == selectedBranch }
    }

    /** Имя профиля по id из каталога — null, если профиль не подключён или
     * не найден в каталоге (см. одноимённую логику в MainViewModel/MainScreen). */
    private fun profileNameOf(profiles: List<Profile>, profileId: String?): String? =
        profileId?.let { id -> profiles.firstOrNull { it.id == id }?.name }

    /** Последнее (по порядку в истории) сообщение с непустыми фактами —
     * "факты сохранены под последним отправленным сообщением" (Доработка 7). */
    private fun latestFactsFrom(all: List<Message>): String? =
        all.lastOrNull { !it.facts.isNullOrBlank() }?.facts

    fun onInputTextChange(text: String) {
        _state.update { it.copy(inputText = text) }
    }

    /** Inline-переименование чата прямо на экране чата (не только со страницы
     * настроек): локально — сразу, на сервер — с debounce, как и остальные
     * текстовые поля настроек в приложении. Пустое имя не отправляется —
     * сервер отклонит переименование в пустую строку. */
    fun renameChat(newTitle: String) {
        _state.update { it.copy(chatTitle = newTitle) }
        renameJob?.cancel()
        if (newTitle.isBlank()) return
        renameJob = viewModelScope.launch {
            delay(RENAME_DEBOUNCE_MS)
            try {
                repository.renameChat(chatId, newTitle.trim())
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = errorText(e)) }
            }
        }
    }

    /** Отправляет введённый текст как асинхронный запуск на сервере: ответ
     * формируется в фоне, экран можно закрыть и вернуться — черновик
     * продолжит дописываться (см. [RunsRepository]). Клиент лишь сообщает,
     * какая стратегия включена у чата (флаги `get_facts`/`sliding_window`/
     * `autosummary`); всю логику выполняет AgentsCore. Потоковый или обычный
     * вызов модели сервер выбирает сам по настройке чата `stream`. */
    fun sendMessage() {
        val text = state.value.inputText.trim()
        if (text.isEmpty() || state.value.isBusy) return
        val currentChat = chat
        val strategy = currentChat?.settings?.context_strategy
        val stickyFacts = strategy == "sticky_facts"
        val slidingWindow = strategy == "sliding_window"
        val autosummary = currentChat?.settings?.autosummary ?: "off"
        val targetBranch = state.value.selectedBranch
        val branch = targetBranch.takeIf { it != 0 }

        // Сообщение пользователя видно сразу, не дожидаясь ответа сервера —
        // настоящее (с id) придёт при обновлении и заменит заглушку целиком.
        val optimisticMessage = Message(
            id = PENDING_USER_MESSAGE_ID,
            chat_id = chatId,
            role = "user",
            content = text,
            created_at = System.currentTimeMillis() / 1000,
            branch = targetBranch,
        )
        allMessages = allMessages + optimisticMessage

        _state.update {
            it.copy(
                inputText = "",
                isSending = true,
                errorMessage = null,
                messageCount = allMessages.size,
                messages = visibleMessages(allMessages, it.selectedBranch),
                streamingDraft = StreamingDraft(),
            )
        }

        viewModelScope.launch {
            try {
                runsRepository.startMessageRun(chatId, text, stickyFacts, slidingWindow, autosummary, branch)
                refreshChatAndMessages()
            } catch (e: Exception) {
                allMessages = allMessages.filterNot { it.id == PENDING_USER_MESSAGE_ID }
                _state.update {
                    it.copy(
                        inputText = if (it.inputText.isEmpty()) text else it.inputText,
                        streamingDraft = if (it.activeRunId == null) null else it.streamingDraft,
                        messageCount = allMessages.size,
                        messages = visibleMessages(allMessages, it.selectedBranch),
                    )
                }
                refreshChatAndMessages()
                val message = if (e is AgentApiException && e.statusCode == 409) {
                    "В этом чате ещё формируется ответ — дождитесь его или остановите"
                } else {
                    errorText(e)
                }
                _state.update { it.copy(errorMessage = message) }
            } finally {
                _state.update { it.copy(isSending = false) }
            }
        }
    }

    /** Кнопка «Остановить» — отмена идущего запуска на сервере; частичный
     * ответ сохраняется с пометкой «Остановлено». */
    fun stopGeneration() {
        viewModelScope.launch {
            try {
                runsRepository.cancelChatRun(chatId)
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = errorText(e)) }
            }
        }
    }

    // ---- "Менеджер задач" (редизайн, замечание пользователя) -----------------
    //
    // Новая концепция: при включённой у чата настройке "Отслеживать задачи"
    // модель, прежде чем планировать/выполнять/проверять задачу, ОСТАНАВЛИВАЕТСЯ
    // (сервер сам ставит задачу на паузу — см. `_handle_start_task`/
    // `_handle_apply_task_action(auto_pause=True)` на бэкенде) и в чате под
    // последним сообщением показывается карточка-подтверждение (см.
    // [TaskConfirmationCard] в ChatScreen.kt): "Задача: …, следующий этап: ….
    // Продолжить выполнение?" с тремя кнопками. Никакого автозапуска цикла
    // здесь больше нет — каждое действие явное, по нажатию кнопки.

    /** Кнопка "Продолжить" — один шаг Менеджера задач (серверный запуск
     * `task_step`), после которого сервер сам снова ставит задачу на паузу. */
    fun continueTaskStep(taskId: String) = startTaskManagerRun(taskId, autoPause = true)

    /** Кнопка "Выполнить" — шаги подряд до завершения задачи; цикл идёт на
     * СЕРВЕРЕ (запуск `task_run`), поэтому продолжается и после ухода с
     * экрана чата. Прервать — кнопкой "Пауза" (см. [pauseTask]). */
    fun executeTaskUntilDone(taskId: String) = startTaskManagerRun(taskId, autoPause = false)

    private fun startTaskManagerRun(taskId: String, autoPause: Boolean) {
        if (state.value.isBusy) return
        _state.update { it.copy(isSending = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                runsRepository.startTaskRun(chatId, taskId, autoPause)
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = errorText(e)) }
            } finally {
                _state.update { it.copy(isSending = false) }
            }
        }
    }

    /** Кнопка "Пауза": останавливает идущий запуск Менеджера задач этой
     * задачи (серия шагов — после текущего шага) и ставит задачу на паузу. */
    fun pauseTask(taskId: String) {
        viewModelScope.launch {
            try {
                if (state.value.taskManagerActiveTaskId == taskId) {
                    runsRepository.cancelChatRun(chatId)
                }
                repository.applyTaskActionManually(taskId, "pause")
                refreshChatAndMessages()
            } catch (e: AgentApiException) {
                // 400 «уже на паузе» — не ошибка для пользователя.
                if (e.statusCode != 400) _state.update { it.copy(errorMessage = errorText(e)) }
                refreshChatAndMessages()
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = errorText(e)) }
            }
        }
    }

    /** Запрашивает у сервера суммаризацию истории чата в новое сообщение.
     * Использует отдельный флаг [ChatUiState.isSummarizing] (замечание 14),
     * а не [ChatUiState.isSending] — суммаризация не является отправкой
     * сообщения пользователя, но должна так же блокировать интерфейс. */
    fun summarize() {
        if (!state.value.canSummarize) return
        _state.update { it.copy(isSummarizing = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                repository.summarizeChat(chatId)
                refreshChatAndMessages()
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = errorText(e)) }
            } finally {
                _state.update { it.copy(isSummarizing = false) }
            }
        }
    }

    // ---- ветки диалога ------------------------------------------------------

    /** Выбирает ветку для просмотра/отправки (0 — основная). Пересчитывает
     * видимые сообщения немедленно (без сети) и статистику токенов под эту
     * ветку отдельным запросом (замечание 2 — "на форме чата при выборе
     * ветки число токенов переначитывается"). */
    fun selectBranch(number: Int) {
        _state.update { it.copy(selectedBranch = number, messages = visibleMessages(allMessages, number)) }
        viewModelScope.launch {
            try {
                val loadedChat = repository.getChat(chatId, branch = number)
                chat = loadedChat
                _state.update { it.copy(stats = loadedChat.stats) }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = errorText(e)) }
            }
        }
    }

    /** Создаёт новую ветку диалога и сразу выбирает её (Доработка 8: "после
     * добавления — выбрана новая"). Имя не запрашивается у пользователя —
     * сервер сам подставляет "Ветка N" с номером, реально присвоенным ветке
     * (замечание "имя по шаблону ... цифра должна соответствовать номеру ветки"). */
    fun createBranch() {
        viewModelScope.launch {
            try {
                val branch = repository.createBranch(chatId)
                _state.update { it.copy(branches = it.branches + branch) }
                selectBranch(branch.number)
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = errorText(e)) }
            }
        }
    }

    /** Удаляет ветку [number] вместе с её сообщениями. Если удаляемая ветка
     * сейчас выбрана — переключает просмотр на основную ветку (0). */
    fun deleteBranch(number: Int) {
        viewModelScope.launch {
            try {
                repository.deleteBranch(chatId, number)
                _state.update { it.copy(branches = it.branches.filterNot { b -> b.number == number }) }
                if (state.value.selectedBranch == number) {
                    selectBranch(0)
                } else {
                    allMessages = allMessages.filterNot { it.branch == number }
                    _state.update {
                        it.copy(
                            messageCount = allMessages.size,
                            messages = visibleMessages(allMessages, it.selectedBranch),
                        )
                    }
                }
            } catch (e: Exception) {
                _state.update { it.copy(errorMessage = errorText(e)) }
            }
        }
    }

    // ---- факты (Sticky Facts) ------------------------------------------------

    fun toggleFactsPanel() {
        _state.update { it.copy(showFactsPanel = !it.showFactsPanel) }
    }

    fun toggleMessageFactsExpanded(messageId: Long) {
        _state.update {
            val expanded = it.expandedFactsIds
            it.copy(expandedFactsIds = if (messageId in expanded) expanded - messageId else expanded + messageId)
        }
    }

    /** Разворачивает/сворачивает блок "рассуждения" под сообщением ассистента. */
    fun toggleReasoningExpanded(messageId: Long) {
        _state.update {
            val expanded = it.expandedReasoningIds
            it.copy(
                expandedReasoningIds = if (messageId in expanded) expanded - messageId else expanded + messageId,
            )
        }
    }

    /** Долгое нажатие на сообщение: включает режим выбора и сразу выбирает
     * это сообщение. */
    fun onMessageLongPress(messageId: Long) {
        _state.update { it.copy(selectedIds = it.selectedIds + messageId) }
    }

    /** Обычный тап по сообщению в режиме выбора переключает его отметку. Вне
     * режима выбора ничего не делает — экран сам решает, что делать с тапом
     * (например, раскрыть рассуждения). */
    fun onMessageTap(messageId: Long) {
        val current = state.value
        if (!current.isSelectionMode) return
        val selected = current.selectedIds
        _state.update {
            it.copy(selectedIds = if (messageId in selected) selected - messageId else selected + messageId)
        }
    }

    /** Полностью выходит из режима выбора, снимая все отметки. */
    fun clearSelection() {
        _state.update { it.copy(selectedIds = emptySet()) }
    }

    fun requestDeleteSelected() {
        if (state.value.selectedIds.isNotEmpty()) {
            _state.update { it.copy(showDeleteDialog = true) }
        }
    }

    fun dismissDeleteDialog() {
        _state.update { it.copy(showDeleteDialog = false) }
    }

    /** Подтверждение удаления в диалоге: одно сообщение — через
     * `deleteMessage`, несколько — одним пакетным вызовом `bulkDeleteMessages`. */
    fun confirmDeleteSelected() {
        val ids = state.value.selectedIds.toList()
        if (ids.isEmpty()) {
            _state.update { it.copy(showDeleteDialog = false) }
            return
        }
        viewModelScope.launch {
            try {
                if (ids.size == 1) {
                    repository.deleteMessage(chatId, ids.first())
                } else {
                    repository.bulkDeleteMessages(chatId, ids)
                }
                _state.update { it.copy(showDeleteDialog = false, selectedIds = emptySet()) }
                refreshChatAndMessages()
            } catch (e: Exception) {
                _state.update { it.copy(showDeleteDialog = false, errorMessage = errorText(e)) }
            }
        }
    }

    /** Сбрасывает показанное сообщение об ошибке после того, как Snackbar его отобразил. */
    fun consumeErrorMessage() {
        _state.update { it.copy(errorMessage = null) }
    }

    private fun errorText(e: Throwable): String = when (e) {
        is AgentApiException -> e.apiMessage ?: e.message ?: "Ошибка сервера (${e.statusCode})"
        is AgentUnreachableException -> e.message ?: "Сервер недоступен"
        else -> e.message ?: "Неизвестная ошибка"
    }
}
