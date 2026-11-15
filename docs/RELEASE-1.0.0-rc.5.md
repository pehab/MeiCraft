# MeiCraft 1.0.0-rc.5 — RTDB-Livekanal

Teststand auf PR #20, kein Merge und kein öffentliches Release.

## Einrichtung

1. Firebase → Realtime Database → Daten → vorhandene Datenbank in Belgien verwenden:
   `https://meicraft-be298-default-rtdb.europe-west1.firebasedatabase.app`
2. Unter **Regeln** den vollständigen Inhalt von `database.rules.json` aus diesem Testpaket veröffentlichen. Kein öffentlicher Testmodus. Die Regeln erlauben anonyme Firebase-Anmeldungen ausschließlich in berechtigten Welten und für eigene Positionen/Signale.
3. Zusätzlich die mitgelieferten `firestore.rules` und `firestore.indexes.json` verwenden, falls rc.4 noch nicht eingerichtet wurde. PR/CI/APK veröffentlichen keine Firebase-Regeln automatisch.
4. APK auf allen Geräten über die bestehende App installieren. **Keine App-Daten löschen:** anonyme UID, Berechtigungen und privater Inventarschlüssel bleiben erhalten.
5. Welt einmal als Besitzer öffnen. Bei älteren Firestore-Welten läuft zunächst die fortsetzbare rc.4-Migration. Anschließend richtet der Besitzer den separaten RTDB-Namensraum mit den bestehenden Zugriffsrechten ein. Danach können Mitglieder auch ohne anwesenden Besitzer spielen.
6. Online-Diagnose kontrollieren: `RTDB-Livekanal: verbunden`. Ohne veröffentlichte RTDB-Regeln bleibt der Firestore-Fallback aktiv; das ist kein erfolgreicher RTDB-Test.

## Verhalten und Einsparungen

- Bestehende Java-/REST-Architektur, dieselbe anonyme Firebase-ID. Keine neue SDK-Anmeldung, keine zusätzlichen Gradle-Abhängigkeiten und kein eigener Server.
- Ein persistenter RTDB-EventSource empfängt ausschließlich Positionen und kleine Änderungsrevisionen. Bewegungen bis 10 Hz, nur bei mindestens 2 cm Positionsänderung oder ca. 1,4° Blickänderung; stillstehend ein RTDB-Heartbeat alle 2 s. Höchstens eine Positionsanfrage gleichzeitig, keine nachlaufende Warteschlange alter Positionen. Die tatsächliche Frequenz hängt von der Netzwerklaufzeit ab.
- Bewegungen werden mit 100 ms Puffer zwischen zwei Samples interpoliert. Renderfrequenz und Winkelwechsel bei ±π verändern das Verhalten nicht; Teleports springen direkt.
- Erfolgreiche Bauaktionen senden ihre bestätigte Revision. Gleichzeitige Signale werden 25 ms gesammelt; Mitspieler lesen dann einen konsistenten Firestore-Snapshot. Positionen lösen **keine** Firestore-Reads aus. Signale enthalten keine verbindlichen Block- oder Inventardaten.
- Solange alle sichtbaren Mitspieler RTDB nutzen: keine regelmäßigen Firestore-Positionswrites; Sicherheitsabgleich nur alle 30 s. Bei RTDB-Ausfall automatisch bisherige 2 s mit Mitspielern bzw. 5 s allein. Ältere Protokoll-5-Clients bleiben über diesen Fallback sichtbar und spielbar.
- Bestehender lokaler Bereichscache und kausale Deltas bleiben erhalten: warmer Wiederbeitritt lädt keine unveränderten Chunks; bei Ringlücken werden fehlende Chunks nachgeladen.
- Block und privates Inventar bleiben **sofort atomar in Firestore gesichert: zwei Dokumentwrites pro Bauaktion**, Crafting ein Inventarwrite. Ein Antwortverlust wird vor weiterer Freigabe abgeglichen und anschließend signalisiert. Es gibt in diesem Stand **keine Host-Checkpoints im 60-s-Takt und kein verzögertes Inventarspeichern**. Dafür ist ein gesondertes dauerhaftes Journal mit gekoppelter Inventar-/Blockbestätigung erforderlich. Die vorher diskutierte Checkpoint-Schätzung beschreibt nicht diesen Build.
- RTDB-Zugriffe verwenden Eigentümer-UID und Welt-ID als Namensraum. Mitglieder erhalten nur mit dem vorhandenen Einladungscode Zugang. Sperren bleiben als Tombstone erhalten; ein alter Code hebt sie nicht auf. Unterbrochene Sperraufträge werden lokal gespeichert und beim Besitzer erneut versucht. Die zwei Datenbanken bieten keine gemeinsame atomare Transaktion; die UI bestätigt eine Sperre erst nach beiden Schritten.
- Beim normalen Verlassen wird die eigene RTDB-Position entfernt. Beim Absturz/Netzausfall verschwindet sie nach 8 s anhand des Serverzeitstempels. Die REST-Implementierung hat **kein SDK-`onDisconnect()`**. Im Hintergrund pausiert der Livekanal und wird beim Zurückkehren neu verbunden.
- Weltlöschung schließt zuerst den RTDB-Namensraum und lässt einen geschlossenen Tombstone zurück; danach läuft die bestehende fortsetzbare Firestore-Löschung.
- Diagnose trennt Firestore-Dokumentreads/-writes und bestätigte RTDB-Writes. Bytes zählen dekodierte Inhalte; TLS, HTTP, Verteilungsvolumen an alle Clients und Firebase-Abrechnung werden damit nicht vollständig erfasst. RTDB-Verbindungsfehler erscheinen in der Diagnose als RTDB, nicht als Firestore.

## Größenordnung, kein Abrechnungsversprechen

Zwei Stunden, vier durchgehend mit RTDB verbundene Geräte: 960 regelmäßige Firestore-Sicherheitsreads statt 14.400 bei Zweiesekunden-Polling (93 % weniger Grundlast). Hinzu kommen Initialisierung, Bau-Benachrichtigungen, Chunk-Lücken, Regel-/Indexreads und Wiederverbindungen. Regelmäßige Firestore-Positionswrites fallen weg. Blockaktionen bleiben zwei Writes; mehr Spieler erhalten dieselbe bestätigte Änderung, wodurch weitere Reads entstehen.

RTDB verteilt jede Positionsänderung an die verbundenen Teilnehmer. Dauerbewegung mit vier Spielern kann einschließlich Transportoverhead weiterhin einige hundert MB pro zweistündiger Runde kosten; tatsächliches Volumen im Firebase-Profiler messen. Kostenloses Kontingent und verbleibende Spielzeit sind keine feste Laufzeitgarantie. 100–200 ms Gesamtlatenz ist ein Messziel, kein Testergebnis: Blockbestätigung benötigt weiterhin Firestore-Commit, RTDB-Signal und einen Snapshot; Bewegung enthält zusätzlich den 100-ms-Interpolationspuffer.

## Tablet-Test

- Zwei bis vier Geräte: laufen, Blick drehen, über Bereichsgrenzen wechseln; Mitspieler sollen flüssig bleiben.
- Gleichzeitig verschiedene und dieselben Blöcke bearbeiten; Ressourcen und Wasserverhalten vergleichen.
- Flugmodus/App-Pause/Abbruch während Bauaktion, danach wieder beitreten: kein doppelter Vorrat, keine verschwundenen bestätigten Bauten.
- Besitzer beendet die App: andere bauen weiter; kein Hostwechsel nötig.
- Besitzer sperrt ein Gerät; Wiederbeitritt mit altem QR-Code bleibt gesperrt. Unterbrochenen Sperrauftrag erneut vom Besitzer ausführen lassen.
- Warm erneut beitreten und lokale Cache-/Chunk-Zähler prüfen. Nach etwa 10 Minuten Diagnose beider Geräte vergleichen und Firebase-RTDB-Downloadvolumen kontrollieren.
