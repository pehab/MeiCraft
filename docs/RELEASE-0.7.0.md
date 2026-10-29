# meiCraft 0.7.0 · Gemeinsam bauen

Neue Online-Kreativwelten für gemeinsames Bauen auf mehreren Geräten:

- Anonyme Geräteanmeldung ohne Benutzerkonto oder Passwort.
- Beitritt über Einladungs-QR oder Einladungstext.
- Vier oder neun zusammenhängende Gebiete mit insgesamt 192 × 192 bzw. 288 × 288 Blöcken Grundfläche.
- Gemeinsam gespeicherte Blockänderungen und sichtbare Mitspieler mit Anzeigenamen.
- Besitzer können andere Geräte-IDs aus ihrer Welt entfernen und sperren.
- Erneutes Öffnen bereits besuchter Online-Welten über die lokale Online-Weltenliste.

Die Online-Welt bleibt statisch: keine Tag/Nacht-Wechsel, Gegner, Flüssigkeitsströme oder automatischen Materialreaktionen. Es werden nur eure Bauänderungen und Spielfigurpositionen synchronisiert. Die erste Fassung aktualisiert ungefähr alle 2,5 Sekunden; beim Gebietswechsel gibt es eine kurze Ladepause. Online-Spiel benötigt eine Verbindung. Bei Verbindungsproblemen pausiert das Bauen, bis die Synchronisierung wieder funktioniert.

Lokale Kreativ- und Storywelten, gespeicherte Fortschritte und das bisherige Offline-QR-Teilen bleiben erhalten. Online-Welten liegen getrennt in Firebase. Vor dem ersten Start müssen anonyme Anmeldung und die mitgelieferten Firestore-Regeln im Projekt eingerichtet sein. Weitere Hinweise stehen in [ONLINE.md](ONLINE.md).

Zum Testen auf mehreren Tablets: Auf dem ersten Gerät im Hauptmenü „Gemeinsam bauen“ öffnen, eine Online-Welt anlegen und anschließend unter „ONLINE → Einladungs-QR“ die Einladung anzeigen. Auf dem anderen Gerät „Gemeinsam bauen → Beitreten / Öffnen → Einladungs-QR scannen“ auswählen.
