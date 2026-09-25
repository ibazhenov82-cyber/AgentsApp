package com.example.agentsapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.agentsapp.data.remote.ServerConnectionSettings
import com.example.agentsapp.data.repository.AgentsCoreRepository
import com.example.agentsapp.data.repository.McpRepository
import com.example.agentsapp.data.repository.RunsRepository
import com.example.agentsapp.data.repository.SchedulerRepository
import com.example.agentsapp.data.UiPreferences
import com.example.agentsapp.ui.chat.ChatViewModel
import com.example.agentsapp.ui.invariants.InvariantsViewModel
import com.example.agentsapp.ui.main.MainViewModel
import com.example.agentsapp.ui.mcp.GitHostsViewModel
import com.example.agentsapp.ui.mcp.McpViewModel
import com.example.agentsapp.ui.memory.MemoryViewModel
import com.example.agentsapp.ui.models.ModelsViewModel
import com.example.agentsapp.ui.profiles.ProfilesViewModel
import com.example.agentsapp.ui.scheduler.JobEditViewModel
import com.example.agentsapp.ui.scheduler.JobRunsViewModel
import com.example.agentsapp.ui.scheduler.SchedulerViewModel
import com.example.agentsapp.ui.settings.SettingsMode
import com.example.agentsapp.ui.settings.SettingsViewModel
import com.example.agentsapp.ui.taskmachines.TaskMachinesViewModel
import com.example.agentsapp.ui.tasks.TaskDetailViewModel

/** По одной небольшой фабрике на экран — проще единой фабрики с ветвлением
 * по `Class<T>`, когда аргументы конструктора (например, `chatId`)
 * отличаются для разных пунктов навигации. */

class MainViewModelFactory(
    private val repository: AgentsCoreRepository,
    private val runsRepository: RunsRepository,
    private val uiPreferences: UiPreferences,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MainViewModel(repository, runsRepository, uiPreferences) as T
}

class ModelsViewModelFactory(
    private val repository: AgentsCoreRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ModelsViewModel(repository) as T
}

class SettingsViewModelFactory(
    private val mode: SettingsMode,
    private val repository: AgentsCoreRepository,
    private val connectionSettings: ServerConnectionSettings,
    private val mcpConnectionSettings: com.example.agentsapp.data.remote.McpConnectionSettings,
    private val schedulerConnectionSettings: com.example.agentsapp.data.remote.SchedulerConnectionSettings,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(mode, repository, connectionSettings, mcpConnectionSettings, schedulerConnectionSettings) as T
}

class ChatViewModelFactory(
    private val chatId: String,
    private val repository: AgentsCoreRepository,
    private val runsRepository: RunsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatViewModel(chatId, repository, runsRepository) as T
}

class MemoryViewModelFactory(
    private val chatId: String,
    private val repository: AgentsCoreRepository,
    private val runsRepository: RunsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MemoryViewModel(chatId, repository, runsRepository) as T
}

class ProfilesViewModelFactory(
    private val repository: AgentsCoreRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ProfilesViewModel(repository) as T
}

class InvariantsViewModelFactory(
    private val repository: AgentsCoreRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        InvariantsViewModel(repository) as T
}

class TaskMachinesViewModelFactory(
    private val repository: AgentsCoreRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        TaskMachinesViewModel(repository) as T
}

class TaskDetailViewModelFactory(
    private val taskId: String,
    private val repository: AgentsCoreRepository,
    private val runsRepository: RunsRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        TaskDetailViewModel(taskId, repository, runsRepository) as T
}

// ---- Отдельный MCP-сервер (новое ТЗ, третий компонент) — свой набор
// фабрик, все принимают [McpRepository] (не [AgentsCoreRepository]). ----

class McpViewModelFactory(
    private val repository: McpRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        McpViewModel(repository) as T
}

class GitHostsViewModelFactory(
    private val repository: McpRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        GitHostsViewModel(repository) as T
}

// ---- Планировщик (scheduler_service) ----

class SchedulerViewModelFactory(
    private val repository: SchedulerRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SchedulerViewModel(repository) as T
}

class JobEditViewModelFactory(
    private val jobId: String?,
    private val scheduler: SchedulerRepository,
    private val core: AgentsCoreRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        JobEditViewModel(jobId, scheduler, core) as T
}

class JobRunsViewModelFactory(
    private val jobId: String,
    private val repository: SchedulerRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        JobRunsViewModel(jobId, repository) as T
}
