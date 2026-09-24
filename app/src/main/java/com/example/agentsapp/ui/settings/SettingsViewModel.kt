package com.example.agentsapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.agentsapp.data.remote.AgentApiException
import com.example.agentsapp.data.remote.AgentUnreachableException
import com.example.agentsapp.data.remote.DefaultSettings
import com.example.agentsapp.data.remote.Invariant
import com.example.agentsapp.data.remote.McpConnectionSettings
import com.example.agentsapp.data.remote.McpToolDescription
import com.example.agentsapp.data.remote.ModelInfo
import com.example.agentsapp.data.remote.Profile
import com.example.agentsapp.data.remote.ServerConnectionSettings
import com.example.agentsapp.data.remote.Settings
import com.example.agentsapp.data.remote.SettingsFieldDef
import com.example.agentsapp.data.remote.DEFAULT_SETTINGS_FIELDS
import com.example.agentsapp.data.remote.AGENT_SETTINGS_FIELDS
import com.example.agentsapp.data.remote.jsonValueOf
import com.example.agentsapp.data.remote.readDefaultSettingsField
import com.example.agentsapp.data.remote.readSettingsField
import com.example.agentsapp.data.repository.AgentsCoreRepository
import com.example.agentsapp.data.repository.McpRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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
    /** Профиль-пайплайн персонализации выбирается здесь (не через generic
     * [SettingsFieldDef] — это не поле [Settings], а отдельная связь агента/
     * чата с общим справочником профилей, см. Agent.default_profile_id /
     * Chat.active_profile_id). Показывается в режимах [SettingsMode.Agent] и
     * [SettingsMode.Chat] — в режиме агента это выбор "по умолчанию" для
     * НОВЫХ чатов, в режиме чата — активный профиль именно этого чата. */
    val showProfilePicker: Boolean = false,
    val availableProfiles: List<Profile> = emptyList(),
    val selectedProfileId: String? = null,
    /** Инварианты ("День 14") — по аналогии с профилем выше, но
     * МНОЖЕСТВЕННЫЙ выбор из общего справочника (Agent.invariant_ids /
     * Chat.invariant_ids) вместо одиночной ссылки. Итоговый набор,
     * действующий в конкретном чате, — объединение выбора агента и выбора
     * самого чата (см. Repository._effective_invariant_ids на сервере). */
    val showInvariantsPicker: Boolean = false,
    val availableInvariants: List<Invariant> = emptyList(),
    val selectedInvariantIds: List<String> = emptyList(),
    /** Адрес отдельного MCP-сервера (новое ТЗ) — второе поле блока
     * "Соединение с сервером", показывается только в режиме [SettingsMode.Default],
     * рядом с адресом AgentsCore, но сохраняется в [McpConnectionSettings]. */
    val mcpConnectionUrl: String = "",
    /** Режим "Выбрать из доступных" для поля "Список функций в формате OpenAI"
     * (tools_json) — показывается только в режимах [SettingsMode.Agent]/[SettingsMode.Chat],
     * рядом с полем GROUP_TOOLS. Переключение на ручной ввод JSON и обратно
     * НЕ объединяет изменения — список отмеченных инструментов при входе в
     * режим строится заново по содержимому текущего tools_json (см.
     * [parseSelectedMcpToolNames]), а всё остальное содержимое JSON вне
     * распознанных функций при последующем изменении из списка теряется. */
    val toolsPickerMode: Boolean = false,
    val availableMcpTools: List<McpToolDescription> = emptyList(),
    val selectedMcpToolNames: Set<String> = emptySet(),
    val errorMessage: String? = null,
)

class SettingsViewModel(
    private val mode: SettingsMode,
    private val repository: AgentsCoreRepository,
    private val connectionSettings: ServerConnectionSettings,
    private val mcpConnectionSettings: McpConnectionSettings,
    private val mcpRepository: McpRepository,
) : ViewModel() {

    private val toolsJson = Json { ignoreUnknownKeys = true; prettyPrint = true }

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
            mcpConnectionUrl = if (mode is SettingsMode.Default) mcpConnectionSettings.currentBaseUrl() else "",
            showProfilePicker = mode is SettingsMode.Agent || mode is SettingsMode.Chat,
            showInvariantsPicker = mode is SettingsMode.Agent || mode is SettingsMode.Chat,
        )
        loadAll()
        if (mode is SettingsMode.Agent || mode is SettingsMode.Chat) {
            viewModelScope.launch {
                // Только инструменты, которые модель может реально ВЫЗВАТЬ через
                // MCP-протокол: `schedulable == true` записи `/api/tools`
                // (git_pull/http_fetch/git_host_poll) — это виды ПЕРИОДИЧЕСКИХ
                // задач, а не MCP-инструменты; выбранные здесь, они давали
                // модели заведомо недоступную функцию ("unknown tool").
                val tools = runCatching { mcpRepository.listTools() }.getOrDefault(emptyList())
                    .filter { !it.schedulable }
                _state.value = _state.value.copy(availableMcpTools = tools)
            }
        }
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
                        val profiles = runCatching { repository.listProfiles() }.getOrDefault(emptyList())
                        val invariants = runCatching { repository.listInvariants() }.getOrDefault(emptyList())
                        _state.value = _state.value.copy(
                            isLoading = false,
                            entityName = agent.name,
                            values = valuesFromSettings(settings),
                            models = models,
                            availableProfiles = profiles,
                            selectedProfileId = agent.default_profile_id,
                            availableInvariants = invariants,
                            selectedInvariantIds = agent.invariant_ids,
                        )
                    }
                    is SettingsMode.Chat -> {
                        val chat = repository.getChat(mode.chatId)
                        val settings = repository.getChatSettings(mode.chatId)
                        val profiles = runCatching { repository.listProfiles() }.getOrDefault(emptyList())
                        val invariants = runCatching { repository.listInvariants() }.getOrDefault(emptyList())
                        _state.value = _state.value.copy(
                            isLoading = false,
                            entityName = chat.title,
                            values = valuesFromSettings(settings),
                            models = emptyList(),
                            availableProfiles = profiles,
                            selectedProfileId = chat.active_profile_id,
                            availableInvariants = invariants,
                            selectedInvariantIds = chat.invariant_ids,
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

    /** Немедленное сохранение одного поля — для переключателей и выпадающих списков.
     *
     * "Разрешить агенту сохранять память" (memory_tools_enabled) — особый
     * случай (по замечанию пользователя): переключатели типов памяти
     * недоступны, пока эта настройка выключена (см. MEMORY_TYPE_FIELD_NAMES в
     * SettingsScreen.kt), поэтому их состояние должно оставаться осмысленным
     * само по себе, без отдельного открытого экрана. При включении —
     * включаются "Рабочая" и "Долговременная" (остальные три — выключены), при
     * выключении — сбрасываются в выключено все пять типов сразу. */
    fun onImmediateChange(sysName: String, value: Any?) {
        if (sysName == "memory_tools_enabled") {
            val enabled = value as? Boolean ?: false
            val updates = mapOf(
                "memory_tools_enabled" to enabled,
                "working_memory_enabled" to enabled,
                "long_term_memory_enabled" to enabled,
                "episodic_memory_enabled" to false,
                "semantic_memory_enabled" to false,
                "procedural_memory_enabled" to false,
            )
            updates.forEach { (name, v) -> setLocalValue(name, v) }
            debounceJobs[sysName]?.cancel()
            pushPatch(updates)
            return
        }
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

    /** Адрес отдельного MCP-сервера — тот же debounce-приём, что и у адреса
     * AgentsCore выше, но сохраняется в [McpConnectionSettings] (независимый
     * сервис, свой адрес, см. AppContainer). */
    fun onMcpConnectionUrlChange(url: String) {
        _state.value = _state.value.copy(mcpConnectionUrl = url)
        debounceJobs["__mcp_connection_url__"]?.cancel()
        debounceJobs["__mcp_connection_url__"] = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            mcpConnectionSettings.setBaseUrl(url)
        }
    }

    // ---- Режим "Выбрать из доступных" для tools_json (Agent/Chat) --------

    /** Включение режима списка — набор отмеченных функций строится заново по
     * ТЕКУЩЕМУ содержимому tools_json (сопоставление по имени функции с
     * инструментами MCP-сервера); переключение обратно на JSON ничего не
     * меняет в самом tools_json — это тот же текст, что и был. */
    fun onToolsPickerModeChange(enabled: Boolean) {
        val selected = if (enabled) {
            parseSelectedMcpToolNames(_state.value.values["tools_json"] as? String ?: "")
        } else {
            _state.value.selectedMcpToolNames
        }
        _state.value = _state.value.copy(toolsPickerMode = enabled, selectedMcpToolNames = selected)
    }

    /** Отметка/снятие инструмента в списке — tools_json полностью
     * перестраивается из текущего набора отмеченных инструментов (без
     * объединения с тем, что было в JSON до входа в режим списка). */
    fun onMcpToolToggle(tool: McpToolDescription) {
        val updated = if (tool.name in _state.value.selectedMcpToolNames) {
            _state.value.selectedMcpToolNames - tool.name
        } else {
            _state.value.selectedMcpToolNames + tool.name
        }
        val rebuilt = buildToolsJson(_state.value.availableMcpTools, updated)
        _state.value = _state.value.copy(selectedMcpToolNames = updated)
        onDebouncedChange("tools_json", rebuilt)
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

    /** Подключить/отключить профиль ([profileId] == null — отключить).
     * В режиме [SettingsMode.Agent] — это default_profile_id (действует
     * только на НОВЫЕ чаты этого агента, см. Repository.create_chat на
     * сервере); в режиме [SettingsMode.Chat] — active_profile_id именно
     * этого чата, немедленно. */
    fun onProfileChange(profileId: String?) {
        _state.value = _state.value.copy(selectedProfileId = profileId)
        viewModelScope.launch {
            try {
                when (mode) {
                    is SettingsMode.Agent -> repository.setAgentDefaultProfile(mode.agentId, profileId)
                    is SettingsMode.Chat -> repository.setChatActiveProfile(mode.chatId, profileId)
                    is SettingsMode.Default -> Unit
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = errorTextFor(e))
            }
        }
    }

    /** Множественный выбор инвариантов — заменяет весь набор целиком (см.
     * PUT /agents/{id}/invariants и PUT /chats/{id}/invariants). В режиме
     * [SettingsMode.Agent] действует во всех чатах этого агента; в режиме
     * [SettingsMode.Chat] — только в этом чате (итоговый набор для чата —
     * объединение обоих, считается на сервере). */
    fun onInvariantsChange(invariantIds: List<String>) {
        _state.value = _state.value.copy(selectedInvariantIds = invariantIds)
        viewModelScope.launch {
            try {
                when (mode) {
                    is SettingsMode.Agent -> repository.setAgentInvariants(mode.agentId, invariantIds)
                    is SettingsMode.Chat -> repository.setChatInvariants(mode.chatId, invariantIds)
                    is SettingsMode.Default -> Unit
                }
            } catch (e: Exception) {
                _state.value = _state.value.copy(errorMessage = errorTextFor(e))
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

    /** Разбирает текущий tools_json (массив объектов вида `{"type":"function",
     * "function":{"name":...}}`, формат OpenAI) и возвращает имена, которые
     * совпадают с именами инструментов MCP-сервера — используется только для
     * предзаполнения списка при ВХОДЕ в режим "Выбрать из доступных" (см.
     * [onToolsPickerModeChange]); произвольный ручной JSON без узнаваемых
     * функций даёт пустой набор, без ошибок. */
    private fun parseSelectedMcpToolNames(rawToolsJson: String): Set<String> {
        if (rawToolsJson.isBlank()) return emptySet()
        return runCatching {
            val array = toolsJson.parseToJsonElement(rawToolsJson) as? JsonArray ?: return emptySet()
            array.mapNotNull { entry ->
                val obj = entry as? JsonObject ?: return@mapNotNull null
                val fn = obj["function"]?.jsonObject ?: obj
                fn["name"]?.jsonPrimitive?.content
            }.toSet()
        }.getOrDefault(emptySet())
    }

    /** Строит tools_json (формат OpenAI: массив `{"type":"function","function":
     * {"name":...,"description":...,"parameters":...}}`) из выбранных
     * инструментов MCP-сервера — ПОЛНАЯ замена содержимого поля, без
     * объединения с тем, что было в JSON раньше (см. класс-докстринг
     * [SettingsUiState.toolsPickerMode]). */
    private fun buildToolsJson(available: List<McpToolDescription>, selected: Set<String>): String {
        val chosen = available.filter { it.name in selected }
        if (chosen.isEmpty()) return ""
        val array = buildJsonArray {
            chosen.forEach { tool ->
                add(
                    buildJsonObject {
                        put("type", "function")
                        put(
                            "function",
                            buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parameters)
                            },
                        )
                    },
                )
            }
        }
        return toolsJson.encodeToString(JsonArray.serializer(), array)
    }
}
