package com.example

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.example.service.TunnelService
import com.example.ui.VpnScreen
import com.example.ui.theme.DedicatedVpnTheme
import com.example.viewmodel.VpnViewModel

class MainActivity : ComponentActivity() {

    private val vpnViewModel: VpnViewModel by viewModels()

    // ActivityResultLauncher for VpnService.prepare() dialog
    private val vpnPrepareLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            TunnelService.startVpn(this)
        }
    }

    // Permission launcher for Android 13+ (API 33) notification permission
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        // Notification permission granted/denied handled gracefully
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        requestNotificationPermissionIfNeeded()

        setContent {
            DedicatedVpnTheme {
                val uiState by vpnViewModel.uiState.collectAsState()

                VpnScreen(
                    uiState = uiState,
                    onConnectClick = {
                        val prepareIntent = VpnService.prepare(this)
                        if (prepareIntent != null) {
                            vpnPrepareLauncher.launch(prepareIntent)
                        } else {
                            TunnelService.startVpn(this)
                        }
                    },
                    onDisconnectClick = {
                        vpnViewModel.onDisconnectClicked(this)
                    },
                    onDismissError = {
                        vpnViewModel.clearError()
                    },
                    onSaveKeys = { clientPriv, serverPub, psk ->
                        vpnViewModel.saveCustomKeys(clientPriv, serverPub, psk)
                    },
                    onResetKeys = {
                        vpnViewModel.resetToDefaultKeys()
                    }
                )
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
