package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.config.VpnConfig
import com.example.model.TunnelStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground Service maintaining the persistent notification and
 * network change monitoring for auto-reconnection.
 */
class TunnelService : Service() {

    companion object {
        const val ACTION_CONNECT = "com.example.vpn.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "com.example.vpn.ACTION_DISCONNECT"

        private const val CHANNEL_ID = "dedicated_vpn_channel"
        private const val NOTIFICATION_ID = 42001

        fun startVpn(context: Context) {
            val intent = Intent(context, TunnelService::class.java).apply {
                action = ACTION_CONNECT
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopVpn(context: Context) {
            val intent = Intent(context, TunnelService::class.java).apply {
                action = ACTION_DISCONNECT
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var tunnelManager: VpnTunnelManager
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate() {
        super.onCreate()
        tunnelManager = VpnTunnelManager.getInstance(applicationContext)
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        createNotificationChannel()
        registerNetworkCallback()
        observeTunnelState()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_CONNECT -> {
                startInForeground(buildNotification("Connecting to ${VpnConfig.SERVER_IP}…", "Starting encrypted WireGuard tunnel"))
                serviceScope.launch {
                    tunnelManager.connect()
                }
            }
            ACTION_DISCONNECT -> {
                serviceScope.launch {
                    tunnelManager.disconnect()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_STICKY
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun observeTunnelState() {
        serviceScope.launch {
            tunnelManager.statusFlow.collectLatest { status ->
                when (status) {
                    TunnelStatus.CONNECTED -> {
                        updateNotification(
                            title = "Protected: ${VpnConfig.SERVER_IP}",
                            content = "Traffic securely encrypted. Kill switch active."
                        )
                    }
                    TunnelStatus.CONNECTING -> {
                        updateNotification(
                            title = "Connecting to ${VpnConfig.SERVER_IP}…",
                            content = "Establishing WireGuard handshake"
                        )
                    }
                    TunnelStatus.RECONNECTING -> {
                        updateNotification(
                            title = "Reconnecting…",
                            content = "Network changed. Resuming session with ${VpnConfig.SERVER_IP}"
                        )
                    }
                    TunnelStatus.DISCONNECTED -> {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                    TunnelStatus.DISCONNECTING -> {
                        updateNotification(
                            title = "Disconnecting…",
                            content = "Closing secure tunnel"
                        )
                    }
                    TunnelStatus.ERROR -> {
                        val error = tunnelManager.errorFlow.value ?: "Connection error"
                        updateNotification(
                            title = "VPN Error",
                            content = error
                        )
                    }
                }
            }
        }

        // Periodically update notification with data stats when connected
        serviceScope.launch {
            tunnelManager.statsFlow.collectLatest { stats ->
                if (tunnelManager.statusFlow.value == TunnelStatus.CONNECTED) {
                    val content = "↓ ${stats.formattedRx}  ↑ ${stats.formattedTx}  ⏱ ${stats.formattedDuration}"
                    updateNotification(
                        title = "Protected: ${VpnConfig.SERVER_IP}",
                        content = content
                    )
                }
            }
        }
    }

    private fun updateNotification(title: String, content: String) {
        val notification = buildNotification(title, content)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(title: String, content: String): Notification {
        val mainActivityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainActivityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val disconnectIntent = Intent(this, TunnelService::class.java).apply {
            action = ACTION_DISCONNECT
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            1,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.notification_action_disconnect),
                disconnectPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun registerNetworkCallback() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    tunnelManager.onNetworkAvailable()
                }

                override fun onLost(network: Network) {
                    super.onLost(network)
                    tunnelManager.onNetworkLost()
                }
            }
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        } catch (e: Exception) {
            // Ignore if registration fails
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
