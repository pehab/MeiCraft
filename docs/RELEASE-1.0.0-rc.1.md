# meiCraft 1.0.0-rc.1 · Verhalten erhalten, Umsetzung messen

Testkandidat für die 1.0-Optimierung. Keine endgültige Performancefreigabe.

- Online-Abgleiche lesen Übersicht und Blockseiten aus demselben Server-Snapshot.
  Bereits vollständig gelesene Zeitstempelgrenzen werden nicht nochmals übertragen.
- Blockabfragen übertragen nur die benötigten Felder; Versions- und Rechteprüfungen bleiben erhalten.
- Neuer separat prüfbarer Geometriekern mit identischem Vertexstrom und wiederverwendeten
  Geometrie-/Uploadpuffern. Keine Änderung von Texturen, Sichtweite, Landschaft oder Regeln.
- Online-Diagnose ergänzt Frameabstände, CPU-Zeiten und Geometriezeiten für Gerätevergleiche.
- Protokoll 4 und Firestore-Regeln bleiben unverändert. Keine neue Welt oder Migration erforderlich.
  Alle Geräte brauchen weiterhin mindestens die bereits für 0.8.3 veröffentlichten Regeln.
- Abfrageintervall, unmittelbare Einzel-Commits und drei atomare Dokumentänderungen je Block bleiben gleich.

Die endgültige 1.0 setzt grüne technische Prüfungen und einen Vergleich auf den tatsächlichen
Tablets voraus. Desktop-Benchmarks allein erlauben keine Aussage über die Gesamt-Framerate.

[Verträge, Architektur und Freigabekriterien](1.0-CONTRACT.md)
