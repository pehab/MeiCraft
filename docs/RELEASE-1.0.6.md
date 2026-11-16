# meiCraft 1.0.6 – Sand wird auch online zu Glas

Sand mit direktem Kontakt zu Lava wird nun auch in Online-Welten zu Glas. Kontakt über eine der sechs Blockflächen genügt, auch Sand direkt über einer Lavaquelle und Kontakt über Bereichsgrenzen. Diagonalen und Luftspalten zählen nicht. Die Lava wird nicht verbraucht.

Die Umwandlung erfolgt im nächsten geplanten Reaktionsschritt, normalerweise nach etwa drei Sekunden; Bauaktionen behalten Vorrang. Welt geöffnet lassen. Bereits mit 1.0.5 gesetzter Sand wird beim nächsten Beitritt mitgeprüft. Alte, unberührte natürliche Sand-/Lavaformationen bleiben geschützt. Lava, die im selben Schritt durch Wasser zu Basalt wird, kann den Sand nicht mehr schmelzen. Offline bleibt die bestehende Sandreaktion unverändert.

Glas, Journal, Spielerrevisionen und Bereichszähler werden atomar gespeichert. Es werden keine zusätzlichen Inventargegenstände erzeugt. Mitspieler, Checkpoints und erneuter Beitritt übernehmen denselben Glasblock.

Über die bestehende Installation aktualisieren. Keine neuen Firebase-Regeln, Indizes oder Speicherformate nötig. Mitspieler ebenfalls aktualisieren, damit alle dieselbe Reaktionslogik verwenden.

Prüfungen: alle sechs Kontaktrichtungen, Diagonale und Luftspalt, Schutz natürlicher Altbestände, gleichzeitige Wasserabkühlung, atomare RTDB-Regeln, zwei Spieler, Bereichsgrenze, unveränderte Vorräte/Lava sowie Checkpoint und Wiederbeitritt.
