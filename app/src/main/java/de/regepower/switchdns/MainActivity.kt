package de.regepower.switchdns

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.text.method.DigitsKeyListener
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var toggle: Button
    private lateinit var serverName: TextView
    private lateinit var serverAddr: TextView
    private lateinit var appsBtn: Button
    private lateinit var appsMode: TextView
    private val listener: () -> Unit = { renderState() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        // Config files saved by the app before it was renamed.
        AppShell.legacyNames = listOf("MinDNSChanger")

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16), px(8), px(16), px(16))
        }

        content.addView(
            AppShell.header(this, prefs.sp),
            fullWidth()
        )

        // DNS server card: header shows the state, tap the server = list, button below it.
        status = header(getString(R.string.header_server))
        content.addView(status)
        val serverCard = card()
        val info = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(4), px(4), px(4), px(4))
            setBackgroundResource(rowRipple())
            tooltipText = getString(R.string.hint_server)
            setOnClickListener { pickServer() }
        }
        serverName = TextView(this).apply {
            textSize = 17f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        serverAddr = TextView(this).apply {
            textSize = 13f
            setTextColor(getColor(R.color.md_on_surface_variant))
        }
        info.addView(serverName)
        info.addView(serverAddr)
        serverCard.addView(info, fullWidth())
        toggle = Button(this).apply { setOnClickListener { onToggle() } }
        serverCard.addView(toggle, fullWidth(8))
        content.addView(serverCard, fullWidth())
        content.addView(hint(getString(R.string.hint_tile)))

        // App filter
        content.addView(header(getString(R.string.header_apps)))
        appsMode = hint("")
        content.addView(appsMode)
        appsBtn = button("", null) { startActivity(Intent(this, AppsActivity::class.java)) }
        content.addView(appsBtn, fullWidth(4))

        // Options
        content.addView(header(getString(R.string.header_options)))
        val autostart = Switch(this).apply {
            text = getString(R.string.autostart)
            textSize = 15f
            isChecked = prefs.autostart
            setPadding(px(4), px(4), px(4), px(4))
            setOnCheckedChangeListener { _, on -> prefs.autostart = on }
        }
        content.addView(autostart, fullWidth())
        content.addView(optionSwitch(R.string.opt_mobile, prefs.onMobile) { prefs.onMobile = it })
        content.addView(optionSwitch(R.string.opt_wifi, prefs.onWifi) { prefs.onWifi = it })
        content.addView(optionSwitch(R.string.opt_captive, prefs.pauseCaptive) { prefs.pauseCaptive = it })
        content.addView(
            button(getString(R.string.btn_always_on), null) { startActivity(Intent(Settings.ACTION_VPN_SETTINGS)) },
            fullWidth(8)
        )
        content.addView(hint(getString(R.string.hint_always_on)))

        setContentView(
            ScrollView(this).apply {
                fitsSystemWindows = true
                addView(content)
            }
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY)
        }
        renderServer()
    }

    override fun onResume() {
        super.onResume()
        DnsVpnService.listeners.add(listener)
        renderState()
        val count = prefs.packages.size
        appsBtn.text = getString(R.string.btn_apps, count)
        appsMode.text = getString(if (prefs.whitelist) R.string.mode_whitelist else R.string.mode_blacklist)
    }

    override fun onPause() {
        DnsVpnService.listeners.remove(listener)
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VPN) {
            if (resultCode == RESULT_OK) {
                DnsVpnService.start(this)
            } else {
                Toast.makeText(this, R.string.err_vpn_denied, Toast.LENGTH_SHORT).show()
            }
            return
        }
        AppShell.onResult(this, requestCode, resultCode, data, prefs.sp) {
            if (DnsVpnService.running) DnsVpnService.start(this)
            recreate()
        }
    }

    /** Switch that stores [save] and re-applies the rules to a running service. */
    private fun optionSwitch(textRes: Int, checked: Boolean, save: (Boolean) -> Unit) = Switch(this).apply {
        text = getString(textRes)
        textSize = 15f
        isChecked = checked
        setPadding(px(4), px(4), px(4), px(4))
        layoutParams = fullWidth()
        setOnCheckedChangeListener { _, on ->
            save(on)
            if (DnsVpnService.running) DnsVpnService.start(this@MainActivity)
        }
    }

    private fun onToggle() {
        if (DnsVpnService.running) {
            DnsVpnService.stop(this)
            return
        }
        val consent = VpnService.prepare(this)
        if (consent != null) {
            @Suppress("DEPRECATION")
            startActivityForResult(consent, REQ_VPN)
        } else {
            DnsVpnService.start(this)
        }
    }

    private fun renderState() {
        val active = DnsVpnService.active
        val paused = DnsVpnService.pausedReason
        val state = when {
            active != null -> getString(R.string.state_on)
            paused != null -> getString(R.string.state_paused, getString(paused))
            else -> getString(R.string.state_off)
        }
        status.text = getString(R.string.header_server_state, getString(R.string.header_server), state)
        if (active != null || paused != null) {
            toggle.text = getString(R.string.btn_stop)
            styleButton(toggle, R.color.md_container, R.color.md_on_container)
        } else {
            toggle.text = getString(R.string.btn_start)
            styleButton(toggle, R.color.md_primary, R.color.md_on_primary)
        }
    }

    private fun renderServer() {
        val s = prefs.current()
        serverName.text = label(s)
        serverAddr.text = s.addresses
    }

    private fun selectServer(name: String) {
        prefs.selected = name
        renderServer()
        if (DnsVpnService.running) DnsVpnService.start(this)
    }

    /** Own entries are marked with a star. */
    private fun label(s: DnsServer) = if (s.custom) "$STAR ${s.name}" else s.name

    /**
     * All servers in a dialog: own entries (★) first, then the presets. Name on line 1, both
     * addresses on line 2 (condensed, shrinks to stay on one line). Long-press deletes own entries.
     */
    private fun pickServer() {
        val list = prefs.servers()
        val current = prefs.current().name
        val adapter = object : BaseAdapter() {
            override fun getCount() = list.size
            override fun getItem(position: Int) = list[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
                serverRow(list[position], list[position].name == current)
        }
        // Own scrollable list with a capped height, so the buttons always stay visible below it.
        val listView = ListView(this).apply {
            this.adapter = adapter
            divider = null
            isVerticalScrollBarEnabled = true
        }
        val row = serverRow(list[0], false).apply { measure(0, 0) }
        val maxHeight = (resources.displayMetrics.heightPixels * 0.55f).toInt()
        val height = minOf(row.measuredHeight * list.size, maxHeight)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                hint(getString(R.string.hint_list)).apply { setPadding(px(24), 0, px(24), px(4)) },
                fullWidth()
            )
            addView(listView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height))
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.header_server)
            .setView(box)
            .setNeutralButton(R.string.btn_add_server) { _, _ -> addServerDialog() }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        listView.setOnItemClickListener { _, _, position, _ ->
            selectServer(list[position].name)
            dialog.dismiss()
        }
        listView.setOnItemLongClickListener { _, _, position, _ ->
            val srv = list[position]
            if (srv.custom) {
                dialog.dismiss()
                deleteServerDialog(srv)
            } else {
                Toast.makeText(this, R.string.err_preset_delete, Toast.LENGTH_SHORT).show()
            }
            true
        }
        dialog.show()
        listView.setSelection(maxOf(0, list.indexOfFirst { it.name == current } - 2))
    }

    private fun serverRow(srv: DnsServer, checked: Boolean): View = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(px(20), px(6), px(20), px(6))
        addView(
            RadioButton(context).apply {
                isChecked = checked
                isClickable = false
                isFocusable = false
            }
        )
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(
            TextView(context).apply {
                text = label(srv)
                textSize = 17f
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
        )
        texts.addView(
            TextView(context).apply {
                text = srv.addresses
                maxLines = 1
                typeface = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
                setTextColor(getColor(R.color.md_on_surface_variant))
                // Long addresses (e.g. OpenDNS) shrink instead of wrapping.
                setAutoSizeTextTypeUniformWithConfiguration(10, 14, 1, TypedValue.COMPLEX_UNIT_SP)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(20))
        )
        addView(
            texts,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart =
                    px(12)
            }
        )
    }

    private fun addServerDialog() {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(20), px(8), px(20), 0)
        }
        fun field(hintRes: Int, ip: Boolean) = EditText(this).apply {
            hint = getString(hintRes)
            setSingleLine()
            if (ip) {
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                keyListener = DigitsKeyListener.getInstance("0123456789.")
            } else {
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            }
            form.addView(this)
        }
        val name = field(R.string.field_name, false)
        val primary = field(R.string.field_primary, true)
        val secondary = field(R.string.field_secondary, true)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.dlg_add_title)
            .setView(form)
            .setPositiveButton(R.string.btn_save, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        // Validate before closing.
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val n = name.text.toString().trim()
            val p = primary.text.toString().trim()
            val s = secondary.text.toString().trim()
            val names = prefs.servers().map { it.name.lowercase() }
            var ok = true
            if (n.isEmpty() || n.contains('\t') || n.lowercase() in names) {
                name.error = getString(R.string.err_name)
                ok = false
            }
            if (!DnsServer.isIpv4(p)) {
                primary.error = getString(R.string.err_ip)
                ok = false
            }
            if (s.isNotEmpty() && !DnsServer.isIpv4(s)) {
                secondary.error = getString(R.string.err_ip)
                ok = false
            }
            if (!ok) return@setOnClickListener
            prefs.custom = prefs.custom + DnsServer(n, p, s.ifEmpty { null }, true)
            selectServer(n)
            dialog.dismiss()
        }
    }

    private fun deleteServerDialog(s: DnsServer) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.dlg_delete_title, s.name))
            .setPositiveButton(R.string.btn_delete) { _, _ ->
                val wasActive = prefs.current().name == s.name
                prefs.custom = prefs.custom.filter { it.name != s.name }
                if (wasActive) selectServer(DnsServer.PRESETS[0].name)
                pickServer()
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> pickServer() }
            .show()
    }

    companion object {
        private const val STAR = "★"
        private const val REQ_VPN = 1
        private const val REQ_NOTIFY = 2
    }
}
