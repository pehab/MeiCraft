# meiCraft 1.1.1 – Stabile Mechanik und 3×3-Türen

- Bis zu neun bewegliche Blöcke auswählen, zum Beispiel für eine 3×3-Tür. Hin- und Rückbewegung bleiben atomar, auch bei 19 geänderten Zellen einschließlich Schalter.
- Online-Schalter laden nur benötigte Chunks und verwenden geprüfte Zwischenspeicher. Nachgewiesene Positions- und Weltkonflikte werden erneut geplant; verlorene Antworten lösen keine doppelte Bewegung aus.
- Mechaniken benennen, umbenennen und anhand von Entfernung, Blockzahl, Stellung und Schalterkoordinaten erkennen. **In der Welt markieren** richtet den Blick auf den Schalter und hebt die zugehörigen Blöcke hervor.
- Einrichtung mit Zusammenfassung und Speicherbestätigung. Bei Fehlern bleibt die Auswahl erhalten. Vorübergehende Hindernisse werden beim Schalten geprüft.
- Noch fällige Bäume nach einem abgeschlossenen Tagesdurchlauf erneut prüfen. Blockierte Bäume erhalten weitere Versuche; weiterhin höchstens eine Wachstumsstufe pro Baum und UTC-Tag.
- Diagnose mit unterscheidbaren Mechanik-/Naturvorgängen und gemeinsamem Checkpoint-Stand.

## Vor dem Online-Spiel

1. Die Online-Welt auf allen Geräten verlassen.
2. In Firebase **Realtime Database → Regeln** den gesamten Inhalt ersetzen und veröffentlichen: [database.rules.json](https://raw.githubusercontent.com/pehab/MeiCraft/v1.1.1/database.rules.json).
3. Alle mitspielenden Geräte auf **1.1.1** aktualisieren, bevor benannte Mechaniken oder Neun-Block-Gruppen verwendet werden.

**Nur die RTDB-Regeln ändern.** Firestore-Regeln und Indizes bleiben unverändert. Die App-Veröffentlichung installiert die Regeln nicht automatisch. Über die vorhandene App installieren, um Daten zu erhalten.

[Bedienung](https://github.com/pehab/MeiCraft/blob/v1.1.1/docs/MECHANICS.md)

## Prüfungen

174 Mechanik-Prüfungen, 131 Firebase-Regeltests sowie Android-Build, Lint und vollständige Emulator-Tests auf API 30 und 37. Enthalten sind Neun-Block-Tür mit Hin-/Rückweg und zweitem Spieler, Live-Ring-Überlauf, Positionskonflikte, verlorene Serverantwort, Entwurfserhalt bei Fehlern, Verwaltung und erneute Baumprüfung.
