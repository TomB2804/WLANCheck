package de.tom.wlancheck

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Info-Seite: App-Daten und ein kleines WLAN-Wiki. */
class InfoActivity : Activity() {

    private lateinit var root: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Info & WLAN-Wiki"
        actionBar?.setDisplayHomeAsUpEnabled(true)

        val dp = resources.displayMetrics.density
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt())
        }
        setContentView(ScrollView(this).apply { addView(root) })

        // ---------------------------------------------------------- App-Info
        section("Über die App")
        card(
            "App:          WLAN-Check\n" +
            "Version:      ${versionName()}\n" +
            "Stand:        ${AppInfo.DATE}\n" +
            "Ersteller:    ${AppInfo.AUTHOR}\n" +
            "Programmiert: mit Claude (Anthropic)\n" +
            "Quellcode:    github.com/TomB2804/WLANCheck",
            mono = true
        )

        // ---------------------------------------------------------- Wiki
        section("Signalstärke (dBm)")
        card(
            "dBm gibt an, wie stark das WLAN-Signal beim Handy ankommt. " +
            "Die Werte sind negativ: je näher an 0, desto besser.\n\n" +
            "  -30 bis -55   sehr gut\n" +
            "  -55 bis -67   gut (Video, Telefonie ok)\n" +
            "  -67 bis -75   mäßig (Surfen ok)\n" +
            "  -75 bis -85   schwach (Abbrüche möglich)\n" +
            "  unter -85     sehr schwach\n\n" +
            "Schon 3 dB weniger bedeuten die halbe Signalleistung. " +
            "Wände, Decken (Stahlbeton!), Fußbodenheizung und Spiegel dämpfen stark."
        )

        section("2,4 GHz oder 5 GHz?")
        card(
            "2,4 GHz: größere Reichweite, geht besser durch Wände, aber langsamer " +
            "und oft überfüllt (Nachbarn, Bluetooth, Mikrowelle).\n\n" +
            "5 GHz: deutlich schneller und weniger gestört, aber kürzere Reichweite.\n\n" +
            "Faustregel: In der Nähe des Access Points 5 GHz, weit weg 2,4 GHz. " +
            "Viele Smart-Home-Geräte (Kameras, Steckdosen, Mähroboter) können nur 2,4 GHz."
        )

        section("Kanäle")
        card(
            "Auf 2,4 GHz überlappen sich benachbarte Kanäle. Nur 1, 6 und 11 stören sich " +
            "nicht gegenseitig – eigene Geräte sollten auf verschiedenen davon funken.\n\n" +
            "Auf 5 GHz gibt es viel mehr Kanäle (36–64, 100–140). Kanäle ab 52 sind " +
            "„DFS“-Kanäle: Erkennt der Router ein Radar, wechselt er kurz den Kanal.\n\n" +
            "Die Kanalübersicht im Scan zeigt, wie voll es ist, und empfiehlt den besten 2,4-GHz-Kanal."
        )

        section("SSID, BSSID & Access Point")
        card(
            "SSID = der Name des WLANs (z. B. TP-LINK_5400).\n\n" +
            "BSSID = die Hardware-Adresse des einzelnen Senders. Senden mehrere Geräte " +
            "dieselbe SSID, unterscheidet man sie an der BSSID. Ein Router hat je Band eine eigene BSSID.\n\n" +
            "Tipp: Im Scan einen Eintrag antippen und benennen (z. B. „AC2350 oben 5 GHz“). " +
            "Dann zeigt die App immer, an welchem Gerät du hängst."
        )

        section("Access Point vs. Repeater")
        card(
            "Access Point: per LAN-Kabel angebunden – volle Geschwindigkeit, keine Verluste. " +
            "Die beste Lösung.\n\n" +
            "Repeater: empfängt das WLAN per Funk und sendet es weiter. Jedes Paket wird " +
            "doppelt gefunkt, das halbiert die Leistung. Steht er zu weit weg, gibt es " +
            "Paketverluste und hohe Latenz.\n\n" +
            "Mesh: mehrere Geräte arbeiten zusammen und lenken Handys automatisch zum besten Sender."
        )

        section("Link-Speed")
        card(
            "Der Link-Speed ist die Brutto-Datenrate zwischen Handy und Access Point. " +
            "Real kommen etwa 50–70 % davon an. Er schwankt mit dem Signal und " +
            "sagt nichts über den Internetanschluss aus."
        )

        section("Ping, Latenz & Paketverlust")
        card(
            "Latenz (Ping) = Antwortzeit in Millisekunden.\n" +
            "  Zum Router:     unter 10 ms gut, über 30 ms schlecht\n" +
            "  Ins Internet:   unter 30 ms gut, über 80 ms schlecht\n\n" +
            "Paketverlust = Anteil verlorener Pakete. Ideal 0 %, ab 2 % merkt man es " +
            "(Ruckeln, Abbrüche). Hoher Verlust trotz gutem Signal deutet auf Störungen, " +
            "einen überlasteten Kanal oder einen schlecht angebundenen Repeater hin."
        )

        section("Speedtest")
        card(
            "Misst die Download-Geschwindigkeit über Cloudflare (bis 25 MB). " +
            "Das Ergebnis zeigt WLAN und Internetanschluss zusammen – das langsamere " +
            "von beiden begrenzt. Mehrmals und an verschiedenen Stellen messen."
        )

        section("Live-Messung")
        card(
            "Aktualisiert jede Sekunde und merkt sich Minimum und Maximum. " +
            "Damit durch die Räume gehen und Funklöcher finden. Der Bildschirm bleibt dabei an."
        )

        section("Warum Standort-Berechtigung?")
        card(
            "Android gibt WLAN-Namen und Scan-Ergebnisse nur heraus, wenn die App die " +
            "Standort-Berechtigung hat und der Standort eingeschaltet ist. Die App " +
            "speichert oder sendet keinen Standort. Scans sind von Android auf 4 pro 2 Minuten begrenzt."
        )

        footer("Gerät: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun versionName(): String = try {
        @Suppress("DEPRECATION")
        val info = packageManager.getPackageInfo(packageName, 0)
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
        "${info.versionName} (Build $code)"
    } catch (e: Exception) {
        "?"
    }

    private fun section(text: String) {
        val dp = resources.displayMetrics.density
        root.addView(TextView(this).apply {
            this.text = text
            textSize = 18f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.parseColor("#1565C0"))
            setPadding(0, (18 * dp).toInt(), 0, (6 * dp).toInt())
        })
    }

    private fun card(text: String, mono: Boolean = false) {
        val dp = resources.displayMetrics.density
        root.addView(TextView(this).apply {
            this.text = text
            textSize = if (mono) 13f else 14f
            if (mono) typeface = Typeface.MONOSPACE
            setTextColor(Color.parseColor("#212121"))
            setBackgroundColor(Color.parseColor("#F2F5FA"))
            setLineSpacing(0f, 1.15f)
            val p = (12 * dp).toInt()
            setPadding(p, p, p, p)
        })
    }

    private fun footer(text: String) {
        val dp = resources.displayMetrics.density
        root.addView(TextView(this).apply {
            this.text = text
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, (20 * dp).toInt(), 0, 0)
        })
    }
}

/** Zentrale App-Daten – bei neuen Versionen hier und in app/build.gradle.kts anpassen. */
object AppInfo {
    const val DATE = "23.11.2026"
    const val AUTHOR = "Tom (TomB2804)"
}
