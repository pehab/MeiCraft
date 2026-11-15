# 1.0.0-rc.3 · Lokale Online-Bereichsstände

- Wiederbeitritt verwendet vollständig abgeglichene lokale Blockstände, einschließlich
  abgebauter Blöcke und erneuerbarer Wasserquellen. Die normale Prüfung von Welt,
  Mitgliedschaft und Inventar auf dem Server bleibt vor der Verwendung bestehen.
- Gleiche Bereichsrevision: keine Blockabfrage. Neuere Revision: nur Änderungen nach
  dem letzten vollständig gelesenen Server-Snapshot, über alle Seiten konsistent.
- Unvollständige Downloads verändern den gespeicherten Checkpoint nicht. Fehlende,
  beschädigte, inkompatible oder vom Betriebssystem entfernte Cachedateien führen zum
  vollständigen normalen Nachladen. Zurückgesetzte Revisionen verwenden keine neuere
  Zeitgrenze. Cache ist kein Backup und ermöglicht keinen Offline-Zutritt.
- Atomare Dateien mit Prüfsumme; maximal 16 MiB pro Datei und 64 MiB insgesamt.
  Hintergrundschreiber hält höchstens den neuesten wartenden Stand pro Bereich.
  Der Netzwerkworker kopiert nur vollständige geänderte Stände; Datei-I/O und fsync
  laufen getrennt von Netzwerk und Rendering. Bestätigte Bauaktionen warten nicht auf
  die Festplatte. Letzte Änderungen vor einem abrupten Ende werden nötigenfalls beim
  nächsten Beitritt erneut heruntergeladen; Cloud-Daten bleiben maßgeblich.
- Diagnose zeigt wiederhergestellte Bereiche/Blockstände und sichert den lokalen
  Netzwerk-Zählerbericht samt App-Version beim Fehler für den nächsten App-Start.
  Die Zähler sind weiterhin keine Abrechnungswerte; Regeln, Indexzugriffe und leere
  Mindestabfragen sind darin nicht enthalten. Gesicherter Bericht enthält keine Rohantworten,
  URLs, Zugangsdaten oder Einladungen.

## Häufigere Positionen

Alle Spielerpositionen stehen bereits in einem gemeinsamen Übersichts-Dokument.
Ein erfolgreicher Poll empfängt dieses eine Dokument, unabhängig von der Spielerzahl;
zusätzliche Regelzugriffe sind darin nicht enthalten. Die schnellere Taktung erhöht
bei zwei ständig bewegten Spielern den rechnerischen Zweistundenbedarf von 2.880 auf
7.200 Übersichts-Dokumente und von bis zu 2.880 auf 7.200 Positions-Dokumentwrites,
vor Bauaktionen und zusätzlichen Regel-/Indexzugriffen. Vorhandene Piggyback-Commits
können separate Positionswrites einsparen. Das ist ein bewusster Verbrauchs-Tausch
für häufigere Bewegungssynchronisierung, keine weitere Verbrauchsreduktion.

## Prüfung

- Hosttests prüfen 3.000 gespeicherte Änderungen, entfernte Blöcke, Wasserstatus,
  exakte Zeitstempel, Welt-/Seed-/Größenisolation, fehlerhafte und abgeschnittene Dateien,
  atomaren Erhalt des alten Checkpoints und Fertigschreiben beim Sitzungsende.
- Android-Instrumentierung prüft den echten Session-/REST-Pfad: kalter Beitritt,
  erneuter unveränderter Beitritt ohne Blockabfrage, genau ein offline entstandener
  Peer-Edit und Wiederholung nach beschädigtem Cache plus Abbruch der zweiten Seite.
- Zusätzliche Android-Prüfung: ein HTTP-429-Fehler hinterlässt Zähler und bereinigte
  Fehlerbeschreibung in persistenten Einstellungen.
- Firestore-Protokoll, Regeln und sofortige atomare Bau-Commits bleiben unverändert.
  Bei mindestens einem weiteren aktiven Spieler werden Übersicht und bewegte Positionen
  alle zwei Sekunden abgeglichen; allein bleiben es fünf Sekunden. Stillstehende Positionen
  bleiben bei 30 Sekunden. Eigene Präsenz zählt nicht als Mitspieler. Quoten-Backoff bleibt
  maßgeblich. Es gibt keine Firebase-Veröffentlichung oder Änderung am kostenlosen Tarif.

## Gerätetest auf beiden Tablets

1. Beitreten, mehrere Bereiche ansehen, bauen/abbauen, mindestens einen erfolgreichen
   Abgleich abwarten, verlassen und erneut beitreten. Bauzustand muss gleich sein;
   Diagnose muss wiederhergestellte Bereiche melden, Blocklesevorgänge stark sinken.
2. Ein Gerät verlässt die Welt. Das andere verändert alte und neue Blockpositionen.
   Wieder beitreten: alle Änderungen müssen sichtbar sein, alte Bauten erhalten bleiben.
3. Direkt nach einer bestätigten Aktion beenden und erneut starten. Fehlende jüngste
   Cacheänderungen müssen aus der Cloud nachgeladen werden.
4. Flüssigkeit und Aktionsdauer mit rc.2 vergleichen. Eine garantierte Gesamtlaufzeit
   bis zum Tageslimit lässt sich aus dem kontrollierten Cache-Test nicht ableiten.

PR #20 bleibt Entwurf und ungemergt; kein öffentliches Release.
