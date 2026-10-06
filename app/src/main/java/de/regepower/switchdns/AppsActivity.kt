package de.regepower.switchdns

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import java.util.Locale

/**
 * App selector from BootDelay (search, selected/available lists), without drag sorting, plus filter mode.
 * Framework ListView instead of RecyclerView: no AndroidX dependency (RecyclerView cost ~105 KB APK).
 */
class AppsActivity : Activity() {
    private class AppItem(
        val label: String,
        val pkg: String,
        val icon: Drawable,
        var selected: Boolean,
        // false = system app/service without launcher icon (e.g. Play Store update service)
        val launcher: Boolean
    )

    private lateinit var prefs: Prefs
    private lateinit var selectedHeader: TextView
    private val selectedAdapter = AppAdapter()
    private val availableAdapter = AppAdapter()
    private var all: List<AppItem> = emptyList()
    private val chosen = mutableSetOf<String>()
    private var query = ""
    private var changed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)

        val pad = px(8)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            fitsSystemWindows = true
        }

        val black = RadioButton(this).apply {
            id = ID_BLACK
            text = getString(R.string.mode_blacklist)
        }
        val white = RadioButton(this).apply {
            id = ID_WHITE
            text = getString(R.string.mode_whitelist)
        }
        val mode = RadioGroup(this).apply {
            addView(black)
            addView(white)
            check(if (prefs.whitelist) ID_WHITE else ID_BLACK)
            setOnCheckedChangeListener { _, id ->
                prefs.whitelist = id == ID_WHITE
                changed = true
            }
        }
        // No window title bar (theme): own bold title like the main screen.
        root.addView(
            TextView(this).apply {
                text = getString(R.string.header_apps)
                textSize = 22f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                setTextColor(getColor(R.color.md_on_container))
                setPadding(px(4), px(4), 0, px(4))
            }
        )
        root.addView(mode)
        root.addView(hint(getString(R.string.hint_apps)))

        val search = EditText(this).apply {
            hint = getString(R.string.search_hint)
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString().orEmpty().trim().lowercase(Locale.getDefault())
                    refreshLists()
                }
            })
        }
        root.addView(search)
        root.addView(
            Switch(this).apply {
                text = getString(R.string.show_system)
                textSize = 14f
                isChecked = prefs.showSystem
                tooltipText = getString(R.string.help_show_system)
                setPadding(px(4), px(4), px(4), px(4))
                setOnCheckedChangeListener { _, on ->
                    prefs.showSystem = on
                    refreshLists()
                }
            },
            fullWidth()
        )

        selectedHeader = header(getString(R.string.header_selected, 0))
        root.addView(selectedHeader)
        root.addView(appList(selectedAdapter), LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(header(getString(R.string.header_available)))
        root.addView(
            appList(availableAdapter),
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.4f)
        )
        setContentView(root)
        loadApps()
    }

    override fun onPause() {
        save()
        // Apply new filter to a running tunnel.
        if (changed && DnsVpnService.running) DnsVpnService.start(this)
        changed = false
        super.onPause()
    }

    private fun appList(a: AppAdapter) = ListView(this).apply {
        adapter = a
        divider = null
        setBackgroundResource(R.drawable.bg_card)
        clipToOutline = true
        val p = dp.toInt()
        setPadding(p, p, p, p)
        clipToPadding = true
        setOnItemClickListener { _, _, position, _ -> toggle(a.getItem(position)) }
    }

    private fun toggle(item: AppItem) {
        item.selected = !item.selected
        if (item.selected) chosen.add(item.pkg) else chosen.remove(item.pkg)
        changed = true
        save()
        refreshLists()
    }

    private fun refreshLists() {
        val byLabel = compareBy<AppItem> { it.label.lowercase(Locale.getDefault()) }
        selectedAdapter.set(all.filter { it.selected }.sortedWith(byLabel))
        selectedHeader.text = getString(R.string.header_selected, selectedAdapter.count)
        availableAdapter.set(
            all.filter { !it.selected && (it.launcher || prefs.showSystem) }
                .filter {
                    query.isEmpty() || it.label.lowercase(Locale.getDefault()).contains(query) || it.pkg.contains(query)
                }
                .sortedWith(byLabel)
        )
    }

    private fun save() {
        if (all.isNotEmpty()) prefs.packages = chosen.toList()
    }

    private fun loadApps() {
        chosen.clear()
        chosen.addAll(prefs.packages)
        val selected = chosen.toSet()
        Thread {
            // All installed packages; those with a launcher entry are "normal" apps, the rest only
            // appear when "show system apps" is on (selected ones are always listed).
            val pm = packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val launcherPkgs = pm.queryIntentActivities(launcherIntent, 0).map { it.activityInfo.packageName }.toSet()
            val items = pm.getInstalledApplications(0)
                .filter { it.packageName != packageName }
                .map {
                    AppItem(
                        it.loadLabel(pm).toString(),
                        it.packageName,
                        it.loadIcon(pm),
                        it.packageName in selected,
                        it.packageName in launcherPkgs
                    )
                }
            runOnUiThread {
                all = items
                chosen.retainAll(items.map { it.pkg }.toSet())
                refreshLists()
            }
        }.start()
    }

    private class Holder(val icon: ImageView, val name: TextView, val pkg: TextView, val check: CheckBox)

    private inner class AppAdapter : BaseAdapter() {
        private val items = mutableListOf<AppItem>()

        fun set(newItems: List<AppItem>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        override fun getCount() = items.size

        override fun getItem(position: Int) = items[position]

        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: newRow()
            val holder = row.tag as Holder
            val item = items[position]
            holder.icon.setImageDrawable(item.icon)
            holder.name.text = item.label
            holder.pkg.text = item.pkg
            holder.check.isChecked = item.selected
            return row
        }

        private fun newRow(): View {
            val p = px(6)
            val row = LinearLayout(this@AppsActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(p, p, p, p)
            }
            val size = px(36)
            val icon = ImageView(this@AppsActivity)
            row.addView(icon, LinearLayout.LayoutParams(size, size))
            val texts = LinearLayout(this@AppsActivity).apply { orientation = LinearLayout.VERTICAL }
            val name = TextView(this@AppsActivity).apply { textSize = 15f }
            val pkg = TextView(this@AppsActivity).apply {
                textSize = 10f
                setTextColor(getColor(R.color.md_on_surface_variant))
            }
            texts.addView(name)
            texts.addView(pkg)
            row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = px(10) })
            val check = CheckBox(this@AppsActivity).apply {
                isClickable = false
                isFocusable = false
            }
            row.addView(check)
            row.tag = Holder(icon, name, pkg, check)
            return row
        }
    }

    companion object {
        private const val ID_BLACK = 101
        private const val ID_WHITE = 102
    }
}
