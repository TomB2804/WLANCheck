# WLAN-Check (Android)

Prüft dein WLAN direkt am Handy:

- **Aktuelle Verbindung:** Signal (dBm, %, Bewertung), SSID, Access Point (BSSID), Band, Kanal, Link-Speed, WLAN-Standard, IP, Router, DNS
- **Live-Messung:** aktualisiert jede Sekunde mit Min/Max – zum Durchlaufen der Räume und Finden von Funklöchern
- **Diagnose:** schwaches Signal, „Kleben“ am schwächeren Access Point, 2,4 statt 5 GHz, überlasteter Kanal
- **Ping-Test:** Router und Internet (Latenz, Paketverlust)
- **Speedtest:** Download über Cloudflare
- **Scan:** alle WLANs in der Nähe, Filter nach Band, Kanalbelegung mit Kanal-Empfehlung für 2,4 GHz
- **Access Points benennen:** Eintrag antippen → z. B. „FRITZ!Box Keller“, „Xiaomi“, „Repeater“. Praktisch, wenn mehrere Geräte dieselbe SSID senden.

Android 8.0 oder neuer. Die App braucht die Standort-Berechtigung und eingeschalteten Standort – ohne die zeigt Android keinen Netznamen und keine Scan-Ergebnisse. Es werden keine Daten verschickt (außer dem Speedtest-Download und den Pings).

## APK bauen

**Variante A – Android Studio (PC):** Ordner öffnen → warten bis Gradle fertig ist → Handy per USB verbinden (USB-Debugging an) → ▶ Run.
Oder: *Build → Build APK(s)* → APK aufs Handy kopieren und installieren.

**Variante B – ohne PC, über GitHub:** Neues Repository anlegen, alle Dateien hochladen. Unter *Actions* läuft „APK bauen“ automatisch; danach unter dem Lauf das Artefakt **WLAN-Check-APK** herunterladen, entpacken und die `app-debug.apk` installieren (Installation aus unbekannten Quellen erlauben).
