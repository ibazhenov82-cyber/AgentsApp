package com.example.agentsapp.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agentsapp.data.remote.AgentApiException
import com.example.agentsapp.data.remote.AgentUnreachableException
import com.example.agentsapp.data.remote.AgentWithChats
import com.example.agentsapp.data.remote.ModelInfo
import com.example.agentsapp.data.remote.Profile
import com.example.agentsapp.data.remote.TaskManagerStepEvent
import com.example.agentsapp.data.remote.TaskSummary
import com.example.agentsapp.data.repository.AgentsCoreRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Состояние главного экрана — списка агентов вместе с их чатами. */
data class MainUiState(
    val isLoading: Boolean = true,
    val agentsWithChats: List<AgentWithChats> = emptyList(),
    val models: List<ModelInfo> = emptyList(),
    // Для бейджа "Профиль" у карточки агента (default_profile_id) и строки
    // чата (active_profile_id) — сами Agent/Chat хранят только id профиля,
    // имя нужно искать здесь по id.
    val profiles: List<Profile> = emptyList(),
    // Агрегированный блок "Задачи" на карточке агента (замечание пользователя,
    // пункт 1) — только ТЕКУЩИЕ задачи (активные и на паузе), по одному
    // ключу на агента; загружается только для агентов с включённой настройкой
    // task_tracking_enabled (см. loadData). Пусто у агента — блок не рисуется.
    val agentTasks: Map<String, List<TaskSummary>> = emptyMap(),
    // Завершённые задачи — подгружаются лениво, только когда пользователь
    // явно раскрыл "показать выполненные" у конкретного агента (пункт 1:
    // "опционально можно показать и выполненные задачи").
    val agentCompletedTasks: Map<String, List<TaskSummary>> = emptyMap(),
    // Какие карточки агентов сейчас развёрнуты (блок "Задачи" по умолчанию
    // свёрнут — пункт 1) и у каких из них включён показ завершённых.
    val expandedTaskAgents: Set<String> = emptySet(),
    val showCompletedTaskAgents: Set<String> = emptySet(),
    // Id задач, для которых сейчас выполняется инлайн-шаг/цикл "Менеджера
    // задач" (кнопки "Продолжить"/"Выполнить" — см. [MainViewModel.continueTaskInline]/
    // [executeTaskInline], редизайн "Менеджера задач", замечание пользователя:
    // кнопки в списке задач должны дублировать функционал кнопок в чате).
    val runningTaskIds: Set<String> = emptySet(),
    // true — идёт одиночный шаг ("Продолжить"), false — непрерывное
    // выполнение ("Выполнить", можно прервать в любой момент кнопкой "Пауза" —
    // дополнение пользователя). Валидно только для id из [runningTaskIds].
    val runningTaskAutoPause: Map<String, Boolean> = emptyMap(),
    val errorMessage: String? = null,
) {
    /** Имя профиля по id — null, если профиль не подключён или почему-то не
     * найден в справочнике (например, удалён параллельно другим клиентом). */
    fun profileNameOf(profileId: String?): String? =
        profileId?.let { id -> profiles.firstOrNull { it.id == id }?.name }
}

/**
 * ViewModel главного экрана. Работает исключительно через
 * [AgentsCoreRepository] — адрес сервера и проверка соединения теперь
 * настраиваются на экране "Настройки" (см. [com.example.agentsapp.ui.settings.SettingsViewModel]),
 * этот экран их больше не показывает и не редактирует.
 */
class MainViewModel(
    private val repository: AgentsCoreRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    /** Корутины инлайн-шагов/циклов "Менеджера задач", по одной на задачу — та
     * же логика прерывания, что и в `ChatViewModel.taskManagerJob`. */
    private val taskManagerJobs: MutableMap<String, Job> = mutableMapOf()

    init {
        refresh()
    }

    /** Полностью перечитывает список агентов/чатов с сервера. Каталог
     * моделей запрашивается только один раз и переиспользуется дальше.
     * Вызывается и при первом открытии экрана, и при каждом возврате на
     * него (см. `LaunchedEffect` в [com.example.agentsapp.ui.main.MainScreen]) —
     * иначе статистика по токенам после отправки сообщений в чате не
     * обновилась бы на главном экране. */
    fun refresh() {
        viewModelScope.launch {
            loadData()
        }
    }

    private suspend fun loadData() {
        _uiState.update { it.copy(isLoading = true) }
        try {
            val agents = repository.listAgentsWithChats()
            val existingModels = _uiState.value.models
            val models = if (existingModels.isEmpty()) {
                runCatching { repository.listModels() }.getOrDefault(emptyList())
            } else {
                existingModels
            }
            // В отличие от models — не кэшируется между обновлениями:
            // профили редактируются на отдельном экране ("Профили"), и после
            // возврата оттуда бейдж должен сразу показать актуальное имя.
            val profiles = runCatching { repository.listProfiles() }.getOrDefault(emptyList())
            // Только для агентов с включённым "Отслеживать задачи" — иначе
            // задачи вообще не существуют на сервере (пункт 4 замечаний).
            val agentTasks = agents
                .filter { it.agent.settings.task_tracking_enabled }
                .associate { it.agent.id to runCatching { repository.listAgentTasks(it.agent.id) }.getOrDefault(emptyList()) }
            _uiState.update {
                it.copy(
                    isLoading = false, agentsWithChats = agents, models = models, profiles = profiles,
                    agentTasks = agentTasks,
                )
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(isLoading = false, errorMessage = friendlyMessage(e)) }
        }
    }

    /** Сбрасывает показанное сообщение об ошибке (после того как снэкбар его показал). */
    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /** Имя подставляется в диалоге создания заранее ("Агент N" по
     * порядковому номеру — см. [MainScreen]), но пользователь может его
     * скорректировать перед подтверждением. Пустая строка — на случай, если
     * поле всё же очистили — не отправляется, тогда имя подставит сервер. */
    fun createAgent(name: String) = performAction {
        repository.createAgent(name = name.ifBlank { null }, model = null)
    }

    fun deleteAgent(agentId: String) = performAction {
        repository.deleteAgent(agentId)
    }

    /** Имя подставляется в диалоге создания заранее ("Чат N" по порядковому
     * номеру чата внутри агента — см. [MainScreen]), пользователь может его
     * скорректировать перед подтверждением. */
    fun createChat(agentId: String, title: String) = performAction {
        repository.createChat(agentId, title = title.ifBlank { null })
    }

    fun deleteChat(chatId: String) = performAction {
        repository.deleteChat(chatId)
    }

    fun copyChat(chatId: String, title: String) = performAction {
        repository.copyChat(chatId, title)
    }

    // ---- Агрегированный блок "Задачи" на карточке агента (пункт 1) ------------

    /** Блок по умолчанию свёрнут — разворачивается/сворачивается по клику на
     * заголовок "Задачи (N)" в карточке агента. */
    fun toggleTasksExpanded(agentId: String) {
        _uiState.update { state ->
            val expanded = state.expandedTaskAgents.toMutableSet()
            if (!expanded.add(agentId)) expanded.remove(agentId)
            state.copy(expandedTaskAgents = expanded)
        }
    }

    /** "Опционально можно показать и выполненные задачи" (пункт 1) —
     * подгружается только при первом включении, дальше переиспользуется. */
    fun toggleShowCompletedTasks(agentId: String) {
        val alreadyShown = agentId in _uiState.value.showCompletedTaskAgents
        _uiState.update { state ->
            val shown = state.showCompletedTaskAgents.toMutableSet()
            if (!shown.add(agentId)) shown.remove(agentId)
            state.copy(showCompletedTaskAgents = shown)
        }
        if (!alreadyShown && agentId !in _uiState.value.agentCompletedTasks) {
            viewModelScope.launch {
                val completed = runCatching { repository.listAgentTasks(agentId, includeCompleted = true) }
                    .getOrNull()
                    ?.filter { it.status == "done" }
                    ?: return@launch
                _uiState.update { it.copy(agentCompletedTasks = it.agentCompletedTasks + (agentId to completed)) }
            }
        }
    }

    /** Инлайн-действие "поставить на паузу" прямо из списка (пункт 1) —
     * единственное оставшееся ручное действие ("Отклонить" в системе больше
     * нет). "Продолжить"/"Выполнить" — ОБРАЩАЮТСЯ К МОДЕЛИ, отдельные методы
     * [continueTaskInline]/[executeTaskInline] (редизайн "Менеджера задач") —
     * этот метод им не подходит, поэтому сначала прерывает такой запущенный
     * шаг/цикл для этой задачи, если он идёт. */
    fun pauseTaskInline(agentId: String, taskId: String) {
        cancelInlineTaskRun(taskId)
        viewModelScope.launch {
            try {
                repository.applyTaskActionManually(taskId, "pause")
                val refreshed = runCatching { repository.listAgentTasks(agentId) }.getOrDefault(emptyList())
                _uiState.update { it.copy(agentTasks = it.agentTasks + (agentId to refreshed)) }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = friendlyMessage(e)) }
            }
        }
    }

    /** Кнопка "Продолжить" в списке задач — один шаг (обращение к модели),
     * после которого сервер сам снова ставит задачу на паузу — дублирует по
     * функционалу одноимённую кнопку в карточке-подтверждении на экране чата
     * (замечание пользователя). */
    fun continueTaskInline(agentId: String, taskId: String, chatId: String) =
        startInlineTaskRun(agentId, taskId, chatId, autoPause = true)

    /** Кнопка "Выполнить" в списке задач — без остановок до состояния done;
     * прервать можно в любой момент кнопкой "Пауза" (дополнение пользователя). */
    fun executeTaskInline(agentId: String, taskId: String, chatId: String) =
        startInlineTaskRun(agentId, taskId, chatId, autoPause = false)

    private fun startInlineTaskRun(agentId: String, taskId: String, chatId: String, autoPause: Boolean) {
        if (taskManagerJobs[taskId]?.isActive == true) return
        taskManagerJobs[taskId] = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    runningTaskIds = it.runningTaskIds + taskId,
                    runningTaskAutoPause = it.runningTaskAutoPause + (taskId to autoPause),
                )
            }
            try {
                if (autoPause) {
                    runOneInlineStep(chatId, taskId, autoPause = true)
                } else {
                    var shouldContinue = true
                    while (shouldContinue) {
                        shouldContinue = runOneInlineStep(chatId, taskId, autoPause = false)
                    }
                }
            } finally {
                taskManagerJobs.remove(taskId)
                _uiState.update { it.copy(runningTaskIds = it.runningTaskIds - taskId) }
                val refreshed = runCatching { repository.listAgentTasks(agentId) }.getOrDefault(emptyList())
                _uiState.update { it.copy(agentTasks = it.agentTasks + (agentId to refreshed)) }
            }
        }
    }

    /** Один шаг — возвращает `true`, если цикл нужно продолжать (та же
     * логика, что и в `ChatViewModel.runOneTaskManagerStep`, но без черновика
     * ответа — в списке задач не показывается текст ответа ассистента). */
    private suspend fun runOneInlineStep(chatId: String, taskId: String, autoPause: Boolean): Boolean {
        var shouldContinue = false
        try {
            repository.stepTaskManager(chatId, taskId, autoPause).collect { event ->
                when (event) {
                    is TaskManagerStepEvent.Done -> shouldContinue = event.shouldContinue
                    is TaskManagerStepEvent.Error -> {
                        _uiState.update { it.copy(errorMessage = event.message) }
                        shouldContinue = false
                    }
                    else -> Unit
                }
            }
        } catch (e: Exception) {
            _uiState.update { it.copy(errorMessage = friendlyMessage(e)) }
            shouldContinue = false
        }
        return shouldContinue
    }

    /** Прерывает инлайн-шаг/цикл для задачи [taskId], если он сейчас идёт
     * (кнопка "Пауза" — см. [pauseTaskInline]). */
    private fun cancelInlineTaskRun(taskId: String) {
        taskManagerJobs[taskId]?.cancel()
        taskManagerJobs.remove(taskId)
        _uiState.update { it.copy(runningTaskIds = it.runningTaskIds - taskId) }
    }

    /** Выполняет действие, требующее сети, и по завершении обновляет список
     * (или показывает ошибку, если действие не удалось). */
    private fun performAction(action: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                action()
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = friendlyMessage(e)) }
            }
        }
    }

    private fun friendlyMessage(e: Exception): String = when (e) {
        is AgentApiException -> e.apiMessage ?: e.message ?: "Ошибка сервера"
        is AgentUnreachableException -> e.message ?: "Сервер недоступен"
        else -> e.message ?: "Неизвестная ошибка"
    }
}
