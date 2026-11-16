# meiCraft 1.0.4 – Holz über Lava wird Kohle

Ein vom Spieler gesetzter Holzblock direkt über Lava verkohlt nach drei aktiven Reaktionsschritten (normalerweise etwa neun Sekunden) zu einem Kohleblock. Die Lava bleibt erhalten. Wasser neben dem Holz oder eine entfernte/ersetzte Wärmequelle unterbrechen beziehungsweise starten den Vorgang neu. Seitlich benachbarte Lava genügt nicht; es gibt keine Brandausbreitung.

Die frisch erzeugte Kohle bleibt zum Abbauen liegen und reagiert nicht automatisch zu Glutstein weiter. Für das bestehende Glutsteinrezept die Kohle abbauen und anschließend erneut über Lava setzen. Unmarkierte alte Holzgebäude und gewachsene Baumstämme werden nicht automatisch verkohlt. Es entstehen keine zusätzlichen Gegenstände im Inventar, bevor die Kohle regulär abgebaut wird; Werkzeuganforderungen bleiben bestehen.

Offline bleiben Reaktionsdauer und fertige Kohle beim Speichern, Neustart und QR-Teilen erhalten. Speicherformat 8 liest weiterhin Formate 1–7. Empfänger neuer Spielstände müssen ebenfalls aktualisiert sein. Online werden die Phasen und ihr Ergebnis über den bestehenden atomaren RTDB-Journalpfad mit gemeinsamem Zeitabstand gespeichert. Alle Mitspieler vor Verwendung des neuen Rezepts aktualisieren, damit sie die neuen Reaktionsmetadaten lesen können.

Keine weiteren RTDB-/Firestore-Regeln oder Indexänderungen erforderlich. Das für die Rohstoffergänzung aus 1.0.3 erforderliche RTDB-Regelupdate bleibt davon unberührt. Über die bestehende Installation aktualisieren; keine App-Daten löschen.

## Nachträgliche Regelkorrektur für bestehende Online-Welten

Die vollständige aktuelle `database.rules.json` erneut in Realtime Database veröffentlichen. Beim Import alter Firestore-Chunks schreibt 1.0.4 ein leeres `nature`-Feld, bevor ein Journal-Kopf existiert. Dessen untergeordnete Validierung verweigerte bisher die Übernahme trotz Besitzerberechtigung (HTTP 401). Neue leere Welten waren davon nicht betroffen.

Die Korrektur übernimmt für dieses Feld dieselbe Besitzer-Ausnahme während der Migration wie für die übrigen Blockdaten. Nach `control/phase = ready` gilt weiterhin die Bindung an den atomaren Journaleintrag. Kein APK-Update, keine Firestore-/Indexänderung und kein Zurücksetzen von Daten erforderlich. Nach Veröffentlichung dieselbe bestehende Welt als Besitzer erneut öffnen; der Import setzt auf der eingefrorenen Quelle auf.
