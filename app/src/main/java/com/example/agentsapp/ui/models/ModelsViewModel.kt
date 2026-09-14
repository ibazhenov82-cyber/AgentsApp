package com.example.agentsapp.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agentsapp.data.remote.AgentApiException
import com.example.agentsapp.data.remote.AgentUnreachableException
import com.example.agentsapp.data.remote.ModelInfo
import com.example.agentsapp.data.repository.AgentsCoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Состояние последней проверки связи с моделью. */
enum class HealthState {
    UNKNOWN,
    CHECKING,
    HEALTHY,
    UNHEALTHY,
}

data class ModelsUiState(
    val isLoading: Boolean = true,
    val models: List<ModelInfo> = emptyList(),
    val health: Map<String, HealthState> = emptyMap(),
    val errorMessage: String? = null,
)

class ModelsViewModel(private val repository: AgentsCoreRepository) : ViewModel() {

    private val _state = MutableStateFlow(ModelsUiState())
    val state: StateFlow<ModelsUiState> = _state.asStateFlow()

    init {
        loadModels()
    }

    private fun loadModels() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val models = repository.listModels()
                _state.value = _state.value.copy(isLoading = false, models = models)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = errorTextFor(e))
            }
        }
    }

    /** Запускает проверку связи с моделью [modelId] и обновляет её состояние в карте [ModelsUiState.health]. */
    fun checkHealth(modelId: String) {
        _state.value = _state.value.copy(health = _state.value.health + (modelId to HealthState.CHECKING))
        viewModelScope.launch {
            val result = try {
                if (repository.modelHealth(modelId).healthy) HealthState.HEALTHY else HealthState.UNHEALTHY
            } catch (e: Exception) {
                HealthState.UNHEALTHY
            }
            _state.value = _state.value.copy(health = _state.value.health + (modelId to result))
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
