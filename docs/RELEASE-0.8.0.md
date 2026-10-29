# meiCraft 0.8.0 · Zusammenhängende Online-Welten mit Rohstoffen

- Online startet zu Fuß; Blöcke müssen gesammelt oder hergestellt werden.
- Werkzeugfortschritt und Werkbank funktionieren wie im lokalen Storymodus.
- Eigene Vorräte bleiben pro Online-Welt beim erneuten Öffnen erhalten.
- Bau- und Abbauänderungen werden atomar mit dem Inventar bestätigt; gleichzeitiges Abbauen vervielfacht keine Rohstoffe.
- Durchgehende Weltkoordinaten mit Nachladen in Sichtweite: keine Abschnittswechsel oder Versetzungen mehr.
- Farbige Mitspieler mit Haut, Kleidung, Blickrichtung und leichter Laufanimation.
- Kleine Weltkarte mit Gesamtgröße und Position.

Die Welt bleibt ohne Tag/Nacht-Wechsel, Gegner, Flüssigkeitsströme und automatische Materialreaktionen. Lokale Spielstände und vorhandene Online-Bauten bleiben erhalten.

**Einmalig erforderlich:** Die aktualisierte `firestore.rules` veröffentlichen. Bestehende Online-Welten zuerst vom Besitzer mit 0.8.0 öffnen; anschließend alle Mitspieler auf 0.8.0 aktualisieren. Neue Inventare starten leer.
