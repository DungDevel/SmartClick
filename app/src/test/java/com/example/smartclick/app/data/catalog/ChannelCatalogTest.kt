package com.example.smartclick.app.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import org.json.JSONArray

class ChannelCatalogTest {
    @Test fun `catalog preserves every legacy app protocol id`() {
        val legacy = JSONArray(File("src/main/assets/apps.json").readText())
        val catalog = ChannelCatalog.parse(File("src/main/assets/channels.json").readText()).getOrThrow()
        val keyMap = mapOf("lg_app_id" to "lgwebos", "samsung_app_id" to "samsung", "roku_app_id" to "roku", "android_tv_app_id" to "androidtv")
        val missing = mutableListOf<String>()
        for (i in 0 until legacy.length()) {
            val item = legacy.getJSONObject(i)
            val entry = catalog.firstOrNull { it.id == "app:${item.getString("symbol").lowercase()}" }
            keyMap.forEach { (legacyKey, protocol) ->
                val id = item.optString(legacyKey)
                if (id.isNotBlank() && entry?.protocolIds?.get(protocol) != id) missing += "${item.getString("symbol")} $protocol=$id"
            }
        }
        assertTrue("Missing legacy IDs: $missing", missing.isEmpty())
    }
    @Test fun `parses valid legacy and protocol mapped entries`() {
        val result = ChannelCatalog.parse("""[
            {"id":"channel-1","name":"Channel 1","channel_number":"1","keywords":["one"]},
            {"id":"app:youtube","name":"YouTube","type":"app","category":"apps","protocol_ids":{"roku":"837"}}
        ]""")
        assertTrue(result.isSuccess)
        assertEquals("1", result.getOrThrow()[0].protocolIds["channel"])
        assertEquals("837", result.getOrThrow()[1].protocolIds["roku"])
    }

    @Test fun `rejects missing required fields`() {
        assertTrue(ChannelCatalog.parse("""[{"id":"missing-name"}]""").isFailure)
        assertTrue(ChannelCatalog.parse("""[{"name":"missing-id"}]""").isFailure)
    }

    @Test fun `protocol without id is allowed and remains absent`() {
        val entry = ChannelCatalog.parse("""[{"id":"app:x","name":"X","type":"app","protocol_ids":{"roku":""}}]""").getOrThrow().single()
        assertFalse(entry.protocolIds.containsKey("roku"))
    }

    @Test fun `rejects invalid json`() {
        assertTrue(ChannelCatalog.parse("not-json").isFailure)
    }

    @Test fun `rejects duplicate ids`() {
        assertTrue(ChannelCatalog.parse("""[{"id":"same","name":"One"},{"id":"same","name":"Two"}]""").isFailure)
    }
}
