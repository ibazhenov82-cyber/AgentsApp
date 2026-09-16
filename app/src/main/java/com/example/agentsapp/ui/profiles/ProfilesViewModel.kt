package com.example.agentsapp.ui.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agentsapp.data.remote.AgentApiException
import com.example.agentsapp.data.remote.AgentUnreachableException
import com.example.agentsapp.data.remote.Profile
import com.example.agentsapp.data.remote.RegisteredSkill
import com.example.agentsapp.data.remote.jsonValueOf
import com.example.agentsapp.data.repository.AgentsCoreRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

data class ProfilesUiState(
    val isLoading: Boolean = true,
    val profiles: List<Profile> = emptyList(),
    val registeredSkills: List<RegisteredSkill> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Общий справочник профилей-пайплайнов персонализации (замечание
 * пользователя: "нужен общий справочник возможных профилей для всех
 * агентов (доступ к справочнику с главного экрана) с возможностью
 * добавить вручную, а профиль выбирается в настройках агента/чата").
 * Список/создание/удаление профилей живут здесь; ПОДКЛЮЧЕНИЕ профиля к
 * конкретному агенту (default_profile_id) или чату (active_profile_id)
 * происходит в SettingsViewModel — этот экран профили ни к чему не
 * привязывает и о конкретном агенте/чате ничего не знает.
 */
class ProfilesViewModel(
    private val repository: AgentsCoreRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProfilesUiState())
    val state: StateFlow<ProfilesUiState> = _state.asStateFlow()

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val profiles = repository.listProfiles()
                val skills = repository.listRegisteredSkills()
                _state.value = _state.value.copy(isLoading = false, profiles = profiles, registeredSkills = skills)
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = errorTextFor(e))
            }
        }
    }

    /** Полноценное создание профиля через интерфейс: имя обязательно,
     * остальные текстовые поля и список привязанных скиллов (по имени из
     * реестра `GET /skills`, см. [RegisteredSkill]) — опциональны. */
    fun createProfile(
        name: String,
        style: String? = null,
        format: String? = null,
        constraints: String? = null,
        skillNames: List<String> = emptyList(),
        orchestrationPrompt: String? = null,
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                repository.createProfile(
                    name = name,
                    style = style?.takeIf { it.isNotBlank() },
                    format = format?.takeIf { it.isNotBlank() },
                    constraints = constraints?.takeIf { it.isNotBlank() },
                    skillNames = skillNames.takeIf { it.isNotEmpty() },
                    orchestrationPrompt = orchestrationPrompt?.takeIf { it.isNotBlank() },
                )
                _state.value = _state.value.copy(profiles = repository.listProfiles())
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = errorTextFor(e))
            }
        }
    }

    /** Редактирование существующего профиля — в отличие от [createProfile],
     * пустое текстовое поле здесь ЯВНО очищает соответствующее значение на
     * сервере (а не просто "не отправляется"), и набор скиллов всегда
     * перезаписывается целиком выбором из диалога (в том числе в пустой
     * список, если пользователь снял все галочки) — это ожидаемое поведение
     * формы редактирования "что видишь, то и сохранится". */
    fun updateProfile(
        profileId: String,
        name: String,
        style: String = "",
        format: String = "",
        constraints: String = "",
        skillNames: List<String> = emptyList(),
        orchestrationPrompt: String = "",
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val patch: Map<String, JsonElement> = mapOf(
                    "name" to jsonValueOf(name.trim()),
                    "style" to jsonValueOf(style.takeIf { it.isNotBlank() }),
                    "format" to jsonValueOf(format.takeIf { it.isNotBlank() }),
                    "constraints" to jsonValueOf(constraints.takeIf { it.isNotBlank() }),
                    "orchestration_prompt" to jsonValueOf(orchestrationPrompt.takeIf { it.isNotBlank() }),
                    "skill_names" to jsonValueOf(skillNames),
                )
                repository.updateProfile(profileId, patch)
                _state.value = _state.value.copy(profiles = repository.listProfiles())
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = errorTextFor(e))
            }
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            try {
                repository.deleteProfile(profileId)
                _state.value = _state.value.copy(profiles = repository.listProfiles())
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = errorTextFor(e))
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
