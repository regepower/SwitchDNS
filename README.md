# SwitchDNS

Schlanker DNS-Changer für Android 12–16 (minSdk 31, targetSdk 36). Keine Bibliotheken, APK ≈ 62 KB.

## Funktionen

- Reines DNS-VPN: `VpnService` mit Adresse + DNS-Servern, **ohne Routen** – Android löst Namen über den gewählten Server auf, der Verkehr läuft normal weiter. Kein Paket-Loop, nichts wird protokolliert.
- Nur IPv4-DNS. Presets (Cloudflare, Quad9, AdGuard, HaGeZi, Google, OpenDNS …) + eigene Einträge (★, oben in der Liste; lange drücken = löschen).
- Serverwechsel im laufenden Betrieb: Tunnel wird geschlossen und nach 1 s neu aufgebaut, damit der neue Server sofort greift.
- VPN nicht getaktet (`setMetered(false)`): Play Store & Co. erkennen WLAN weiterhin.
- App-Filter (App-Selector aus BootDelay, Umschalter „Systemapps anzeigen“): „Alle außer ausgewählten“ oder „Nur ausgewählte“.
- Netzregeln: nur bei mobilen Daten und/oder WLAN. Netzwechsel werden 2 s abgewartet, damit der Tunnel nicht flattert. Pausiert = Tunnel zu, Dienst bleibt aktiv und schaltet automatisch wieder ein.
- **WLAN-Anmeldeseiten** (Captive Portal, z. B. WIFI@DB): Pause plus Benachrichtigung „WLAN-Anmeldung nötig“ mit „WLAN-Einstellungen öffnen“. Bei „Bitte nicht stören“ ploppt sie nur auf, wenn SwitchDNS bzw. der Kanal „Hotspot-Anmeldung“ als Ausnahme erlaubt ist.
- **Android Auto**: Pause, solange Android Auto verbunden ist (abschaltbar).
- Schnelleinstellungs-Kachel „SwitchDNS“ (Kategorie Konnektivität; ab Android 13 Button „Kachel hinzufügen“), Start beim Booten, durchgehend aktives VPN (Always-on). „Verbindungen ohne VPN blockieren“ nicht aktivieren.
- Einstellungen speichern/laden als JSON-Datei, Hilfe mit Kapiteln, Update-Prüfung einmal täglich gegen GitHub-Releases.

## Installation & Updates

APK aus den [Releases](https://github.com/regepower/SwitchDNS/releases) laden (`SwitchDNS-<Version>.apk`), z. B. per [Obtainium](https://github.com/ImranR98/Obtainium) automatisch aktuell halten.

## Build

- GitHub Actions: ktlint, Android-Lint, Debug + signiertes Release, APK als Artifact.
- Release: Actions → **Release** → Run workflow (veröffentlicht die APK des letzten Builds als `1.0.<Build>`), oder Actions → Build → Run workflow mit Haken „release“.
- Version `1.0.<CI-Build>` (z. B. Build #57 → 1.0.57), Hilfe zeigt Version und Build-Datum.
- Signierung: Secrets `KEYSTORE_BASE64` + `KEYSTORE_PASSWORD` (Alias und Key-Passwort optional); ohne Secrets Debug-Key.

## Unterstützen

Diese App ist kleiner als ein Foto. Wer mag, kann die Entwicklung über [Liberapay](https://liberapay.com/regepower/donate) oder [GitHub Sponsors](https://github.com/sponsors/regepower) unterstützen.

## Lizenz

[GPL-3.0](LICENSE) – freie Software: nutzen, ändern und weitergeben erlaubt, abgeleitete Versionen müssen ebenfalls unter der GPL-3.0 stehen.
