# meiCraft

Eigenständiges Offline-Klötzchenspiel für Android ab 8.0, mit nativer 3D-Welt, eigener Grafik und lokal gespeicherten Welten. Keine Konten, Werbung oder Käufe. Nur die abschaltbare Update-Prüfung verwendet das Internet.

**[APK herunterladen](https://github.com/pehab/MeiCraft/releases/latest)** · Version 0.2.0

## Kreativ und Story

Die Kreativwelt behält unbegrenzte Materialien, sofortiges Abbauen und optionales Fliegen. Vorhandene Spielstände aus 0.1.0 werden automatisch geladen.

Der **Storymodus** speichert in einer eigenen Datei. Über **STORY** oben im Spiel wechselst du den Modus beziehungsweise öffnest Aufträge und Werkbank. Du läufst, springst und sammelst Material. Gesetzte Blöcke werden vom Inventar abgezogen. Zum Abbauen hältst du **−** gedrückt; härtere Blöcke brauchen bessere Werkzeuge.

1. Gras/Erde mit der Hand sammeln; Miko tauscht 8 Erde gegen ein Holzwerkzeug.
2. Holz und Erde für Werkbank und Holzspitzhacke sammeln. Damit wird Stein abbaubar.
3. Eine Steinspitzhacke herstellen und Eisenerz erschließen.
4. Einen Ofen bauen und Erz mit Holz als Brennstoff schmelzen.
5. Eisenwerkzeug herstellen, Gebäude mit Fenstern bauen und tief im Gestein Leuchtstein finden.

Die Werkbank stellt Bretter, Ziegel und Grasblöcke her. Im Ofen entstehen Glas und Eisenbarren; später lassen sich Eisenblöcke bauen. Die sieben Einführungskapitel gehen anschließend in freies Bauen mit begrenzten Ressourcen über. Hunger, Gegner, Multiplayer und fließendes Wasser sind noch nicht enthalten. Die Welt ist 96 × 96 × 48 Blöcke groß.

![Storymodus](docs/story.png)

## Steuerung

**Die ursprüngliche Steuerung bleibt:** links am virtuellen Joystick bewegen, rechts wischen zum Umsehen. Mit **+** neben dem anvisierten Block bauen. In der Kreativwelt baut **−** sofort ab; im Storymodus hältst du **−** gedrückt. **↑** springt oder schwimmt aufwärts. In der Kreativwelt können **↑ / ↓** zum Fliegen genutzt werden. Gesammelte Blöcke helfen beim Bau von Stufen aus Gruben.

Hoch- und Querformat werden unterstützt. Unter **☰ → Weltmodus wechseln** findest du ebenfalls beide Welten. **Neue Welt** ersetzt nach Bestätigung ausschließlich den gerade aktiven Speicherplatz.

## Speicher und Updates

Automatisches Speichern alle 30 Sekunden und beim Wechsel in den Hintergrund; zusätzlich manuell im Menü. Kreativwelt: `world.mcworld`, Story: `story.mcworld`, jeweils atomar geschriebene GZIP-Dateien im privaten App-Speicher. Ein Moduswechsel speichert die alte Welt zuerst. Schreibaufträge erfassen ihren Ziel-Speicherplatz beim Erstellen. Version-1-Spielstände aus 0.1.0 bleiben kompatibel. Beschädigte Dateien werden vor einer Neuerstellung als Sicherung behalten.

Updates: täglich automatisch beim Öffnen, abschaltbar, zusätzlich unter **☰ → Updates**. Downloads brauchen eine Bestätigung. Die App prüft SHA-256, Größe, Paketname, Version und Signatur vor der Android-Installation. Ein Update mit gleichem Signierschlüssel erhält beide Welten; eine Deinstallation entfernt den lokalen Fortschritt.

Die Release-Pipeline erzeugt Prüfsumme und Update-Manifest aus der geprüften APK. Fehlt das private Signier-Secret in CI, kann eine lokal mit dem ursprünglichen Schlüssel signierte APK unter `dist/` verwendet werden. Dafür müssen sämtliche APK-Inhalte (Code, Manifest, Ressourcen und Assets; ausgenommen Signaturdateien) bytegenau mit dem frischen CI-Build übereinstimmen. Veraltete Pakete oder abweichende Signaturen verhindern die Veröffentlichung. Gegner, Waffen und Rüstung sind in der [Roadmap](docs/ROADMAP.md) vorgemerkt.

## Bauen und prüfen

JDK 17, Android SDK Platform 37.0 und Build Tools 36.0.0:

```sh
export ANDROID_SDK_ROOT=/pfad/zum/android-sdk
./test.sh
./build-local.sh
bash scripts/build-upgrade-fixture.sh
./test-android-build.sh
```

Die APK liegt unter `build/meiCraft-0.2.0.apk`. Der direkte SDK-Build benötigt keine Spielbibliotheken. `.signing/` enthält den privaten, lokal wiederverwendeten Schlüssel und ist aus Git ausgeschlossen. Updates veröffentlichter APKs brauchen den ursprünglichen Projektschlüssel. Das private Secret `MEICRAFT_SIGNING_JSON` hat die Felder `keystore` (Base64-PKCS12), `password` und `alias` (`meicraft`); `scripts/restore-signing.py` stellt daraus den lokalen Schlüssel wieder her. CI-Testschlüssel werden nie veröffentlicht.

Alternativ Android Studio oder `./gradlew --warning-mode=fail assembleDebug lintDebug`. Versionen stehen in `version.properties`. Compiler-, Lint- und Gradle-Warnungen führen zu Fehlern. Der Gradle-Prüfpfad verwendet AGP 9.5.0-rc01 mit Kotlin-Build-Komponente 2.4.21, um deren dokumentierte Configuration.setVisible-Probleme mit Gradle 9.8.2 zu beheben. Die veröffentlichte APK entsteht direkt mit den stabilen SDK-Werkzeugen.

Auf einem Emulator ohne wertvollen Spielstand:

```sh
bash scripts/smoke-android.sh
```

CI prüft Android 11 und Android 17: tatsächliche Aktualisierung der ursprünglichen 0.1.0-APK (für CI neu signiert), Spielstanderhalt, ursprüngliche Wischsteuerung, Blick nach unten, Touch-Abbruch, beide Ausrichtungen, System-Zurück, zeitgesteuertes Abbauen, Materialkosten, Werkzeug-Sperren, Rezeptdialoge und beide Speicherplätze. Die Tests verändern ihre Testwelten.

## Code und Assets

`World.java`: Terrain, Kollisionen, Raycasting, Ressourcenaktionen und Speicherformat. `Story.java`: Aufträge, Werkzeugstufen, Inventar und Rezepte. `GameView.java`: bisherige Touch-Steuerung, Bewegung und HUD. `VoxelRenderer.java`: sichtbare Blockflächen in 16 × 16 großen Chunks, Texturen, Licht und Nebel. `MainActivity.java`: Dialoge, Speicherplätze und Lebenszyklus.

Alle Texturen sind prozedurale Originale. Neuerzeugen mit `python3 scripts/generate-textures.py` (Pillow). Keine Minecraft-Dateien oder Original-Assets; unabhängig von Mojang und Microsoft.
