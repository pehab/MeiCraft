# Nachwachsende Bäume und Laubzerfall

## Spielverhalten

Ein vom Spieler einzeln auf Gras/Erde gesetzter Laubblock ist ein Setzling. Direkt angrenzende Laubblöcke werden als Dekoration behandelt. Es gibt keine neuen Block-IDs. Online durchläuft der Baum vier Zustände: einzelnes Laub, ein Stammblock mit Laubspitze, zwei Stammblöcke mit Laubspitze, vier Stammblöcke mit kompakter Krone. Frühestens am folgenden UTC-Tag wächst er eine Stufe. Versäumte Tage werden nicht nachgeholt. Ausgewachsene Bäume wachsen nicht weiter.

Der erste Beitritt pro UTC-Tag übernimmt eine serverseitig geprüfte 15-Sekunden-Lease mit ETag-CAS. Die Regeln vergleichen den Tag mit `now` auf dem Server. Jeder gewachsene Baum erhält im selben atomaren Write seinen neuen Tagescursor. Nach einem Abbruch kann der nächste Beitritt die abgelaufene Lease übernehmen; bereits gewachsene Bäume werden übersprungen. Ein abgeschlossener Tageslauf bleibt bis zum nächsten UTC-Tag geschlossen. Ein wegen Platzmangel blockierter Baum wartet auf einen späteren Tageslauf.

Wachstum überschreibt nur die eigene Laubspitze, sonst ausschließlich Luft. Boden, Stamm und Platz werden vor jeder Stufe erneut geprüft. Aktuelle eigene und über RTDB registrierte fremde Körper blockieren Wachstum. Ein atomarer Positionszähler verwirft Wachstumspläne, wenn während der Prüfung jemand beitritt, sich bewegt oder die Welt verlässt. Reine Laubentfernung benötigt diese Sperre und Positionsabfrage nicht und bleibt beim Herumlaufen anderer Spieler aktiv. Auch beliebige konkurrierende Weltänderungen verwerfen den gesamten Plan. Die kompakte Krone bleibt innerhalb ihrer Region.

## Dauerhafter Zustand und Konflikte

`JournalBatch` schreibt bis zu 16 normale Zell-/Inventarrevisionen in einem einzigen RTDB-Multi-Location-Update. Jede Revision hat einen regulären dauerhaften Journaleintrag, eine Nonce-Kette und einen Platz im bestehenden 16er-Ring. Ein Batch erhöht `head/g` einmal; Regions-/Chunkzähler steigen exakt um ihre jeweilige Zellanzahl. Regeln prüfen alle betroffenen Zellen, alle Journaleinträge, Gegenstände, Zähler und Vorbedingungen. Teilupdates scheitern vollständig. Baumwachstum und Zerfall verbrauchen oder erzeugen keine Inventargegenstände.

Die vorhandenen Wege für Live-Head, Ringlücken, Chunk-Neuladen, Regionscache und Checkpoints bleiben zuständig. Es werden keine unbestätigten Naturblöcke lokal gemalt. Jede Baumwurzel trägt `tree_<Stufe>_<UTC-Tag>_<Pflanzzeit>` als Zellmetadaten, erzeugtes Laub `leaf`, platziertes Dekolaub `decor`. Metadaten werden im Journal und in den normalen Chunk-Checkpointwerten mitgespeichert. Ein fehlender sekundärer Setzlingsindex wird einmalig daraus rekonstruiert; dies setzt keine Wachstumsstufen oder Tagescursor zurück.

Ältere Zellen und siebenteilige Journaleinträge bleiben lesbar. Die Naturmetadaten verwenden optional ein achtes Journalfeld bzw. viertes Zellwertfeld. Bestehende Online-Welten brauchen keine Landschaftsmigration. Vor Verwendung die neue `database.rules.json` installieren und alle mitspielenden APKs aktualisieren. Alte APKs können die erweiterten Ereignisse nicht sicher lesen; ihre v6-Positionswrites ohne atomaren Positionszähler werden abgelehnt. Firebase-Regeln werden durch PR/CI nicht deployt.

## Laubzerfall und Schutz

Online wird nach Beitritt und nach erkanntem Stammabbau eine lokale, begrenzte Prüfung angestoßen. Nur bei tatsächlich vorhandenem ungestütztem Laub folgen Datenbankzugriffe. Maximal vier Blätter je Region verschwinden je drei Sekunden als ein atomarer Batch. Ein gemeinsamer Serverzeit-Cursor begrenzt auch mehrere gleichzeitig prüfende Clients auf dieses Tempo. Ein neuer Beitritt setzt unterbrochenen Zerfall fort. Der bestätigte Journalzustand ist für alle Spieler maßgeblich. Konkurrenz führt zum Verwerfen des Plans; spätere Stammänderungen oder Beitritte prüfen erneut.

Holz im horizontalen Radius 3 (Manhattan-Abstand höchstens 4), vertikal ±4 schützt Laub. Blätter direkt auf Gras/Erde sowie registrierte Wachstumsspitzen bleiben bestehen. Spielerplatzierungen unbekannter/alter Herkunft werden vorsichtig als Dekoration geschützt; nur unverändertes generiertes Laub und ausdrücklich erzeugte Kronen zerfallen. Bereits schwebende natürliche Kronen werden beim Beitritt nach und nach entfernt. An Regionsrändern werden notwendige Nachbarregionen zur Holzstützenprüfung nachgeladen; dortige Stämme schützen ihre grenzübergreifenden Kronen ebenfalls.

Offline behalten Bäume ihre bisherige zeitgesteuerte vollständige Wachstumsmechanik. Neu platzierte Laubbauten werden jetzt ausdrücklich geschützt. Seit Speicherformat 6 werden natürliche und platzierte Blätter kompakt als Bitsets persistiert. Aktuell speichert Format 8 zusätzlich Materialreaktionen, fertige Holzkohle und Generationsversion; Formate 1–7 bleiben lesbar. Bei alten Offline-Spielständen wird die generierte Ausgangslandschaft zum Erkennen natürlichen Laubs verwendet; historisch ausgetauschtes Laub an exakt derselben natürlichen Position ist nicht unterscheidbar.

## Traffic

Keine zusätzliche Stream-Verbindung und kein permanentes Naturpolling. Nur tatsächliche Ortsänderungen, Beitritt und Verlassen ergänzen im vorhandenen Positionswrite einen skalaren Inkrementwert, ohne weitere HTTP-Anfrage. Stationäre Keepalives und reine Blickdrehungen verändern diesen Zähler nicht; im Leerlauf entstehen keine zusätzlichen Natur-Nutzdaten. Jeder Beitritt liest den kleinen Tageslauf; der Gewinner liest den Setzlingsindex und prüft die betroffenen Regionen. Er lädt nur veränderte Chunks nach, vorhandene Natur-Snapshots werden zwischen Bäumen/Zerfallswellen wiederverwendet. Beim erstmaligen Aufbau/Wiederherstellen des Index werden einmal die vorhandenen veränderten Chunks untersucht.

Ein Wachstumsschritt braucht einen atomaren RTDB-Write plus Prüfreads; die aktive Tageslease wird je geprüftem Baum erneuert und einmal abgeschlossen. Eine Zerfallswelle schreibt bis zu vier Zelländerungen gemeinsam. Jeder Zelländerung entspricht weiterhin eine verschlüsselte Inventarrevision, damit die bestehenden Wiederherstellungs-/Checkpointverträge vollständig gelten. Firestore erhält ausschließlich die vorhandenen gebündelten Checkpoints; unveränderte Regionen werden nicht erneut hochgeladen. Große Bestände und Konfliktwiederholungen verursachen entsprechend mehr Nutzdaten. Es gibt kein pauschales Quota-/Latenzversprechen.

## Prüfung

- `bash test.sh`: bestehende Logiksuite plus reine Baumplanung, Kollisionen, UTC-Grenzen, Herkunftsschutz, Speichern/Laden und Batch-/Lückenabgleich.
- `npm run test:rules`: echte Firestore-/RTDB-Emulatoren; einschließlich atomarer Zwei-/13-Zell-Batches, Teilwrite-Ablehnung, Counterprüfung, Konkurrenz, Positionskonflikten, täglicher Lease, Übernahme und Tagescursor.
- Android `JournalChecks`: echte `OnlineV6`-/Natur-/Checkpointpfade mit Offline-Transporten; Pflanzen, drei Tagesstufen, wiederholter Login, Peerabgleich, Checkpointmetadaten, Zerfall und Neustart.
- Bestehende Android-/Lint-/Update-Verträge bleiben Teil der CI.

RTDB-Regeln werden in dieser Reihenfolge erzeugt; die wiederholte Erzeugung muss identische Dateien liefern (automatisiert in einem temporären Verzeichnis geprüft):

```sh
python3 scripts/add-journal-rules.py
python3 scripts/nature-rules.py
```
