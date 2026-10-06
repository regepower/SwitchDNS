# SwitchDNS

Schlanker DNS-Changer für Android 12–16 (minSdk 31, targetSdk 36).

- Reines DNS-VPN: `VpnService` mit Adresse + DNS-Servern, **ohne Routen** – Android löst Namen über den gewählten Server auf, der Verkehr läuft normal weiter. Kein Paket-Loop.
- Nur IPv4-DNS. Presets (Cloudflare, Quad9, AdGuard, Google, OpenDNS …) + eigene Einträge (lange drücken = löschen).
- VPN nicht getaktet (`setMetered(false)`): Play Store & Co. erkennen WLAN weiterhin.
- App-Filter (App-Selector aus BootDelay, Umschalter „Systemapps anzeigen“): „Alle außer ausgewählten“ oder „Nur ausgewählte“.
- Netzregeln: nur bei mobilen Daten und/oder WLAN; Pause bei WLAN-Anmeldeseiten (Captive Portal, z. B. WIFIonICE). Pausiert = Tunnel zu, Dienst bleibt aktiv und schaltet automatisch wieder ein.
- Schnelleinstellungs-Kachel „SwitchDNS“, Start beim Booten, durchgehend aktives VPN (Always-on). „Verbindungen ohne VPN blockieren“ nicht aktivieren.
- Build: GitHub Actions (ktlint, Android-Lint, Debug + Release), APK als Artifact; Tag `v*` → Release.
- Version `1.0.<CI-Build>` (z. B. Build #57 → 1.0.57), Hilfe zeigt Version und Build-Datum.
- Signierung: Secrets `KEYSTORE_BASE64` + `KEYSTORE_PASSWORD` (Alias und Key-Passwort optional); ohne Secrets Debug-Key.

## Unterstützen

Diese App ist kleiner als ein Foto. Unterstütze die Entwicklung auf [Liberapay](https://liberapay.com/regepower/donate) oder [GitHub Sponsors](https://github.com/sponsors/regepower).

## Lizenz

[GPL-3.0](LICENSE) – freie Software: nutzen, ändern und weitergeben erlaubt, abgeleitete Versionen müssen ebenfalls unter der GPL-3.0 stehen.
