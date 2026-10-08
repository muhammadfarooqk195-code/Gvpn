package com.example.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.TunnelStats
import com.example.model.TunnelStatus
import com.example.model.VpnUiState
import com.example.ui.theme.StatusConnected
import com.example.ui.theme.StatusConnecting
import com.example.ui.theme.StatusDisconnected
import com.example.ui.theme.StatusError
import com.example.ui.theme.StatusReconnecting

@Composable
fun VpnScreen(
    uiState: VpnUiState,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onDismissError: () -> Unit,
    onSaveKeys: (clientPriv: String, serverPub: String, psk: String) -> Unit = { _, _, _ -> },
    onResetKeys: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showKeyDialog by remember { mutableStateOf(false) }

    val statusColor by animateColorAsState(
        targetValue = when (uiState.status) {
            TunnelStatus.CONNECTED -> StatusConnected
            TunnelStatus.CONNECTING -> StatusConnecting
            TunnelStatus.RECONNECTING -> StatusReconnecting
            TunnelStatus.ERROR -> StatusError
            else -> StatusDisconnected
        },
        label = "statusColor"
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Bar
                HeaderBar(
                    onConfigureKeys = { showKeyDialog = true },
                    onOpenVpnSettings = {
                        try {
                            val intent = Intent(Settings.ACTION_VPN_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Hardcoded Server Badge (Read-Only)
                HardcodedServerCard(
                    serverIp = uiState.serverIp,
                    serverPort = uiState.serverPort,
                    isConnected = uiState.status == TunnelStatus.CONNECTED
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Error Message Card
                AnimatedVisibility(visible = uiState.errorMessage != null) {
                    uiState.errorMessage?.let { error ->
                        ErrorMessageCard(
                            message = error,
                            onDismiss = onDismissError
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }

                // Big Status Hero & Connect / Disconnect Action
                BigConnectionAction(
                    status = uiState.status,
                    statusColor = statusColor,
                    onConnectClick = onConnectClick,
                    onDisconnectClick = onDisconnectClick
                )

                Spacer(modifier = Modifier.height(24.dp))

                // Connection Duration Timer
                if (uiState.status == TunnelStatus.CONNECTED) {
                    DurationTimerCard(duration = uiState.stats.formattedDuration)
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Traffic Bandwidth Statistics
                TrafficStatsRow(stats = uiState.stats)

                Spacer(modifier = Modifier.height(16.dp))

                // Key Configuration Quick Access Card
                KeysOverviewCard(
                    clientPrivKey = uiState.clientPrivateKey,
                    serverPubKey = uiState.serverPublicKey,
                    onEditKeys = { showKeyDialog = true }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Security & Kill Switch Shield Indicators
                SecurityFeaturesCard(
                    killSwitchActive = uiState.killSwitchActive,
                    dnsLeakProtection = uiState.dnsLeakProtectionActive
                )

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showKeyDialog) {
        KeyConfigurationDialog(
            initialClientPriv = uiState.clientPrivateKey,
            initialServerPub = uiState.serverPublicKey,
            initialPsk = uiState.presharedKey,
            onDismiss = { showKeyDialog = false },
            onSave = { clientPriv, serverPub, psk ->
                onSaveKeys(clientPriv, serverPub, psk)
                showKeyDialog = false
                Toast.makeText(context, "WireGuard keys saved", Toast.LENGTH_SHORT).show()
            },
            onReset = {
                onResetKeys()
                showKeyDialog = false
                Toast.makeText(context, "Reset to default matched keys", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@Composable
private fun HeaderBar(
    onConfigureKeys: () -> Unit,
    onOpenVpnSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = "Security Shield",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "DEDICATED VPN",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "WireGuard Protocol",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row {
            IconButton(
                onClick = onConfigureKeys,
                modifier = Modifier.testTag("key_config_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Configure Keys",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onOpenVpnSettings,
                modifier = Modifier.testTag("vpn_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "System VPN Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun HardcodedServerCard(
    serverIp: String,
    serverPort: Int,
    isConnected: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("server_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked Endpoint",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "HARDCODED TARGET SERVER",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Surface(
                    color = if (isConnected) StatusConnected.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = if (isConnected) "CONNECTED" else "LOCKED IP",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected) StatusConnected else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = serverIp,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Port: $serverPort  •  UDP  •  No DNS lookup",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun BigConnectionAction(
    status: TunnelStatus,
    statusColor: Color,
    onConnectClick: () -> Unit,
    onDisconnectClick: () -> Unit
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (status == TunnelStatus.CONNECTING || status == TunnelStatus.RECONNECTING) 1.08f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(190.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            statusColor.copy(alpha = 0.25f),
                            statusColor.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(140.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(3.dp, statusColor, CircleShape)
                    .shadow(elevation = 8.dp, shape = CircleShape)
                    .clickable {
                        if (status == TunnelStatus.CONNECTED || status == TunnelStatus.CONNECTING || status == TunnelStatus.RECONNECTING) {
                            onDisconnectClick()
                        } else {
                            onConnectClick()
                        }
                    }
                    .testTag("big_power_button"),
                contentAlignment = Alignment.Center
            ) {
                if (status == TunnelStatus.CONNECTING || status == TunnelStatus.DISCONNECTING) {
                    CircularProgressIndicator(
                        color = statusColor,
                        modifier = Modifier.size(60.dp),
                        strokeWidth = 4.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "VPN Power Toggle",
                        tint = statusColor,
                        modifier = Modifier.size(56.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = when (status) {
                TunnelStatus.CONNECTED -> "SECURE & CONNECTED"
                TunnelStatus.CONNECTING -> "ESTABLISHING TUNNEL…"
                TunnelStatus.RECONNECTING -> "AUTO-RECONNECTING…"
                TunnelStatus.DISCONNECTING -> "DISCONNECTING…"
                TunnelStatus.ERROR -> "CONNECTION FAILED"
                TunnelStatus.DISCONNECTED -> "DISCONNECTED"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.1.sp,
            color = statusColor,
            modifier = Modifier.testTag("status_text")
        )

        Text(
            text = when (status) {
                TunnelStatus.CONNECTED -> "All traffic encrypted through 203.189.226.237"
                TunnelStatus.CONNECTING -> "Performing WireGuard key exchange"
                TunnelStatus.RECONNECTING -> "Network changed, resuming tunnel"
                TunnelStatus.ERROR -> "Check server configuration or network"
                else -> "Tap the button below to connect securely"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        val isWorking = status == TunnelStatus.CONNECTING || status == TunnelStatus.DISCONNECTING
        val isConnected = status == TunnelStatus.CONNECTED || status == TunnelStatus.RECONNECTING

        Button(
            onClick = {
                if (isConnected) onDisconnectClick() else onConnectClick()
            },
            enabled = !isWorking,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("main_action_button"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isConnected) StatusError else MaterialTheme.colorScheme.primary,
                contentColor = Color.White
            )
        ) {
            Icon(
                imageVector = if (isConnected) Icons.Default.PowerSettingsNew else Icons.Default.VpnKey,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = when {
                    isWorking -> "Please wait…"
                    isConnected -> "DISCONNECT"
                    else -> "CONNECT TO DEDICATED SERVER"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

@Composable
private fun DurationTimerCard(duration: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("duration_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "CONNECTED DURATION",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = duration,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Surface(
                color = StatusConnected.copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "ACTIVE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = StatusConnected,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun TrafficStatsRow(stats: TunnelStats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            title = "DOWNLOAD",
            value = stats.formattedRx,
            icon = Icons.Default.ArrowDownward,
            iconColor = StatusConnected,
            modifier = Modifier.weight(1f).testTag("stat_download")
        )
        StatCard(
            title = "UPLOAD",
            value = stats.formattedTx,
            icon = Icons.Default.ArrowUpward,
            iconColor = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f).testTag("stat_upload")
        )
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(iconColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun KeysOverviewCard(
    clientPrivKey: String,
    serverPubKey: String,
    onEditKeys: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEditKeys() }
            .testTag("keys_overview_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "WIREGUARD KEYS (CURVE25519)",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "TAP TO EDIT",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Client Private Key: ${clientPrivKey.take(8)}…${clientPrivKey.takeLast(6)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Server Public Key: ${serverPubKey.take(8)}…${serverPubKey.takeLast(6)}",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SecurityFeaturesCard(
    killSwitchActive: Boolean,
    dnsLeakProtection: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "PROTECTION MATRIX",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))

            SecurityFeatureRow(
                title = "Kill Switch Enabled",
                subtitle = "Blocks unencrypted traffic if tunnel drops",
                isActive = killSwitchActive
            )
            Spacer(modifier = Modifier.height(10.dp))
            SecurityFeatureRow(
                title = "DNS Leak Shield (1.1.1.1, 8.8.8.8)",
                subtitle = "All DNS queries routed through tunnel",
                isActive = dnsLeakProtection
            )
            Spacer(modifier = Modifier.height(10.dp))
            SecurityFeatureRow(
                title = "Full-Tunnel Routing",
                subtitle = "0.0.0.0/0 & ::/0 routed to 203.189.226.237",
                isActive = true
            )
        }
    }
}

@Composable
private fun SecurityFeatureRow(
    title: String,
    subtitle: String,
    isActive: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = if (isActive) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = null,
            tint = if (isActive) StatusConnected else StatusError,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun ErrorMessageCard(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = StatusError.copy(alpha = 0.12f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                tint = StatusError,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = StatusError,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Dismiss",
                    tint = StatusError,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun KeyConfigurationDialog(
    initialClientPriv: String,
    initialServerPub: String,
    initialPsk: String,
    onDismiss: () -> Unit,
    onSave: (clientPriv: String, serverPub: String, psk: String) -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    var clientPriv by remember { mutableStateOf(initialClientPriv) }
    var serverPub by remember { mutableStateOf(initialServerPub) }
    var psk by remember { mutableStateOf(initialPsk) }

    val serverWgConfText = """
[Interface]
Address = 10.8.0.1/24, fd42:42:42::1/64
ListenPort = 51820
PrivateKey = <SERVER_PRIVATE_KEY>

[Peer]
# Android Client (matches key in this app)
PublicKey = 4ASNxmIEAziiiYvYbQgCp6EoeWX/FWWcBt51CKIMlG4=
AllowedIPs = 10.8.0.2/32, fd42:42:42::2/128
    """.trimIndent()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "WireGuard Keys Configuration",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Endpoint is strictly locked to 203.189.226.237:51820 (cannot be changed). You can update the cryptographic keys below:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedTextField(
                    value = clientPriv,
                    onValueChange = { clientPriv = it },
                    label = { Text("Client Private Key") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = serverPub,
                    onValueChange = { serverPub = it },
                    label = { Text("Server Public Key") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = psk,
                    onValueChange = { psk = it },
                    label = { Text("Pre-Shared Key (Optional)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(14.dp))

                OutlinedButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("wg0.conf", serverWgConfText))
                        Toast.makeText(context, "Copied matching server wg0.conf to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Matching Server wg0.conf", style = MaterialTheme.typography.labelSmall)
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = onReset,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                ) {
                    Text("Reset to Matched Default Keys", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(clientPriv, serverPub, psk) }
            ) {
                Text("Save Keys")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
