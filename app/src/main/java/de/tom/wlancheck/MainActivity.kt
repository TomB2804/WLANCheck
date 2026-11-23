package de.tom.wlancheck

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

@SuppressLint("MissingPermission", "SetTextI18n")
class MainActivity : Activity() {

    private lateinit var wifi: WifiManager
    private lateinit var cm: ConnectivityManager
    private lateinit var prefs: SharedPreferences
    private val ui = Handler(Looper.getMainLooper())
    private val io = Executors.newCachedThreadPool()

    private lateinit var tvSignal: TextView
    private lateinit var tvConn: TextView
    private lateinit var tvDiag: TextView
    private lateinit var tvTests: TextView
    private lateinit var tvScanInfo: TextView
    private lateinit var tvChannels: TextView
    private lateinit var scanList: LinearLayout
    private lateinit var btnLive: Button
    private lateinit var bandGroup: RadioGroup

    private var lastResults: List<ScanResult> = emptyList()
    private var receiverRegistered = false
    private var live = false
    private var liveMin = 0
    private var liveMax = -200

    private val liveTick = object : Runnable {
        override fun run() {
            if (!live) return
            updateConnection()
            ui.postDelayed(this, 1000)
        }
    }

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val updated = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)
            showScanResults(if (updated) null else "Android hat den Scan gedrosselt – zeige letzte Ergebnisse.")
            updateConnection()
        }
    }

    // ---------------------------------------------------------------- Lifecycle

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        prefs = getSharedPreferences("ap_namen", MODE_PRIVATE)

        tvSignal = findViewById(R.id.tvSignal)
        tvConn = findViewById(R.id.tvConn)
        tvDiag = findViewById(R.id.tvDiag)
        tvTests = findViewById(R.id.tvTests)
        tvScanInfo = findViewById(R.id.tvScanInfo)
        tvChannels = findViewById(R.id.tvChannels)
        scanList = findViewById(R.id.scanList)
        btnLive = findViewById(R.id.btnLive)
        bandGroup = findViewById(R.id.bandGroup)

        findViewById<Button>(R.id.btnRefresh).setOnClickListener { updateConnection() }
        findViewById<Button>(R.id.btnPing).setOnClickListener { runPing() }
        findViewById<Button>(R.id.btnSpeed).setOnClickListener { runSpeedtest() }
        findViewById<Button>(R.id.btnScan).setOnClickListener { startScan() }
        btnLive.setOnClickListener { toggleLive() }
        bandGroup.setOnCheckedChangeListener { _, _ -> showScanResults(null) }

        if (!hasPermissions()) requestPermissions(neededPermissions(), 1)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(scanReceiver, filter)
        }
        receiverRegistered = true
        if (hasPermissions()) showScanResults(null)
        updateConnection()
    }

    override fun onPause() {
        if (live) toggleLive()
        if (receiverRegistered) {
            unregisterReceiver(scanReceiver)
            receiverRegistered = false
        }
        super.onPause()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- Berechtigungen

    private fun neededPermissions(): Array<String> {
        val p = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) p += Manifest.permission.NEARBY_WIFI_DEVICES
        return p.toTypedArray()
    }

    private fun hasPermissions() =
        neededPermissions().all { checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateConnection()
        if (hasPermissions()) startScan()
        else toast("Ohne Standort-Berechtigung zeigt Android keinen Netznamen und keine Scans.")
    }

    @Suppress("DEPRECATION")
    private fun locationEnabled(): Boolean {
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled
        else lm.isProviderEnabled(LocationManager.GPS_PROVIDER) || lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    // ---------------------------------------------------------------- Verbindung

    @Suppress("DEPRECATION")
    private fun updateConnection() {
        if (!wifi.isWifiEnabled) {
            tvSignal.text = "WLAN ist ausgeschaltet"
            tvSignal.setTextColor(Color.GRAY)
            tvConn.text = "—"
            tvDiag.text = "Schalte WLAN ein, um zu messen."
            return
        }
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val onWifi = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val info = wifi.connectionInfo
        if (!onWifi || info == null) {
            tvSignal.text = "Nicht mit einem WLAN verbunden"
            tvSignal.setTextColor(Color.GRAY)
            tvConn.text = "—"
            tvDiag.text = "Verbinde dich mit deinem WLAN. Der Scan unten funktioniert trotzdem."
            return
        }

        val rssi = info.rssi
        val freq = info.frequency
        var ssid = info.ssid?.removeSurrounding("\"") ?: "?"
        if (ssid == "<unknown ssid>") {
            ssid = if (!locationEnabled()) "(unbekannt – Standort einschalten)" else "(unbekannt – Berechtigung fehlt)"
        }
        val bssid = currentBssid()
        val apLabel = bssid?.let { apName(it) }

        val lp = cm.getLinkProperties(cm.activeNetwork)
        val ip = lp?.linkAddresses?.map { it.address }?.firstOrNull { it is Inet4Address }?.hostAddress
        val dns = lp?.dnsServers?.mapNotNull { it.hostAddress }?.joinToString(", ").orEmpty()

        // Signal groß anzeigen
        var signal = "$rssi dBm · ${quality(rssi)} % · ${rating(rssi)}"
        if (live) {
            liveMin = minOf(liveMin, rssi)
            liveMax = maxOf(liveMax, rssi)
            signal += "\nLive  min $liveMin / max $liveMax dBm"
        }
        tvSignal.text = signal
        tvSignal.setTextColor(signalColor(rssi))

        val sb = StringBuilder()
        sb.appendLine("Netz (SSID):  $ssid")
        sb.appendLine("Access Point: ${bssid ?: "unbekannt"}")
        if (apLabel != null) sb.appendLine("              = $apLabel")
        sb.appendLine("Band/Kanal:   ${band(freq)} · Kanal ${channel(freq)} ($freq MHz)")
        sb.appendLine("Link-Speed:   ${info.linkSpeed} Mbit/s")
        if (Build.VERSION.SDK_INT >= 29) {
            sb.appendLine("Senden/Empf.: ${info.txLinkSpeedMbps} / ${info.rxLinkSpeedMbps} Mbit/s")
        }
        if (Build.VERSION.SDK_INT >= 30) {
            sb.appendLine("Standard:     ${wifiStandard(info.wifiStandard)}")
        }
        sb.appendLine("IP-Adresse:   ${ip ?: "—"}")
        sb.appendLine("Router:       ${gatewayAddress() ?: "—"}")
        sb.append("DNS:          ${dns.ifEmpty { "—" }}")
        tvConn.text = sb.toString()

        tvDiag.text = diagnose(rssi, freq, ssid, bssid, info.linkSpeed)
    }

    @Suppress("DEPRECATION")
    private fun currentBssid(): String? =
        wifi.connectionInfo?.bssid?.lowercase()?.takeIf { it != "02:00:00:00:00:00" }

    private fun gatewayAddress(): String? {
        val lp = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
    }

    // ---------------------------------------------------------------- Diagnose

    @Suppress("DEPRECATION")
    private fun diagnose(rssi: Int, freq: Int, ssid: String, bssid: String?, linkSpeed: Int): String {
        val tips = mutableListOf<String>()

        when {
            rssi < -80 -> tips += "⚠ Sehr schwaches Signal. Näher an den Access Point gehen oder einen AP/Repeater näher platzieren."
            rssi < -70 -> tips += "⚠ Signal nur mäßig – bei Videocalls oder Kameras kann es ruckeln."
        }
        if (linkSpeed in 1..19) tips += "⚠ Niedrige Link-Geschwindigkeit ($linkSpeed Mbit/s)."

        val same = lastResults.filter { it.SSID == ssid }
        if (same.isNotEmpty()) {
            val aps = same.map { it.BSSID.lowercase() }.toSet()
            if (aps.size > 1) {
                tips += "ℹ ${aps.size} Sender strahlen „$ssid“ aus (mehrere Access Points/Bänder). " +
                        "Tippe unten auf einen Eintrag, um ihn zu benennen (z. B. FRITZ!Box, Xiaomi, Repeater)."
            }
            val best = same.maxByOrNull { it.level }
            if (best != null && bssid != null && !best.BSSID.equals(bssid, true) && best.level - rssi >= 10) {
                val n = apName(best.BSSID) ?: best.BSSID
                tips += "⚠ Ein stärkerer Sender desselben Netzes ist verfügbar: $n mit ${best.level} dBm " +
                        "(${band(best.frequency)}). Dein Gerät „klebt“ am schwächeren – WLAN kurz aus/an schalten."
            }
            if (freq < 3000 && same.any { it.frequency in 5000..5925 && it.level > -70 }) {
                tips += "ℹ Du bist im 2,4-GHz-Band, obwohl das Netz auch auf 5 GHz gut empfangbar ist. 5 GHz ist meist schneller."
            }
        }

        if (freq < 3000 && lastResults.isNotEmpty()) {
            val myCh = channel(freq)
            val neighbours = lastResults.count {
                it.frequency < 3000 && abs(channel(it.frequency) - myCh) <= 4 && !it.BSSID.equals(bssid, true)
            }
            if (neighbours >= 4) {
                tips += "⚠ Kanal $myCh ist mit $neighbours anderen Netzen stark belegt. Empfehlung siehe Kanalübersicht unten."
            }
        }

        if (lastResults.isEmpty()) tips += "ℹ Für eine genauere Diagnose unten „Jetzt scannen“ tippen."
        if (tips.none { it.startsWith("⚠") }) tips.add(0, "✓ Verbindung sieht gut aus.")
        return tips.joinToString("\n\n")
    }

    // ---------------------------------------------------------------- Live

    private fun toggleLive() {
        live = !live
        btnLive.text = if (live) "Live stoppen" else "Live-Messung"
        if (live) {
            liveMin = 0
            liveMax = -200
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            toast("Geh durch die Räume – die Anzeige aktualisiert sich jede Sekunde.")
            ui.post(liveTick)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            ui.removeCallbacks(liveTick)
        }
    }

    // ---------------------------------------------------------------- Scan

    @Suppress("DEPRECATION")
    private fun startScan() {
        if (!hasPermissions()) {
            requestPermissions(neededPermissions(), 1)
            return
        }
        if (!locationEnabled()) toast("Bitte Standort einschalten – sonst liefert Android keine Scan-Ergebnisse.")
        val started = wifi.startScan()
        if (started) {
            tvScanInfo.text = "Scanne …"
        } else {
            showScanResults("Android erlaubt nur 4 Scans in 2 Minuten – zeige letzte Ergebnisse.")
        }
    }

    @Suppress("DEPRECATION")
    private fun showScanResults(status: String?) {
        if (!hasPermissions()) {
            tvScanInfo.text = "Für den Scan braucht die App die Standort-Berechtigung."
            return
        }
        lastResults = try { wifi.scanResults ?: emptyList() } catch (e: SecurityException) { emptyList() }

        val filterBand = when (bandGroup.checkedRadioButtonId) {
            R.id.rb24 -> "2,4 GHz"
            R.id.rb5 -> "5 GHz"
            R.id.rb6 -> "6 GHz"
            else -> null
        }
        val list = lastResults
            .filter { filterBand == null || band(it.frequency) == filterBand }
            .sortedByDescending { it.level }
        val connected = currentBssid()

        tvScanInfo.text = (status ?: "${list.size} Sender gefunden.") + " Antippen = Access Point benennen."
        tvChannels.text = channelReport(lastResults)

        scanList.removeAllViews()
        for (r in list) {
            val isMine = r.BSSID.equals(connected, true)
            val name = r.SSID.ifBlank { "(verstecktes Netz)" }
            val label = apName(r.BSSID)?.let { "  ★ $it" } ?: ""
            val tv = TextView(this).apply {
                text = (if (isMine) "● VERBUNDEN · " else "") + name + "\n" +
                        "${r.level} dBm (${quality(r.level)} %) · ${band(r.frequency)} · Kanal ${channel(r.frequency)} · ${security(r.capabilities)}\n" +
                        r.BSSID + label
                textSize = 14f
                setTextColor(Color.parseColor("#212121"))
                setBackgroundColor(if (isMine) Color.parseColor("#DCEBFF") else Color.parseColor("#F2F5FA"))
                setPadding(28, 20, 28, 20)
                setOnClickListener { renameAp(r.BSSID) }
            }
            val bar = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(signalColor(r.level))
                setPadding(12, 0, 0, 0)
                addView(tv, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            }
            val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 10
            scanList.addView(bar, lp)
        }
    }

    private fun channelReport(results: List<ScanResult>): String {
        if (results.isEmpty()) return "Noch keine Scan-Ergebnisse."
        val sb = StringBuilder()
        val r24 = results.filter { it.frequency < 3000 }
        if (r24.isNotEmpty()) {
            sb.appendLine("2,4 GHz – Sender pro Kanal:")
            for (c in 1..13) {
                val n = r24.count { channel(it.frequency) == c }
                if (n > 0) sb.appendLine("Kanal ${c.toString().padStart(2)} ${"█".repeat(n)} $n")
            }
            val best = listOf(1, 6, 11).minBy { c ->
                r24.filter { abs(channel(it.frequency) - c) <= 4 }.sumOf { (it.level + 100).coerceAtLeast(1) }
            }
            sb.appendLine("→ Empfehlung 2,4 GHz: Kanal $best (am wenigsten Überlappung)")
        }
        val r5 = results.filter { it.frequency in 5000..5925 }
        if (r5.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("5 GHz – Sender pro Kanal:")
            r5.groupBy { channel(it.frequency) }.toSortedMap().forEach { (c, l) ->
                sb.appendLine("Kanal ${c.toString().padStart(3)} ${"█".repeat(l.size)} ${l.size}")
            }
        }
        return sb.toString().trimEnd()
    }

    private fun renameAp(bssid: String) {
        val input = EditText(this).apply {
            setText(apName(bssid) ?: "")
            hint = "z. B. FRITZ!Box Keller"
        }
        AlertDialog.Builder(this)
            .setTitle("Access Point benennen")
            .setMessage(bssid)
            .setView(input)
            .setPositiveButton("Speichern") { _, _ ->
                prefs.edit().putString(bssid.lowercase(), input.text.toString().trim()).apply()
                showScanResults(null)
                updateConnection()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun apName(bssid: String): String? =
        prefs.getString(bssid.lowercase(), null)?.takeIf { it.isNotBlank() }

    // ---------------------------------------------------------------- Ping & Speedtest

    private fun runPing() {
        val gw = gatewayAddress()
        tvTests.text = "Ping läuft (ca. 10 Sekunden) …"
        io.execute {
            val router = if (gw != null) "Router ($gw):\n" + ping(gw, lan = true) else "Router: keine Adresse gefunden"
            val inet = "Internet (1.1.1.1):\n" + ping("1.1.1.1", lan = false)
            ui.post { tvTests.text = "$router\n\n$inet" }
        }
    }

    private fun ping(host: String, lan: Boolean): String = try {
        val p = ProcessBuilder("ping", "-c", "10", "-i", "0.5", "-W", "2", host)
            .redirectErrorStream(true).start()
        val out = p.inputStream.bufferedReader().use { it.readText() }
        p.waitFor()
        val loss = Regex("""([\d.]+)% packet loss""").find(out)?.groupValues?.get(1)?.toDoubleOrNull()
        val rtt = Regex("""= ([\d.]+)/([\d.]+)/([\d.]+)""").find(out)
        if (rtt == null) {
            "keine Antwort (Verlust ${loss ?: 100.0} %)"
        } else {
            val avg = rtt.groupValues[2].toDouble()
            val verdict = when {
                (loss ?: 0.0) > 2 -> "⚠ Paketverlust"
                lan && avg > 30 -> "⚠ hohe Latenz für das Heimnetz"
                !lan && avg > 80 -> "⚠ hohe Latenz"
                else -> "✓ gut"
            }
            "Ø ${rtt.groupValues[2]} ms (min ${rtt.groupValues[1]}, max ${rtt.groupValues[3]}) · Verlust ${loss ?: 0.0} % · $verdict"
        }
    } catch (e: Exception) {
        "Fehler: ${e.message}"
    }

    private fun runSpeedtest() {
        tvTests.text = "Speedtest läuft (bis 25 MB, max. 15 s) …"
        io.execute {
            val txt = try {
                val conn = URL("https://speed.cloudflare.com/__down?bytes=25000000").openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 15000
                val buf = ByteArray(64 * 1024)
                var total = 0L
                val t0 = System.nanoTime()
                conn.inputStream.use { s ->
                    while (true) {
                        val n = s.read(buf)
                        if (n < 0) break
                        total += n
                        if (System.nanoTime() - t0 > 15_000_000_000L) break
                    }
                }
                conn.disconnect()
                val sec = (System.nanoTime() - t0) / 1e9
                val mbps = total * 8 / sec / 1e6
                String.format(Locale.GERMANY, "Download: %.1f Mbit/s\n(%.1f MB in %.1f s – misst WLAN + Internetanschluss)", mbps, total / 1e6, sec)
            } catch (e: Exception) {
                "Speedtest fehlgeschlagen: ${e.message}"
            }
            ui.post { tvTests.text = txt }
        }
    }

    // ---------------------------------------------------------------- Hilfsfunktionen

    private fun channel(f: Int): Int = when {
        f == 2484 -> 14
        f in 2412..2472 -> (f - 2407) / 5
        f in 5160..5925 -> (f - 5000) / 5
        f in 5955..7115 -> (f - 5950) / 5
        else -> -1
    }

    private fun band(f: Int) = when {
        f < 3000 -> "2,4 GHz"
        f < 5950 -> "5 GHz"
        else -> "6 GHz"
    }

    private fun quality(rssi: Int) = ((rssi + 100) * 2).coerceIn(0, 100)

    private fun rating(rssi: Int) = when {
        rssi >= -55 -> "sehr gut"
        rssi >= -67 -> "gut"
        rssi >= -75 -> "mäßig"
        rssi >= -85 -> "schwach"
        else -> "sehr schwach"
    }

    private fun signalColor(rssi: Int) = when {
        rssi >= -67 -> Color.parseColor("#2E7D32")
        rssi >= -75 -> Color.parseColor("#F9A825")
        else -> Color.parseColor("#C62828")
    }

    private fun security(caps: String) = when {
        caps.contains("SAE") || caps.contains("WPA3") -> "WPA3"
        caps.contains("RSN") || caps.contains("WPA2") -> "WPA2"
        caps.contains("WPA") -> "WPA (alt)"
        caps.contains("WEP") -> "WEP ⚠ unsicher"
        else -> "offen ⚠"
    }

    private fun wifiStandard(s: Int) = when (s) {
        1 -> "802.11a/b/g"
        4 -> "Wi-Fi 4 (802.11n)"
        5 -> "Wi-Fi 5 (802.11ac)"
        6 -> "Wi-Fi 6 (802.11ax)"
        8 -> "Wi-Fi 7 (802.11be)"
        else -> "unbekannt"
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
