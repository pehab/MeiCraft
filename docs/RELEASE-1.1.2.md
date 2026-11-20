# meiCraft 1.1.2 – Baumwachstum und freie Online-Werkzeuge

- Online-Baumwachstum korrigiert: Firebase kann den Pflanzungsindex je nach belegten Weltbereichen als Liste oder Objekt liefern. Beide Formen werden jetzt gelesen. Bestehende Setzlinge und bereits gewachsene junge Bäume bleiben erhalten und können weiterwachsen.
- Die Diagnose zeigt bei einer fehlgeschlagenen Baumprüfung den Prüfschritt und den bereinigten Fehlertext.
- Eisenspitzhacke, Werkbank, Ofen und ein wiederverwendbarer Eimer sind in Online-Welten sofort verfügbar, auch für bestehende Spieler. Werkzeug-Aufträge entfallen online.
- Baumaterialien bleiben endlich. Abbauen sammelt Ressourcen; Bauen und Materialrezepte verbrauchen Vorräte. Flüssigkeiten müssen weiterhin aufgenommen oder hergestellt werden.
- Offline-Storywelten behalten ihre Werkzeug-Aufträge. Bestehende Kapitel, Vorräte und Eimer werden erhalten.

## Aktualisieren

Über die vorhandene App installieren und alle mitspielenden Geräte aktualisieren. Bereits gepflanzte Bäume stehen lassen. Nach dem Beitritt wird die Wachstumsprüfung erneut ausgeführt; weiterhin höchstens eine Wachstumsstufe pro Baum und UTC-Tag.

**Gegenüber 1.1.1 sind keine neuen Firebase-Regeln oder Indizes erforderlich.** Falls noch die Regeln einer älteren Version verwendet werden, die [RTDB-Regeln](https://raw.githubusercontent.com/pehab/MeiCraft/v1.1.2/database.rules.json) übernehmen.

## Prüfungen

132 Firebase-Emulatortests, einschließlich echter REST-Antworten mit numerischen Bereichslisten und null-Lücken, sowie Java-Tests für Werkzeuge, endliche Vorräte und Offline-Fortschritt. Android-Instrumentierung prüft neue Besitzer und Gäste, Wachstum bis zur vollständigen Krone, blockierte Bäume, erneute Tagesprüfungen und den Erhalt vorhandener Inventare.
