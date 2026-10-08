package de.regepower.switchdns

import android.content.Context
import android.content.SharedPreferences

class Prefs(context: Context) {
    /** Raw store, also used for config export/import. */
    val sp: SharedPreferences = context.getSharedPreferences("switchdns", Context.MODE_PRIVATE)

    /** Name of the selected DNS entry. */
    var selected: String
        get() = sp.getString(KEY_SELECTED, null) ?: DnsServer.PRESETS[0].name
        set(v) = sp.edit().putString(KEY_SELECTED, v).apply()

    /** User-defined entries, one per line: name TAB primary TAB secondary. */
    var custom: List<DnsServer>
        get() = sp.getString(KEY_CUSTOM, "").orEmpty().split('\n').mapNotNull { line ->
            val f = line.split('\t')
            if (f.size < 2 || f[0].isBlank()) {
                null
            } else {
                DnsServer(f[0], f[1], f.getOrNull(2)?.takeIf { it.isNotBlank() }, true)
            }
        }
        set(v) = sp.edit().putString(
            KEY_CUSTOM,
            v.joinToString("\n") { "${it.name}\t${it.primary}\t${it.secondary.orEmpty()}" }
        ).apply()

    /** true: only the selected apps use the DNS; false: all apps except the selected ones. */
    var whitelist: Boolean
        get() = sp.getBoolean(KEY_WHITELIST, false)
        set(v) = sp.edit().putBoolean(KEY_WHITELIST, v).apply()

    var packages: List<String>
        get() = sp.getString(KEY_PKGS, "").orEmpty().split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString(KEY_PKGS, v.joinToString("\n")).apply()

    var autostart: Boolean
        get() = sp.getBoolean(KEY_AUTOSTART, false)
        set(v) = sp.edit().putBoolean(KEY_AUTOSTART, v).apply()

    var onMobile: Boolean
        get() = sp.getBoolean(KEY_MOBILE, true)
        set(v) = sp.edit().putBoolean(KEY_MOBILE, v).apply()

    var onWifi: Boolean
        get() = sp.getBoolean(KEY_WIFI, true)
        set(v) = sp.edit().putBoolean(KEY_WIFI, v).apply()

    /** Close the tunnel while a Wi-Fi network waits for a captive-portal login (e.g. train hotspots). */
    var pauseCaptive: Boolean
        get() = sp.getBoolean(KEY_CAPTIVE, true)
        set(v) = sp.edit().putBoolean(KEY_CAPTIVE, v).apply()

    /** App selector also lists system apps without launcher icon. */
    var showSystem: Boolean
        get() = sp.getBoolean(KEY_SHOW_SYSTEM, false)
        set(v) = sp.edit().putBoolean(KEY_SHOW_SYSTEM, v).apply()

    /** Set by the tile service; hides the "add tile" button. */
    var tileAdded: Boolean
        get() = sp.getBoolean(KEY_TILE_ADDED, false)
        set(v) = sp.edit().putBoolean(KEY_TILE_ADDED, v).apply()

    /** Own entries first, then the presets. */
    fun servers(): List<DnsServer> = custom + DnsServer.PRESETS

    fun current(): DnsServer = servers().firstOrNull { it.name == selected } ?: DnsServer.PRESETS[0]

    companion object {
        private const val KEY_SELECTED = "selected"
        private const val KEY_CUSTOM = "custom"
        private const val KEY_WHITELIST = "whitelist"
        private const val KEY_PKGS = "pkgs"
        private const val KEY_AUTOSTART = "autostart"
        private const val KEY_MOBILE = "on_mobile"
        private const val KEY_WIFI = "on_wifi"
        private const val KEY_CAPTIVE = "pause_captive"
        private const val KEY_SHOW_SYSTEM = "show_system"
        const val KEY_TILE_ADDED = "tile_added"
    }
}
