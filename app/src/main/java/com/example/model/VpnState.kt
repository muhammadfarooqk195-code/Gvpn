package com.example.model

import com.example.config.VpnConfig
import java.util.Locale

enum class TunnelStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTING,
    ERROR
}

data class TunnelStats(
    val rxBytes: Long = 0L,
    val txBytes: Long = 0L,
    val durationSeconds: Long = 0L
) {
    val formattedRx: String
        get() = formatBytes(rxBytes)

    val formattedTx: String
        get() = formatBytes(txBytes)

    val formattedDuration: String
        get() {
            val hours = durationSeconds / 3600
            val minutes = (durationSeconds % 3600) / 60
            val seconds = durationSeconds % 60
            return if (hours > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format(Locale.US, "%02d:%02d", minutes, seconds)
            }
        }

    companion object {
        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 B"
            val kb = bytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.US, "%.2f GB", gb)
                mb >= 1.0 -> String.format(Locale.US, "%.2f MB", mb)
                kb >= 1.0 -> String.format(Locale.US, "%.1f KB", kb)
                else -> "$bytes B"
            }
        }
    }
}

data class VpnUiState(
    val status: TunnelStatus = TunnelStatus.DISCONNECTED,
    val stats: TunnelStats = TunnelStats(),
    val serverIp: String = VpnConfig.SERVER_IP,
    val serverPort: Int = VpnConfig.SERVER_PORT,
    val clientPrivateKey: String = VpnConfig.DEFAULT_CLIENT_PRIVATE_KEY,
    val serverPublicKey: String = VpnConfig.DEFAULT_SERVER_PUBLIC_KEY,
    val presharedKey: String = "",
    val killSwitchActive: Boolean = true,
    val dnsLeakProtectionActive: Boolean = true,
    val isPlaceholderConfig: Boolean = false,
    val errorMessage: String? = null
)
