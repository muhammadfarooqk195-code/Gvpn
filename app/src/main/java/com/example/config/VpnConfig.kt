package com.example.config

import android.content.Context
import android.content.SharedPreferences
import com.wireguard.config.Config
import com.wireguard.config.InetEndpoint
import com.wireguard.config.InetNetwork
import com.wireguard.config.Interface
import com.wireguard.config.Peer
import java.net.InetAddress

/**
 * Single source of configuration for the Dedicated WireGuard VPN.
 *
 * CRITICAL RESTRICTION:
 * This app is strictly locked to ONE hardcoded server: 203.189.226.237:51820.
 * Any attempt to use any other endpoint, hostname, or domain is rejected
 * before connection establishment.
 */
object VpnConfig {

    // =========================================================================
    // HARDCODED DEDICATED SERVER ENDPOINT (STRICTLY IMMUTABLE)
    // =========================================================================
    const val SERVER_IP: String = "203.189.226.237"
    const val SERVER_PORT: Int = 51820

    // =========================================================================
    // READY-TO-USE CURVE25519 CRYPTOGRAPHIC KEYS
    // =========================================================================
    // These keys are generated and matched with the server configuration in wg0.conf
    const val DEFAULT_CLIENT_PRIVATE_KEY: String = "IOzlNJJdDrs10BvBf/ek7PbE63Jlc3FGxqfJqil2UWs="
    const val DEFAULT_SERVER_PUBLIC_KEY: String = "OX2em4bGara+Ya4OisZsMWRQ5QqW6734IXIF6TU8WWU="
    const val DEFAULT_CLIENT_PUBLIC_KEY: String = "4ASNxmIEAziiiYvYbQgCp6EoeWX/FWWcBt51CKIMlG4="
    const val DEFAULT_SERVER_PRIVATE_KEY: String = "QOZHVfBDpAiPob2plBVh1rV1A0/vRJE6CHlymwdM1ms="

    var clientPrivateKey: String = DEFAULT_CLIENT_PRIVATE_KEY
    var serverPublicKey: String = DEFAULT_SERVER_PUBLIC_KEY
    var presharedKey: String = ""

    /**
     * Tunnel internal IPv4 address allocated for this client.
     */
    const val CLIENT_TUNNEL_IPV4: String = "10.8.0.2/24"

    /**
     * Optional tunnel internal IPv6 address allocated for this client.
     */
    const val CLIENT_TUNNEL_IPV6: String = "fd42:42:42::2/64"

    /**
     * Primary DNS server used inside the encrypted tunnel (Prevents DNS leaks).
     * Cloudflare secure DNS (1.1.1.1).
     */
    const val DNS_PRIMARY: String = "1.1.1.1"

    /**
     * Secondary DNS server used inside the encrypted tunnel.
     * Google public DNS (8.8.8.8).
     */
    const val DNS_SECONDARY: String = "8.8.8.8"

    /**
     * Routing rules: All IPv4 and IPv6 traffic is routed into the tunnel.
     * (Full-tunnel, zero leaks).
     */
    const val ALLOWED_IPS: String = "0.0.0.0/0, ::/0"

    /**
     * MTU (Maximum Transmission Unit) for the WireGuard interface.
     */
    const val MTU: Int = 1420

    /**
     * Keepalive packet frequency in seconds to maintain NAT traversal.
     */
    const val PERSISTENT_KEEPALIVE_SECONDS: Int = 25

    // =========================================================================
    // STRICT VALIDATION & SECURITY ENFORCEMENT
    // =========================================================================

    private val EXPECTED_IP_BYTES = byteArrayOf(
        203.toByte(),
        189.toByte(),
        226.toByte(),
        237.toByte()
    )

    /**
     * Validates that the endpoint strictly matches 203.189.226.237:51820.
     * Rejects any attempt to use domain names, DNS resolution, or alternate addresses.
     */
    @Throws(SecurityException::class)
    fun validateEndpoint(ip: String, port: Int) {
        // Strict literal string check
        if (ip != SERVER_IP) {
            throw SecurityException(
                "Violation: Hardcoded VPN endpoint check failed. Expected $SERVER_IP but received $ip"
            )
        }

        // Strict port check
        if (port != SERVER_PORT) {
            throw SecurityException(
                "Violation: Dedicated port mismatch. Expected $SERVER_PORT but received $port"
            )
        }

        // Low-level byte verification without triggering DNS lookup
        val parts = ip.split(".")
        if (parts.size != 4) {
            throw SecurityException("Invalid IPv4 address format: $ip")
        }

        val rawBytes = ByteArray(4)
        for (i in 0..3) {
            val byteVal = parts[i].toIntOrNull()
                ?: throw SecurityException("Non-numeric IPv4 octet in $ip")
            if (byteVal !in 0..255) {
                throw SecurityException("IPv4 octet out of range: $byteVal")
            }
            rawBytes[i] = byteVal.toByte()
        }

        if (!rawBytes.contentEquals(EXPECTED_IP_BYTES)) {
            throw SecurityException("IPv4 byte validation mismatch for $ip")
        }
    }

    /**
     * Loads keys from SharedPreferences or falls back to built-in defaults.
     */
    fun loadFromPreferences(context: Context) {
        val prefs = context.getSharedPreferences("vpn_keys_pref", Context.MODE_PRIVATE)
        clientPrivateKey = prefs.getString("client_private_key", DEFAULT_CLIENT_PRIVATE_KEY) ?: DEFAULT_CLIENT_PRIVATE_KEY
        serverPublicKey = prefs.getString("server_public_key", DEFAULT_SERVER_PUBLIC_KEY) ?: DEFAULT_SERVER_PUBLIC_KEY
        presharedKey = prefs.getString("preshared_key", "") ?: ""
    }

    /**
     * Saves custom keys to SharedPreferences.
     */
    fun saveToPreferences(context: Context, clientPriv: String, serverPub: String, psk: String) {
        val prefs = context.getSharedPreferences("vpn_keys_pref", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("client_private_key", clientPriv.trim())
            .putString("server_public_key", serverPub.trim())
            .putString("preshared_key", psk.trim())
            .apply()

        clientPrivateKey = clientPriv.trim()
        serverPublicKey = serverPub.trim()
        presharedKey = psk.trim()
    }

    /**
     * Resets keys to default built-in keypair.
     */
    fun resetToDefaults(context: Context) {
        saveToPreferences(context, DEFAULT_CLIENT_PRIVATE_KEY, DEFAULT_SERVER_PUBLIC_KEY, "")
    }

    /**
     * Builds and validates the strongly-typed WireGuard Config instance.
     */
    @Throws(Exception::class)
    fun buildWireGuardConfig(): Config {
        // Enforce hardcoded endpoint restriction
        validateEndpoint(SERVER_IP, SERVER_PORT)

        val privKey = clientPrivateKey.trim()
        val pubKey = serverPublicKey.trim()

        if (privKey.length < 40 || pubKey.length < 40) {
            throw IllegalStateException("Invalid WireGuard keys. Key length must be at least 44 characters Base64.")
        }

        // Construct Interface
        val interfaceBuilder = Interface.Builder()
        interfaceBuilder.parsePrivateKey(privKey)

        // Add Client IPv4 Address
        interfaceBuilder.addAddress(InetNetwork.parse(CLIENT_TUNNEL_IPV4.trim()))

        // Add Client IPv6 Address if configured
        if (CLIENT_TUNNEL_IPV6.isNotBlank()) {
            try {
                interfaceBuilder.addAddress(InetNetwork.parse(CLIENT_TUNNEL_IPV6.trim()))
            } catch (_: Exception) {}
        }

        // Add DNS servers to prevent DNS leaks
        if (DNS_PRIMARY.isNotBlank()) {
            interfaceBuilder.addDnsServer(InetAddress.getByName(DNS_PRIMARY.trim()))
        }
        if (DNS_SECONDARY.isNotBlank()) {
            interfaceBuilder.addDnsServer(InetAddress.getByName(DNS_SECONDARY.trim()))
        }

        if (MTU > 0) {
            interfaceBuilder.setMtu(MTU)
        }

        // Construct Peer (Dedicated Server 203.189.226.237:51820)
        val peerBuilder = Peer.Builder()
        peerBuilder.parsePublicKey(pubKey)

        // Strict endpoint construction using parsed literal InetEndpoint
        peerBuilder.setEndpoint(InetEndpoint.parse("$SERVER_IP:$SERVER_PORT"))

        // Route all traffic (Full-tunnel)
        peerBuilder.parseAllowedIPs(ALLOWED_IPS)

        if (presharedKey.isNotBlank()) {
            peerBuilder.parsePreSharedKey(presharedKey.trim())
        }

        if (PERSISTENT_KEEPALIVE_SECONDS > 0) {
            peerBuilder.setPersistentKeepalive(PERSISTENT_KEEPALIVE_SECONDS)
        }

        return Config.Builder()
            .setInterface(interfaceBuilder.build())
            .addPeer(peerBuilder.build())
            .build()
    }
}
