# meiCraft 1.0.1 – Korrektur der Geländetexturen

Behebt falsch zugeordnete und gestreifte Geländetexturen in Offline- und Online-Welten. Der Gelände-Renderer verwendete versehentlich den alten Vergleichspfad mit UV-Koordinaten für einen 1024 Pixel breiten Atlas statt des aktuellen 2048-Pixel-Atlas. Außerdem wurde Glas dadurch zusätzlich im undurchsichtigen Durchgang gezeichnet.

Der Renderer verwendet nun ausdrücklich den undurchsichtigen aktuellen Durchgang. Der alte Pfad ist als reiner Vergleichspfad benannt, damit er nicht versehentlich als Standard verwendet wird. Neue Tests prüfen beide Glasarten, die tatsächlichen Mesh-Uploads und einen Pixel aus dem echten Texturatlas auf Android.

Der Fehler betrifft die Darstellung. Gespeicherte Block-IDs, Bauten und Inventare werden durch die Korrektur nicht verändert. Das Update benötigt keine neuen Firebase-Regeln und keine zusätzliche Migration. Über die vorhandene Installation aktualisieren; nicht deinstallieren oder App-Daten löschen.
