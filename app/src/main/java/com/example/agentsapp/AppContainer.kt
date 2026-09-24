package com.example.agentsapp

import android.content.Context
import com.example.agentsapp.data.remote.AgentsCoreApiClient
import com.example.agentsapp.data.remote.McpApiClient
import com.example.agentsapp.data.remote.McpConnectionSettings
import com.example.agentsapp.data.remote.ServerConnectionSettings
import com.example.agentsapp.data.repository.AgentsCoreRepository
import com.example.agentsapp.data.repository.McpRepository

/** Небольшой самодельный DI-контейнер: настройки подключения к серверу +
 * API-клиент + репозиторий, по одному экземпляру на процесс приложения.
 *
 * Новое ТЗ (интеграция с отдельным MCP-сервером, третий компонент) — второй,
 * полностью независимый набор из тех же трёх слоёв (настройки адреса/клиент/
 * репозиторий): MCP-сервер это отдельный сервис со своим адресом, приложение
 * обращается к нему НАПРЯМУЮ, не через AgentsCore (см. README AgentsApp,
 * раздел "MCP-сервер"). */
class AppContainer(context: Context) {
    val connectionSettings = ServerConnectionSettings(context.applicationContext)
    val apiClient = AgentsCoreApiClient(baseUrlProvider = { connectionSettings.currentBaseUrl() })
    val repository = AgentsCoreRepository(apiClient)

    val mcpConnectionSettings = McpConnectionSettings(context.applicationContext)
    val mcpApiClient = McpApiClient(baseUrlProvider = { mcpConnectionSettings.currentBaseUrl() })
    val mcpRepository = McpRepository(mcpApiClient)
}
