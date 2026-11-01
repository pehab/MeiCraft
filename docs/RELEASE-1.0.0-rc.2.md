# meiCraft 1.0.0-rc.2 · Weniger Wiederholungen und Grafikarbeit

Weiterer Testkandidat auf PR #20. Bleibt ohne Merge und ohne öffentliches App-Release.

- Vollständig bekannte Bereiche erhalten auch nach ausschließlich eigenen bestätigten
  Änderungen einen aktuellen Snapshot-Wasserstand. Eine spätere Änderung eines Mitspielers
  lädt dadurch nicht die eigene, ältere Bauhistorie nochmals herunter.
- Inventar-Updates übertragen nur veränderte Felder plus Aktionsbezug. Serverversion und
  atomarer Block-/Inventar-/Revisions-Commit bleiben gleich. Das Inventar wird nicht
  vor einer Serverbestätigung verändert. Drei Dokumentänderungen pro Block bleiben bestehen.
- Geländeteile, die vollständig außerhalb des Kamerabilds liegen, müssen nicht von der
  GPU gezeichnet werden. Der bestehende Aufbau dieser Abschnitte bleibt erhalten, damit
  Umdrehen keinen neuen Geometrie-Stau erzeugt. Nebel, Sichtweite und Geometrie bleiben gleich.
- Bei voller Umgebungshelligkeit entfallen die acht Lampen-Distanzberechnungen pro
  Gelände-Pixel: Keine Lampe dieser App ist heller als das bereits volle Umgebungslicht.
  Nacht-/Dämmerungslicht und selbstleuchtende Materialien behalten ihre Berechnung.
  Ein GPU-Pixelvergleich gegen den alten Shader prüft Tag, Dämmerung, Nacht, Nebel und
  normale bzw. selbstleuchtende Flächen im Android-Test.
- Häufige Hilfsarrays, Shader-Samplerposition und leere Mitspielerlisten werden wiederverwendet.
- Diagnose ergänzt p99 der Frame-/CPU-Zeiten und gezählte ausgelassene Gelände-Zeichnungen.
- Nach grüner PR-CI wird für diesen eigenen Entwicklungsbranch eine APK mit der bestehenden
  Update-Signatur als `meiCraft-test-apk` bereitgestellt. Kein Merge oder Release dafür nötig.

Optional vorbereitet: `firestore.indexes.json` entfernt nicht benutzte Feldindizes, vor allem
auf den großen Spieler-/Revisionskarten und dem Inventar. Der für Blockabfragen nötige
aufsteigende `updatedAt`-Index bleibt erhalten. Das verändert keine Daten und spart keine
abgerechneten Dokument-Schreibvorgänge; es soll unnötige serverseitige Indexarbeit verringern.
Diese Konfiguration ist im PR vorbereitet und wird **nicht automatisch in Firebase veröffentlicht**.

Protokoll 4, Firestore-Regeln, bestehende Welten, Einladungen und Vorräte bleiben erhalten.
Die endgültige 1.0-Freigabe benötigt weiterhin den Vergleich auf den tatsächlichen Tablets.
