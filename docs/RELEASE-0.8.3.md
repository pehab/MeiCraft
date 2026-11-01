# meiCraft 0.8.3 · Weniger Zugriffe beim gemeinsamen Bauen

- Fällige Positionsmeldungen werden beim Bauen in die bereits nötige Revisionsänderung integriert. Ein zusätzlicher Positions-Commit entfällt. Block und Inventar bleiben sofort und atomar gespeichert; es gibt keine zusätzliche Sammelwartezeit.
- Bereiche mit ausschließlich eigenen, erfolgreich bestätigten Änderungen werden nicht erneut abgefragt. Fremde oder unklare Änderungen lösen weiterhin den normalen Abgleich im Fünf-Sekunden-Takt aus.
- Die Diagnose zählt integrierte Positionsmeldungen und dadurch übersprungene Bereichsabfragen. Diese Werte ergänzen die lokalen Zugriffszähler; sie sind keine Firebase-Abrechnung.
- Protokoll 4, gespeicherte Bauten, Vorräte, Gelände und Einladung bleiben erhalten. 0.8.2 kann mit den aktualisierten Regeln weiterhin mitspielen.

**Vor Nutzung die aktualisierten `firestore.rules` veröffentlichen.** Es ist keine neue Welt und keine Migration einer 0.8.2-Welt erforderlich.

Kontrolliertes Testszenario: 180 Bewegungszyklen mit je zwei Blockaktionen benötigen 1.080 statt 1.260 Dokument-Schreibvorgänge, entsprechend 14,3 % weniger. Bei ausschließlich eigenen Änderungen entfallen die 180 zusätzlichen Bereichsabfragen. Die tatsächliche Einsparung hängt vom Spielverhalten ab; die drei atomaren Dokumentänderungen pro Block bleiben bestehen.

[Einrichtung und Diagnose](ONLINE.md)
