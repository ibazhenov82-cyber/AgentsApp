package com.example.agentsapp.data.repository

import com.example.agentsapp.data.remote.Agent
import com.example.agentsapp.data.remote.AgentWithChats
import com.example.agentsapp.data.remote.AgentsCoreApiClient
import com.example.agentsapp.data.remote.Branch
import com.example.agentsapp.data.remote.Chat
import com.example.agentsapp.data.remote.DefaultSettings
import com.example.agentsapp.data.remote.HealthResponse
import com.example.agentsapp.data.remote.Invariant
import com.example.agentsapp.data.remote.LongTermMemoryEntry
import com.example.agentsapp.data.remote.MemorySnapshot
import com.example.agentsapp.data.remote.Message
import com.example.agentsapp.data.remote.ModelHealth
import com.example.agentsapp.data.remote.McpToolDescription
import com.example.agentsapp.data.remote.ModelInfo
import com.example.agentsapp.data.remote.Profile
import com.example.agentsapp.data.remote.RegisteredSkill
import com.example.agentsapp.data.remote.Settings
import com.example.agentsapp.data.remote.TaskDetail
import com.example.agentsapp.data.remote.TaskStateMachineInfo
import com.example.agentsapp.data.remote.TaskSummary
import com.example.agentsapp.data.remote.WorkingMemoryEntry
import kotlinx.serialization.json.JsonElement

/** Единственная точка, через которую экраны обращаются к данным — тонкая
 * прослойка поверх [AgentsCoreApiClient]. Не содержит собственного
 * состояния/кэша: каждый вызов уходит на сервер. */
class AgentsCoreRepository(private val api: AgentsCoreApiClient) {

    suspend fun health(): HealthResponse = api.health()
    suspend fun listModels(): List<ModelInfo> = api.listModels()
    suspend fun listMcpTools(): List<McpToolDescription> = api.listMcpTools()
    suspend fun modelHealth(modelId: String): ModelHealth = api.modelHealth(modelId)

    suspend fun getDefaultSettings(): DefaultSettings = api.getDefaultSettings()
    suspend fun getNewAgentSettings(): Settings = api.getNewAgentSettings()
    suspend fun updateDefaultSettings(patch: Map<String, JsonElement>): DefaultSettings = api.updateDefaultSettings(patch)
    suspend fun resetDefaultSettings(): DefaultSettings = api.resetDefaultSettings()

    suspend fun listAgentsWithChats(): List<AgentWithChats> = api.listAgentsWithChats()
    suspend fun createAgent(name: String?, model: String?): Agent = api.createAgent(name, model)
    suspend fun getAgent(agentId: String): Agent = api.getAgent(agentId)
    suspend fun renameAgent(agentId: String, name: String): Agent = api.renameAgent(agentId, name)
    suspend fun deleteAgent(agentId: String) = api.deleteAgent(agentId)
    suspend fun getAgentSettings(agentId: String): Settings = api.getAgentSettings(agentId)
    suspend fun updateAgentSettings(agentId: String, patch: Map<String, JsonElement>): Settings = api.updateAgentSettings(agentId, patch)
    suspend fun createChat(agentId: String, title: String?): Chat = api.createChat(agentId, title)

    suspend fun listChats(agentId: String? = null): List<Chat> = api.listChats(agentId)
    suspend fun getChat(chatId: String, branch: Int? = null): Chat = api.getChat(chatId, branch)
    suspend fun renameChat(chatId: String, title: String): Chat = api.renameChat(chatId, title)
    suspend fun deleteChat(chatId: String) = api.deleteChat(chatId)
    suspend fun copyChat(chatId: String, title: String): Chat = api.copyChat(chatId, title)
    suspend fun getChatSettings(chatId: String): Settings = api.getChatSettings(chatId)
    suspend fun updateChatSettings(chatId: String, patch: Map<String, JsonElement>): Settings = api.updateChatSettings(chatId, patch)
    suspend fun summarizeChat(chatId: String): Message = api.summarizeChat(chatId)

    suspend fun listBranches(chatId: String): List<Branch> = api.listBranches(chatId)
    suspend fun createBranch(chatId: String, name: String? = null): Branch = api.createBranch(chatId, name)
    suspend fun deleteBranch(chatId: String, number: Int) = api.deleteBranch(chatId, number)

    suspend fun listMessages(chatId: String): List<Message> = api.listMessages(chatId)
    suspend fun clearMessages(chatId: String) = api.clearMessages(chatId)
    suspend fun deleteMessage(chatId: String, messageId: Long) = api.deleteMessage(chatId, messageId)
    suspend fun bulkDeleteMessages(chatId: String, ids: List<Long>) = api.bulkDeleteMessages(chatId, ids)
    // Отправка сообщений и шаги Менеджера задач — асинхронные запуски, см.
    // [RunsRepository] (живёт на уровне приложения, а не экрана чата).

    // ---- Рабочая/долговременная память и профили-пайплайны -------------------

    suspend fun listWorkingMemory(chatId: String): List<WorkingMemoryEntry> = api.listWorkingMemory(chatId)
    suspend fun saveWorkingMemory(chatId: String, key: String, value: String): WorkingMemoryEntry =
        api.saveWorkingMemory(chatId, key, value)
    suspend fun deleteWorkingMemory(chatId: String, key: String) = api.deleteWorkingMemory(chatId, key)

    suspend fun listLongTermMemory(agentId: String, category: String? = null): List<LongTermMemoryEntry> =
        api.listLongTermMemory(agentId, category)
    suspend fun saveLongTermMemory(agentId: String, category: String, key: String, value: String): LongTermMemoryEntry =
        api.saveLongTermMemory(agentId, category, key, value)
    suspend fun deleteLongTermMemory(agentId: String, category: String, key: String) =
        api.deleteLongTermMemory(agentId, category, key)

    // Профили — общий справочник для всех агентов (см. Profile в AgentsCoreModels.kt).
    suspend fun listProfiles(): List<Profile> = api.listProfiles()
    suspend fun listRegisteredSkills(): List<RegisteredSkill> = api.listRegisteredSkills()
    suspend fun createProfile(
        name: String,
        style: String? = null,
        format: String? = null,
        constraints: String? = null,
        skillsJson: String = "",
        skillNames: List<String>? = null,
        orchestrationPrompt: String? = null,
    ): Profile = api.createProfile(name, style, format, constraints, skillsJson, skillNames, orchestrationPrompt)
    suspend fun updateProfile(profileId: String, patch: Map<String, JsonElement>): Profile = api.updateProfile(profileId, patch)
    suspend fun deleteProfile(profileId: String) = api.deleteProfile(profileId)
    suspend fun setChatActiveProfile(chatId: String, profileId: String?): Chat = api.setChatActiveProfile(chatId, profileId)
    suspend fun setAgentDefaultProfile(agentId: String, profileId: String?): Agent = api.setAgentDefaultProfile(agentId, profileId)

    suspend fun getMemorySnapshot(chatId: String): MemorySnapshot = api.getMemorySnapshot(chatId)

    // ---- День 14. Инварианты (общий справочник, см. Invariant в AgentsCoreModels.kt) ---

    suspend fun listInvariants(): List<Invariant> = api.listInvariants()
    suspend fun createInvariant(
        title: String, ruleText: String, kind: String? = null, isActive: Boolean = true,
    ): Invariant = api.createInvariant(title, ruleText, kind, isActive)
    suspend fun updateInvariant(invariantId: String, patch: Map<String, JsonElement>): Invariant =
        api.updateInvariant(invariantId, patch)
    suspend fun deleteInvariant(invariantId: String) = api.deleteInvariant(invariantId)
    suspend fun setAgentInvariants(agentId: String, invariantIds: List<String>): Agent =
        api.setAgentInvariants(agentId, invariantIds)
    suspend fun setChatInvariants(chatId: String, invariantIds: List<String>): Chat =
        api.setChatInvariants(chatId, invariantIds)

    // ---- День 13/15. Состояние задачи (Task State Machine) --------------------

    suspend fun getTaskStateMachine(): TaskStateMachineInfo = api.getTaskStateMachine()
    suspend fun setTaskStateMachineInvariants(invariantIds: List<String>): TaskStateMachineInfo =
        api.setTaskStateMachineInvariants(invariantIds)

    /** [includeCompleted] — см. `AgentsCoreApiClient.listChatTasks`. */
    suspend fun listChatTasks(chatId: String, includeCompleted: Boolean = false): List<TaskSummary> =
        api.listChatTasks(chatId, includeCompleted)
    suspend fun listAgentTasks(agentId: String, includeCompleted: Boolean = false): List<TaskSummary> =
        api.listAgentTasks(agentId, includeCompleted)
    suspend fun getTask(taskId: String): TaskDetail = api.getTask(taskId)
    suspend fun applyTaskActionManually(taskId: String, action: String, note: String? = null): TaskDetail =
        api.applyTaskActionManually(taskId, action, note)
    suspend fun deleteTask(taskId: String) = api.deleteTask(taskId)

}
