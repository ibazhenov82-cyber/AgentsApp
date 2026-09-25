package com.example.agentsapp

import android.content.Context
import com.example.agentsapp.data.UiPreferences
import com.example.agentsapp.data.remote.AgentsCoreApiClient
import com.example.agentsapp.data.remote.McpApiClient
import com.example.agentsapp.data.remote.McpConnectionSettings
import com.example.agentsapp.data.remote.SchedulerApiClient
import com.example.agentsapp.data.remote.SchedulerConnectionSettings
import com.example.agentsapp.data.remote.ServerConnectionSettings
import com.example.agentsapp.data.repository.AgentsCoreRepository
import com.example.agentsapp.data.repository.McpRepository
import com.example.agentsapp.data.repository.RunsRepository
import com.example.agentsapp.data.repository.SchedulerRepository

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

    /** Асинхронные ответы, непрочитанные и общая лента изменений — живут
     * столько же, сколько приложение, а не отдельный экран (см. [RunsRepository]). */
    val runsRepository = RunsRepository(apiClient)

    val uiPreferences = UiPreferences(context.applicationContext)

    val mcpConnectionSettings = McpConnectionSettings(context.applicationContext)
    val mcpApiClient = McpApiClient(baseUrlProvider = { mcpConnectionSettings.currentBaseUrl() })
    val mcpRepository = McpRepository(mcpApiClient)

    /** Сервис планировщика — третий адрес, свой набор настройки/клиента/репозитория. */
    val schedulerConnectionSettings = SchedulerConnectionSettings(context.applicationContext)
    val schedulerApiClient = SchedulerApiClient(baseUrlProvider = { schedulerConnectionSettings.currentBaseUrl() })
    val schedulerRepository = SchedulerRepository(schedulerApiClient)
}
