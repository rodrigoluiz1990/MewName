package com.mewname.app.domain

import android.content.Context
import org.json.JSONObject

/** Explicit form equivalences: never deduplicate captures merely by their sprite. */
internal object CollectionFormAliases {
    @Volatile private var catalog: JSONObject? = null
    private fun data(context: Context): JSONObject = catalog ?: synchronized(this) {
        catalog ?: JSONObject(context.assets.open("pokemon/collection_form_aliases.json").bufferedReader().use { it.readText() })
            .also { catalog = it }
    }
    fun resolve(context: Context, target: PersonalCollectionTarget): PersonalCollectionTarget {
        val data = data(context)
        val key = data.getJSONObject("aliases").optString(target.key, target.key)
        val label = data.getJSONObject("labels").optString(key).ifBlank {
            target.label.replace(Regex(" · (.+)$"), " ($1)")
        }
        return if (key == target.key && label == target.label) target else PersonalCollectionTarget(key, label)
    }
}
