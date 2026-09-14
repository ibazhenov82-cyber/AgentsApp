package com.example.agentsapp.ui.models

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.agentsapp.data.remote.ModelInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelsScreen(viewModel: ModelsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Модели") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxWidth().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        // Сетка по 3 карточки в ряд (замечание пользователя) — карточка
        // компактная: имя модели, провайдер, ключевые параметры одной
        // строкой и небольшая кнопка проверки соединения снизу.
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxWidth().padding(padding),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.models) { model ->
                ModelCard(
                    model = model,
                    health = state.health[model.id] ?: HealthState.UNKNOWN,
                    onCheckHealth = { viewModel.checkHealth(model.id) },
                )
            }
        }
    }
}

@Composable
private fun ModelCard(model: ModelInfo, health: HealthState, onCheckHealth: () -> Unit) {
    // ElevatedCard — актуальные гайдлайны Google Material Design 3 для
    // карточек второстепенного уровня (замечание 17); компоновка в столбик,
    // рассчитана на треть ширины экрана (сетка 3 в ряд).
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val title = model.display_name.ifBlank { model.model_id }
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (model.is_local) {
                Text(
                    "(локальная)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                model.provider,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            model.context_window?.let {
                Text(
                    "Контекст: $it",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            val capabilities = buildList {
                if (model.supports_thinking) add("рассуждения")
                if (model.supports_tools) add("инструменты")
                if (model.supports_json_mode) add("JSON")
                if (model.supports_logprobs) add("logprobs")
            }
            if (capabilities.isNotEmpty()) {
                Text(
                    capabilities.joinToString(", "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Кнопка проверки соединения — только иконка, без цветовой
            // подложки (TextButton прозрачен), плюс статус под ней.
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onCheckHealth,
                    enabled = health != HealthState.CHECKING,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.Wifi, contentDescription = "Проверить соединение", modifier = Modifier.padding(end = 4.dp))
                    Text("Проверить", style = MaterialTheme.typography.labelSmall)
                }
            }
            HealthLabel(health)
        }
    }
}

@Composable
private fun HealthLabel(health: HealthState) {
    when (health) {
        HealthState.UNKNOWN -> Text(
            "Не проверялось",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HealthState.CHECKING -> Text(
            "Проверка…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HealthState.HEALTHY -> Text(
            "Доступна",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF2E7D32),
            fontWeight = FontWeight.Bold,
        )
        HealthState.UNHEALTHY -> Text(
            "Недоступна",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
        )
    }
}
