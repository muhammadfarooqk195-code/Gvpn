package com.example.service

import android.content.Context
import android.util.Log
import com.example.config.VpnConfig
import com.example.model.TunnelStats
import com.example.model.TunnelStatus
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Manages the WireGuard tunnel lifecycle, stats polling, and state propagation.
 * Strict zero-log policy for cryptographic keys.
 */
class VpnTunnelManager private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "VpnTunnelManager"
        private const val TUNNEL_NAME = "wg-dedicated"

        @Volatile
        private var INSTANCE: VpnTunnelManager? = null

        fun getInstance(context: Context): VpnTunnelManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: VpnTunnelManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var statsJob: Job? = null
    private var connectedTimestamp: Long = 0L

    private val backend: Backend by lazy {
        GoBackend(appContext)
    }

    private val wireguardTunnel = object : Tunnel {
        override fun getName(): String = TUNNEL_NAME

        override fun onStateChange(newState: Tunnel.State) {
            Log.d(TAG, "Tunnel onStateChange: $newState")
            scope.launch {
                when (newState) {
                    Tunnel.State.UP -> {
                        _statusFlow.value = TunnelStatus.CONNECTED
                        startStatsPolling()
                    }
                    Tunnel.State.DOWN -> {
                        stopStatsPolling()
                        _statusFlow.value = TunnelStatus.DISCONNECTED
                    }
                    Tunnel.State.TOGGLE -> {
                        _statusFlow.value = TunnelStatus.CONNECTING
                    }
                }
            }
        }
    }

    private val _statusFlow = MutableStateFlow(TunnelStatus.DISCONNECTED)
    val statusFlow: StateFlow<TunnelStatus> = _statusFlow.asStateFlow()

    private val _statsFlow = MutableStateFlow(TunnelStats())
    val statsFlow: StateFlow<TunnelStats> = _statsFlow.asStateFlow()

    private val _errorFlow = MutableStateFlow<String?>(null)
    val errorFlow: StateFlow<String?> = _errorFlow.asStateFlow()

    init {
        VpnConfig.loadFromPreferences(appContext)

        // Query initial state on startup
        scope.launch(Dispatchers.IO) {
            try {
                val state = backend.getState(wireguardTunnel)
                withContext(Dispatchers.Main) {
                    if (state == Tunnel.State.UP) {
                        _statusFlow.value = TunnelStatus.CONNECTED
                        startStatsPolling()
                    } else {
                        _statusFlow.value = TunnelStatus.DISCONNECTED
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to get initial tunnel state: ${e.message}")
            }
        }
    }

    suspend fun connect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _errorFlow.value = null
            _statusFlow.value = TunnelStatus.CONNECTING

            VpnConfig.loadFromPreferences(appContext)

            // 1. Strict validation check (hardcoded IP and port)
            VpnConfig.validateEndpoint(VpnConfig.SERVER_IP, VpnConfig.SERVER_PORT)

            // 2. Build WireGuard Config
            val config = VpnConfig.buildWireGuardConfig()

            // 4. Activate tunnel via GoBackend
            Log.i(TAG, "Establishing connection to dedicated endpoint ${VpnConfig.SERVER_IP}:${VpnConfig.SERVER_PORT}")
            val resultState = backend.setState(wireguardTunnel, Tunnel.State.UP, config)

            withContext(Dispatchers.Main) {
                if (resultState == Tunnel.State.UP) {
                    _statusFlow.value = TunnelStatus.CONNECTED
                    connectedTimestamp = System.currentTimeMillis()
                    startStatsPolling()
                } else {
                    _statusFlow.value = TunnelStatus.DISCONNECTED
                }
            }
            Result.success(Unit)
        } catch (se: SecurityException) {
            Log.e(TAG, "Security constraint violation during connect: ${se.message}")
            withContext(Dispatchers.Main) {
                _statusFlow.value = TunnelStatus.ERROR
                _errorFlow.value = se.message ?: "Endpoint security violation"
            }
            Result.failure(se)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bring tunnel UP: ${e.message}")
            withContext(Dispatchers.Main) {
                _statusFlow.value = TunnelStatus.ERROR
                _errorFlow.value = e.message ?: "Connection failed"
            }
            Result.failure(e)
        }
    }

    suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            withContext(Dispatchers.Main) {
                _statusFlow.value = TunnelStatus.DISCONNECTING
            }
            stopStatsPolling()

            backend.setState(wireguardTunnel, Tunnel.State.DOWN, null)

            withContext(Dispatchers.Main) {
                _statusFlow.value = TunnelStatus.DISCONNECTED
                _statsFlow.value = TunnelStats()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error disconnecting tunnel: ${e.message}")
            withContext(Dispatchers.Main) {
                _statusFlow.value = TunnelStatus.ERROR
                _errorFlow.value = e.message ?: "Disconnection error"
            }
            Result.failure(e)
        }
    }

    /**
     * Called during network transitions (e.g. WiFi -> Mobile) to ensure session continuity.
     */
    fun onNetworkAvailable() {
        if (_statusFlow.value == TunnelStatus.CONNECTED || _statusFlow.value == TunnelStatus.RECONNECTING) {
            Log.d(TAG, "Network restored. Verifying tunnel state...")
            scope.launch(Dispatchers.IO) {
                try {
                    val currentState = backend.getState(wireguardTunnel)
                    if (currentState != Tunnel.State.UP) {
                        Log.i(TAG, "Tunnel dropped during network change. Auto-reconnecting...")
                        _statusFlow.value = TunnelStatus.RECONNECTING
                        connect()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error during network auto-reconnect check: ${e.message}")
                }
            }
        }
    }

    fun onNetworkLost() {
        if (_statusFlow.value == TunnelStatus.CONNECTED) {
            Log.w(TAG, "Network lost while tunnel was connected. Kill switch blocking traffic.")
            _statusFlow.value = TunnelStatus.RECONNECTING
        }
    }

    private fun startStatsPolling() {
        statsJob?.cancel()
        if (connectedTimestamp == 0L) {
            connectedTimestamp = System.currentTimeMillis()
        }
        statsJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                try {
                    val stats = backend.getStatistics(wireguardTunnel)
                    val durationSec = (System.currentTimeMillis() - connectedTimestamp) / 1000L
                    val totalRx = stats.totalRx()
                    val totalTx = stats.totalTx()

                    withContext(Dispatchers.Main) {
                        _statsFlow.value = TunnelStats(
                            rxBytes = totalRx,
                            txBytes = totalTx,
                            durationSeconds = if (durationSec > 0) durationSec else 0L
                        )
                    }
                } catch (e: Exception) {
                    // Suppress polling noise
                }
                delay(1000L)
            }
        }
    }

    private fun stopStatsPolling() {
        statsJob?.cancel()
        statsJob = null
        connectedTimestamp = 0L
    }

    fun clearError() {
        _errorFlow.value = null
    }
}
