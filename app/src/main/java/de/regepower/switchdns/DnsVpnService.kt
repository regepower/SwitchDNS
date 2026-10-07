package de.regepower.switchdns

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.service.quicksettings.TileService
import android.system.OsConstants
import android.util.Log
import java.util.concurrent.CopyOnWriteArraySet

/**
 * DNS-only VPN: the tunnel gets an address and the chosen DNS servers but no routes.
 * Android then resolves names for the covered apps through these servers, while all
 * traffic keeps using the normal network. No packet loop, nothing read from the tunnel.
 *
 * While the service runs it watches the network: the tunnel is closed ("paused") on an
 * excluded network type or while a Wi-Fi network waits for a captive-portal login, and
 * re-opened when the network fits again. The service itself stays in the foreground.
 */
class DnsVpnService : VpnService() {
    private var tunnel: ParcelFileDescriptor? = null
    private val main = Handler(Looper.getMainLooper())
    private var cm: ConnectivityManager? = null

    /** Underlying default network of this app (the app itself is always excluded from the tunnel). */
    private var defaultCaps: NetworkCapabilities? = null
    private val wifiCaps = HashMap<Network, NetworkCapabilities>()

    private val defaultCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            defaultCaps = caps
            evaluate(false)
        }

        override fun onLost(network: Network) {
            defaultCaps = null
            evaluate(false)
        }
    }

    private val wifiCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            wifiCaps[network] = caps
            evaluate(false)
        }

        override fun onLost(network: Network) {
            wifiCaps.remove(network)
            evaluate(false)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            shutdown()
            return START_NOT_STICKY
        }
        // ACTION_START, always-on start by the system (android.net.VpnService) or sticky restart (null intent).
        if (prepare(this) != null) {
            Log.w(TAG, "VPN permission missing, not starting")
            shutdown()
            return START_NOT_STICKY
        }
        goForeground(Prefs(this).current(), null)
        watchNetworks()
        // Re-open the tunnel so changed settings (server, apps) apply immediately.
        evaluate(true)
        return START_STICKY
    }

    override fun onRevoke() {
        shutdown()
        super.onRevoke()
    }

    override fun onDestroy() {
        unwatchNetworks()
        closeQuietly(tunnel)
        tunnel = null
        publish(this, null, null)
        super.onDestroy()
    }

    private fun shutdown() {
        unwatchNetworks()
        closeQuietly(tunnel)
        tunnel = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        publish(this, null, null)
    }

    private fun watchNetworks() {
        if (cm != null) return
        val c = getSystemService(ConnectivityManager::class.java)
        cm = c
        c.registerDefaultNetworkCallback(defaultCallback, main)
        c.registerNetworkCallback(
            NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build(),
            wifiCallback,
            main
        )
    }

    private fun unwatchNetworks() {
        val c = cm ?: return
        cm = null
        try {
            c.unregisterNetworkCallback(defaultCallback)
            c.unregisterNetworkCallback(wifiCallback)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "unregister failed", e)
        }
        defaultCaps = null
        wifiCaps.clear()
    }

    /** String resource explaining why the tunnel must stay closed, or null if it may be open. */
    private fun pauseReason(): Int? {
        val prefs = Prefs(this)
        if (prefs.pauseCaptive) {
            val captive = (wifiCaps.values + listOfNotNull(defaultCaps))
                .any { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) }
            if (captive) return R.string.pause_captive
        }
        val caps = defaultCaps ?: return null
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !prefs.onWifi) return R.string.pause_wifi
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) && !prefs.onMobile) return R.string.pause_mobile
        return null
    }

    /** Opens or closes the tunnel to match the network; [reopen] forces a fresh tunnel. */
    private fun evaluate(reopen: Boolean) {
        if (cm == null) return
        val server = Prefs(this).current()
        val reason = pauseReason()
        if (reason != null) {
            if (tunnel != null || reopen || pausedReason != reason) {
                closeQuietly(tunnel)
                tunnel = null
                goForeground(server, reason)
                publish(this, null, reason)
            }
            return
        }
        if (tunnel != null && !reopen) return
        val old = tunnel
        tunnel = establish(server)
        closeQuietly(old)
        if (tunnel == null) {
            shutdown()
            return
        }
        goForeground(server, null)
        publish(this, server, null)
    }

    private fun goForeground(server: DnsServer, reason: Int?) {
        try {
            val n = notification(server, reason)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SYSTEM_EXEMPTED)
            } else {
                startForeground(NOTIFICATION_ID, n)
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "startForeground failed", e)
        }
    }

    /** Tries a few private addresses in case one collides with the current network. */
    private fun establish(server: DnsServer): ParcelFileDescriptor? {
        val prefs = Prefs(this)
        for ((address, prefix) in ADDRESSES) {
            try {
                val b = Builder()
                    .setSession(getString(R.string.app_name))
                    .addAddress(address, prefix)
                    // No routes: keep IPv6 traffic flowing over the normal network instead of blocking it.
                    .allowFamily(OsConstants.AF_INET)
                    .allowFamily(OsConstants.AF_INET6)
                    .setMtu(1500)
                    // Default for targetSdk 29+ is "metered": Play Store etc. would treat Wi-Fi as mobile data.
                    // false = inherit metered state from the underlying network.
                    .setMetered(false)
                    .setConfigureIntent(mainIntent())
                b.addDnsServer(server.primary)
                server.secondary?.let { b.addDnsServer(it) }
                applyAppFilter(b, prefs)
                val fd = b.establish()
                if (fd != null) return fd
            } catch (e: Exception) {
                Log.w(TAG, "establish with $address failed", e)
            }
        }
        return null
    }

    private fun applyAppFilter(b: Builder, prefs: Prefs) {
        val pkgs = prefs.packages
        if (prefs.whitelist && pkgs.isNotEmpty()) {
            pkgs.forEach { pkg ->
                try {
                    b.addAllowedApplication(pkg)
                } catch (_: PackageManager.NameNotFoundException) {
                    Log.i(TAG, "skip uninstalled $pkg")
                }
            }
        } else {
            b.addDisallowedApplication(packageName)
            if (!prefs.whitelist) {
                pkgs.forEach { pkg ->
                    try {
                        b.addDisallowedApplication(pkg)
                    } catch (_: PackageManager.NameNotFoundException) {
                        Log.i(TAG, "skip uninstalled $pkg")
                    }
                }
            }
        }
    }

    private fun mainIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE
    )

    private fun notification(server: DnsServer, reason: Int?): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW)
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, DnsVpnService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val title = if (reason == null) {
            getString(R.string.notif_title, server.name)
        } else {
            getString(R.string.notif_paused, getString(reason))
        }
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentIntent(mainIntent())
            .setOngoing(true)
            .setShowWhen(false)
            .addAction(Notification.Action.Builder(null, getString(R.string.action_stop), stop).build())
            .build()
    }

    private fun closeQuietly(fd: ParcelFileDescriptor?) {
        try {
            fd?.close()
        } catch (e: Exception) {
            Log.w(TAG, "close failed", e)
        }
    }

    companion object {
        private const val TAG = "SwitchDNS"
        private const val CHANNEL = "vpn"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "de.regepower.switchdns.START"
        const val ACTION_STOP = "de.regepower.switchdns.STOP"

        private val ADDRESSES = listOf(
            "172.31.255.253" to 30,
            "192.168.234.55" to 24,
            "10.111.222.1" to 32
        )

        /** Server in use while the tunnel is up, null otherwise. */
        @Volatile
        var active: DnsServer? = null
            private set

        /** Why the running service keeps the tunnel closed (string resource), null otherwise. */
        @Volatile
        var pausedReason: Int? = null
            private set

        /** Service switched on (tunnel up or paused). */
        val running: Boolean get() = active != null || pausedReason != null

        /** UI callbacks (main thread) for state changes. */
        val listeners = CopyOnWriteArraySet<() -> Unit>()

        private fun publish(ctx: Context, server: DnsServer?, reason: Int?) {
            active = server
            pausedReason = reason
            TileService.requestListeningState(ctx, ComponentName(ctx, DnsTileService::class.java))
            Handler(Looper.getMainLooper()).post { listeners.forEach { it() } }
        }

        /** Caller must have checked [VpnService.prepare] == null. Also re-applies changed settings. */
        fun start(ctx: Context) {
            ctx.startForegroundService(Intent(ctx, DnsVpnService::class.java).setAction(ACTION_START))
        }

        fun stop(ctx: Context) {
            if (running) ctx.startService(Intent(ctx, DnsVpnService::class.java).setAction(ACTION_STOP))
        }
    }
}
