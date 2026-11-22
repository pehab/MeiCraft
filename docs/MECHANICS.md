# Einfache Mechanik und Materialleiste

## Bedienung

Die untere Materialleiste lässt sich mit dem Finger nach links und rechts schieben. Alle Materialien zeigen ihre Blocktextur. Antippen wählt ein Material; Wischen wählt nichts und dreht die Kamera nicht. Die Anzahl bleibt im Story- und Online-Modus sichtbar. Das bisherige Materialmenü bleibt zusätzlich erreichbar.

Im Menü **Mechanik** oder über das Zahnrad:

1. **Neue Mechanik** wählen, einen Namen und **Antippen** oder **Draufstehen** wählen, einen festen Schalterblock anvisieren und **+** antippen.
2. Bis zu **neun feste Blöcke** einzeln anvisieren und mit **+** markieren. Erneutes Antippen entfernt eine Markierung. Markierungen erhalten einen Rahmen.
3. Das Zahnrad antippen, eine der sechs Weltachsen und eine Strecke von **ein bis vier Blöcken** wählen.
4. Die Zusammenfassung prüfen und speichern. Die Auswahl bleibt bis zur Speicherbestätigung erhalten.
5. Zum Schalten den Schalter anvisieren und das Zahnrad antippen. Beim nächsten Schalten kehrt die Gruppe zurück. Im Modus **Draufstehen** hält jede belegte Druckplatte die Gruppe verschoben. Sobald der letzte Spieler die Platten verlässt, kehrt sie automatisch zurück, wenn der Weg frei ist.

Richtung X/Z bezieht sich auf die Weltachsen, nicht auf die Blickrichtung. Mit kleinen Strecken ausprobieren. Beispiel: sechs Ziegel als Wand, daneben einen Eisenblock als Schalter; die Wand zwei Blöcke seitlich verschieben. Die Bewegung erfolgt in einem Schritt, ohne Drehung oder freie Physik. Keine automatische Rückstellung und keine Mitfahrt von Spielern.

Der Schalter bleibt stehen. Flüssigkeiten und Laub werden nicht bewegt. Der gesamte Weg muss frei sein, auch der Rückweg. Spieler blockieren eine Bewegung; blockierte oder inzwischen veränderte Gruppen bleiben vollständig stehen. Es werden keine Materialien verbraucht oder gutgeschrieben. **Verbindungen entfernen** löscht nur die Zuordnung und lässt alle Blöcke an ihrer aktuellen Position.

In der Verwaltung zeigen die nach Entfernung sortierten Einträge Name, Blockzahl, Entfernung, Stellung und Schalterkoordinaten. Über **In der Welt markieren** den Blick auf den Schalter richten und die Gruppe für 15 Sekunden hervorheben. Dort sind auch Schalten, Umbenennen und Entfernen erreichbar. Über **Weiteren Schalter hinzufügen** können bis zu drei Auslöser dieselbe Gruppe steuern. Zusätzliche Schalter müssen im gleichen Weltbereich und höchstens 16 Blöcke pro Achse vom ersten Schalter entfernt liegen. **Auslöser ändern** wechselt zwischen Antippen und Draufstehen.

## Offline und online

Offline werden Zuordnung und Schalterstellung im Spielstandformat 9 gespeichert; Formate 1–8 bleiben lesbar. Alte App-Versionen können neue lokale Spielstände nicht lesen. Vor einem Rückwechsel eine Sicherung behalten.

Online werden selbst gebaute Blöcke ausgewählt; der gesamte Bewegungsbereich muss geladen sein. Schalterdefinitionen sind begrenzte Metadaten ihrer regulären Spielstandzelle. Sie werden durch Journal, Bereichscache und Firestore-Checkpoint mitgesichert. Jede Bewegung inklusive Schalterstellung ist eine einzige atomare RTDB-Änderung mit höchstens 19 Zellen. Versionskonflikte und inzwischen geänderte Spielerpositionen verhindern die Veröffentlichung eines veralteten Plans. Die Regeln verlangen eine eindeutige Zuordnung jedes ursprünglichen festen Blocks zu genau einem Ziel und verbieten Materialwechsel, Duplikate, Verlust und Teilpakete.

**Vor dem Online-Test auf allen mitspielenden Geräten aktualisieren und die neue `database.rules.json` veröffentlichen.** Die alten Clients verstehen die neuen Schaltermetadaten nicht. Firestore-Regeln und Indizes bleiben unverändert. Die Regeln werden durch den PR nicht automatisch bereitgestellt. Ohne aktualisierte RTDB-Regeln wird eine Online-Bewegung abgelehnt.

Keine laufende Abfrage nur für Mechaniken: Konfiguration und Betätigung nutzen den vorhandenen Online-Worker. Ein betätigter Schalter liest einen frischen Stand der betroffenen Bereiche und Spielerpositionen; normale Bauaktionen bleiben unverändert.

## Prüfungen

- Reine Java-Tests: alle Richtungen und Strecken, überlappende Quell-/Zielpositionen, feste Hindernisse entlang des Weges, Spieler, Rückweg, Inventarerhalt, Speicherung und ungültige Metadaten.
- Materialleiste: Antippen, Wischschwelle, Randbegrenzung, Rückwärtswischen und jedes Material.
- RTDB-Emulator: atomare Bewegung, verweigerte Duplikate, Blockverlust, Materialwechsel, falsche Nonces, Teilpakete und geänderte Spielerpositionen.
- Android-Instrumentierung: Online-Bewegung über Bereichsgrenzen, zweiter Spieler, Rückbewegung, Vorräte, Checkpoint und Wiederbeitritt mit warmem Bereichscache.

## Umschauen / Versteckspielen

Im Pausenmenü unter **Spielmodus** zwischen **Bauen** und **Umschauen** wechseln. Die Einstellung gilt für dieses Gerät und wird beim Neustart beibehalten. Umschauen verhindert direktes Setzen, Abbauen, Angreifen und Aufnehmen von Flüssigkeiten. Laufen, Springen und Umsehen sowie alle Mechaniken bleiben aktiv. Sowohl Druckplatten als auch über ⚙ betätigte Schalter lassen sich für versteckte Türen nutzen. Der Modus ist eine persönliche Bedienungssperre; andere Spieler können ihren eigenen Modus wählen.

Version 1.2.0 verwendet für Druckplatten und mehrere Schalter erweiterte Mechanik-Metadaten. Vor der gemeinsamen Nutzung alle Geräte aktualisieren und die RTDB-Regeln von 1.2.0 einspielen. Vorhandene Mechaniken bleiben unverändert lesbar. Firestore-Regeln und Indizes ändern sich nicht.
