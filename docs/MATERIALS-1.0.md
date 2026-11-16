# Materialien, Höhlen und lokale Reaktionen (PR #20)

## Verhalten

Neue Offline- und RTDB-v6-Welten enthalten deterministisches Kohleerz, tiefen Basalt und kleine Kristallhöhlen. Kohleerz liefert beim Abbau einen Kohleblock. Im Ofen lassen sich auch in alten Welten zwei Holz zu einem Kohleblock sowie ein Leuchtstein und ein Glas zu einem Kristall verarbeiten. Die vorhandenen Werkzeugstufen gelten weiterhin.

Laub bleibt der Setzling ohne neue Baum-Block-ID. Das tägliche UTC-Wachstum und allmählicher Laubzerfall sind in [NATURE-MULTIPLAYER.md](NATURE-MULTIPLAYER.md) beschrieben. Die weiteren Materialien erweitern die Palette, ohne bestehende IDs zu verändern:

| ID | Material |
|---|---|
| 17 | Kohleerz |
| 18 | Kohleblock |
| 19 | Basalt |
| 20 | Kristall |
| 21 | Glutstein |
| 22 | Kristallglas |
| 23 | Polierter Basalt |

Kohle neben Lava brennt begrenzt an dieser Stelle; Wasser löscht sie. Sie entzündet keine Nachbarblöcke. Funken, Rauch, Kristallglanz, Abbaufragmente, Vegetation, Flüssigkeitsanimation und Nebel sind lokale Darstellung ohne Firebase-Schreibvorgänge. Alle Partikel teilen ein Budget von 150; Materialeffekte berücksichtigen höchstens 32 nahe Stellen. Es gibt keine neue frei platzierbare Feuer-ID und keine globale Brandsimulation.

### Geheimreaktionen (technische Auflösung)

- Kohle über Lava wird zu Glutstein; seitlich erhitzte Kohle verbrennt zu Luft.
- Glas neben einem Kristall wird Kristallglas und verbraucht den Kristall.
- Basalt neben einem Eisenblock wird poliert; Eisen bleibt als Katalysator erhalten.
- Wasser an Lava kühlt diese zu Basalt. Alte Offline-Welten mit Generator 1 behalten ihre frühere Stein-Abkühlung.
- Vorhandenes Erzschmelzen über Lava und das gedrehte/gespiegelte 2×3-Leuchtsteinritual bleiben erhalten und funktionieren jetzt auch im v6-Journal.

Neue Materialreaktionen arbeiten in Drei-Sekunden-Schritten. Kohle und Kristallglas benötigen drei Schritte, Online-Erz fünf, das Online-Leuchtsteinritual drei. Offline verwendet das ursprüngliche Schmiedesystem weiterhin seine bisherigen 15 beziehungsweise acht aktiven Sekunden. Menüs pausieren die Offline-Simulation; online arbeitet nur eine aktive Sitzung. Es erfolgt kein Nachholen vergangener Offlinezeit.

## Ergänzung bestehender Online-Welten ab 1.0.3

Die ausdrücklich gewünschte einmalige [Rohstoffergänzung](RESOURCES-1.0.3.md) journalisiert sichere Lagerstätten im ursprünglichen, unberührten Gestein. Sie ändert keine Generatorversion und ersetzt keine bestehenden Bauten. Dies erweitert die ursprüngliche Beschränkung auf neue Welten; Ladepfade regenerieren weiterhin keine Landschaft.

## Kompatibilität

Die gespeicherte Generationsversion ist unveränderlich: fehlend/1 verwendet den bisherigen Generator, 2 ausschließlich neue Welten. Alte Weltinhalte werden beim Laden weder nachgeneriert noch ersetzt. Auch bearbeitete Zellen, deren Block inzwischen dem ursprünglichen Terrain entspricht, bleiben erhalten. Regions-Caches sind nach Generatorversion getrennt. Der alte Online-Terrain-Hash und seine Nahttests bleiben unverändert.

Offlineformat 7 speichert Generatorversion, 24 Inventarplätze, Reaktionsphasen und Bearbeitungshistorie. Formate 1–6 bleiben lesbar; ihre Inventare werden mit Nullen erweitert. Ein neuer Spielstand benötigt einen aktualisierten Empfänger für den QR-Import.

Verschlüsselte Inventare ohne neue Materialien bleiben im bisherigen Format 1 (17 Zähler). Format 2 speichert 24 Zähler; Welt, UID und Revision bleiben authentifiziert. Checkpoints und Wiederherstellung unterstützen beide Formate. Neue Materialien erfordern neue Apps auf allen Geräten. `schemaRev` muss bei jeder v6-Schreibrevision aktualisiert werden; alte Clients können diese Fähigkeit nicht aus einem früheren Commit erben.

## Autorität und Konflikte

Online aktiviert nur eine Spieleränderung mit Herkunftsmarkierung oder eine bereits gestartete Phase eine Reaktion. Ein Beitritt allein verändert alte unmarkierte Dekorationen nicht. Zeitphasen und Zutaten-Fingerprints werden mit den Blöcken gespeichert. Werden Wärmequelle oder Zutat entfernt und neu gesetzt, startet ihre neue Zellnonce die Reaktion erneut.

Ein Schritt verwendet den bestehenden atomaren RTDB-Journalbatch (höchstens 16 Zellen), private Inventarverschlüsselung, Spielerrevisionen, Zellnonces, Chunk-/Regionszähler und Checkpoints. Ein globaler Generationvergleich schützt auch nur gelesene Zutaten. Kristallglas und Ritual verbrauchen Zutaten im selben Commit wie ihr Ergebnis, auch über Regionsgrenzen. Materialreaktionen schenken keine Inventarressourcen.

Die RTDB-Regeln verlangen den gemeinsamen serverseitigen Zeitstempel pro Region und mindestens 2,8 Sekunden Abstand. Zwei Clients können keine zusätzliche Phase für denselben Zeitpunkt committen. Die Verfestigung von Lava benötigt eine unveränderte Positionsgeneration und freie Spielerboxen. Bereits feste Blöcke benötigen keine zusätzliche Positionssperre. Bei Konflikten wird kein spekulativer Block angezeigt; eine weitere Weltänderung beziehungsweise der nächste Beitritt stößt die Prüfung erneut an.

## Firebase-Aufwand

Darstellung erzeugt keinen Netzwerkverkehr. Materialprüfungen starten durch Beitritt oder tatsächliche Regionsänderungen. Nur laufende Reaktionen planen weitere Drei-Sekunden-Schritte; danach stoppt die Arbeit. Es gibt keinen zusätzlichen dauerhaften Listener oder Leerlauf-Polling.

Bei Kandidaten werden Kopf, relevante geänderte Chunks und erforderliche Nachbarregionen geladen; bereits bekannte Chunkzähler vermeiden erneutes Laden. Positionen werden für eine mögliche Verfestigung geprüft. Jede Phase ist ein atomarer RTDB-PATCH mit normalen Journal-/Inventareinträgen je geänderter Zelle. Die Inventarhülle wächst nur beim neuen Format von 165 auf 207 Zeichen. Firestore-Backups bleiben gebündelte Checkpoints statt zusätzlicher unmittelbarer Schreibvorgänge pro Aktion. Keine gemessene prozentuale Einsparung wird behauptet.

## Prüfung und Einführung

`bash test.sh` prüft alte Generator-Hashes, Fensterüberlappung, Inventare, Materialien, Reaktionen, Speicherformate und Renderingverträge. `npm run test:rules` prüft Firebase-Regeln, Zeit-/Generationskonflikte, atomaren Zutatenverbrauch und Versionsschutz. Android-Instrumentierung prüft zwei v6-Spieler, regionsübergreifendes Kristallglas, Glutstein, Basalt sowie Checkpoint und erneuten Beitritt.

Vor gemeinsamer Nutzung müssen **beide** aktualisierten Firebase-Regelsätze (`database.rules.json`, `firestore.rules`) installiert und alle Geräte aktualisiert werden. Der freigegebene PR-Merge übernimmt den Feature-Freeze nach `main`. Die zusätzlich freigegebene Veröffentlichung als 1.0.0 liefert ein Update über die bestehende Installation; weder Merge noch Veröffentlichung deployen Regeln. Migration, unabhängige Sicherung und Grenzen einer Rückkehr sind in [Einführung/Migration](RELEASE-1.0.0-rc.6.md) beschrieben.

Offen bleiben die visuelle Abnahme auf echten Tablets, Messungen mit zwei bis vier physischen Geräten und die Migration einer tatsächlich gesicherten 0.8.3-Produktivwelt. Automatisierte Modell-/Migrationsprüfungen ersetzen diese Abnahme nicht.
