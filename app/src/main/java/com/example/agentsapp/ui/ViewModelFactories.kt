package com.example.agentsapp.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.agentsapp.data.remote.ServerConnectionSettings
import com.example.agentsapp.data.repository.AgentsCoreRepository
import com.example.agentsapp.ui.chat.ChatViewModel
import com.example.agentsapp.ui.invariants.InvariantsViewModel
import com.example.agentsapp.ui.main.MainViewModel
import com.example.agentsapp.ui.memory.MemoryViewModel
import com.example.agentsapp.ui.models.ModelsViewModel
import com.example.agentsapp.ui.profiles.ProfilesViewModel
import com.example.agentsapp.ui.settings.SettingsMode
import com.example.agentsapp.ui.settings.SettingsViewModel
import com.example.agentsapp.ui.taskmachines.TaskMachinesViewModel
import com.example.agentsapp.ui.tasks.TaskDetailViewModel

/** По одной небольшой фабрике на экран — проще единой фабрики с ветвлением
 * по `Class<T>`, когда аргументы конструктора (например, `chatId`)
 * отличаются для разных пунктов навигации. */

class MainViewModelFactory(
    private val repository: AgentsCoreRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MainViewModel(repository) as T
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
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        SettingsViewModel(mode, repository, connectionSettings) as T
}

class ChatViewModelFactory(
    private val chatId: String,
    private val repository: AgentsCoreRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatViewModel(chatId, repository) as T
}

class MemoryViewModelFactory(
    private val chatId: String,
    private val repository: AgentsCoreRepository,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MemoryViewModel(chatId, repository) as T
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
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        TaskDetailViewModel(taskId, repository) as T
}
