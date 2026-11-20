# Baumwachstum: numerische Bereiche lesen

Der Pflanzungsindex unter `storage/growth/saplings` verwendet die numerischen Bereichsschlüssel 0 bis 8. Firebase RTDB gibt dicht belegte numerische Schlüssel über REST als JSON-Array zurück, mit null für Lücken. Bei dünner Belegung bleibt die Antwort ein JSON-Objekt. Der bisherige Leser verlangte immer ein Objekt und brach bei der Array-Antwort mit `IOException: Ungültige RTDB-Antwort` ab, bevor ein Baum geprüft wurde. Die Diagnose blieb dabei irreführend auf „noch nicht geprüft“.

Der Leser normalisiert beide Formen unter Erhalt der ursprünglichen Bereichsnummern und überspringt ausschließlich null-Lücken. Ungültige Typen und Bereiche außerhalb der Welt werden weiterhin abgelehnt. Gespeicherte Pflanzungen werden weder gelöscht noch neu datiert. Es sind keine neuen Regeln, Indizes oder Datenmigrationen erforderlich. Die bisherigen Grenzen für Platz, Spieler, unveränderte Pflanzungen und eine Wachstumsstufe pro Tag bleiben bestehen.

Die Diagnose zeigt bei einem Fehler nun den Prüfschritt und den bereinigten Fehlertext. Tests prüfen die tatsächliche REST-Antwort des Firebase-Emulators sowie den Wachstumsablauf mit numerischen Array-Antworten und neu erstelltem Wachstumsdienst je Tag, einschließlich Stufe 1 bis 3 und vorübergehender Blockierung.
