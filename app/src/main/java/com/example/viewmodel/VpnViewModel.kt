package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.net.VpnService
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.config.VpnConfig
import com.example.model.VpnUiState
import com.example.service.TunnelService
import com.example.service.VpnTunnelManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class VpnViewModel(application: Application) : AndroidViewModel(application) {

    private val tunnelManager = VpnTunnelManager.getInstance(application)
    private val keyRefreshTrigger = MutableStateFlow(0)

    init {
        VpnConfig.loadFromPreferences(application)
    }

    val uiState: StateFlow<VpnUiState> = combine(
        tunnelManager.statusFlow,
        tunnelManager.statsFlow,
        tunnelManager.errorFlow,
        keyRefreshTrigger
    ) { status, stats, error, _ ->
        VpnUiState(
            status = status,
            stats = stats,
            serverIp = VpnConfig.SERVER_IP,
            serverPort = VpnConfig.SERVER_PORT,
            clientPrivateKey = VpnConfig.clientPrivateKey,
            serverPublicKey = VpnConfig.serverPublicKey,
            presharedKey = VpnConfig.presharedKey,
            killSwitchActive = true,
            dnsLeakProtectionActive = true,
            isPlaceholderConfig = false,
            errorMessage = error
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VpnUiState(
            clientPrivateKey = VpnConfig.clientPrivateKey,
            serverPublicKey = VpnConfig.serverPublicKey,
            presharedKey = VpnConfig.presharedKey
        )
    )

    fun onConnectClicked(context: Context, onPermissionRequired: () -> Unit) {
        try {
            VpnConfig.validateEndpoint(VpnConfig.SERVER_IP, VpnConfig.SERVER_PORT)
        } catch (se: SecurityException) {
            return
        }

        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent != null) {
            onPermissionRequired()
        } else {
            TunnelService.startVpn(context)
        }
    }

    fun onDisconnectClicked(context: Context) {
        TunnelService.stopVpn(context)
    }

    fun saveCustomKeys(clientPriv: String, serverPub: String, psk: String) {
        VpnConfig.saveToPreferences(getApplication(), clientPriv, serverPub, psk)
        keyRefreshTrigger.value += 1
    }

    fun resetToDefaultKeys() {
        VpnConfig.resetToDefaults(getApplication())
        keyRefreshTrigger.value += 1
    }

    fun clearError() {
        tunnelManager.clearError()
    }
}
