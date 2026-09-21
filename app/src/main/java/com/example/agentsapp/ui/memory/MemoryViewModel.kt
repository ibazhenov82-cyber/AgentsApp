package com.example.agentsapp.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agentsapp.data.remote.AgentApiException
import com.example.agentsapp.data.remote.AgentUnreachableException
import com.example.agentsapp.data.remote.LongTermMemoryEntry
import com.example.agentsapp.data.remote.MemorySnapshot
import com.example.agentsapp.data.remote.TaskManagerStepEvent
import com.example.agentsapp.data.remote.TaskSummary
import com.example.agentsapp.data.remote.WorkingMemoryEntry
import com.example.agentsapp.data.repository.AgentsCoreRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Вкладки экрана "Память". Профили-пайплайны переехали в отдельный общий
 * справочник (см. ProfilesScreen.kt, доступен с главного экрана) — здесь
 * больше нет вкладки "Профиль", а есть только собственно память чата/агента.
 * [SNAPSHOT] — то, что реально уйдёт в следующий запрос модели, загружается
 * по требованию (при первом открытии вкладки), а не вместе с остальным
 * состоянием — основной инструмент проверки юзкейсов ТЗ. */
// [TASKS] — первая вкладка (пункт 5 замечаний пользователя), видна только
// когда у чата включена настройка "Отслеживать задачи" (task_tracking_enabled).
enum class MemoryTab { TASKS, WORKING, LONG_TERM, SNAPSHOT }

/** Категории долговременной памяти — совпадают с
 * `LONG_TERM_MEMORY_CATEGORIES` на сервере (`agents_core/models.py`). Первые
 * три — "базовые" (управляются общим тумблером "Долговременная память"),
 * остальные три — "расширенные" типы, каждый со своим независимым
 * тумблером на экране "Память и профиль". */
val LONG_TERM_MEMORY_CATEGORIES = listOf(
    "profile", "decision", "knowledge", "episodic", "semantic", "procedural",
)

val LONG_TERM_MEMORY_CATEGORY_LABELS = mapOf(
    "profile" to "профиль (о пользователе)",
    "decision" to "решение/договорённость",
    "knowledge" to "знание",
    "episodic" to "эпизодическая (события)",
    "semantic" to "семантическая (факты)",
    "procedural" to "процедурная (как делать)",
)

/** Соответствие категории расширенной долговременной памяти — полю
 * [Settings], которое её включает/выключает. Категории базовой тройки
 * ("profile"/"decision"/"knowledge") управляются общим
 * `long_term_memory_enabled` и здесь не перечислены. */
val LONG_TERM_MEMORY_EXTENDED_CATEGORIES = listOf("episodic", "semantic", "procedural")

data class MemoryUiState(
    val isLoading: Boolean = true,
    val chatId: String = "",
    val agentId: String = "",
    val selectedTab: MemoryTab = MemoryTab.WORKING,
    val workingMemory: List<WorkingMemoryEntry> = emptyList(),
    val longTermMemory: List<LongTermMemoryEntry> = emptyList(),
    val memoryToolsEnabled: Boolean = false,
    // Тумблеры типов памяти — обычные настройки агента/чата (группа
    // "Память" на экране настроек, см. SettingsFieldMeta.kt). Все пять по
    // умолчанию выключены (см. Settings.working_memory_enabled на сервере) —
    // пользователь включает нужные явно, от этого зависит число вкладок.
    val workingMemoryEnabled: Boolean = false,
    val longTermMemoryEnabled: Boolean = false,
    val episodicMemoryEnabled: Boolean = false,
    val semanticMemoryEnabled: Boolean = false,
    val proceduralMemoryEnabled: Boolean = false,
    // "Отслеживать задачи" (День 13, пункт 4/5 замечаний) — определяет,
    // видна ли вкладка "Задачи" вообще (см. visibleTabs в MemoryScreen.kt).
    val taskTrackingEnabled: Boolean = false,
    val tasks: List<TaskSummary> = emptyList(),
    val tasksIncludeCompleted: Boolean = false,
    val isTasksLoading: Boolean = false,
    // Id задач, для которых сейчас идёт инлайн-шаг/цикл "Менеджера задач"
    // (редизайн "Менеджера задач", замечание пользователя: кнопки в списке
    // задач должны дублировать функционал кнопок в чате) — см.
    // [MemoryViewModel.continueTaskInline]/[executeTaskInline].
    val runningTaskIds: Set<String> = emptySet(),
    val runningTaskAutoPause: Map<String, Boolean> = emptyMap(),
    val snapshot: MemorySnapshot? = null,
    val isSnapshotLoading: Boolean = false,
    val errorMessage: String? = null,
) {
    /** Категории, реально доступные для отображения/выбора прямо сейчас —
     * базовая тройка при включённой долговременной памяти плюс те
     * расширенные типы, что включены по отдельности. */
    val enabledLongTermCategories: List<String>
        get() = buildList {
            if (longTermMemoryEnabled) addAll(listOf("profile", "decision", "knowledge"))
            if (episodicMemoryEnabled) add("episodic")
            if (semanticMemoryEnabled) add("semantic")
            if (proceduralMemoryEnabled) add("procedural")
        }
}

/** Состояние этого экрана НАМЕРЕННО не хранится в [ChatUiState] / не
 * переиспользует [ChatViewModel] — рабочая/долговременная память и профили
 * это отдельные сущности со своим CRUD, а не часть переписки чата, поэтому
 * у них свой небольшой ViewModel, независимо перезагружающий данные при
 * каждом открытии экрана. */
class MemoryViewModel(
    private val chatId: String,
    private val repository: AgentsCoreRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(MemoryUiState(chatId = chatId))
    val state: StateFlow<MemoryUiState> = _state.asStateFlow()

    /** Корутины инлайн-шагов/циклов "Менеджера задач", по одной на задачу —
     * та же логика прерывания, что и в `ChatViewModel.taskManagerJob`. */
    private val taskManagerJobs: MutableMap<String, Job> = mutableMapOf()

    init {
        loadAll()
    }

    fun selectTab(tab: MemoryTab) {
        _state.value = _state.value.copy(selectedTab = tab)
        if (tab == MemoryTab.SNAPSHOT && _state.value.snapshot == null) {
            loadSnapshot()
        }
        if (tab == MemoryTab.TASKS) {
            loadTasks()
        }
    }

    fun loadAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val chat = repository.getChat(chatId)
                val agentId = chat.agent_id
                val working = repository.listWorkingMemory(chatId)
                val longTerm = repository.listLongTermMemory(agentId)
                _state.value = _state.value.copy(
                    isLoading = false,
                    agentId = agentId,
                    workingMemory = working,
                    longTermMemory = longTerm,
                    memoryToolsEnabled = chat.settings.memory_tools_enabled,
                    workingMemoryEnabled = chat.settings.working_memory_enabled,
                    longTermMemoryEnabled = chat.settings.long_term_memory_enabled,
                    episodicMemoryEnabled = chat.settings.episodic_memory_enabled,
                    semanticMemoryEnabled = chat.settings.semantic_memory_enabled,
                    proceduralMemoryEnabled = chat.settings.procedural_memory_enabled,
                    taskTrackingEnabled = chat.settings.task_tracking_enabled,
                )
                if (chat.settings.task_tracking_enabled && _state.value.selectedTab == MemoryTab.TASKS) {
                    loadTasks()
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = errorTextFor(e))
            }
        }
    }

    // Тумблеры пяти типов памяти сами по себе больше не живут в этом
    // ViewModel — они переехали в обычные настройки агента/чата (группа
    // "Память", см. SettingsFieldMeta.kt/SettingsViewModel.pushPatch) и
    // редактируются через общий экран настроек. Здесь только читаем
    // актуальные значения в loadAll() — чтобы отфильтровать вкладки/записи
    // этого экрана по тому, что сейчас включено.

    // ---- рабочая память (чат) ---------------------------------------------

    fun saveWorkingMemory(key: String, value: String) {
        if (key.isBlank()) return
        viewModelScope.launch {
            try {
                repository.saveWorkingMemory(chatId, key, value)
                _state.value = _state.value.copy(workingMemory = repository.listWorkingMemory(chatId))
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = errorTextFor(e))
            }
        }
    }

    fun deleteWorkingMemory(key: String) {
        viewModelScope.launch {
            try {
                repository.deleteWorkingMemory(chatId, key)
                _state.value = _state.value.copy(workingMemory = repository.listWorkingMemory(chatId))
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = errorTextFor(e))
            }
        }
    }

    // ---- долговременная память (агент) ---------------------------------------

    fun saveLongTermMemory(category: String, key: String, value: String) {
        if (key.isBlank()) return
        val agentId = _state.value.agentId
        viewModelScope.launch {
            try {
                repository.saveLongTermMemory(agentId, category, key, value)
                _state.value = _state.value.copy(longTermMemory = repository.listLongTermMemory(agentId))
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = errorTextFor(e))
            }
        }
    }

    fun deleteLongTermMemory(category: String, key: String) {
        val agentId = _state.value.agentId
        viewModelScope.launch {
            try {
                repository.deleteLongTermMemory(agentId, category, key)
                _state.value = _state.value.copy(longTermMemory = repository.listLongTermMemory(agentId))
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = errorTextFor(e))
            }
        }
    }

    // Профили-пайплайны (создание/выбор/удаление) больше не живут здесь —
    // см. ProfilesViewModel (общий справочник) и SettingsViewModel (выбор
    // активного профиля для агента/чата в настройках).

    // ---- задачи (вкладка "Задачи", День 13) -----------------------------------

    fun loadTasks() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isTasksLoading = true)
            try {
                val tasks = repository.listChatTasks(chatId, includeCompleted = _state.value.tasksIncludeCompleted)
                _state.value = _state.value.copy(isTasksLoading = false, tasks = tasks)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isTasksLoading = false, errorMessage = errorTextFor(e))
            }
        }
    }

    /** "Опционально можно показать и выполненные задачи" (пункт 1). */
    fun toggleTasksIncludeCompleted() {
        _state.value = _state.value.copy(tasksIncludeCompleted = !_state.value.tasksIncludeCompleted)
        loadTasks()
    }

    /** Инлайн "поставить на паузу" прямо из списка вкладки — единственное
     * оставшееся ручное действие, см. комментарий у аналогичного
     * `MainViewModel.pauseTaskInline`. "Продолжить"/"Выполнить" — отдельные
     * методы ниже (обращаются к модели). */
    fun pauseTaskInline(taskId: String) {
        cancelInlineTaskRun(taskId)
        viewModelScope.launch {
            try {
                repository.applyTaskActionManually(taskId, "pause")
                loadTasks()
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = errorTextFor(e))
            }
        }
    }

    /** Кнопка "Продолжить" — один шаг (обращение к модели), после которого
     * сервер сам снова ставит задачу на паузу — см. комментарий у аналогичного
     * `MainViewModel.continueTaskInline`. */
    fun continueTaskInline(taskId: String) = startInlineTaskRun(taskId, autoPause = true)

    /** Кнопка "Выполнить" — без остановок до состояния done; прервать можно в
     * любой момент кнопкой "Пауза" (дополнение пользователя). */
    fun executeTaskInline(taskId: String) = startInlineTaskRun(taskId, autoPause = false)

    private fun startInlineTaskRun(taskId: String, autoPause: Boolean) {
        if (taskManagerJobs[taskId]?.isActive == true) return
        taskManagerJobs[taskId] = viewModelScope.launch {
            _state.update {
                it.copy(
                    runningTaskIds = it.runningTaskIds + taskId,
                    runningTaskAutoPause = it.runningTaskAutoPause + (taskId to autoPause),
                )
            }
            try {
                if (autoPause) {
                    runOneInlineStep(taskId, autoPause = true)
                } else {
                    var shouldContinue = true
                    while (shouldContinue) {
                        shouldContinue = runOneInlineStep(taskId, autoPause = false)
                    }
                }
            } finally {
                taskManagerJobs.remove(taskId)
                _state.update { it.copy(runningTaskIds = it.runningTaskIds - taskId) }
                loadTasks()
            }
        }
    }

    private suspend fun runOneInlineStep(taskId: String, autoPause: Boolean): Boolean {
        var shouldContinue = false
        try {
            repository.stepTaskManager(chatId, taskId, autoPause).collect { event ->
                when (event) {
                    is TaskManagerStepEvent.Done -> shouldContinue = event.shouldContinue
                    is TaskManagerStepEvent.Error -> {
                        _state.update { it.copy(errorMessage = event.message) }
                        shouldContinue = false
                    }
                    else -> Unit
                }
            }
        } catch (e: Exception) {
            _state.update { it.copy(errorMessage = errorTextFor(e)) }
            shouldContinue = false
        }
        return shouldContinue
    }

    private fun cancelInlineTaskRun(taskId: String) {
        taskManagerJobs[taskId]?.cancel()
        taskManagerJobs.remove(taskId)
        _state.update { it.copy(runningTaskIds = it.runningTaskIds - taskId) }
    }

    // ---- снимок памяти ---------------------------------------------------------

    fun loadSnapshot() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isSnapshotLoading = true)
            try {
                val snapshot = repository.getMemorySnapshot(chatId)
                _state.value = _state.value.copy(isSnapshotLoading = false, snapshot = snapshot)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isSnapshotLoading = false, errorMessage = errorTextFor(e))
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(errorMessage = null)
    }

    private fun errorTextFor(e: Exception): String = when (e) {
        is AgentUnreachableException -> "Не удалось подключиться к серверу AgentsCore. Проверьте адрес сервера и сеть."
        is AgentApiException -> e.apiMessage ?: "Сервер вернул ошибку (код ${e.statusCode})."
        else -> e.message ?: "Неизвестная ошибка."
    }
}
