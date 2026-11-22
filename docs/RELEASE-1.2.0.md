# meiCraft 1.2.0 – Versteckspielen und Druckplatten

- **Spielmodus → Umschauen** sperrt direkte Bau-, Abbau-, Angriffs- und Eimeraktionen. Bewegung, Springen und Umsehen bleiben frei. Die Einstellung wird auf dem Gerät gespeichert.
- **Anzeigehilfen → Ausblenden** blendet Weltkarte, Namen über Spielerfiguren und Positionsangaben in der Spielerliste aus. Die Figuren bleiben normal sichtbar. Diese Einstellung ist unabhängig vom Umschauen-Modus und wird auf dem Gerät gespeichert.
- **Alle Mechaniken bleiben im Umschauen-Modus aktiv.** Versteckte Türen lassen sich über Schalter oder Druckplatten öffnen.
- Bei der Mechanik-Einrichtung **Antippen** oder **Draufstehen** wählen. Im Druckplatten-Modus bleibt die Gruppe verschoben, solange mindestens ein Spieler auf einer verbundenen Platte steht. Nach Verlassen kehrt sie nach der gewählten Verzögerung (0, 2 oder 5 Sekunden) zurück, sobald der gesamte Weg frei ist. Erneutes Betreten setzt den Countdown zurück. Hindernisse werden niemals überschrieben.
- Bis zu **drei Schalter/Druckplatten** können dieselbe Gruppe steuern. In der Verwaltung **Weiteren Schalter hinzufügen** wählen und einen Block mit + markieren. Weitere Auslöser müssen im selben Weltbereich und höchstens 16 Blöcke pro Achse vom ersten Schalter entfernt liegen.
- Auslöser-Art nachträglich ändern und zusätzliche Schalter entfernen. Namen, Bewegung, Rückweg und bis zu neun bewegliche Blöcke bleiben erhalten.
- Online-Druckplatten nutzen die vorhandenen Spielerpositionen. Zusätzliche Serverabfragen erfolgen nur bei einem lokal erkannten Stellungsunterschied, nicht bei unverändertem Zustand. Auf dem Worker wird die gewünschte Stellung erneut aus dem bestätigten Stand berechnet, damit mehrere Spieler keine Doppelbetätigung auslösen.

## Einführung

Alle mitspielenden Geräte aktualisieren, dann in **Firebase Realtime Database → Regeln** die [database.rules.json](https://raw.githubusercontent.com/pehab/MeiCraft/v1.2.0/database.rules.json) vollständig ersetzen und veröffentlichen. Die erweiterten Mechanik-Metadaten benötigen die neuen RTDB-Regeln. Firestore-Regeln und Indizes bleiben unverändert. Vorhandene Mechaniken und lokale Spielstände bleiben lesbar; ältere Apps verstehen die neuen Druckplatten- und Mehrfachschalter-Metadaten nicht.

Diese Version ist im PR vorbereitet. Ein Merge oder eine Veröffentlichung wird dadurch nicht ausgeführt.
