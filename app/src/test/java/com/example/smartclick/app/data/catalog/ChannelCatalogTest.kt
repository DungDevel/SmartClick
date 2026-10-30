package com.example.smartclick.app.data.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelCatalogTest {
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
