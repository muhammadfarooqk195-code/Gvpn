package com.example

import com.example.config.VpnConfig
import com.example.model.TunnelStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testHardcodedEndpointValidation_Success() {
        // Must succeed for the exact hardcoded server
        VpnConfig.validateEndpoint("203.189.226.237", 51820)
    }

    @Test
    fun testHardcodedEndpointValidation_RefusesOtherIps() {
        assertThrows(SecurityException::class.java) {
            VpnConfig.validateEndpoint("1.1.1.1", 51820)
        }
        assertThrows(SecurityException::class.java) {
            VpnConfig.validateEndpoint("203.189.226.238", 51820)
        }
    }

    @Test
    fun testHardcodedEndpointValidation_RefusesHostnames() {
        assertThrows(SecurityException::class.java) {
            VpnConfig.validateEndpoint("vpn.example.com", 51820)
        }
        assertThrows(SecurityException::class.java) {
            VpnConfig.validateEndpoint("localhost", 51820)
        }
    }

    @Test
    fun testHardcodedEndpointValidation_RefusesWrongPort() {
        assertThrows(SecurityException::class.java) {
            VpnConfig.validateEndpoint("203.189.226.237", 8080)
        }
    }

    @Test
    fun testStatsFormatting() {
        assertEquals("0 B", TunnelStats.formatBytes(0))
        assertEquals("500 B", TunnelStats.formatBytes(500))
        assertEquals("1.0 KB", TunnelStats.formatBytes(1024))
        assertEquals("1.50 MB", TunnelStats.formatBytes((1.5 * 1024 * 1024).toLong()))
        assertEquals("2.00 GB", TunnelStats.formatBytes((2L * 1024 * 1024 * 1024)))
    }
}
