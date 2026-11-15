# MeiCraft 1.0.0-rc.6 — dauerhaftes RTDB-Journal und Host-Checkpoints

Feature-Freeze am 15. November 2026. PR #20 wird nach grüner CI zur Stabilisierung nach `main` übernommen. Dieses Dokument beschreibt den rc.6-Migrationspfad. Die anschließende Veröffentlichung als [1.0.0](RELEASE-1.0.0.md) ist inzwischen zusätzlich freigegeben; Firebase-Regeln werden weiterhin nicht automatisch installiert.

## Vor dem ersten Start

1. In Firebase die vollständige neue **database.rules.json** in Realtime Database veröffentlichen. Adresse bleibt `https://meicraft-be298-default-rtdb.europe-west1.firebasedatabase.app` (Belgien). Kein öffentlicher Testmodus.
2. Auch die neuen **firestore.rules** und **firestore.indexes.json** veröffentlichen. Neue Speicherstände, Uploads und Manifeste benötigen diese Regeln; alte rc.5-Regeln reichen dafür nicht. Die Indexausnahmen verhindern unnötige Indizes auf Chunks, Ciphertexten und großen Manifest-Maps; nur Upload-Zeitstempel werden für die Bereinigung benötigt.
3. APK auf allen Geräten über die bestehende Installation installieren. **App-Daten und Geräteanmeldung erhalten:** UID und privater Inventarschlüssel werden weiterbenutzt. Es gibt keine neue SDK-Anmeldung.
4. Bestehende Welt zuerst als Besitzer öffnen. Alte Protokolle 3/4 durchlaufen zunächst die bestehende fortsetzbare Migration auf 5. Danach wird Protokoll 5 atomar eingefroren und in das dauerhafte RTDB-Format kopiert.
5. Der Migrationsmarker bleibt bis zur geprüften Freigabe bestehen. Bei Abbruch dieselbe Welt erneut als Besitzer öffnen. Andere Geräte warten auf den Abschluss und verbinden sich anschließend normal.

Eine migrierte Welt kann mit alten APKs nicht mehr verändert werden. Das Zurückinstallieren von rc.5 oder 0.8.3 stellt keinen aktuellen Spielstand wieder her. Die eingefrorenen alten Dokumente bleiben als Migrationsquelle bestehen, bis die Welt gelöscht wird.

PR, CI und APK veröffentlichen Firebase-Regeln nicht automatisch.

## Was bei der Migration erhalten bleibt – und was Rückkehr bedeutet

Die Migration kopiert Daten innerhalb derselben Welt-ID; sie erstellt keine zweite, unabhängig auswählbare Online-Welt. Der Welt-Seed und der bisherige Terrain-Generator bleiben erhalten. Übertragen werden gespeicherte Blockänderungen, Mitgliedschaften und Inventarzustände, nicht eine neu erzeugte Landschaft.

1. Bei alten Protokollen werden die bisherigen Blockdokumente nach dem Sperren der Quelle in Firestore-Chunks kopiert. Anzahl und Inhaltsprüfsumme sowie Mitgliedschaften werden vor der Aktivierung geprüft. Die ursprünglichen Blockdokumente bleiben erhalten.
2. Für Protokoll 6 werden die alten v5-Schreiber atomar eingefroren und die Weltmetadaten auf das neue Protokoll gesetzt. Firestore-Chunks und verschlüsselte Spielerzustände werden nach RTDB kopiert. Jeder kopierte Chunk wird vor Freigabe mit der eingefrorenen Quelle verglichen. Noch nicht übernommene private Altinventare werden beim Beitritt ihres jeweiligen Geräts gelesen; ihre Originaldokumente werden nicht gelöscht.
3. Nach erfolgreicher Migration ist RTDB der fortgeschriebene Spielstand. Firestore-Quelldaten bleiben auf dem eingefrorenen alten Stand, neue Firestore-Checkpoints sichern den neuen Betrieb. Die Migration selbst löscht die alten Block-/Inventardokumente nicht. Ein ausdrückliches Löschen der gesamten Welt entfernt auch ihre alten Daten und Backups.

Die Migration verändert also Weltmetadaten und Schreibrechte, obwohl sie die Quelldaten erhält. Eine alte APK, das Zurücksetzen eines Protokollfeldes oder alte Firebase-Regeln sind **kein unterstützter Rollback**. Neue Bauten und Materialien werden nicht zurück in das alte Format gespiegelt. Ein Rückwechsel wäre eine administrative Wiederherstellung aus einer passenden Sicherung; spätere Aktionen müssten separat gerettet werden. Ein automatischer Rückmigrationsknopf ist nicht implementiert.

Vor der ersten Migration eine unabhängige Sicherung des vollständigen Firestore-Weltpfads einschließlich Unterkollektionen und vorhandener RTDB-Weltdaten sowie der Regel-/Indexkonfiguration anlegen. Das Beibehalten alter Dokumente innerhalb derselben Datenbank ersetzt diese Sicherung nicht. Geräte-App-Daten, anonyme UID und private Inventarschlüssel erhalten; eine reine Datenbankkopie ersetzt die Geräteschlüssel nicht. Eine solche unabhängige Produktivsicherung wurde durch PR/CI nicht erstellt.

Abbrüche werden durch erneutes Öffnen als Besitzer fortgesetzt. Nach abgeschlossener Migration wird bei fehlendem RTDB-Zustand absichtlich kein alter Firestore-Stand still aktiviert, damit spätere Bauten und Ressourcen nicht unbemerkt zurückgesetzt werden.

## Ergänzungen im eingefrorenen Funktionsumfang

Nachwachsende Bäume, allmählicher Laubzerfall, neue Materialien/Kristallhöhlen, lokale Effekte, transparentes Kristallglas und Geheimreaktionen sind enthalten. Details: [Materialien](MATERIALS-1.0.md), [Natur](NATURE-MULTIPLAYER.md), [Featureliste](1.0-VISUAL-POLISH-IMPLEMENTATION.md). Offlineformat 7 liest weiterhin Formate 1–6; neue Ressourcen benötigen den aktualisierten Inventarcodec und aktuelle Regeln auf allen Geräten. Bestehende Landschaften bleiben unverändert.

## Spielbetrieb und dauerhafte Bestätigung

- RTDB ist nach dieser Migration die Spielautorität. Eine Aktion schreibt mit **einem atomaren Multi-Location-Update** die Zelle, den verschlüsselten Vorrat, die eigene Revision, kausale Änderungsdaten und einen dauerhaften Journaleintrag. Zusätzlich werden globale, Bereichs- und Chunkzähler atomar erhöht.
- Erst nach Serverbestätigung übernimmt das Spiel die Aktion als bestätigt. Bei verlorener Antwort wird der aktuelle dauerhafte Stand abgeglichen, bevor weitergebaut werden kann. Es gibt kein blindes erneutes Abbuchen einer unklaren Aktion.
- Regeln koppeln Block, Inventar und Journal. Derselbe Block hat eine vorherige Nonce; bei konkurrierenden Änderungen gewinnt eine Aktion. Verschiedene Blöcke desselben Chunks dürfen gleichzeitig geändert werden, ohne eine globale Compare-and-set-Sperre.
- Mitspieler hören direkt den kleinen Journal-Kopf und erhalten aktuelle Änderungen über den begrenzten kausalen Ring. Der normale Bauabgleich benötigt **keinen Firestore-Read und keinen Firestore-Write**.
- Bei einer Ringlücke werden die betroffenen dauerhaften RTDB-Chunks geladen. Änderungen während des Ladens werden durch Ring oder vollständige Journal-Einträge bis zu einem nachweislich zusammengehörigen Kopf aufgeholt.
- Der lokale Bereichscache bleibt erhalten. Er ist nach Welt, Seed und Journal-Epoche getrennt; unveränderte Bereiche müssen beim warmen Wiederbeitritt nicht erneut aus der Datenbank kommen. Der Cache ersetzt keine Rechteprüfung.
- Die Positionen aus rc.5 bleiben bestehen: bis 10 Hz, Schwellwerte gegen Zittern, kurzer Interpolationspuffer, Hintergrundpause und Entfernen beim normalen Verlassen. Bei Absturz werden Positionen nach 8 s ausgeblendet. REST bietet hier kein SDK-onDisconnect.
- Bei einem unterbrochenen Journal-Stream wird der RTDB-Kopf vorübergehend abgefragt. Ist RTDB nicht erreichbar, pausiert das Bauen; es wird **nicht parallel in den eingefrorenen Firestore-Spielstand geschrieben**. Dadurch entstehen keine zwei unabhängigen Weltstände.

## Host, Backups und Abbrüche

- Ein aktiver Spieler hält eine RTDB-Lease mit 15 s Laufzeit und erneuert sie alle 5 s. ETag-Compare-and-set schützt die Übernahme. Bei normalem Verlassen/Background wird die Lease nach einem Backupversuch freigegeben; nach einem Absturz übernimmt beim nächsten Prüftakt ein anderer Spieler.
- Diese Host-Rolle betrifft ausschließlich Firestore-Backups. **Das Spielen und die Bestätigung von Bauten hängen nicht vom Host ab.** Jeder bestätigte Stand liegt bereits dauerhaft in RTDB.
- Geänderte Chunks werden üblicherweise nach 60 s zusammen mit den passenden Inventaren gesichert. Ohne ausstehende Blockänderungen können reine Inventar-/Crafting-Änderungen nach 10 s gebündelt gesichert werden. Unveränderte Daten werden nicht erneut geschrieben. Beim Verlassen/Background erfolgt ein zusätzlicher Versuch.
- Ein Backup sammelt nur die seit dem vorherigen Manifest veränderten Chunks. Es braucht weder eine globale Spielpause noch einen regelmäßigen Download der gesamten Welt. Kausale Nachführung erzeugt trotz laufender Bauaktionen einen zusammengehörigen Snapshot.
- Chunks sind auf 16³ Zellen begrenzt und werden kompakt als Seed-Abweichungen gesichert. Inventare bleiben AES-GCM-Ciphertexte; fremde Vorräte werden vom Host nicht entschlüsselt.
- Unveränderliche Versionen werden in Paketen mit höchstens 200 Dokumenten hochgeladen. Ein abschließender atomarer Firestore-Commit veröffentlicht das neue Manifest und markiert den Upload als abgeschlossen. Das Manifest verbindet Chunks und Inventare desselben Journal-Stands.
- Ein alter Host kann durch monotone Generationen und die Manifest-Vorbedingung keinen neueren Stand zurücksetzen. Abgebrochene Uploads ersetzen kein gültiges Backup. Nach Ablauf werden sie markiert; dann können sie auch von einem verspäteten Schreiber nicht mehr abgeschlossen werden. Nicht mehr referenzierte Versionen werden aufgeräumt.
- Erst nach bestätigtem Manifest wird der Journal-Stand quittiert. Verarbeitete Einträge können danach entfernt werden; die letzten 16 Revisionen je Spieler bleiben bestehen. Dauerhafte Zellen und Inventare werden bei dieser Bereinigung **nicht** entfernt.
- Ist Firestore vorübergehend gestört oder sein Kontingent verbraucht, bleiben Journal und Spielstand in RTDB erhalten. Das Backup wird mit wachsendem Abstand erneut versucht, damit ein erreichtes Kontingent nicht weiter abgefragt wird. Die Diagnose zeigt bestätigte Checkpoints und deren Generation.
- Beim Löschen einer Welt werden zuerst die Zugriffe geschlossen und danach RTDB-Spielstand, Journal und Firestore-Backups entfernt. Ein geschlossener Metadateneintrag verhindert die Wiederverwendung alter Einladungen. Auch eine unterbrochene Löschung einer bereits geschlossenen Welt lässt sich fortsetzen.
- Fehlt nach abgeschlossener Migration der gesamte RTDB-Spielstand, erfolgt kein stiller Rückgriff auf die alten eingefrorenen Dokumente. Ein administratives Löschen/Zurücksetzen der RTDB ist kein normaler Host-Ausfall; ein älteres Backup darf dabei nicht unbemerkt aktuelle Bauten ersetzen.

## Datenmodell

Unter `worlds/{ownerUid}/{worldId}` bleiben `meta`, `members` und `live` erhalten. Neu ist `storage`:

- `control`: Migrationsphase, Epoche und eingefrorene Quelle.
- `head`: Generation, Spielerrevisionen mit begrenztem Änderungsring, Bereichs- und Chunkzähler; dieser Teil wird live abonniert.
- `chunks`: dauerhafte aktuelle Seed-Abweichungen mit Zellen-Nonce.
- `journal/{uid}/{revision}`: atomar bestätigte Aktionen einschließlich passendem Inventar-Ciphertext, bis zum Backup aufbewahrt.
- `lease`: zeitlich begrenzte Backup-Host-Rolle.
- `checkpoint`: quittierter Backup-Stand für die Journal-Bereinigung.

Firestore enthält unter der Welt `saved` (unveränderliche Chunk-/Inventarversionen), `uploads` (Staging und Fencing) und `sync/checkpoint` (gültiges Manifest). Der Host ist ein berechtigter Mitspieler, kein Administrator. Firebase bietet keine atomare Transaktion zwischen RTDB und Firestore; deshalb ist das Journal die dauerhafte Autorität und das Manifest die Veröffentlichung eines Backups.

## Größenordnung

Ein Beispiel mit vier aktiven Spielern, vier geänderten Chunks und vier veränderten Inventaren je Minute benötigt pro zusammengehörigem Backup acht Versionswrites plus Upload und zwei abschließende Dokumentwrites: **elf Writes pro Minute, ungefähr 1.320 Writes in zwei Stunden**, zusätzlich Einrichtung, reine Inventar-Backups, Wiederholungen und Ausnahmen. Unveränderte Minuten kosten keine erneuten Versionswrites. Bereinigung verwendet zusätzliche Löschvorgänge. Die Anzahl einzelner Bauaktionen erhöht die Firestore-Writes dabei nicht mehr unmittelbar.

Das ist ein Modell, keine Firebase-Abrechnung oder Laufzeitgarantie. RTDB-Verkehr wächst weiterhin mit Bewegung, Bauaktionen, Wiederverbindungen und der Verteilung an alle Geräte. Die Diagnose erfasst auch die dekodierten Live-Stream-Nutzdaten; Inhaltsbytes enthalten weiterhin nicht alle Protokoll-/TLS-Kosten. Reale Tablet-Latenz und Monatsvolumen müssen weiter im Spiel und in Firebase gemessen werden.

## Tablet-Test

- Zwei bis vier Geräte: gemeinsam bauen/abbauen, denselben Block gleichzeitig bearbeiten, Wasser und Vorräte vergleichen.
- Besitzer beenden: andere spielen weiter; danach soll ein neuer Backup-Host Checkpoints schreiben.
- Host während eines Backups schließen/Flugmodus aktivieren; wieder verbinden. Bauten und Inventare müssen erhalten bleiben.
- App während einer Bauaktion schließen; nach Neustart dürfen Ressourcen weder doppelt abgebucht noch dupliziert sein.
- Nach vielen Bauaktionen warm wieder beitreten und Cache, Firestore-Zähler, RTDB-Kanal sowie Checkpoint-Generation kontrollieren.
- Besitzer sperrt einen Spieler; alter QR-Code hebt die Sperre nicht auf.
