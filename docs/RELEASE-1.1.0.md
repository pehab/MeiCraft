# meiCraft 1.1.0 – Mechanik und visuelle Materialauswahl

## Neu

- Die untere Materialleiste ist horizontal scrollbar und zeigt echte Blocktexturen. Alle Materialien lassen sich ohne Lesen des Materialmenüs auswählen. Wischen ändert weder die Auswahl noch die Blickrichtung.
- Unter **Mechanik** oder über das Zahnrad einen vorhandenen Schalterblock wählen, bis zu sieben feste Blöcke mit dem Fadenkreuz und **+** markieren und Richtung sowie Strecke von ein bis vier Blöcken festlegen.
- Schalter anvisieren und das Zahnrad antippen: Die Gruppe verschiebt sich in einem Schritt; erneutes Schalten bewegt sie zurück. Der gesamte Weg muss frei von Blöcken und Spielern sein. Flüssigkeiten und Laub sind ausgeschlossen. Materialien werden weder verbraucht noch erzeugt.
- Offline und online einschließlich Speicherung, gemeinsamer Blockänderungen, Checkpoints und Wiederbeitritt. Keine Drehung, Bewegungsanimation, automatische Rückstellung oder Mitfahrt von Spielern in dieser ersten Stufe.

## Vor dem Online-Test

1. Die Online-Welt auf allen Geräten verlassen.
2. In Firebase **Realtime Database → Regeln** den gesamten Inhalt durch die Regeldatei dieser Version ersetzen und veröffentlichen: [database.rules.json](https://raw.githubusercontent.com/pehab/MeiCraft/v1.1.0/database.rules.json).
3. Alle mitspielenden Geräte auf 1.1.0 aktualisieren, bevor eine Mechanik eingerichtet oder ausgelöst wird. Ältere Apps verstehen die neuen Schaltermetadaten nicht.

**Nur die RTDB-Regeln ändern.** Firestore-Regeln und Indizes bleiben unverändert. Die Veröffentlichung der App installiert die Regeln nicht automatisch.

Lokale Welten werden im Format 9 gespeichert. Bisherige Formate 1–8 bleiben lesbar; ältere Apps können neue Spielstände nicht öffnen. Über die vorhandene Installation aktualisieren und App-Daten erhalten.

[Bedienung und Grenzen](https://github.com/pehab/MeiCraft/blob/v1.1.0/docs/MECHANICS.md)

## Prüfungen

Java-Tests einschließlich 156 Mechanik- und 30 Gestenprüfungen, 129 Firebase-Regeltests sowie Android-Build, Lint und Instrumentierung auf API 30 und 37. Geprüft werden unter anderem Hin-/Rückweg, Hindernisse, Inventarerhalt, atomare Online-Bewegung über Bereichsgrenzen, zweiter Spieler, Checkpoint, Wiederbeitritt und Materialauswahl per Wischen/Antippen.
