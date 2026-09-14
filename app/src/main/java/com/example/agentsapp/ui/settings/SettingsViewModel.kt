package com.example.agentsapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agentsapp.data.remote.AgentApiException
import com.example.agentsapp.data.remote.AgentUnreachableException
import com.example.agentsapp.data.remote.DefaultSettings
import com.example.agentsapp.data.remote.ModelInfo
import com.example.agentsapp.data.remote.ServerConnectionSettings
import com.example.agentsapp.data.remote.Settings
import com.example.agentsapp.data.remote.SettingsFieldDef
import com.example.agentsapp.data.remote.DEFAULT_SETTINGS_FIELDS
import com.example.agentsapp.data.remote.AGENT_SETTINGS_FIELDS
import com.example.agentsapp.data.remote.jsonValueOf
import com.example.agentsapp.data.remote.readDefaultSettingsField
import com.example.agentsapp.data.remote.readSettingsField
import com.example.agentsapp.data.repository.AgentsCoreRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Режим экрана настроек: настройки по умолчанию, настройки конкретного
 * агента или настройки конкретного чата — все три используют один и тот же
 * экран, отличаются только источником данных и набором полей. */
sealed class SettingsMode {
    data object Default : SettingsMode()
    data class Agent(val agentId: String) : SettingsMode()
    data class Chat(val chatId: String) : SettingsMode()
}

/** Задержка перед отправкой текстовых/числовых полей на сервер после
 * последнего изменения пользователем (debounce), чтобы не заваливать сеть
 * запросом на каждое нажатие клавиши. */
private const val DEBOUNCE_MS = 500L

data class SettingsUiState(
    val isLoading: Boolean = true,
    val title: String = "",
    val entityName: String? = null,
    val fields: List<SettingsFieldDef> = emptyList(),
    val values: Map<String, Any?> = emptyMap(),
    val modelEditable: Boolean = true,
    val models: List<ModelInfo> = emptyList(),
    val showResetButton: Boolean = false,
    /** Блок "Соединение с сервером" показывается только в режиме [SettingsMode.Default] —
     * это единственная настройка уровня самого приложения, а не сервера. */
    val showConnectionBlock: Boolean = false,
    val connectionUrl: String = "",
    val isCheckingConnection: Boolean = false,
    val connectionCheckResult: String? = null,
    val connectionCheckSucceeded: Boolean = false,
    val errorMessage: String? = null,
)

class SettingsViewModel(
    private val mode: SettingsMode,
    private val repository: AgentsCoreRepository,
    private val connectionSettings: ServerConnectionSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    // Отдельная debounce-корутина на каждое системное имя поля, плюс одна — для
    // переименования сущности (ключ "__name__") и одна — для адреса сервера
    // (ключ "__connection_url__").
    private val debounceJobs = mutableMapOf<String, Job>()

    init {
        val (title, fields, modelEditable, showReset) = when (mode) {
            is SettingsMode.Default -> Quad("Настройки", DEFAULT_SETTINGS_FIELDS, true, true)
            is SettingsMode.Agent -> Quad("Настройки агента", AGENT_SETTINGS_FIELDS, true, false)
            is SettingsMode.Chat -> Quad("Настройки чата", AGENT_SETTINGS_FIELDS, false, false)
        }
        _state.value = _state.value.copy(
            title = title, fields = fields, modelEditable = modelEditable, showResetButton = showReset,
            showConnectionBlock = mode is SettingsMode.Default,
            connectionUrl = if (mode is SettingsMode.Default) connectionSettings.currentBaseUrl() else "",
        )
        loadAll()
    }

    /** Небольшая замена data class с 4 полями, чтобы не заводить отдельный тип верхнего уровня. */
    private data class Quad(val title: String, val fields: List<SettingsFieldDef>, val modelEditable: Boolean, val showReset: Boolean)

    private fun loadAll() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                when (mode) {
                    is SettingsMode.Default -> {
                        val settings = repository.getDefaultSettings()
                        val models = runCatching { repository.listModels() }.getOrDefault(emptyList())
                        _state.value = _state.value.copy(
                            isLoading = false,
                            entityName = null,
                            values = valuesFromDefault(settings),
                            models = models,
                        )
                    }
                    is SettingsMode.Agent -> {
                        val agent = repository.getAgent(mode.agentId)
                        val settings = repository.getAgentSettings(mode.agentId)
                        val models = runCatching { repository.listModels() }.getOrDefault(emptyList())
                        _state.value = _state.value.copy(
                            isLoading = false,
                            entityName = agent.name,
                            values = valuesFromSettings(settings),
                            models = models,
                        )
                    }
                    is SettingsMode.Chat -> {
                        val chat = repository.getChat(mode.chatId)
                        val settings = repository.getChatSettings(mode.chatId)
                        _state.value = _state.value.copy(
                            isLoading = false,
                            entityName = chat.title,
                            values = valuesFromSettings(settings),
                            models = emptyList(),
                        )
                    }
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = errorTextFor(e))
            }
        }
    }

    private fun valuesFromDefault(settings: DefaultSettings): Map<String, Any?> =
        DEFAULT_SETTINGS_FIELDS.associate { it.sysName to readDefaultSettingsField(settings, it.sysName) }

    private fun valuesFromSettings(settings: Settings): Map<String, Any?> =
        AGENT_SETTINGS_FIELDS.associate { it.sysName to readSettingsField(settings, it.sysName) }

    /** Локальное обновление значения поля в состоянии — используется всеми
     * путями сохранения перед отправкой на сервер (или вместо неё, для
     * промежуточных состояний слайдера). */
    private fun setLocalValue(sysName: String, value: Any?) {
        _state.value = _state.value.copy(values = _state.value.values + (sysName to value))
    }

    /** Немедленное сохранение одного поля — для переключателей и выпадающих списков. */
    fun onImmediateChange(sysName: String, value: Any?) {
        setLocalValue(sysName, value)
        debounceJobs[sysName]?.cancel()
        pushPatch(mapOf(sysName to value))
    }

    /** Значение слайдера меняется локально во время перетаскивания без сети. */
    fun onSliderDrag(sysName: String, value: Float) {
        setLocalValue(sysName, value.toDouble())
    }

    /** Значение слайдера отпущено — отправляем на сервер. */
    fun onSliderChangeFinished(sysName: String) {
        val value = _state.value.values[sysName]
        pushPatch(mapOf(sysName to value))
    }

    /** Текстовое/числовое поле изменилось — локально сразу, на сервер — с debounce. */
    fun onDebouncedChange(sysName: String, value: Any?) {
        setLocalValue(sysName, value)
        debounceJobs[sysName]?.cancel()
        debounceJobs[sysName] = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            pushPatch(mapOf(sysName to value))
        }
    }

    /** Переименование агента/чата — локально сразу, на сервер — с debounce. */
    fun onEntityNameChange(newName: String) {
        _state.value = _state.value.copy(entityName = newName)
        debounceJobs["__name__"]?.cancel()
        debounceJobs["__name__"] = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            try {
                when (mode) {
                    is SettingsMode.Agent -> repository.renameAgent(mode.agentId, newName)
                    is SettingsMode.Chat -> repository.renameChat(mode.chatId, newName)
                    is SettingsMode.Default -> Unit
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = errorTextFor(e))
            }
        }
    }

    // ---- Соединение с сервером (только режим Default) ------------------------

    /** Адрес сервера меняется локально сразу; фактическое сохранение (и,
     * значит, переключение всех дальнейших сетевых вызовов на новый адрес)
     * происходит с той же задержкой debounce, что и у остальных полей. */
    fun onConnectionUrlChange(url: String) {
        _state.value = _state.value.copy(connectionUrl = url, connectionCheckResult = null)
        debounceJobs["__connection_url__"]?.cancel()
        debounceJobs["__connection_url__"] = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            connectionSettings.setBaseUrl(url)
        }
    }

    /** Проверка соединения по кнопке рядом с полем ввода адреса: сначала
     * гарантированно сохраняет введённый адрес (отменяя отложенное
     * сохранение и применяя его немедленно), затем пробует `GET /health`. */
    fun checkConnection() {
        val url = _state.value.connectionUrl
        debounceJobs["__connection_url__"]?.cancel()
        connectionSettings.setBaseUrl(url)
        _state.value = _state.value.copy(isCheckingConnection = true, connectionCheckResult = null)
        viewModelScope.launch {
            try {
                val health = repository.health()
                // Версия сервера показывается рядом с "Подключено", чтобы
                // сразу было видно, если сервер запущен со старой версией
                // AgentsCore (например, после обновления приложения забыли
                // передеплоить сервер) — источник многих запутанных
                // расхождений между ожидаемым и фактическим поведением.
                val versionSuffix = if (health.version.isNotBlank()) " (версия сервера: ${health.version})" else ""
                _state.value = _state.value.copy(
                    isCheckingConnection = false,
                    connectionCheckResult = "Подключено$versionSuffix",
                    connectionCheckSucceeded = true,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isCheckingConnection = false,
                    connectionCheckResult = "Не удалось подключиться: ${errorTextFor(e)}",
                    connectionCheckSucceeded = false,
                )
            }
        }
    }

    private fun pushPatch(patch: Map<String, Any?>) {
        viewModelScope.launch {
            try {
                val jsonPatch = patch.mapValues { (_, v) -> jsonValueOf(v) }
                when (mode) {
                    is SettingsMode.Default -> repository.updateDefaultSettings(jsonPatch)
                    is SettingsMode.Agent -> repository.updateAgentSettings(mode.agentId, jsonPatch)
                    is SettingsMode.Chat -> repository.updateChatSettings(mode.chatId, jsonPatch)
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = errorTextFor(e))
            }
        }
    }

    /** Сброс настроек по умолчанию к встроенным значениям сервера (только режим Default). */
    fun resetToDefaults() {
        if (mode !is SettingsMode.Default) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, errorMessage = null)
            try {
                val settings = repository.resetDefaultSettings()
                _state.value = _state.value.copy(isLoading = false, values = valuesFromDefault(settings))
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, errorMessage = errorTextFor(e))
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
