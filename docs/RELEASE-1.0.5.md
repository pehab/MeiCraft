# meiCraft 1.0.5 – Online-Aktionen und fortsetzbare Hintergrundarbeit

Bauen/Abbauen teilt sich mit Rohstoffergänzung und Natur einen seriellen Netzwerk-Worker. Die bisherigen Jobs konnten mehrere Bereiche samt Chunk-Downloads und Bestätigungen in einem Durchlauf bearbeiten. Spieleraktionen mussten dahinter warten.

- Die geplante Rohstoffprüfung verarbeitet höchstens einen Standort in einem Bereich pro Schritt; die Bereiche wechseln reihum. Naturarbeit verarbeitet ebenfalls einen Bereich pro geplantem Schritt.
- Wartende Spieleraktionen unterbrechen Hintergrundarbeit vor weiteren Chunk-Downloads und vor dem nächsten Commit. Bereits bestätigte Änderungen bleiben erhalten; der nächste Schritt setzt anhand der dauerhaften Cursor fort. Laufende einzelne HTTP-Anfragen werden nicht abgebrochen.
- Ein abgelehntes Paket wird gegen den aktuellen Journalstand, erforderliche Spielerpositionen und den gemeinsamen Naturtakt geprüft. Ein nachgewiesener Konflikt wird ohne Verbrauch des Fehlerbudgets neu eingeplant. Unveränderte Regelablehnungen bleiben Fehler. Dadurch muss eine unterbrochene Ergänzung nicht nach fünf normalen Konflikten dauerhaft stehenbleiben.
- Eine überholte tägliche Baumprüfung kann mit ihrer eigenen noch gültigen Tages-Lease fortgesetzt werden.
- Doppelte Kopfabfragen vor Hintergrundpaketen entfallen.
- RTDB-Fehler behalten die strukturierte Firebase-Meldung und zeigen Methode sowie bereinigten Pfad. Eine ausdrückliche „Permission denied“-Antwort invalidiert den gültigen Anmeldetoken nicht mehr.

Die zuvor vorbereitete Besitzer-Ausnahme für das leere `nature`-Feld während der Altweltenmigration bleibt enthalten. Diese vollständige `database.rules.json` muss veröffentlicht sein; darüber hinaus werden keine Regeln oder Indizes geändert. Die APK über die vorhandene Installation aktualisieren. Bestehende Kohle bleibt erhalten, noch offene Kristall-/Lavastandorte werden anhand derselben geschützten Pläne weitergeprüft; bearbeitete Standorte werden nicht aufgefüllt.

Prüfung: vollständige aufeinanderfolgende Pakete mit 8 Kohle- sowie je 12 Kristall-/Lavazellen gegen die echten Firebase-Emulatorregeln, danach Laubpaket. Android-Transportprüfungen erfassen wartende Spieleraktionen, schrittweise Ergänzung, Fehlertext und Tokenschutz. Die tatsächliche p95-Latenz auf dem Tablet und die konkreten Vorkommen in der betroffenen Produktivwelt müssen nach dem Update erneut gemessen werden. Die alte Diagnose enthält nicht genug Fehlerdetails, um jeden der bisherigen 401 eindeutig zuzuordnen.
