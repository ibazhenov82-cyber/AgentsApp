package com.example.agentsapp

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.example.agentsapp.ui.navigation.AppNavHost
import com.example.agentsapp.ui.theme.AgentsAppTheme

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as AgentsApp).container

    /** Android 17+: без разрешения на локальную сеть соединения с серверами
     * по адресам 10.x / 192.168.x (включая 10.0.2.2 эмулятора) блокируются
     * системой — запросы висят до тайм-аута. После выдачи разрешения экран
     * пересоздаётся, чтобы всё загрузилось заново. */
    private val localNetworkPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                recreate()
            } else {
                Toast.makeText(
                    this,
                    "Без разрешения «Устройства поблизости» приложение не подключится к серверу в локальной сети. " +
                        "Его можно выдать в настройках приложения.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

    // Подписки на ответы и общую ленту изменений держатся, только пока
    // приложение на экране; при возврате продолжаются с места остановки.
    override fun onStart() {
        super.onStart()
        container.runsRepository.onForeground()
    }

    override fun onStop() {
        container.runsRepository.onBackground()
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) requestLocalNetworkIfNeeded()

        setContent {
            AgentsAppTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavHost(container = container)
                }
            }
        }
    }

    private fun requestLocalNetworkIfNeeded() {
        if (Build.VERSION.SDK_INT < ANDROID_17) return
        val granted = ContextCompat.checkSelfPermission(this, PERMISSION_LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED
        if (!granted) localNetworkPermission.launch(PERMISSION_LOCAL_NETWORK)
    }

    private companion object {
        const val ANDROID_17 = 37
        const val PERMISSION_LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK"
    }
}
