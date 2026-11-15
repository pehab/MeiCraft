# meiCraft 1.0.2 – besser sichtbares Glas mit Glanz

Glas und Kristallglas sind deutlicher sichtbar: Grunddeckkraft etwa 34 statt 20 Prozent, bei flachem Blickwinkel bis etwa 54 Prozent. Eine dezente, blickwinkelabhängige Himmels-/Bodenspiegelung und ein kleiner Lichtglanz lassen Scheiben beim Bewegen besser erkennen. Die vorhandenen Texturen bleiben erhalten.

Die Spiegelung ist eine vereinfachte Shaderdarstellung, keine echte Spiegelung benachbarter Gebäude oder Spieler. Sie benötigt keine zusätzlichen Renderdurchgänge, Weltänderungen, Mesh-Neuberechnungen pro Frame oder Firebase-Zugriffe. Nachtlicht wird berücksichtigt. Undurchsichtige Blöcke behalten ihre bisherige Beleuchtung.

Android-Pixeltests prüfen Grunddeckkraft, Winkelabhängigkeit, Reflexionsfarbe und Nachtlicht. Die bisherigen Atlas-/Glaspass- und Beleuchtungsvergleiche bleiben Teil der CI.

Über die vorhandene Installation aktualisieren; Welten, Inventare und Anmeldung bleiben erhalten. Keine neuen Regeln, Indexe oder Migration erforderlich.
