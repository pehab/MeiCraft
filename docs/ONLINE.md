# Gemeinsam bauen · Online-Bauwelten (0.8.2)

Online-Welten sind zusammenhängende Landschaften mit begrenzten Rohstoffen. Spieler
starten zu Fuß, sammeln Materialien und schalten an der Werkbank Werkzeuge frei.
Es gibt keine Gegner, keinen Tag/Nacht-Wechsel, keine Flüssigkeitsströme und keine
automatischen Materialreaktionen. Nur Spieleraktionen verändern die Landschaft.

## Firebase einrichten oder auf 0.8.2 aktualisieren

Projekt: `meicraft-be298`, Android-Paket: `de.meiapps.meicraft`.
Die öffentliche Android-Konfiguration liegt unter
`app/src/main/assets/google-services.json`. Die App nutzt Firebase Auth und Firestore
über HTTPS mit Benutzertokens. Es ist kein Admin-Schlüssel in der App enthalten.

1. Authentication → Anmeldemethode → **Anonym** aktivieren. Automatische Löschung
   anonymer Konten ausgeschaltet lassen.
2. Firestore Standard Edition mit Datenbank-ID **`(default)`** verwenden.
3. Firestore → **Regeln**: Den vollständigen Inhalt von
   [`firestore.rules`](../firestore.rules) einsetzen und **Veröffentlichen** klicken.
   **Für 0.8.2 sind aktualisierte Regeln erforderlich**, damit die gemeinsame
   Änderungsübersicht und Protokoll 4 genutzt werden können. Ein Git-Push veröffentlicht diese Regeln nicht.

Die Datei ist für ein eigenes MeiCraft-Projekt bestimmt. Regeln anderer Anwendungen
nicht damit überschreiben. Alternativ mit berechtigtem Firebase-Konto vom Rechner:

```sh
npm ci --ignore-scripts
npx firebase login
npx firebase deploy --only firestore:rules --project meicraft-be298
```

**Zuerst die Regeln veröffentlichen, dann alle Geräte auf 0.8.2 aktualisieren.**
Bestehende 0.8.1-Welten bleiben einschließlich Gelände, Bauten und Inventaren erhalten:
Der Besitzer öffnet die Welt zuerst. Dabei werden die Änderungsübersicht und Protokoll 4
atomar eingerichtet. Danach können Mitspieler mit 0.8.2 wieder beitreten. Frühere
App-Versionen können diese Welt anschließend nicht mehr öffnen. Die Geländeform
bleibt exakt die von 0.8.1; eine neue Welt ist dafür nicht erforderlich.

Ältere Testwelten aus 0.8.0 oder früher behalten ihre Daten, können aber wegen des
anderen Geländegenerators nur vom Besitzer gelöscht werden. Lokale Offline-Welten
bleiben unverändert.

## Spielen

1. Auf allen Geräten dieselbe aktuelle App-Version installieren.
2. Erstes Gerät: **Gemeinsam bauen → Neue Online-Welt**, Spielername und Größe wählen.
3. **Menü → Spieler / Einladung → Einladungs-QR** zeigt die Einladung.
4. Weitere Geräte: **Gemeinsam bauen → Beitreten / Öffnen → Einladungs-QR scannen**.
   Alternativ kann der Einladungstext kopiert und eingegeben werden.
5. Bereits besuchte neue Welten stehen in der Online-Weltenliste. Testwelten vor 0.8.1
   bitte durch eine neue Online-Welt ersetzen.

Der Anzeigename verleiht keine Rechte. Jedes Gerät bekommt eine dauerhaft gespeicherte
anonyme Firebase-UID. Der Besitzer kann andere Geräte-IDs in der Spielerliste entfernen.
Sperrvermerke bleiben erhalten, damit dieselbe UID nicht mit der alten Einladung erneut
beitreten kann. Die Einladung ist ein Zugangsschlüssel und sollte nur an gewünschte
Mitspieler weitergegeben werden.

Eine Neuinstallation oder das Löschen der App-Daten entfernt die Geräteidentität,
einschließlich Besitzerzugang und Zugang zum bisherigen Inventar. Eine Wiederherstellung
über ein Benutzerkonto ist noch nicht enthalten. Die Sperre gilt für die UID; eine neue
Identität auf einem zurückgesetzten Gerät kann mit einer gültigen Einladung neu beitreten.

## Online-Welt löschen

Der Ersteller findet im Spielmenü **Online-Welt löschen**. Nach **Dauerhaft löschen**
wird die Welt für weitere Änderungen gesperrt. Danach entfernt die App alle gespeicherten
Blockänderungen, Mitgliedschaften und persönlichen Inventare sowie das Weltdokument.
Andere Spieler können die Welt danach nicht mehr öffnen. Die Aktion lässt sich nicht
rückgängig machen; lokale Offline-Welten und andere Online-Welten bleiben erhalten.

Bei Verbindungsabbruch bleibt die angefangene Löschung gesperrt und kann über
**Erneut versuchen** fortgesetzt werden. Auch nach einem App-Neustart kann der Besitzer
die Welt aus seiner Online-Liste öffnen und die Löschung fortsetzen. Das gilt ebenfalls
für die alte Testwelt: Beim Öffnen bietet 0.8.2 dem Ersteller die Löschung an. Die Liste auf anderen Geräten ist lokal gespeichert. Einen alten Eintrag auswählen
und **Aus Liste entfernen** antippen; dies funktioniert auch ohne Internet und entfernt
nur diesen Eintrag auf diesem Gerät. Andere Welten, eigene Vorräte und die anonyme
Anmeldung bleiben erhalten. Auch nach fehlgeschlagenem Öffnen bietet der Fehlerdialog
**Eintrag entfernen** an. Bei Zugriffsfehlern wird nichts automatisch aus der Liste gelöscht,
da fehlende Regeln oder eine vorübergehende Störung dieselbe Fehlermeldung auslösen können. Mitspieler dürfen weder Welten noch fremde Vorräte löschen.

## Rohstoffe und Werkbank

- **Laufen ist Standard.** Mit ↑ springen oder schwimmen; online gibt es keinen
  Flugmodus-Schalter.
- **− gedrückt halten** baut einen Block ab. Gras ergibt Erde. Härtere Materialien
  brauchen die gleichen Werkzeuge wie im lokalen Storymodus.
- In **WERKBANK** oben im Spiel oder im Menü wird der erste Auftrag mit 8 Erde
  abgeschlossen. Danach folgen Holzwerkzeug, Holzspitzhacke, Steinspitzhacke,
  Ofen und Eisenwerkzeug. Die dafür gesammelten Materialien werden verbraucht.
- Bretter, Ziegel, Glas, Barren, Eisenblöcke, Gras und Eimer werden aus Rohstoffen
  hergestellt. **+** verbraucht einen Block aus dem eigenen Vorrat.
- Jeder Spieler hat **pro Online-Welt ein eigenes Inventar**. Vorräte, Werkzeuge,
  Herstellungsfortschritt und Bauzähler bleiben nach Verlassen und Wiederverbinden
  mit derselben Geräte-ID erhalten. Verschiedene Geräte haben verschiedene Inventare.
- Natürliche Wasserquellen lassen sich mit einem leeren Eimer aufnehmen. Ausgegossene
  Quellen bleiben endlich. Wasser und Lava fließen nicht. Lava kann über das bekannte
  Ofenrezept hergestellt werden; automatische Materialreaktionen bleiben deaktiviert.
- Neue unterirdische Erz- und Leuchtsteinvorkommen ersetzen nur unberührten natürlichen
  Stein. Bereits gespeicherte Blockänderungen werden darübergelegt und bleiben erhalten.
  Die Vorräte einer neu angelegten Welt starten leer.

## Zusammenhängende Welt und Nachladen

Die Welt misst **192 × 192** oder **288 × 288 Blöcke**, jeweils 48 Blöcke hoch. Die
kleine Weltkarte zeigt Gesamtgröße, eigene Position und farbige Mitspieler. Beim
Laufen werden Koordinaten kontinuierlich fortgeführt: keine Teleports, Abschnittsmenüs
oder vollständigen Weltwechsel an internen Speichergrenzen. Auch Mitspieler jenseits
einer solchen Grenze sind in Sichtweite sichtbar.

Die Welt hat ein gemeinsames Koordinatenraster. Landschaft und bestätigte Änderungen
werden in der Nähe des Spielers vorgeladen. Firestore wird nur für die umliegenden
Speicherbereiche abgefragt, mit Randbereich vor der sichtbaren Landschaft. Bereits
besuchte Landschaften bleiben als begrenzter lokaler Cache erhalten. Das Grundraster
belegt bei 288 × 288 × 48 rund 4 MB für Blocktypen; es wird nicht als vollständige Welt
von Firebase heruntergeladen. Die GPU-Geometrie wird in 16 × 16 großen Bereichen
aufgebaut und bei größerem Abstand wieder freigegeben.

Bei langsamer Verbindung kann der Spieler kurz vor einer noch nicht synchronisierten
Umgebung anhalten, bis sie geladen ist. Die Kamera wird dabei nicht versetzt. Die gemeinsame Änderungsübersicht wird etwa alle fünf Sekunden gelesen. Nur Bereiche
mit geänderter Revision werden abgefragt; Bewegung allein löst keine Blockabfrage aus.
Die Anzeige anderer Spieler wird aus derselben Übersicht gelesen;
Mitspielerbewegungen werden geglättet. Dies ist gemeinsames Bauen, kein schneller
Echtzeit-Kampf. Der Besitzer muss nicht dauerhaft online sein.

## Atomare Änderungen und Speicherung

Eine Bau- oder Abbauaktion schreibt **Block und eigenes Inventar in einem Firestore-
Commit zusammen mit der Bereichsrevision**. Block und Inventar haben Versionsvorbedingungen.
Die Revision wird atomar erhöht, ohne Änderungen anderer Spieler zu überschreiben. Wird derselbe Block gleichzeitig
von zwei Geräten abgebaut, wird nur eine Aktion bestätigt und nur ein Inventar erhält
das Material. Scheitert eine Aktion, werden weder Material noch Block lokal verändert.
Herstellen und Werkzeugfortschritt verwenden ebenfalls eine Inventar-Versionsprüfung.

Bei unterbrochener Verbindung pausiert das Bauen. Es gibt keine versteckte Offline-
Bauwarteschlange. Netzwerkfehler werden mit wachsendem Abstand bis maximal zwei Minuten erneut versucht.
Bei HTTP 429 beginnt die Pause bei einer Minute und wächst bis auf 15 Minuten.
Anwesenheit wird bei Bewegung frühestens alle fünf Sekunden, im Stand alle 30 Sekunden
aktualisiert; nach etwa 75 Sekunden ohne Aktualisierung wird eine Figur ausgeblendet.
Das persönliche Inventar wird beim Verbinden und nach unklarem Schreibausgang oder
Versionskonflikten gelesen, nicht mehr in jedem Abfragezyklus. Im Hintergrund pausieren neue Abfragen.
Eine bereits laufende Anfrage kann noch zu Ende gehen.

Lokale Kreativ- und Storywelten bleiben getrennt erhalten. Das Offline-Speicherformat
bleibt unverändert; große Online-Welten werden nicht in lokale Weltdateien geschrieben
oder als Offline-QR exportiert. Beim Verlassen wird die vorherige lokale Welt geöffnet.
Firestore-Lesezugriffe und Positionsaktualisierungen verbrauchen Kontingent. Die erste
Fassung ist für wenige Familiengeräte gedacht; Verbrauch in der Firebase Console prüfen.

## Datenmodell

```text
onlineWorlds/{weltId}
  owner, name, seed, side, protocol, invite, closed
  members/{firebaseUid}
    name, banned, invite, sector, x, y, z, yaw, updatedAt
    inventory/main
      items[17], chapter, iron, placed, glassPlaced, buckets, sword,
      lastBlock, updatedAt
  sync/state
    regions: {s0..s8: {revision, updatedAt}}
    players: {firebaseUid: {name, sector, x, y, z, yaw, updatedAt}}
  sectors/{0..8}/blocks/{x_y_z}
    x, y, z, type, by, renewable, updatedAt
```

Intern werden Blockänderungen und Anwesenheitsdaten weiter in 96er-Speicherbereichen
abgelegt. **Die Geländeform benutzt einen gemeinsamen Welt-Seed und globale X/Z-
Koordinaten.** Höhen, Erzvorkommen und Baumstandorte hängen weder von der Reihenfolge
des Nachladens noch der Größe des Ausschnitts ab. Baumkronen werden mit einem Randbereich
erzeugt und reichen über Speichergrenzen. Größere Bergzüge mit Steinflanken und
Schneekappen, bewaldete Hügel, Seen und geschwungene Flussläufe setzen sich dadurch
über die gesamte Grundfläche fort. An Gewässern können natürliche steile Ufer und
Schluchten vorkommen; eine künstliche 96er-Geländekante gibt es nicht mehr.

Die Gesamthöhe bleibt bei 48 Blöcken; die höchsten Geländeoberflächen liegen bei
42 Blöcken. Eine gemeinsame Startlichtung bietet ebenen, trockenen Boden. Die Einladungshülle bleibt Version 1. Protokoll 4 ergänzt die Synchronisierung und
verwendet denselben Geländegenerator wie Protokoll 3.
Änderungen an diesem Generator benötigen eine neue Protokollversion.

Welt-ID und Einladung enthalten jeweils 128 Zufallsbits. Welten sind nicht öffentlich
auflistbar. Nur Mitglieder können Weltblöcke lesen und bearbeiten. Ein Inventar kann
nur die zugehörige UID lesen und schreiben. Die Regeln prüfen Inventargröße, Feldtypen,
Wertebereiche und bei Blockänderungen die zugehörige Inventaränderung im selben Commit.
Fremde Inventare, Fundament, ungültige Koordinaten, zusätzliche Felder und das Löschen
von Sperrvermerken sind während des Spielens gesperrt. Nur der Besitzer darf nach dem
Schließen einer Welt deren Daten für eine vollständige Löschung entfernen.

Im Code sind `World.W` und `World.D` weiterhin die Offline-/Speicherbereichsgröße.
Die tatsächlich dargestellte Welt verwendet `world.width`, `world.depth`, `world.idx`
und `world.contains`. Online bleiben Nature- und Survival-Simulationen ausgeschaltet.

## Diagnose und Verbrauch

**Gemeinsam bauen → Beitreten / Öffnen → Online-Diagnose** ist auch ohne aktive
Verbindung erreichbar. In einer Welt: **Menü → Online-Diagnose**. Der Bericht enthält
App-/Android-Version, HTTP-Anfragen nach Vorgang, empfangene Dokumente/Daten,
bestätigte Schreib-/Löschvorgänge und die letzten zwölf Fehler mit Uhrzeit, Firebase-Dienst,
Vorgang, HTTP-/Servercode und bereinigter Servermeldung einschließlich verfügbarer
Kontingentdetails. **Bericht kopieren** kopiert den Text. Der letzte Serverfehler bleibt
auch nach App-Neustart erhalten. Anfrage-URLs, Nutzdaten und Anmeldetokens werden
nicht gespeichert; der Bericht wird nicht automatisch hochgeladen.

Die Zähler messen lokale Antworten, keine Firebase-Abrechnung: Regelzugriffe, Indexlesungen
und Mindestkosten leerer Abfragen sind nicht enthalten. Bei vier sichtbaren Bereichen
sinken die regelmäßigen HTTP-Anfragen ohne Blockänderungen rechnerisch von etwa
10.440 auf 840 pro Stunde im Stand und von 11.520 auf 1.440 bei ständiger Bewegung. Aktives Bauen
verursacht zusätzliche Zugriffe: jeder Block speichert jetzt Block, Inventar und Revision.
Das kostenlose Kontingent bleibt begrenzt; den tatsächlichen Verbrauch in Firebase prüfen.

## Prüfen

```sh
bash test.sh
bash scripts/test-update-contract.sh
npm ci --ignore-scripts
# Java 21+ und Node 24
npm run test:rules
```

Die Tests prüfen Materialverbrauch, Werkzeugfortschritt, statische Welt, kontinuierliche
Koordinaten, sichtabhängiges Nachladen, Einladungs-QR, UID-Rechte, Inventarvalidierung,
atomare REST-Commits und konkurrierendes Abbauen. Zusätzliche Überlappungstests prüfen
Gelände, Bäume und Erz über alle Speichergrenzen hinweg, identische Landschaft bei
beiden Weltgrößen, Schneekappen, Startressourcen und einen festen Gelände-Hash. Die Android-Instrumentierung prüft
zusätzlich durchgehendes Laufen über eine bisherige Speichergrenze, sichtbare farbige
Mitspieler, Werkbank und Erhalt des lokalen Spielstands. Dazu verwendet sie eine lokale
Testszene ohne Produktionsanfragen. CI veröffentlicht `online.png` als Prüfaufnahme.

Auf echten Tablets gemeinsam prüfen: Rohstoffe sammeln, Werkzeug freischalten,
Bauen/Abbauen desselben Blocks, Werkbank, erneutes Öffnen mit erhaltenem Inventar,
Weiterlaufen über den alten Rand, Flugmodus und Wiederverbindung sowie Spielersperre.
