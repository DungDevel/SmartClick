package com.example.smartclick.app.data.catalog

import android.content.Context
import androidx.annotation.VisibleForTesting
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

data class CatalogEntry(
    val id: String,
    val name: String,
    val category: String?,
    val protocolIds: Map<String, String>,
    val keywords: List<String>,
    val type: String
)

@Singleton
class ChannelCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun load(): Result<List<CatalogEntry>> = runCatching {
        context.assets.open(ASSET_NAME).bufferedReader().use { parse(it.readText()) }
    }.fold(
        onSuccess = { it },
        onFailure = { Result.failure(CatalogException("Unable to read $ASSET_NAME", it)) }
    )

    companion object {
        const val ASSET_NAME = "channels.json"

        @VisibleForTesting
        fun parse(json: String): Result<List<CatalogEntry>> = runCatching {
            val array = JSONArray(json)
            val entries = ArrayList<CatalogEntry>(array.length())
            val ids = HashSet<String>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index)
                    ?: throw CatalogException("Entry $index must be an object")
                val id = item.requiredString("id", index)
                val name = item.requiredString("name", index)
                if (!ids.add(id)) throw CatalogException("Duplicate catalog id: $id")
                val protocolIds = parseProtocolIds(item, index)
                val keywords = item.optJSONArray("keywords")?.let { values ->
                    (0 until values.length()).mapNotNull { values.optString(it).takeIf(String::isNotBlank) }
                }.orEmpty()
                entries += CatalogEntry(
                    id = id,
                    name = name,
                    category = item.optString("category").takeIf(String::isNotBlank),
                    protocolIds = protocolIds,
                    keywords = keywords,
                    type = item.optString("type", "channel").ifBlank { "channel" }
                )
            }
            entries
        }

        private fun parseProtocolIds(item: JSONObject, index: Int): Map<String, String> {
            val ids = LinkedHashMap<String, String>()
            item.optJSONObject("protocol_ids")?.let { map ->
                val keys = map.keys()
                while (keys.hasNext()) {
                    val protocol = keys.next()
                    val value = map.optString(protocol).trim()
                    if (value.isNotEmpty()) ids[protocol] = value
                }
            }
            // Accept legacy per-protocol fields alongside protocol_ids during migration.
            val legacyProtocolKeys = mapOf(
                "roku_app_id" to "roku",
                "lg_app_id" to "lgwebos",
                "samsung_app_id" to "samsung",
                "android_tv_app_id" to "androidtv"
            )
            legacyProtocolKeys.forEach { (key, protocol) ->
                val value = item.optString(key).trim()
                if (value.isNotEmpty()) ids.putIfAbsent(protocol, value)
            }
            item.optString("channel_number").takeIf(String::isNotBlank)?.let {
                ids.putIfAbsent("channel", it)
            }
            return ids
        }

        private fun JSONObject.requiredString(key: String, index: Int): String =
            optString(key).trim().takeIf(String::isNotEmpty)
                ?: throw CatalogException("Entry $index is missing required field '$key'")
    }
}

class CatalogException(message: String, cause: Throwable? = null) : Exception(message, cause)
