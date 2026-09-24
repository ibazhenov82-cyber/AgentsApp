package com.example.agentsapp.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.agentsapp.AppContainer
import com.example.agentsapp.ui.AddScheduledToolViewModelFactory
import com.example.agentsapp.ui.ChatViewModelFactory
import com.example.agentsapp.ui.GitHostsViewModelFactory
import com.example.agentsapp.ui.MainViewModelFactory
import com.example.agentsapp.ui.McpViewModelFactory
import com.example.agentsapp.ui.MemoryViewModelFactory
import com.example.agentsapp.ui.ModelsViewModelFactory
import com.example.agentsapp.ui.ProfilesViewModelFactory
import com.example.agentsapp.ui.ScheduledToolRunsViewModelFactory
import com.example.agentsapp.ui.SettingsViewModelFactory
import com.example.agentsapp.ui.chat.ChatScreen
import com.example.agentsapp.ui.chat.ChatViewModel
import com.example.agentsapp.ui.InvariantsViewModelFactory
import com.example.agentsapp.ui.invariants.InvariantsScreen
import com.example.agentsapp.ui.invariants.InvariantsViewModel
import com.example.agentsapp.ui.main.MainScreen
import com.example.agentsapp.ui.main.MainViewModel
import com.example.agentsapp.ui.mcp.AddScheduledToolScreen
import com.example.agentsapp.ui.mcp.AddScheduledToolViewModel
import com.example.agentsapp.ui.mcp.GitHostsScreen
import com.example.agentsapp.ui.mcp.GitHostsViewModel
import com.example.agentsapp.ui.mcp.McpScreen
import com.example.agentsapp.ui.mcp.McpViewModel
import com.example.agentsapp.ui.mcp.ScheduledToolRunsScreen
import com.example.agentsapp.ui.mcp.ScheduledToolRunsViewModel
import com.example.agentsapp.ui.memory.MemoryScreen
import com.example.agentsapp.ui.memory.MemoryViewModel
import com.example.agentsapp.ui.models.ModelsScreen
import com.example.agentsapp.ui.models.ModelsViewModel
import com.example.agentsapp.ui.profiles.ProfilesScreen
import com.example.agentsapp.ui.profiles.ProfilesViewModel
import com.example.agentsapp.ui.settings.SettingsMode
import com.example.agentsapp.ui.settings.SettingsScreen
import com.example.agentsapp.ui.settings.SettingsViewModel
import com.example.agentsapp.ui.TaskMachinesViewModelFactory
import com.example.agentsapp.ui.taskmachines.TaskMachinesScreen
import com.example.agentsapp.ui.taskmachines.TaskMachinesViewModel
import com.example.agentsapp.ui.TaskDetailViewModelFactory
import com.example.agentsapp.ui.tasks.TaskDetailScreen
import com.example.agentsapp.ui.tasks.TaskDetailViewModel
import java.net.URLDecoder
import java.net.URLEncoder

private object Routes {
    const val MAIN = "main"
    const val MODELS = "models"
    const val PROFILES = "profiles"
    const val INVARIANTS = "invariants"
    const val TASK_MACHINES = "taskMachines"
    const val DEFAULT_SETTINGS = "defaultSettings"
    const val AGENT_SETTINGS = "agentSettings/{agentId}"
    const val CHAT_SETTINGS = "chatSettings/{chatId}"
    const val CHAT = "chat/{chatId}"
    const val MEMORY = "memory/{chatId}"
    // Id задачи глобально уникален на сервере (UUID) — отдельный chatId в
    // маршруте не нужен, но экран читает `TaskDetail.chat_id` из самого
    // ответа сервера, чтобы знать, куда вести "Список задач" (пункт 6).
    const val TASK_DETAIL = "task/{taskId}"

    // Отдельный MCP-сервер (новое ТЗ, третий компонент) — свой маленький
    // под-граф навигации, независимый от остальных маршрутов выше.
    const val MCP = "mcp"
    const val MCP_ADD_TASK = "mcp/addTask"
    const val GIT_HOSTS = "mcp/gitHosts"
    const val MCP_TASK_RUNS = "mcp/tasks/{toolId}/runs?name={toolName}"

    fun agentSettings(agentId: String) = "agentSettings/$agentId"
    fun chatSettings(chatId: String) = "chatSettings/$chatId"
    fun chat(chatId: String) = "chat/$chatId"
    fun memory(chatId: String) = "memory/$chatId"
    fun taskDetail(taskId: String) = "task/$taskId"
    fun mcpTaskRuns(toolId: String, toolName: String) =
        "mcp/tasks/$toolId/runs?name=${URLEncoder.encode(toolName, "UTF-8")}"
}

@Composable
fun AppNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            val factory = remember { MainViewModelFactory(container.repository) }
            val vm: MainViewModel = viewModel(factory = factory)
            MainScreen(
                viewModel = vm,
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
                onOpenAgentSettings = { agentId -> navController.navigate(Routes.agentSettings(agentId)) },
                onOpenChatSettings = { chatId -> navController.navigate(Routes.chatSettings(chatId)) },
                onOpenModels = { navController.navigate(Routes.MODELS) },
                onOpenDefaultSettings = { navController.navigate(Routes.DEFAULT_SETTINGS) },
                onOpenProfiles = { navController.navigate(Routes.PROFILES) },
                onOpenInvariants = { navController.navigate(Routes.INVARIANTS) },
                onOpenTaskMachines = { navController.navigate(Routes.TASK_MACHINES) },
                onOpenTask = { taskId -> navController.navigate(Routes.taskDetail(taskId)) },
                onOpenMcp = { navController.navigate(Routes.MCP) },
            )
        }

        composable(Routes.MODELS) {
            val factory = remember { ModelsViewModelFactory(container.repository) }
            val vm: ModelsViewModel = viewModel(factory = factory)
            ModelsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.PROFILES) {
            val factory = remember { ProfilesViewModelFactory(container.repository) }
            val vm: ProfilesViewModel = viewModel(factory = factory)
            ProfilesScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.INVARIANTS) {
            val factory = remember { InvariantsViewModelFactory(container.repository) }
            val vm: InvariantsViewModel = viewModel(factory = factory)
            InvariantsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.TASK_MACHINES) {
            val factory = remember { TaskMachinesViewModelFactory(container.repository) }
            val vm: TaskMachinesViewModel = viewModel(factory = factory)
            TaskMachinesScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.DEFAULT_SETTINGS) {
            val factory = remember { SettingsViewModelFactory(SettingsMode.Default, container.repository, container.connectionSettings, container.mcpConnectionSettings, container.mcpRepository) }
            val vm: SettingsViewModel = viewModel(key = "defaultSettings", factory = factory)
            SettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.AGENT_SETTINGS,
            arguments = listOf(navArgument("agentId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val agentId = backStackEntry.arguments?.getString("agentId").orEmpty()
            val factory = remember(agentId) { SettingsViewModelFactory(SettingsMode.Agent(agentId), container.repository, container.connectionSettings, container.mcpConnectionSettings, container.mcpRepository) }
            val vm: SettingsViewModel = viewModel(key = "agentSettings-$agentId", factory = factory)
            SettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.CHAT_SETTINGS,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
            val factory = remember(chatId) { SettingsViewModelFactory(SettingsMode.Chat(chatId), container.repository, container.connectionSettings, container.mcpConnectionSettings, container.mcpRepository) }
            val vm: SettingsViewModel = viewModel(key = "chatSettings-$chatId", factory = factory)
            SettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
            val factory = remember(chatId) { ChatViewModelFactory(chatId, container.repository) }
            val vm: ChatViewModel = viewModel(key = "chat-$chatId", factory = factory)
            ChatScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenChatSettings = { navController.navigate(Routes.chatSettings(chatId)) },
                onOpenMemory = { navController.navigate(Routes.memory(chatId)) },
                onOpenTask = { taskId -> navController.navigate(Routes.taskDetail(taskId)) },
                // Несколько параллельных открытых задач (пункт 7) — ведём на
                // список задач этого чата (вкладка "Задачи" экрана "Память").
                onOpenTaskList = { navController.navigate(Routes.memory(chatId)) },
            )
        }

        composable(
            route = Routes.MEMORY,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
            val factory = remember(chatId) { MemoryViewModelFactory(chatId, container.repository) }
            val vm: MemoryViewModel = viewModel(key = "memory-$chatId", factory = factory)
            MemoryScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenTask = { taskId -> navController.navigate(Routes.taskDetail(taskId)) },
            )
        }

        composable(
            route = Routes.TASK_DETAIL,
            arguments = listOf(navArgument("taskId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val taskId = backStackEntry.arguments?.getString("taskId").orEmpty()
            val factory = remember(taskId) { TaskDetailViewModelFactory(taskId, container.repository) }
            val vm: TaskDetailViewModel = viewModel(key = "task-$taskId", factory = factory)
            TaskDetailScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                // Переход к списку задач (пункт 6) — НЕЗАВИСИМО от системной
                // кнопки "назад": ведёт на вкладку "Задачи" родительского чата
                // задачи, даже если на детали задачи попали из агрегированного
                // блока на главном экране, а не из самого чата.
                onOpenTaskList = { chatId -> navController.navigate(Routes.memory(chatId)) },
            )
        }

        // ---- Отдельный MCP-сервер (новое ТЗ, третий компонент) ----

        composable(Routes.MCP) {
            val factory = remember { McpViewModelFactory(container.mcpRepository) }
            val vm: McpViewModel = viewModel(factory = factory)
            McpScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onOpenGitHosts = { navController.navigate(Routes.GIT_HOSTS) },
                onOpenAddTask = { navController.navigate(Routes.MCP_ADD_TASK) },
                onOpenRuns = { toolId, toolName -> navController.navigate(Routes.mcpTaskRuns(toolId, toolName)) },
            )
        }

        composable(Routes.MCP_ADD_TASK) {
            val factory = remember { AddScheduledToolViewModelFactory(container.mcpRepository) }
            val vm: AddScheduledToolViewModel = viewModel(factory = factory)
            AddScheduledToolScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onCreated = { navController.popBackStack() },
            )
        }

        composable(Routes.GIT_HOSTS) {
            val factory = remember { GitHostsViewModelFactory(container.mcpRepository) }
            val vm: GitHostsViewModel = viewModel(factory = factory)
            GitHostsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.MCP_TASK_RUNS,
            arguments = listOf(
                navArgument("toolId") { type = NavType.StringType },
                navArgument("toolName") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val toolId = backStackEntry.arguments?.getString("toolId").orEmpty()
            val toolName = backStackEntry.arguments?.getString("toolName").orEmpty()
                .let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrDefault(it) }
            val factory = remember(toolId) { ScheduledToolRunsViewModelFactory(toolId, container.mcpRepository) }
            val vm: ScheduledToolRunsViewModel = viewModel(key = "mcpRuns-$toolId", factory = factory)
            ScheduledToolRunsScreen(viewModel = vm, toolName = toolName, onBack = { navController.popBackStack() })
        }
    }
}
