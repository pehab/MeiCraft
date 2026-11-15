# meiCraft 1.0.0 – gemeinsam bauen und erneuerbare Natur

Dieses Update wird auf Nutzerwunsch für die gemeinsame Stabilisierungs- und Testphase veröffentlicht. Der Funktionsumfang ist eingefroren; echte Mehrgeräte-/Performance-Abnahme und die Migration einer gesicherten Produktivwelt stehen noch aus. Fehlerkorrekturen können als weitere Updates folgen.

## Neu

- Dauerhaftes RTDB-Journal: Bauten und private Ressourcen werden atomar bestätigt; Firestore sichert gebündelte Checkpoints. Wiederverbindung und Hostwechsel erhalten bestätigte Änderungen.
- Nachwachsende Bäume aus gepflanztem Laub: online höchstens eine Stufe je UTC-Login-Tag, mit Platz-/Spielerschutz. Ungestütztes natürliches Laub zerfällt schrittweise; Setzlinge und markierte Dekoration bleiben geschützt.
- Kohleerz, Kohleblock, Basalt, Kristalle/Kristallhöhlen, Glutstein, Kristallglas und polierter Basalt. Neue Landschaftselemente entstehen nur in neuen Welten. Ofenrezepte machen Kohle und Kristalle auch in alten Welten verfügbar.
- Begrenztes Kohlefeuer und Geheimreaktionen auch im Multiplayer; kein ausbreitender Weltbrand. Lokale Vegetation, Nebel, Flüssigkeitsanimation, Rauch/Funken/Kristallglanz und Abbaupartikel ohne zusätzliche kosmetische Weltwrites.
- Transparentes Glas und Kristallglas; kompatible Offlineformate und erweiterte private Inventare.

## Über die vorhandene Installation aktualisieren

Version 1.0.0 verwendet Versionscode 24, dieselbe Paketkennung und die bisherige Updatesignatur. Damit kann sie 0.8.3 und die bisherigen RC-Kandidaten bis Versionscode 23 aktualisieren. **Nicht deinstallieren und keine App-Daten löschen:** lokale Testwelten, Einstellungen, anonyme Online-UID und private Inventarschlüssel liegen in den App-Daten. Ein normal erfolgreich installiertes Update übernimmt sie; neu gespeicherte Welten sind nicht für alte APKs rückwärtskompatibel.

## Vor dem Öffnen einer Online-Welt

1. Eine unabhängige Sicherung der bestehenden Welt und Firebase-Konfiguration anlegen; Geräte-App-Daten erhalten.
2. Aktuelle [RTDB-Regeln](../database.rules.json), [Firestore-Regeln](../firestore.rules) und [Indexkonfiguration](../firestore.indexes.json) installieren. Der Download oder das Installieren der APK deployt keine Regeln.
3. Alle Mitspieler aktualisieren; die bestehende Welt zuerst als Besitzer öffnen und die einmalige Migration abschließen lassen.

Die Migration kopiert bestehende Daten innerhalb derselben Welt-ID. Alte Firestore-Block-/Inventardokumente werden nicht gelöscht, aber alte Schreibzugriffe gesperrt und die Protokoll-Metadaten umgestellt. Sie bleiben ein eingefrorener Ausgangsstand, keine weiter synchronisierte zweite Welt. Abbrüche lassen sich als Besitzer fortsetzen. Eine alte APK ist kein automatischer Rückweg; spätere Bauten werden nicht zurück in das alte Format kopiert.

Details: [Migration und Rückkehrgrenzen](RELEASE-1.0.0-rc.6.md), [Materialien und Rezepte](MATERIALS-1.0.md), [Baumwachstum/Laubzerfall](NATURE-MULTIPLAYER.md), [offene Geräteabnahme](1.0-VALIDATION.md).

Das veröffentlichte APK wird aus dem geprüften Quellstand gebaut, gegen die ursprüngliche Signatur geprüft und mit SHA-256 sowie Update-Manifest bereitgestellt. Firebase-Regeln und Migration werden durch die Veröffentlichung nicht automatisch ausgeführt.
