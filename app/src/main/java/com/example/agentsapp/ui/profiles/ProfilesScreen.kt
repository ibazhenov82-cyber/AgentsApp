package com.example.agentsapp.ui.profiles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.agentsapp.data.remote.Profile
import com.example.agentsapp.data.remote.RegisteredSkill

/**
 * Общий справочник профилей-пайплайнов персонализации — доступен с главного
 * экрана, не привязан ни к одному конкретному агенту. Здесь профили только
 * добавляются/просматриваются/удаляются; какой профиль использовать для
 * НОВЫХ чатов агента или для конкретного чата — выбирается на экране
 * настроек агента/чата (см. SettingsScreen, поле "Профиль").
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(viewModel: ProfilesViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editTarget by remember { mutableStateOf<Profile?>(null) }
    var deleteTarget by remember { mutableStateOf<Profile?>(null) }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Профили") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Создать профиль")
            }
        },
    ) { padding ->
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        if (state.profiles.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Справочник профилей пока пуст — добавьте первый кнопкой \"+\". Профиль подключается к агенту или чату на экране их настроек.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.profiles, key = { it.id }) { profile ->
                    val skillNames = remember(profile.skills_json) { extractSkillNames(profile.skills_json) }
                    val description = buildString {
                        if (profile.style != null) append("Стиль: ${profile.style}. ")
                        if (skillNames.isNotEmpty()) append("Скиллы: ${skillNames.joinToString(", ")}.")
                        if (isEmpty()) append("Профиль без скиллов и стиля.")
                    }
                    ProfileCard(
                        name = profile.name,
                        description = description,
                        onEdit = { editTarget = profile },
                        onDelete = { deleteTarget = profile },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        ProfileFormDialog(
            title = "Новый профиль",
            confirmText = "Создать",
            initial = null,
            availableSkills = state.registeredSkills,
            onConfirm = { name, style, format, constraints, skillNames, orchestrationPrompt ->
                viewModel.createProfile(name, style, format, constraints, skillNames, orchestrationPrompt)
                showCreateDialog = false
            },
            onDismiss = { showCreateDialog = false },
        )
    }

    editTarget?.let { profile ->
        ProfileFormDialog(
            title = "Редактировать профиль",
            confirmText = "Сохранить",
            initial = profile,
            availableSkills = state.registeredSkills,
            onConfirm = { name, style, format, constraints, skillNames, orchestrationPrompt ->
                viewModel.updateProfile(profile.id, name, style, format, constraints, skillNames, orchestrationPrompt)
                editTarget = null
            },
            onDismiss = { editTarget = null },
        )
    }

    deleteTarget?.let { profile ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Удалить профиль?") },
            text = {
                Text(
                    "Профиль «${profile.name}» будет удалён из общего справочника. Он автоматически " +
                        "отключится от всех агентов/чатов, где сейчас используется.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(profile.id)
                    deleteTarget = null
                }) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun ProfileCard(name: String, description: String, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                if (description.isNotBlank()) {
                    Text(description, style = MaterialTheme.typography.bodySmall)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Редактировать профиль")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Удалить профиль")
            }
        }
    }
}

/** Очень простое извлечение имён функций из `skills_json` без полного
 * разбора JSON-схемы — этого достаточно для краткого описания профиля в
 * списке (полный JSON виден на вкладке "Снимок" экрана "Память", если
 * понадобится). */
private fun extractSkillNames(skillsJson: String): List<String> {
    val regex = Regex("\"name\"\\s*:\\s*\"([a-zA-Z0-9_]+)\"")
    return regex.findAll(skillsJson).map { it.groupValues[1] }.distinct().toList()
}

/** Форма профиля — название + необязательные текстовые поля + выбор скиллов
 * из реестра сервиса (`GET /skills`, см. [RegisteredSkill]). Используется и
 * для создания ([initial] == null, поля пустые), и для редактирования
 * ([initial] — редактируемый профиль, поля предзаполнены его текущими
 * значениями; выбор скиллов предзаполняется по извлечённым из его
 * skills_json именам функций — см. [extractSkillNames]). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileFormDialog(
    title: String,
    confirmText: String,
    initial: Profile?,
    availableSkills: List<RegisteredSkill>,
    onConfirm: (
        name: String,
        style: String,
        format: String,
        constraints: String,
        skillNames: List<String>,
        orchestrationPrompt: String,
    ) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var style by remember { mutableStateOf(initial?.style.orEmpty()) }
    var format by remember { mutableStateOf(initial?.format.orEmpty()) }
    var constraints by remember { mutableStateOf(initial?.constraints.orEmpty()) }
    var orchestrationPrompt by remember { mutableStateOf(initial?.orchestration_prompt.orEmpty()) }
    val selectedSkills = remember {
        mutableStateListOf<String>().apply {
            initial?.skills_json?.let { addAll(extractSkillNames(it)) }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название профиля") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = style, onValueChange = { style = it }, label = { Text("Стиль (необязательно)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = format, onValueChange = { format = it }, label = { Text("Формат ответа (необязательно)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = constraints, onValueChange = { constraints = it }, label = { Text("Ограничения (необязательно)") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = orchestrationPrompt, onValueChange = { orchestrationPrompt = it }, label = { Text("Промпт-оркестрация (необязательно)") }, modifier = Modifier.fillMaxWidth())
                if (availableSkills.isNotEmpty()) {
                    SkillsMultiSelectField(
                        availableSkills = availableSkills,
                        selectedSkills = selectedSkills,
                        onToggle = { name ->
                            if (selectedSkills.contains(name)) selectedSkills.remove(name) else selectedSkills.add(name)
                        },
                    )
                } else {
                    Text(
                        "Справочник скиллов пуст — он ведётся на сервере, в переменной окружения AGENT_REGISTERED_SKILLS (см. .env.example у AgentsCore), а не в приложении.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onConfirm(name.trim(), style, format, constraints, selectedSkills.toList(), orchestrationPrompt)
                },
            ) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Выбор скиллов профиля — из общего справочника скиллов сервиса (`GET
 * /skills`, ведётся в переменной окружения AGENT_REGISTERED_SKILLS на
 * AgentsCore, а не в приложении). Оформлено тем же выпадающим списком, что и
 * выбор модели/профиля в настройках (см. ModelPickerField/ProfilePickerField
 * в SettingsScreen.kt) — только список остаётся открытым после выбора
 * пункта, чтобы можно было отметить несколько скиллов подряд, а не только
 * один. Текущий выбор дополнительно показывается чипами под полем — по ним
 * же можно снять выбор одним нажатием, не открывая список заново. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SkillsMultiSelectField(
    availableSkills: List<RegisteredSkill>,
    selectedSkills: List<String>,
    onToggle: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val fieldValue = when (selectedSkills.size) {
        0 -> "Не выбрано"
        else -> selectedSkills.joinToString(", ")
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = fieldValue,
                onValueChange = {},
                readOnly = true,
                label = { Text("Скиллы (общий справочник)") },
                modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                availableSkills.forEach { skill ->
                    val checked = selectedSkills.contains(skill.name)
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(skill.name, style = MaterialTheme.typography.bodyMedium)
                                if (skill.description.isNotBlank()) {
                                    Text(
                                        skill.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        },
                        leadingIcon = if (checked) {
                            { Icon(Icons.Filled.Check, contentDescription = null) }
                        } else null,
                        // Пункт НЕ закрывает список (в отличие от обычного
                        // single-select ExposedDropdownMenu) — так можно
                        // отметить сразу несколько скиллов, не открывая
                        // список заново после каждого выбора.
                        onClick = { onToggle(skill.name) },
                    )
                }
            }
        }

        if (selectedSkills.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                selectedSkills.forEach { name ->
                    FilterChip(
                        selected = true,
                        onClick = { onToggle(name) },
                        label = { Text(name) },
                        trailingIcon = { Icon(Icons.Filled.Close, contentDescription = "Убрать «$name»", modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }
    }
}
