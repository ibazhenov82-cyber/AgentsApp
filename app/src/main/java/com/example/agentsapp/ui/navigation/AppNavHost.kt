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
import com.example.agentsapp.ui.ChatViewModelFactory
import com.example.agentsapp.ui.MainViewModelFactory
import com.example.agentsapp.ui.ModelsViewModelFactory
import com.example.agentsapp.ui.SettingsViewModelFactory
import com.example.agentsapp.ui.chat.ChatScreen
import com.example.agentsapp.ui.chat.ChatViewModel
import com.example.agentsapp.ui.main.MainScreen
import com.example.agentsapp.ui.main.MainViewModel
import com.example.agentsapp.ui.models.ModelsScreen
import com.example.agentsapp.ui.models.ModelsViewModel
import com.example.agentsapp.ui.settings.SettingsMode
import com.example.agentsapp.ui.settings.SettingsScreen
import com.example.agentsapp.ui.settings.SettingsViewModel

private object Routes {
    const val MAIN = "main"
    const val MODELS = "models"
    const val DEFAULT_SETTINGS = "defaultSettings"
    const val AGENT_SETTINGS = "agentSettings/{agentId}"
    const val CHAT_SETTINGS = "chatSettings/{chatId}"
    const val CHAT = "chat/{chatId}"

    fun agentSettings(agentId: String) = "agentSettings/$agentId"
    fun chatSettings(chatId: String) = "chatSettings/$chatId"
    fun chat(chatId: String) = "chat/$chatId"
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
            )
        }

        composable(Routes.MODELS) {
            val factory = remember { ModelsViewModelFactory(container.repository) }
            val vm: ModelsViewModel = viewModel(factory = factory)
            ModelsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(Routes.DEFAULT_SETTINGS) {
            val factory = remember { SettingsViewModelFactory(SettingsMode.Default, container.repository, container.connectionSettings) }
            val vm: SettingsViewModel = viewModel(key = "defaultSettings", factory = factory)
            SettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.AGENT_SETTINGS,
            arguments = listOf(navArgument("agentId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val agentId = backStackEntry.arguments?.getString("agentId").orEmpty()
            val factory = remember(agentId) { SettingsViewModelFactory(SettingsMode.Agent(agentId), container.repository, container.connectionSettings) }
            val vm: SettingsViewModel = viewModel(key = "agentSettings-$agentId", factory = factory)
            SettingsScreen(viewModel = vm, onBack = { navController.popBackStack() })
        }

        composable(
            route = Routes.CHAT_SETTINGS,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val chatId = backStackEntry.arguments?.getString("chatId").orEmpty()
            val factory = remember(chatId) { SettingsViewModelFactory(SettingsMode.Chat(chatId), container.repository, container.connectionSettings) }
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
            )
        }
    }
}
