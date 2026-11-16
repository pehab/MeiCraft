# meiCraft 1.0.3 – Rohstoffe in bestehenden Welten finden und ergänzen

- Bestehende Online-Welten erhalten beim Besitzerbeitritt einmalig sichere Kohleadern, kleine Kristallhöhlen und geschlossene Lavataschen. Auch bereits migrierte Welten werden berücksichtigt. Welt-ID, vorhandene Bauten und der alte Generator bleiben erhalten.
- Ergänzung ausschließlich in ursprünglichem, unberührtem Stein mit kompletter Schutzschicht um die Kammer; bearbeitete Zellen, Gebäude, Wasser und belegte Plätze werden übersprungen. Jeder Platz wird atomar im bestehenden RTDB-Journal gespeichert. Kein erneutes Auffüllen abgebauter Vorkommen.
- Kleine Oberflächenhinweise für Kohle, Kristalle und Lava sowie ein Tiefenhinweis beim Annähern – offline und online. Die Hinweise beruhen auf tatsächlich vorhandenen geladenen Blöcken, verändern keine Welt und erzeugen keinen Firebase-Traffic.

**Vorher einmal die neue RTDB-Regeldatei `database.rules.json` installieren.** Firestore-Regeln und die 19 Indexeinträge bleiben unverändert. Dann alle Geräte über die vorhandene Installation aktualisieren und zuerst als Besitzer beitreten. Keine App-Daten löschen. Die Veröffentlichung deployt keine Regeln.

Details, Schutzregeln, Wiederaufnahme und erwarteter Traffic: [Rohstoffergänzung](https://github.com/pehab/MeiCraft/blob/main/docs/RESOURCES-1.0.3.md).
