# Mechanik und Baumwachstum: Stabilitätskorrektur

## Mechanik

Bis zu neun Blöcke können als Gruppe ausgewählt werden, etwa für eine 3×3-Tür. Auch eine vollständige Verschiebung (19 geänderte Zellen einschließlich Schalter) bleibt atomar. Der 16-Ereignis-Livering bleibt kompatibel; ältere Ereignisse stehen weiterhin im dauerhaften Journal.

- Ein Schaltvorgang liest nur die Chunks des Schalters und der tatsächlich überstrichenen Bewegung, statt sämtliche Änderungen ganzer Bereiche herunterzuladen. Ein sitzungsgebundener Cache wird anhand der bestätigten Chunk-Zähler geprüft. Das senkt die Zahl der Anfragen besonders in stark bebauten Welten.
- Nachgewiesene Welt-/Positionskonflikte werden höchstens dreimal neu geplant. Unbekannte Berechtigungsfehler werden nicht als harmlose Konflikte ausgegeben. Nonces, atomare Blockpermutation und die serverseitige Positionssperre bleiben aktiv.
- Eine verlorene Antwort wird über die bestätigte Aktionskennung abgeglichen. Eine bereits gespeicherte Bewegung wird nicht ein zweites Mal ausgeführt.
- Die Auswahl bleibt bis zur Bestätigung erhalten. Nach einem Speicherfehler kann sie weiterverwendet oder bewusst abgebrochen werden.
- Beim Einrichten dürfen Personen oder vorübergehende Hindernisse im Bewegungsweg stehen. Der komplette Weg wird erst bei der tatsächlichen Bewegung geprüft. Geänderte ausgewählte Blöcke werden weiterhin abgelehnt.
- Mechaniken haben gemeinsame Namen (bis 16 Zeichen / 20 UTF-8-Bytes), können umbenannt werden und sind in der Verwaltung nach Entfernung sortiert. Jede Zeile zeigt Name, Blockzahl, Entfernung, Stellung und Schalterkoordinaten. **In der Welt markieren** richtet den Blick auf den Schalter und markiert Schalter und bewegliche Blöcke für 15 Sekunden; es teleportiert niemanden.
- Entfernen verlangt eine Bestätigung und entfernt nur die Verbindung, keine Blöcke.

## Bäume

Der einmalige Tagesdurchlauf konnte blockierte Bäume für den Rest des Tages ausschließen. Die Sperre liegt nun bei jedem Baum: höchstens eine Wachstumsstufe pro UTC-Tag. Ein fertig markierter Weltdurchlauf darf erneut geöffnet werden, um noch fällige Bäume zu prüfen. Nach Hindernissen wird mit einer Minute Abstand erneut geprüft. Ein lokaler Tagwechselwächter erkennt einen neuen UTC-Tag auch in einer offen gebliebenen Sitzung. Vollständig geprüfte Tage erzeugen dadurch keine regelmäßigen Netzabfragen.

Die Diagnose zeigt zusätzlich den Zustand der Baumprüfung, den Checkpoint-Host und einen zuletzt beobachteten gemeinsamen Checkpoint. Null lokal erzeugte Checkpoints allein bedeuten nicht, dass ein anderes Gerät keine Sicherung erstellt hat. Mechanik-, Laub- und Wachstumsfehler haben jetzt unterscheidbare Vorgangsbezeichnungen.

## Einführung

Die aktualisierte `database.rules.json` ist erforderlich, damit ein beendeter Wachstumsdurchlauf erneut geprüft werden darf. Firestore-Regeln und Indizes ändern sich nicht. Die vorhandenen namenlosen Mechaniken und lokalen Spielstände bleiben lesbar. Alle mitspielenden Geräte müssen aktualisiert werden, bevor Mechaniken mit neuen Namen gespeichert werden; 1.1.0 versteht die erweiterten Namensmetadaten nicht. Regeln werden vom PR nicht automatisch bereitgestellt.

## Validierung

Java-Tests für alte/neue Schaltermetadaten, Namensgrenzen, Speicherung und Einrichtung bei blockiertem Weg; Firebase-Regeltests für wiederholte Baumprüfung und weiterhin höchstens eine Wachstumsstufe pro Baum/Tag; Android-Instrumentierung für begrenzte Chunk-Abfragen, abgefangenen Positionskonflikt, verlorene Antwort, echte Regelablehnung, geteilte Namen, Umbenennung, Speicherbestätigung und visuelle Auswahl in der Verwaltung.
