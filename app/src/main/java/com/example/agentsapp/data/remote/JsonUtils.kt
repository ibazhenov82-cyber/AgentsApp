package com.example.agentsapp.data.remote

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

/** Строит [JsonElement] из типизированного значения Kotlin — используется при
 * сборке частичных тел `PUT .../settings` (одно изменённое поле за раз). */
fun jsonValueOf(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is JsonElement -> value
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Double -> JsonPrimitive(value)
    is Float -> JsonPrimitive(value.toDouble())
    is List<*> -> JsonArray(value.map { jsonValueOf(it) })
    else -> error("unsupported settings value type: ${value::class}")
}
