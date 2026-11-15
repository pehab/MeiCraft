# 1.0.0-rc.4 · Protokoll 5 für gemeinsames Bauen

## Was sich ändert

Funktion, Landschaft, Steuerung und Weltkarte bleiben gleich. Die Weltkarte verwendet
bereits die normalen Positionsdaten und verursacht keine eigene Abfrage. Zwei Sekunden
für mehrere aktive Spieler, fünf Sekunden allein und 30 Sekunden im Stillstand bleiben.

Das neue Datenmodell verbindet die schnelle Übersicht mit einem begrenzten Änderungsring:

| Daten | Speicherort | Zweck |
| --- | --- | --- |
| Weltmetadaten | `onlineWorlds/{world}` | Einladung, Erstzugriff, Migration, Löschen |
| Zugriffsrechte, Positionen, Inventar-Ciphertexte, letzte 16 Änderungen je Spieler, Bereichsrevisionen | `sync/v5` | ein gemeinsamer Snapshot je Abgleich |
| Dauerhafter Blockstand, nach 16×16×16 Zellen gebündelt | `sectors/{sector}/chunks/{chunk}` | Erstladung und Nachladen nach längerer Abwesenheit |
| Bisherige Einzelblöcke und private Inventare | bisherige Pfade | unveränderte Migrationsquelle; keine weitere Spielmutation |

- Eine Bauaktion schreibt sofort und atomar **zwei Dokumente statt drei**: einen
  Zellen-Patch und den eigenen Inventar-/Änderungs-/Revisions-Patch in der Übersicht.
  Fällige Positionen werden weiterhin in denselben Commit aufgenommen.
- Nur die betroffene Zelle und die eigenen Spielerfelder werden geändert. Es gibt keinen
  Versionsvergleich des gesamten gemeinsamen Dokuments oder Chunks. Gleichzeitige
  Änderungen verschiedener Zellen werden daher nicht zu künstlichen Konflikten.
- Vorher-/Nachher-Nonces schützen dieselbe Zelle; eine monotone eigene Inventarrevision
  schützt den Vorrat. Beim selben Block gewinnt genau eine Aktion. Der Verlierer
  übernimmt den Serverstand, bevor eine neue Aktion möglich ist.
- Die Übersicht enthält die letzten 16 bestätigten Aktionen je Spieler. Stimmen
  Änderungsanzahl und kausale Ketten mit der Bereichsrevision überein, übernehmen andere
  Geräte diese direkt. Eine ältere fremde Aktion kann keine spätere eigene überschreiben.
- Nach längerer Abwesenheit oder bei einer Lücke lädt die App die seit dem letzten
  vollständigen Snapshot geänderten Chunks. Cache und alle Seiten bleiben an einen
  konsistenten Server-Snapshot gebunden. Ein Abbruch speichert keinen halben Checkpoint.
- Berechtigungen normaler Übersichtslese- und Positionsschreibvorgänge werden direkt
  aus dem betroffenen Dokument geprüft. Welt/Mitgliedschaft werden dafür nicht zusätzlich
  per `get`/`exists` geladen. Chunk-Zugriffe prüfen die zentrale aktuelle Berechtigung.

## Größen- und Latenzentscheidung

Die gesamte Welt passt nicht dauerhaft in das Firestore-Limit von 1 MiB je Dokument.
Ein alleiniger Schreibvorgang in einen Zwischenspeicher bräuchte spätere Verdichtung;
volle Zwischenspeicher oder Verdichtungskonflikte können zusätzliche Wartezeiten und
Wiederholungen erzeugen. Dieser Kandidat behält deshalb für jede Aktion einen direkt
haltbaren Chunk-Stand und eine feste atomare Zwei-Dokument-Operation bei. Er puffert
keine bestätigten Bauten nur lokal und benötigt keinen nachlaufenden Serverprozess.
Der Emulator prüft zudem eine Übersicht mit 256 Identitäten und je 16 Einträgen gegen
die Dokument- und Antwortgrößen. Das ersetzt keinen realen Last-/Tabletvergleich.

## Private Inventare

Die gemeinsame Übersicht enthält **keine lesbaren fremden Vorräte**. Ein eigener,
zufälliger 256-Bit-Schlüssel bleibt in denselben privaten App-Einstellungen wie die
anonyme Geräteanmeldung und wird vor dem ersten Cloud-Schreiben dauerhaft gesichert.
AES-GCM bindet den Inhalt an Welt, Geräte-ID und Inventarrevision; bei jeder Änderung
wird eine neue 96-Bit-Nonce verwendet. Alle Ressourcen, Werkzeuge und Fortschrittsfelder
werden vor Verschlüsselung und nach Entschlüsselung validiert. Der Payload ist 165 Zeichen
lang. Andere Spielerfelder, Zugriffsrechte und fremde Inventare bleiben schreibgeschützt.
Die Regeln prüfen den begrenzten Ciphertext statt einzelner Klartext-Inventarzähler.

Normale Polls projizieren fremde Inventar-Ciphertexte und die eigenen schon bestätigten
Änderungseinträge aus der Antwort. Eine unklare Serverantwort erzwingt einen vollständigen
Abgleich. Verlorene Commit-Antworten erzeugen keine doppelte Rohstoffgutschrift.

## Installation und Migration

1. **Zuerst die neuen `firestore.rules` veröffentlichen**, dazu die Indexkonfiguration aus
   `firestore.indexes.json` übernehmen. Alte Protokolle bleiben bis zur Umstellung unterstützt.
   APK/PR-Push veröffentlicht keine Firebase-Konfiguration automatisch.
2. Neue APK auf allen beteiligten Geräten **über die bestehende App installieren**.
3. Bestehende Welt zuerst als Besitzer öffnen. Die App sperrt Änderungen kurzzeitig und
   kopiert die alten Einzelblöcke seitenweise in Chunks. Chunk-Patches und Fortschrittscursor
   sind je Seite atomar. Ein Tageslimit, Abbruch oder verlorene Antwort lässt sich beim
   nächsten Besitzer-Beitritt fortsetzen; die Quelle bleibt gesperrt und unverändert.
4. Vor Freigabe werden Anzahl, SHA-256-basierte Inhaltsprüfung, Chunk-Zuordnung und
   Berechtigungen kontrolliert. Erst dann werden Wurzel und neue Übersicht atomar geöffnet.
5. Private alte Inventare werden während dieser Migration weder gelesen noch verändert.
   Jedes Gerät übernimmt beim ersten Beitritt nur seinen eigenen bisherigen Vorrat in
   den verschlüsselten neuen Stand. Auch der Besitzer erhält keine fremden Klartextinventare.
   Bauten, Wasserstatus, Fortschritt, Werkzeuge und gesperrte Geräte bleiben erhalten.

Alte Apps können eine migrierte Welt nicht mehr verändern. Nach Protokoll 5 ist eine
Rückkehr zur alten APK kein sicherer Rollback: neue Bauten stehen ausschließlich in Chunks.
Die alten Daten bleiben als unveränderte Ausgangskopie erhalten, werden aber nicht weiter
aktualisiert. Löschen der Welt entfernt beide Strukturen. Es erfolgt kein Tarifwechsel.

Bereits früher autorisierte historische Snapshots verhalten sich wie im bisherigen
Protokoll. Die Emulatortests vergleichen beide Protokolle direkt: historische Antworten
enthalten nur den früheren Stand; nach einer Sperre werden aktuelle Reads und sämtliche
Schreibversuche verweigert. Ein Cache oder historischer Stand lässt sich nicht nachträglich
von einem bereits berechtigten Gerät entfernen.

## Gemessene/prüfbare Grenzen

- Kontrollierte Zwei-Stunden-Simulation, gleicher Zweiesekundentakt, eine Bauaktion je
  Spieler alle 20 Sekunden: bei zwei Spielern 720 Aktionen, 2.160 → 1.440 Blockwrites;
  bei drei Spielern 1.080 Aktionen, 3.240 → 2.160 Blockwrites. Zusätzliche Chunk-Abfragen
  nach dem Ausgangsstand: **null**. Zusätzlich bleiben in einminütigen Prüfungen mit
  zwölf sofortigen Aktionen je Spieler und Abgleich alle Clients ohne Chunk-Abfrage synchron. Positionswrites sind dabei separat zu berücksichtigen;
  sie sinken nur durch bereits vorhandenes Piggybacking.
- Normale Übersicht: eine empfangene Dokumentversion pro Gerät/Abgleich, ohne explizite
  abhängige Regeldokumente. Normale Position: ein Write ohne solche Zusatzdokumente.
  Bau-Commit: zwei Writes; die Regeln referenzieren höchstens zwei verschiedene abhängige
  Dokumentpfade statt Welt, Mitgliedschaft, separatem Inventar, Einzelblock und Übersicht.
- Android-Szenario: 3.000 bestehende Blockdokumente werden zu zwölf räumlichen Chunks.
  Dieser Wert gilt für die gemessene Verteilung, nicht für beliebig verstreute Änderungen.
- Das Änderungsprotokoll ist begrenzt. Bei sehr schnellem Bauen, längerer Abwesenheit,
  kaltem Cache oder einer unklaren Folge ist Nachladen weiterhin erforderlich.
- Die Übersicht ist größer als bei rc.3. Projizierte Antworten und kompakte Ciphertexte
  begrenzen diesen Mehraufwand. Weniger Dokumentoperationen sind kein Nachweis identisch
  reduzierter Netzwerkbytes, realer Abrechnung oder garantierter zusätzlicher Spielstunden.
- Ein globales Optimum und unveränderte Tablet-p99 sind noch nicht belegt. Vergleich auf
  den tatsächlichen Geräten bleibt vor Merge/Freigabe erforderlich.

Der bestehende PR bleibt Entwurf und ungemergt. Keine automatische Produktionsmigration,
Regelveröffentlichung oder öffentliche App-Veröffentlichung durch CI.

Primärreferenzen: [Firestore-Limits](https://firebase.google.com/docs/firestore/quotas),
[Regel-Lesezugriffe](https://firebase.google.com/docs/firestore/pricing),
[BatchGet-Masken und Snapshots](https://firebase.google.com/docs/firestore/reference/rest/v1/projects.databases.documents/batchGet).
