package com.example.smartclick.app.tvconnection.discovery

import android.content.Context
import com.example.smartclick.app.domain.model.TvProtocol
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProbeFactorySonyTest {

    @Test
    fun `Sony Bravia protocol scans the ports used by its fingerprint`() {
        val factory = ProbeFactory(mockk<Context>(relaxed = true))

        val probes = factory.createProbesForProtocol(TvProtocol.SONY_BRAVIA)

        assertEquals(1, probes.size)
        val probe = probes.single()
        assertTrue(probe is PortProbe)
        assertEquals(TvProtocol.SONY_BRAVIA, probe.protocol)

        val portsField = PortProbe::class.java.getDeclaredField("ports").apply { isAccessible = true }
        assertEquals(listOf(80, 52323), portsField.get(probe))
    }
}
