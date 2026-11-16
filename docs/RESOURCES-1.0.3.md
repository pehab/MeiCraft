# Rohstoffe in bestehenden Online-Welten und Suchhinweise

## Für Spieler

Bestehende Online-Welten behalten ihre Welt-ID, ihren Seed, die bisherigen Bauten und den ursprünglichen Terrain-Generator. Beim Beitritt des Besitzers ergänzt die App einmalig sichere Rohstoffplätze. Das gilt sowohl während des ersten Umstiegs auf das RTDB-Journal als auch beim späteren Beitritt zu einer schon migrierten Welt.

Pro 96×96-Region werden bis zu vier Kohleadern (je acht Blöcke), vier kleine Kristallhöhlen (3×2×2 Innenraum, mit zwei Kristallen) und vier geschlossene Lavataschen geprüft. Diese kompakten Kammern passen vollständig in einen atomaren Journalbatch. Neue Generator-2-Welten haben Kohle und Kristallhöhlen bereits und erhalten nur sichere Lavataschen. Das Nachfüllen ist keine wiederkehrende Rohstoffregeneration.

Jeder Innenraum ist von einer vollständigen, mindestens einen Block dicken natürlichen Steinschicht umgeben, auch am Boden und an der Decke. Die Ergänzung sucht relativ oberflächennahe Plätze im Gestein. Der aktuelle Blockbestand und die gesamte bekannte Bearbeitungshistorie müssen mit dem ursprünglichen Stein übereinstimmen. Auch vom Spieler wieder zu Stein zurückgesetzte Blöcke bleiben geschützt. Bearbeitete oder bebaute Plätze werden übersprungen. Auch Spieler in einem vorgesehenen Schutzvolumen verhindern die Ergänzung. Bei zu wenig sicherem Platz entstehen weniger Vorkommen; vorhandene Inhalte werden nicht verdrängt.

Während die begrenzte Ergänzung läuft, kann weitergespielt werden. Nach Abschluss erscheint eine kurze Meldung. Unterbrechungen werden beim erneuten Beitritt als Besitzer fortgesetzt. Die App führt nach Abschluss keine fortlaufenden Rohstoffprüfungen aus.

## Rohstoffe finden – offline und online

Über tatsächlich vorhandenen unterirdischen Vorkommen erscheinen lokale, kleine Hinweise:

- Kohle: dunkle Kohlesplitter.
- Kristalle: kleine blaue Kristallsplitter mit leichtem Glitzern.
- Lava: dunkle Gesteinsstücke, etwas Rauch und gelegentlich ein kleiner warmer Lichtpunkt.

Nahe einem Hinweis erscheint beispielsweise „Kristallsplitter · ca. 8 Blöcke tiefer“. Die Spuren weisen auf die Fundstelle darunter; vorsichtig und seitlich nach unten graben, besonders bei Lava. Sie sind Dekoration, keine abbaubaren neuen Blocktypen.

Die Darstellung wertet nur vorhandene, lokal geladene Blöcke aus. In entladenen Online-Regionen werden keine Vorkommen geraten. Nach dem Abbau verschwinden Hinweise beim nächsten lokalen Abgleich beziehungsweise weisen auf weitere noch vorhandene Vorkommen. Auf bekannten bearbeiteten Oberflächen werden keine Hinweise gesetzt. Die Erkennung ist keine zusätzliche Weltgeneration: Alte Offline-Welten ohne Kohle/Kristalle erhalten dadurch keine neuen Lagerstätten; Lava-Hinweise funktionieren auch dort für vorhandene Quellen. Für Kohle/Kristalle bleiben dort die bisherigen Ofenrezepte verfügbar.

## Vor der Nutzung einmal aktualisieren

**Nur die neue `database.rules.json` in RTDB installieren**, anschließend die aktualisierte APK auf allen Geräten über die bestehende Installation installieren und zuerst als Besitzer beitreten. Firestore-Regeln und `firestore.indexes.json` bleiben unverändert; die 19 Einzelfeld-Einträge müssen nicht erneut angelegt werden. App-Daten, Anmeldung und Inventarschlüssel erhalten.

Mit alten RTDB-Regeln funktioniert der bisherige Spielbetrieb weiter, aber die neue Ergänzung kann nicht gespeichert werden. Die App beendet fehlgeschlagene Ergänzungsversuche nach begrenzten Wiederholungen mit einem Hinweis auf Regeln und erneuten Besitzerbeitritt. Es gibt keine unbestätigten lokalen Höhlen. Dieses Update veröffentlicht die Regeln nicht automatisch.

## Technik, Konflikte und Wiederherstellung

`ResourceRetrofit` bestimmt jeden Kandidaten allein aus dem unveränderten Generator, Seed, Region und einer festen Platznummer. Nicht der aktuelle Bauzustand entscheidet über Ausweichplätze. Für die natürliche Ausgangslandschaft werden mehrere Kandidaten gesucht; sobald ein natürlicher Kandidat feststeht, wird er bei Bearbeitung komplett übersprungen. Diese feste Zuordnung verhindert auch nach Verlust des sekundären Fortschritts, dass abgebaute Lagerstätten an anderen Stellen erneut erzeugt werden.

`OnlineResources` läuft nur beim Besitzer auf dem bestehenden seriellen Session-Worker. Ein Schritt je Region wird in einer begrenzten Welle geprüft. Ein geeigneter Platz schreibt alle maximal zwölf Blockänderungen, die normalen privaten Inventarrevisionen, den dauerhaften Journalring/-verlauf, Chunk-/Regionszähler und seinen Fortschrittscursor in **einem** atomaren RTDB-PATCH. Inventarmengen ändern sich dabei nicht. Der globale Kopfvergleich schützt die unveränderte Schutzschicht; eine Positionsgeneration schützt gegen gleichzeitig beitretende/bewegte Spieler. Spieleraktionen bei einer gelesenen oder geänderten Zelle verwerfen den Plan vollständig.

Fortschritt liegt unter `storage/resources/v1/s<region>` mit zwölf endlichen Platznummern. Regeln erlauben nur dem aktiven Besitzer Fortschritt und passende Batch-Ausgaben; doppelte, übersprungene oder rückwärts gesetzte Nummern werden abgewiesen. Generator-2-Welten können den bereits vorhandenen Kohle-/Kristallteil einmalig ohne Zelländerungen als nativ markieren. Ein ungeeigneter Platz schreibt nur einen konfliktgeprüften Überspring-Cursor. Antwortverlust oder Konkurrenz führen zum erneuten Lesen des autoritativen Zustands, nicht zur blinden Wiederholung.

Die Blockänderungen und ihre Nonces durchlaufen die vorhandenen Cache-, Journal- und Firestore-Checkpointpfade. Sekundäre Fortschrittsdaten werden nicht als neue Firestore-Dokumente gesichert. Falls dieser sekundäre Cursor nach administrativer Wiederherstellung fehlt, wird konservativ anhand der unverändert checkpointbaren Zellhistorie geprüft; bereits ergänzte oder abgebaute Plätze werden nicht nachgefüllt. Ein vollständiger administrativer RTDB-Verlust bleibt wie bisher ein gesonderter Wiederherstellungsfall ohne stillen Rückgriff auf alte Weltstände.

## Datenverbrauch und Darstellungskosten

Die Ergänzung verursacht einmalig Prüfreads und höchstens 128 geänderte Zellen je Region; neun Regionen ergeben höchstens 1.152 Zellen. Häufig sind es wegen unsicherer Stellen weniger. Jede geänderte Zelle behält die normalen Journal-/Inventarrevisionen. Firestore wird über die vorhandenen gebündelten Checkpoints beschrieben, nicht mit zusätzlichen unmittelbaren Writes pro Zelle. Nach Abschluss genügt beim Besitzerbeitritt das kleine Fortschrittsdokument; Gäste lösen keine zusätzliche Ergänzung aus. Fehlversuche werden begrenzt und bei Kontingentproblemen mit Backoff wiederholt.

Suchhinweise erzeugen **keinen Firebase-Traffic, keine persistenten Weltänderungen und keine zusätzlichen Drawcalls**. Die lokale Suche ist auf die Umgebung von ungefähr 16 Blöcken begrenzt und wird höchstens einmal pro Sekunde bei verändertem Weltbestand oder Bewegung in einen anderen Viererbereich erneuert. Es erscheinen höchstens drei Hinweise je Rohstoffart. Ihre kleinen Partikel teilen das bestehende Gesamtbudget von 150. Echte Tablet-Messungen bleiben für die Gesamtleistung maßgeblich.

## Automatisierte Prüfung

- Reine Planung: bestehende Bauten, alte Änderungen trotz identischem Stein, vollständige Schutzschicht, Spieler, Wiederholungen und konservative Wiederherstellung.
- Suchhinweise: alle drei Rohstoffarten, reale Tiefe, entladene Regionen, bearbeitete Oberflächen, Abbau und unveränderte Weltdaten.
- RTDB-Emulator: Eigentümerrechte, Fortschritt, atomarer Zutaten-/Zellzustand, doppelte Besitzerzugriffe, veraltete Generation, Positionskonflikte und regionenübergreifende Chunks.
- Android: tatsächlicher Renderer mit Hinweisen; zwei echte v6-Clients gegen Offline-Transporte, bestehende Welt, geschütztes Bauwerk, verlorene Antwort, Checkpoint, Neustart und verlorener sekundärer Cursor.
