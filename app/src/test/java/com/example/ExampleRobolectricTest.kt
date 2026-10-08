package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun testAppNameResource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Dedicated VPN", appName)
    }

    @Test
    fun testServerIpResource() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val serverIp = context.getString(R.string.server_ip)
        assertEquals("203.189.226.237", serverIp)
    }
}
