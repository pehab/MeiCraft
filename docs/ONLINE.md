# Gemeinsam bauen · Online-Kreativwelten (0.7.0)

## Firebase einmalig einrichten

Projekt: `meicraft-be298`, Android-Paket: `de.meiapps.meicraft`.
Die öffentliche Android-Konfiguration liegt als `app/src/main/assets/google-services.json`
vor. Die App verwendet Firebase Auth und Cloud Firestore über HTTPS mit Firebase-
Benutzertokens. Ein Admin-Schlüssel ist weder nötig noch in der App enthalten.

1. Firebase Console → Authentication → Anmeldemethode → **Anonym** aktivieren.
   Eine automatische Löschung anonymer Konten ausgeschaltet lassen.
2. Firestore → Standard Edition → Datenbank **`(default)`** anlegen.
3. Firestore → **Regeln** öffnen. Den gesamten Inhalt der Datei
   [`firestore.rules`](../firestore.rules) einsetzen und **Veröffentlichen** klicken.
   Diese Datei gilt für ein eigenes MeiCraft-Projekt; bestehende Regeln anderer
   Anwendungen nicht damit überschreiben.

Alternativ vom Rechner aus, mit einem Firebase-Konto mit Berechtigung für das Projekt:

```sh
npm ci --ignore-scripts
npx firebase login
npx firebase deploy --only firestore:rules --project meicraft-be298
```

Es müssen keine Collections manuell angelegt werden. Produktionsdaten wurden bei der
Entwicklung nicht verändert; die Tests verwenden ausschließlich `demo-meicraft` im
lokalen Emulator. Die Regeln werden durch einen Git-Push nicht automatisch in Firebase
veröffentlicht.

## Auf zwei Geräten spielen

1. Auf beiden Geräten dieselbe App-Version installieren.
2. Gerät A: Hauptmenü → **Gemeinsam bauen** → **Neue Online-Welt**.
   Weltname, Spielername und vier oder neun Gebiete auswählen.
3. Im Spiel → **ONLINE** oder Menü → **Spieler / Einladung** → **Einladungs-QR**.
4. Gerät B: **Gemeinsam bauen** → **Beitreten / Öffnen** → **Einladungs-QR scannen**.
   Alternativ kann der Besitzer den Einladungstext kopieren und der Mitspieler ihn eingeben.
5. Nach der ersten Verbindung erscheint die Welt in der lokalen Online-Weltenliste.
   Ein erneuter Beitritt verwendet die bestehende Mitgliedschaft.

Die Geräte erhalten dauerhaft gespeicherte anonyme Firebase-UIDs. Der Anzeigename ist
frei wählbar und verleiht keine Berechtigungen. Der Besitzer kann in der Spielerliste
einen anderen Spieler antippen und **Entfernen** wählen. Dessen Mitgliedsdokument
bleibt als Sperrvermerk erhalten; dieselbe UID kann mit der Einladung nicht erneut
beitreten. Eine Neuinstallation oder das Löschen der App-Daten entfernt die Geräte-ID
und damit auch die Identität des Besitzers. Geräteübergreifende Kontowiederherstellung
ist noch nicht enthalten. Ein Einladungs-QR ist ein Zugangsschlüssel; nur mit den
gewünschten Mitspielern teilen. Eine Sperre gilt für die UID, nicht für eine Person,
die mit gelöschten App-Daten eine neue Identität erzeugt.

## Umfang und Synchronisierung

- Online ist zunächst **Kreativmodus**. Kein gemeinsames Story-Inventar, keine
  Nachtgegner und keine automatischen Materialreaktionen oder Flüssigkeitsströme.
- Vier Gebiete ergeben 192 × 192, neun Gebiete 288 × 288 Blöcke Grundfläche,
  jeweils 48 Blöcke hoch. Ein Gebiet umfasst 96 × 96 Blöcke. Beim Überqueren
  des äußeren Randes wird das benachbarte Gebiet geladen. Zwischen den Gebieten
  gibt es eine kurze Ladepause; Nachbargebiete werden nicht gleichzeitig gerendert.
- Im selben Gebiet sind Mitspieler als Blockfiguren mit Namen sichtbar. Eine
  Verbindung zur gleichen Welt reicht; der Besitzer muss nicht online bleiben.
- Authentifizierung und alle HTTP-Anfragen laufen auf einem separaten, seriellen
  Netzwerkthread. Die App fragt Änderungen ungefähr alle 2,5 Sekunden ab. Es handelt
  sich um gemeinsame Bauwelten mit spürbarer Aktualisierungsverzögerung, nicht um
  schnellen Echtzeit-Kampf. Bewegungen werden für die Darstellung geglättet.
- Ein Block wird erst nach erfolgreicher Firestore-Bestätigung verändert. Für
  konkurrierende Änderungen verwendet die App `currentDocument.updateTime` oder
  `exists=false`. Ein Konflikt lädt den aktuellen Block, statt ihn still zu überschreiben.
- Bei unterbrochener Verbindung ist Bauen gesperrt. Fehlgeschlagene Leseversuche
  werden mit zunehmendem Abstand bis maximal 80 Sekunden wiederholt. Es gibt keine
  versteckte Offline-Bauwarteschlange. Beim Verlassen wird die lokale Welt wiederhergestellt.
- Lokale Weltdateien und ihr Speicherformat bleiben unverändert. Online-Welten
  werden nicht als lokale Welten gespeichert und nicht per Offline-QR exportiert.
- Anwesenheit wird spätestens alle zehn Sekunden aktualisiert. Nach ungefähr
  zwanzig Sekunden ohne Aktualisierung wird eine Figur ausgeblendet. Im Hintergrund
  pausieren neue Abfragen. Eine laufende Anfrage kann noch zu Ende gehen.
- Firestore-Abfragen und Positionsaktualisierungen verbrauchen Kontingent. Diese
  erste Version ist für wenige Familiengeräte gedacht. Verbrauch in der Firebase
  Console beobachten; es wird keine unbegrenzte kostenlose Nutzung zugesichert.

## Datenmodell und Berechtigungen

```text
onlineWorlds/{zufälligeWeltId}
  owner, name, seed, side, protocol, invite, closed
  members/{firebaseUid}
    name, banned, invite, sector, x, y, z, yaw, updatedAt
  sectors/{0..8}/blocks/{x_y_z}
    x, y, z, type, by, updatedAt
```

Die Welt-ID und Einladung enthalten jeweils 128 Zufallsbits. Eine Welt und ihre
Besitzermitgliedschaft werden atomar angelegt. Welten können nicht öffentlich
aufgelistet werden. Nur Mitglieder können Weltdaten lesen und Blöcke verändern.
Mitglieder dürfen an ihrem eigenen Dokument ausschließlich Anzeigename, Position,
Blickrichtung und Zeitstempel ändern. Nur der Besitzer kann andere UIDs sperren;
Sperrvermerke können vom Client nicht gelöscht werden. Fundament, unbekannte
Materialien, zusätzliche Felder und Änderungen außerhalb der Weltgrenzen sind gesperrt.

Das ursprüngliche Gelände wird deterministisch aus Seed und Gebietsnummer erzeugt;
Firestore speichert dauerhaft nur die abweichenden Blöcke. Änderungen an der
Geländegenerierung erfordern eine bewusste Online-Protokollversionierung. Vor einer
solchen Änderung muss die Kompatibilität bestehender Online-Welten erhalten werden.

## Prüfen

```sh
bash test.sh
bash scripts/test-update-contract.sh
npm ci --ignore-scripts
# Java 21+ für die aktuellen Firebase-Emulatoren, Node 24
npm run test:rules
```

Die Regeltests prüfen atomare Welterstellung, Einladung, fremde Zugriffe, Positionen,
Besitzerrechte, Blockvalidierung, Zeitstempel, Sperrvermerke und das Schließen einer
Welt. CI führt sie als eigenen Job aus. Android-Gerätetests für den bisherigen
Offline-Modus bleiben aktiv. Zusätzlich vor einem Release auf zwei echten Geräten
prüfen: QR-Beitritt, gemeinsames Bauen, erneutes Öffnen, Gebietswechsel, Hintergrund,
Flugmodus und Wiederverbindung, Entfernen eines Spielers und Erhalt lokaler Spielstände.
