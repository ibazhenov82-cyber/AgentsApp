package com.example.agentsapp.ui.memory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agentsapp.data.remote.AgentApiException
import com.example.agentsapp.data.remote.AgentUnreachableException
import com.example.agentsapp.data.remote.LongTermMemoryEntry
import com.example.agentsapp.data.remote.MemorySnapshot
import com.example.agentsapp.data.remote.WorkingMemoryEntry
import com.example.agentsapp.data.repository.AgentsCoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Вкладки экрана "Память". Профили-пайплайны переехали в отдельный общий
 * справочник (см. ProfilesScreen.kt, доступен с главного экрана) — здесь
 * больше нет вкладки "Профиль", а есть только собственно память чата/агента.
 * [SNAPSHOT] — то, что реально уйдёт в следующий запрос модели, загружается
 * по требованию (при первом открытии вкладки), а не вместе с остальным
 * состоянием — основной инструмент проверки юзкейсов ТЗ. */
enum class MemoryTab { WORKING, LONG_TERM, SNAPSHOT }

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

    init {
        loadAll()
    }

    fun selectTab(tab: MemoryTab) {
        _state.value = _state.value.copy(selectedTab = tab)
        if (tab == MemoryTab.SNAPSHOT && _state.value.snapshot == null) {
            loadSnapshot()
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
                )
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
