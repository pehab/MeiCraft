# meiCraft 0.8.2 · Sparsamere Online-Synchronisierung

- Eine gemeinsame Änderungsübersicht wird alle fünf Sekunden gelesen. Unveränderte Bereiche und persönliche Inventare werden nicht ständig neu abgefragt. Mitspielerpositionen stammen aus derselben Übersicht; im Stand werden sie nur alle 30 Sekunden geschrieben.
- Block, Inventar und Bereichsrevision werden gemeinsam bestätigt. Gleichzeitiges Bauen bleibt durch Versionsvorbedingungen abgesichert. Mehrseitige Blockabfragen verwenden denselben Server-Snapshot.
- Online-Diagnose mit lokalen Zugriffszählern, Firebase-Dienst, Vorgang, HTTP-/Servercode und bereinigter Servermeldung. Bericht kopierbar, letzter Fehler nach Neustart verfügbar. Keine automatische Übertragung des Berichts.
- Kontingentfehler pausieren weitere Versuche zunächst eine Minute, bei Wiederholung bis zu 15 Minuten.
- Gelöschte Testwelten aus der lokalen Liste eines anderen Tablets entfernen: Eintrag auswählen → **Aus Liste entfernen**. Auch im Verbindungsfehlerdialog verfügbar. Andere Welten und die Geräteanmeldung bleiben erhalten.

**Vor dem Spielen die aktualisierten `firestore.rules` veröffentlichen und alle Geräte aktualisieren.** Bestehende 0.8.1-Welten zuerst mit dem Besitzer öffnen: Protokoll 4 und die Änderungsübersicht werden atomar eingerichtet. Bauten, Landschaft und Inventare bleiben erhalten; ältere App-Versionen können anschließend nicht mehr beitreten. Testwelten vor 0.8.1 benötigen weiterhin eine neue Landschaft.

[Einrichtung und Diagnose](ONLINE.md)
