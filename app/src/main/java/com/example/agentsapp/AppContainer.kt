package com.example.agentsapp

import android.content.Context
import com.example.agentsapp.data.remote.AgentsCoreApiClient
import com.example.agentsapp.data.remote.ServerConnectionSettings
import com.example.agentsapp.data.repository.AgentsCoreRepository

/** Небольшой самодельный DI-контейнер: настройки подключения к серверу +
 * API-клиент + репозиторий, по одному экземпляру на процесс приложения. */
class AppContainer(context: Context) {
    val connectionSettings = ServerConnectionSettings(context.applicationContext)
    val apiClient = AgentsCoreApiClient(baseUrlProvider = { connectionSettings.currentBaseUrl() })
    val repository = AgentsCoreRepository(apiClient)
}
