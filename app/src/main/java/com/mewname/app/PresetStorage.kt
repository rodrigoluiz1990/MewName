package com.mewname.app

import com.mewname.app.model.NamingBlock
import com.mewname.app.model.NamingConfig
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener

const val SAVED_PRESETS_KEY = "saved_presets"
const val SAVED_PRESETS_BACKUP_KEY = "saved_presets_backup"
private const val PRESET_SCHEMA_VERSION = 1

data class SavedPresetPayload(
    val configs: List<NamingConfig>,
    val requiresMigration: Boolean
)

fun decodeSavedPresets(raw: String): SavedPresetPayload {
    val value = JSONTokener(raw).nextValue()
    val presets = when (value) {
        is JSONArray -> value
        is JSONObject -> {
            val version = value.optInt("schemaVersion", 0)
            require(version in 1..PRESET_SCHEMA_VERSION) { "Unsupported preset schema: $version" }
            value.getJSONArray("presets")
        }
        else -> error("Invalid preset payload")
    }
    val configs = buildList {
        for (index in 0 until presets.length()) {
            add(jsonToNamingConfig(presets.getJSONObject(index)))
        }
    }
    return SavedPresetPayload(configs, requiresMigration = value is JSONArray)
}

fun encodeSavedPresets(configs: List<NamingConfig>): String {
    val presets = JSONArray()
    configs.forEach { config -> presets.put(namingConfigToJson(config)) }
    return JSONObject()
        .put("schemaVersion", PRESET_SCHEMA_VERSION)
        .put("presets", presets)
        .toString()
}

private fun namingConfigToJson(config: NamingConfig): JSONObject {
    return JSONObject().apply {
        put("id", config.id)
        put("name", config.name)
        put("maxLength", config.maxLength)
        put("customSeparator", config.customSeparator)
        put("blocks", JSONArray().apply {
            config.blocks.forEach { block -> put(blockToJson(block)) }
        })
        put("fields", JSONArray().apply {
            config.fields.forEach { field -> put(field.name) }
        })
        put("symbols", JSONObject().apply {
            config.symbols.forEach { (key, value) -> put(key, value) }
        })
    }
}

private fun blockToJson(block: NamingBlock): JSONObject {
    return JSONObject().apply {
        put("id", block.id)
        put("type", block.type.name)
        put("field", block.field?.name)
        put("fixedText", block.fixedText)
    }
}