package com.example.agentsapp.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Собственный компонуемый JSON tree-viewer. Требования называли библиотеку
 * "JsonViewer-Compose", но она не существует в виде надёжно опубликованного
 * пакета (единственные варианты на JitPack — заброшенные форки без тегов),
 * поэтому JSON-сообщения и факты стратегии Sticky Facts рендерятся этим
 * небольшим самостоятельным деревом вместо стороннего пакета.
 */
@Composable
fun JsonTreeView(rawJson: String, modifier: Modifier = Modifier) {
    val parsed = remember(rawJson) {
        runCatching { Json.parseToJsonElement(rawJson) }.getOrNull()
    }
    Column(modifier = modifier) {
        if (parsed == null) {
            // Не удалось разобрать — показываем как есть, моноширинным шрифтом.
            Text(rawJson, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
        } else {
            JsonNode(name = null, element = parsed, depth = 0)
        }
    }
}

@Composable
private fun JsonNode(name: String?, element: JsonElement, depth: Int) {
    when (element) {
        is JsonObject -> JsonContainerNode(
            name = name,
            openBrace = "{", closeBrace = "}",
            childCountLabel = "${element.size} " + pluralKeys(element.size),
            depth = depth,
        ) {
            element.entries.forEach { (key, value) -> JsonNode(name = key, element = value, depth = depth + 1) }
        }
        is JsonArray -> JsonContainerNode(
            name = name,
            openBrace = "[", closeBrace = "]",
            childCountLabel = "${element.size} " + pluralItems(element.size),
            depth = depth,
        ) {
            element.forEachIndexed { index, value -> JsonNode(name = index.toString(), element = value, depth = depth + 1) }
        }
        is JsonPrimitive, is JsonNull -> JsonLeafNode(name = name, element = element, depth = depth)
    }
}

@Composable
private fun JsonContainerNode(
    name: String?,
    openBrace: String,
    closeBrace: String,
    childCountLabel: String,
    depth: Int,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(depth < 2) }
    Column {
        Row(
            modifier = Modifier
                .padding(start = (depth * 16).dp)
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = null,
                modifier = Modifier.padding(top = 2.dp),
            )
            val label = if (name != null) "$name: $openBrace" else openBrace
            Text(
                text = if (expanded) label else "$label … $closeBrace ($childCountLabel)",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            )
        }
        if (expanded) {
            content()
            Text(
                text = closeBrace,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(start = (depth * 16).dp),
            )
        }
    }
}

@Composable
private fun JsonLeafNode(name: String?, element: JsonElement, depth: Int) {
    val valueText = when (element) {
        is JsonNull -> "null"
        is JsonPrimitive -> if (element.isString) "\"${element.content}\"" else element.content
        else -> element.toString()
    }
    val text = if (name != null) "$name: $valueText" else valueText
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.padding(start = (depth * 16).dp),
    )
}

private fun pluralKeys(count: Int): String = if (count == 1) "ключ" else "ключей"
private fun pluralItems(count: Int): String = if (count == 1) "элемент" else "элементов"
