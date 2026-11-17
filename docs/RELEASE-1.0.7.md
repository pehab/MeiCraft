# meiCraft 1.0.7 – Erster Beitritt zu Online-Welten

Behebt einen Berechtigungsfehler beim ersten Beitritt zu einer Online-Welt: Die RTDB-Mitgliedschaft wird jetzt mit dem Einladungsnachweis angelegt, bevor die App den geschützten Spielstand liest. Dadurch scheitern neue Mitspieler nicht mehr an `RTDB · GET · storage/control · HTTP 401 · Permission denied`.

Bestehende Mitgliedschaften, Einladungsprüfung und Spielersperren bleiben erhalten. Keine Änderungen an Firebase-Regeln, Indizes oder Speicherformaten erforderlich. Über die bestehende Installation aktualisieren; App-Daten und Geräteanmeldung erhalten.

Prüfungen: lokale Java-Tests, 126 Firestore-/RTDB-Regeltests sowie Android-Build, Lint und Instrumentierung auf API 30 und 37. Der Erstbeitritt wird mit einem Transport geprüft, der Spielstandzugriffe vor angelegter RTDB-Mitgliedschaft ablehnt; Regeltests prüfen zusätzlich falsche Einladungen und bestehende Sperren.
