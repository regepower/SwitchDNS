package de.regepower.switchdns

import android.annotation.SuppressLint
import android.app.Activity
import android.net.ConnectivityManager
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

/**
 * Hotspot login page (like the system's captive-portal login, which HyperOS hides).
 * The process is bound to the captive Wi-Fi network, otherwise the page would load over
 * mobile data and the portal never shows. Closes itself once the network is validated.
 */
class LoginActivity : Activity() {
    private var web: WebView? = null
    private val listener: () -> Unit = {
        if (DnsVpnService.captiveNetwork == null) {
            Toast.makeText(this, R.string.login_done, Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    // Portal pages need JavaScript; the page only ever shows the hotspot's own login.
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val network = DnsVpnService.captiveNetwork
        if (network == null) {
            finish()
            return
        }
        getSystemService(ConnectivityManager::class.java).bindProcessToNetwork(network)
        val w = WebView(this).apply {
            fitsSystemWindows = true
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
        }
        web = w
        setContentView(w)
        w.loadUrl(PROBE_URL)
    }

    override fun onResume() {
        super.onResume()
        DnsVpnService.listeners.add(listener)
        listener()
    }

    override fun onPause() {
        DnsVpnService.listeners.remove(listener)
        super.onPause()
    }

    override fun onDestroy() {
        getSystemService(ConnectivityManager::class.java).bindProcessToNetwork(null)
        web?.destroy()
        super.onDestroy()
    }

    companion object {
        /** Plain HTTP on purpose: hotspots redirect it to their login page (same probe as Android). */
        private const val PROBE_URL = "http://connectivitycheck.gstatic.com/generate_204"
    }
}
