package com.example.agentsapp.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agentsapp.data.remote.AgentApiException
import com.example.agentsapp.data.remote.AgentUnreachableException
import com.example.agentsapp.data.remote.AgentWithChats
import com.example.agentsapp.data.remote.ModelInfo
import com.example.agentsapp.data.repository.AgentsCoreRepository
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
    val errorMessage: String? = null,
)

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
            _uiState.update {
                it.copy(isLoading = false, agentsWithChats = agents, models = models)
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
