package de.regepower.switchdns

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/** Quick-settings toggle; opens the app when the VPN permission has not been granted yet. */
class DnsTileService : TileService() {
    private val listener: () -> Unit = { update() }

    override fun onTileAdded() {
        Prefs(this).tileAdded = true
    }

    override fun onTileRemoved() {
        Prefs(this).tileAdded = false
    }

    override fun onStartListening() {
        DnsVpnService.listeners.add(listener)
        update()
    }

    override fun onStopListening() {
        DnsVpnService.listeners.remove(listener)
    }

    override fun onClick() {
        when {
            DnsVpnService.running -> DnsVpnService.stop(this)
            VpnService.prepare(this) != null -> openApp()
            else -> DnsVpnService.start(this)
        }
    }

    private fun update() {
        val tile = qsTile ?: return
        val server = DnsVpnService.active
        tile.state = if (DnsVpnService.running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = when {
            server != null -> server.name
            DnsVpnService.pausedReason != null -> getString(R.string.tile_paused)
            else -> getString(R.string.state_off)
        }
        tile.updateTile()
    }

    // The Intent variant is only used below API 34, where it still works.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
