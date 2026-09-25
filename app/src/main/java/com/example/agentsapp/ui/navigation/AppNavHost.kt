package com.example.agentsapp.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.agentsapp.AppContainer
import com.example.agentsapp.data.repository.NewAnswerNotice
import com.example.agentsapp.ui.common.NewAnswerBanner
import com.example.agentsapp.ui.ChatViewModelFactory
import com.example.agentsapp.ui.GitHostsViewModelFactory
import com.example.agentsapp.ui.MainViewModelFactory
import com.example.agentsapp.ui.JobEditViewModelFactory
import com.example.agentsapp.ui.JobRunsViewModelFactory
import com.example.agentsapp.ui.McpViewModelFactory
import com.example.agentsapp.ui.SchedulerViewModelFactory
import com.example.agentsapp.ui.MemoryViewModelFactory
import com.example.agentsapp.ui.ModelsViewModelFactory
import com.example.agentsapp.ui.ProfilesViewModelFactory
import com.example.agentsapp.ui.SettingsViewModelFactory
import com.example.agentsapp.ui.chat.ChatScreen
import com.example.agentsapp.ui.chat.ChatViewModel
import com.example.agentsapp.ui.InvariantsViewModelFactory
import com.example.agentsapp.ui.invariants.InvariantsScreen
import com.example.agentsapp.ui.invariants.InvariantsViewModel
import com.example.agentsapp.ui.main.MainScreen
import com.example.agentsapp.ui.main.MainViewModel
import com.example.agentsapp.ui.mcp.GitHostsScreen
import com.example.agentsapp.ui.mcp.GitHostsViewModel
import com.example.agentsapp.ui.mcp.McpScreen
import com.example.agentsapp.ui.mcp.McpViewModel
import com.example.agentsapp.ui.memory.MemoryScreen
import com.example.agentsapp.ui.memory.MemoryViewModel
import com.example.agentsapp.ui.models.ModelsScreen
import com.example.agentsapp.ui.models.ModelsViewModel
import com.example.agentsapp.ui.profiles.ProfilesScreen
import com.example.agentsapp.ui.profiles.ProfilesViewModel
import com.example.agentsapp.ui.scheduler.JobEditScreen
import com.example.agentsapp.ui.scheduler.JobEditViewModel
import com.example.agentsapp.ui.scheduler.JobRunsScreen
import com.example.agentsapp.ui.scheduler.JobRunsViewModel
import com.example.agentsapp.ui.scheduler.SchedulerScreen
import com.example.agentsapp.ui.scheduler.SchedulerViewModel
import com.example.agentsapp.ui.settings.SettingsMode
import com.example.agentsapp.ui.settings.SettingsScreen
import com.example.agentsapp.ui.settings.SettingsViewModel
import com.example.agentsapp.ui.TaskMachinesViewModelFactory
import com.example.agentsapp.ui.taskmachines.TaskMachinesScreen
import com.example.agentsapp.ui.taskmachines.TaskMachinesViewModel
import com.example.agentsapp.ui.TaskDetailViewModelFactory
import com.example.agentsapp.ui.tasks.TaskDetailScreen
import com.example.agentsapp.ui.tasks.TaskDetailViewModel
import kotlinx.coroutines.delay

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
    const val GIT_HOSTS = "mcp/gitHosts"

    // Планировщик (scheduler_service).
    const val SCHEDULER = "scheduler"
    const val JOB_NEW = "scheduler/jobs/new"
    const val JOB_EDIT = "scheduler/jobs/{jobId}/edit"
    const val JOB_RUNS = "scheduler/jobs/{jobId}/runs?name={jobName}"

    fun agentSettings(agentId: String) = "agentSettings/$agentId"
    fun chatSettings(chatId: String) = "chatSettings/$chatId"
    fun chat(chatId: String) = "chat/$chatId"
    fun memory(chatId: String) = "memory/$chatId"
    fun taskDetail(taskId: String) = "task/$taskId"
    fun jobEdit(jobId: String) = "scheduler/jobs/$jobId/edit"
    fun jobRuns(jobId: String, jobName: String) =
        "scheduler/jobs/$jobId/runs?name=${Uri.encode(jobName)}"
}

@Composable
fun AppNavHost(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
) {
    // Всплывающее «Новый ответ в «<чат>»» — поверх любого экрана, кроме
    // главного (там непрочитанное видно в списке чатов).
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute by rememberUpdatedState(backStackEntry?.destination?.route)
    var notice by remember { mutableStateOf<NewAnswerNotice?>(null) }
    LaunchedEffect(Unit) {
        container.runsRepository.notices.collect { incoming ->
            if (currentRoute != Routes.MAIN) notice = incoming
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(6_000)
            notice = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    NavHost(navController = navController, startDestination = Routes.MAIN) {
        composable(Routes.MAIN) {
            val factory = remember { MainViewModelFactory(container.repository, container.runsRepository, container.uiPreferences) }
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
                onOpenScheduler = { navController.navigate(Routes.SCHEDULER) },
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
            val factory = remember { SettingsViewModelFactory(SettingsMode.Default, container.repository, container.connectionSettings, container.mcpConnectionSettings, container.schedulerConnectionSettings) }
            val vm: SettingsViewModel = viewModel(key = "defaultSettings", factory = factory)
            SettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.AGENT_SETTINGS,
            arguments = listOf(navArgument("agentId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val agentId = backStackEntry.arguments?.getString("agentId").orEmpty()
            val factory = remember(agentId) { SettingsViewModelFactory(SettingsMode.Agent(agentId), container.repository, container.connectionSettings, container.mcpConnectionSettings, container.schedulerConnectionSettings) }
            val vm: SettingsViewModel = viewModel(key = "agentSettings-$agentId", factory = factory)
            SettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.CHAT_SETTINGS,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
            val factory = remember(chatId) { SettingsViewModelFactory(SettingsMode.Chat(chatId), container.repository, container.connectionSettings, container.mcpConnectionSettings, container.schedulerConnectionSettings) }
            val vm: SettingsViewModel = viewModel(key = "chatSettings-$chatId", factory = factory)
            SettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
            val factory = remember(chatId) { ChatViewModelFactory(chatId, container.repository, container.runsRepository) }
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
            val factory = remember(chatId) { MemoryViewModelFactory(chatId, container.repository, container.runsRepository) }
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
            val factory = remember(taskId) { TaskDetailViewModelFactory(taskId, container.repository, container.runsRepository) }
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
            )
        }

        composable(Routes.GIT_HOSTS) {
            val factory = remember { GitHostsViewModelFactory(container.mcpRepository) }
            val vm: GitHostsViewModel = viewModel(factory = factory)
            GitHostsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        // ---- Планировщик ----

        composable(Routes.SCHEDULER) {
            val factory = remember { SchedulerViewModelFactory(container.schedulerRepository) }
            val vm: SchedulerViewModel = viewModel(factory = factory)
            SchedulerScreen(
                viewModel = vm,
                onBack = { navController.popBackStack() },
                onCreateJob = { navController.navigate(Routes.JOB_NEW) },
                onEditJob = { jobId -> navController.navigate(Routes.jobEdit(jobId)) },
                onOpenRuns = { jobId, jobName -> navController.navigate(Routes.jobRuns(jobId, jobName)) },
            )
        }

        composable(Routes.JOB_NEW) {
            val factory = remember { JobEditViewModelFactory(null, container.schedulerRepository, container.repository) }
            val vm: JobEditViewModel = viewModel(key = "jobNew", factory = factory)
            JobEditScreen(viewModel = vm, onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
        }

        composable(
            route = Routes.JOB_EDIT,
            arguments = listOf(navArgument("jobId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId").orEmpty()
            val factory = remember(jobId) { JobEditViewModelFactory(jobId, container.schedulerRepository, container.repository) }
            val vm: JobEditViewModel = viewModel(key = "jobEdit-$jobId", factory = factory)
            JobEditScreen(viewModel = vm, onBack = { navController.popBackStack() }, onSaved = { navController.popBackStack() })
        }

        composable(
            route = Routes.JOB_RUNS,
            arguments = listOf(
                navArgument("jobId") { type = NavType.StringType },
                navArgument("jobName") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val jobId = backStackEntry.arguments?.getString("jobId").orEmpty()
            // Navigation уже раскодирует аргумент — повторный URLDecoder.decode не нужен.
            val jobName = backStackEntry.arguments?.getString("jobName").orEmpty()
            val factory = remember(jobId) { JobRunsViewModelFactory(jobId, container.schedulerRepository) }
            val vm: JobRunsViewModel = viewModel(key = "jobRuns-$jobId", factory = factory)
            JobRunsScreen(
                viewModel = vm,
                jobName = jobName,
                onBack = { navController.popBackStack() },
                onOpenChat = { chatId -> navController.navigate(Routes.chat(chatId)) },
                onOpenTask = { taskId -> navController.navigate(Routes.taskDetail(taskId)) },
            )
        }
    }

        notice?.let { shown ->
            NewAnswerBanner(
                notice = shown,
                onOpen = {
                    notice = null
                    navController.navigate(Routes.chat(shown.chatId))
                },
                onDismiss = { notice = null },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 64.dp, start = 12.dp, end = 12.dp),
            )
        }
    }
}
