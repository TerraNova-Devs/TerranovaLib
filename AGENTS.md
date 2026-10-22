# TerranovaLib – Regeln für Agenten

Übernommen aus `BetonquestAdditions/AGENTS.md` (Terranova-Content, Stand 729d708), als
Ghost-Blocks und NPCs von dort in die Lib gezogen sind. Was die Features tun und wie man
sie benutzt, steht in [`docs/ghostblocks.md`](docs/ghostblocks.md) und
[`docs/npcs.md`](docs/npcs.md).

## Ghost-Blocks

- Es gibt nur Block-Änderungen an einzelne Clients, die Welt bleibt unberührt. Der
  Client vergisst sie, sobald der Server die Stelle neu schickt. Deshalb folgen sie
  jedem gesendeten Chunk (`PlayerChunkLoadEvent`) und werden kurz nach Klicks,
  Graben und Platzieren in der Nähe erneut geschickt.
- Sichtbarkeit steht pro Gruppe im PDC des Spielers (`terranovaghostblocks:<gruppe>`)
  und wird für Conditions aus beliebigen Threads im Speicher gecacht. Den Namespace
  nie ändern, sonst ist die gespeicherte Sichtbarkeit aller Spieler weg.
- Physik bleibt serverseitig: Auf Ghost-Böden kann man nicht stehen (Vulcan kann
  anschlagen), durch versteckte echte Wände kann man nicht gehen. Nur für Kulisse
  verwenden.

## NPCs

- Die YAML-Dateien in `plugins/TerranovaLib/npcs/` sind die Quelle der Wahrheit. Was
  im Spiel per `/npc` an einem verwalteten NPC geändert wird, überschreibt der nächste
  Reload.
- Verwaltet ist ein NPC durch den Persistent-Data-Schlüssel `terranova-id`.
  Citizens speichert ihn in `saves.yml`. Die numerische Citizens-ID ist egal. NPCs ohne
  den Schlüssel fasst der Abgleich nie an.
- Der Abgleich versetzt NPCs **nie per `teleport()`**: Citizens ignoriert das für
  nicht gespawnte NPCs (etwa in einem nicht geladenen Chunk), und bei gespawnten
  läuft es über Teleport-Events, die ein anderes Plugin abbrechen kann. Auf dem
  Mirror blieb Friedolin so trotz „in sync“ am alten Ort (BetonquestAdditions 1.0.1
  und 1.0.2). Seit BetonquestAdditions 1.0.3: gespawnt → `despawn(PENDING_RESPAWN)`,
  gespeicherten Ort (`CurrentLocation`) setzen, `spawn` am neuen Ort. Jede Korrektur
  steht im Log als „Moving '<id>' (#n) from … to …“. Taucht dieselbe Zeile bei jedem
  Start auf, steht die Definition falsch: Citizens-NPCs haben Schwerkraft, ein Ort in
  der Luft sinkt beim Spielen ab (Friedolin: y 68 → 67). Dann die Höhe in der
  Definition korrigieren, nicht den Abgleich toleranter machen.
- Waisen werden nur gelöscht (`npcs.remove-orphans` in der `config.yml`), wenn **alle**
  Dateien fehlerfrei geladen haben und mindestens eine Definition gefunden wurde; weder
  ein Tippfehler noch ein fehlender oder noch nicht verschobener `npcs/`-Ordner darf
  NPCs löschen (sonst wäre z. B. Prods Friedolin #3 weg). `/npc copy` kopiert die ID
  mit, solche Kopien werden erkannt und freigegeben.
- Bestehende NPCs (z. B. auf Prod) übernimmt der Abgleich selbst: In der Definition
  steht `replaces: <Citizens-UUID>` des alten NPCs. Wo es ihn gibt, wird er übernommen
  und behält seine Citizens-ID; gab es schon eine Kopie, wird die Kopie entfernt. Wo es
  ihn nicht gibt (dev-Welt), entsteht ein neuer. Nie einen NPC nur per Befehl
  übernehmen und die Definition vergessen: sonst entsteht auf dem nächsten Server ein
  Doppelgänger.
- Danach im Quest-Paket `citizens <n>` durch `tnpc <id>` ersetzen (Beispiel Friedolin:
  `replaces: e8cfe600-1ef7-450f-959c-19578ef3a3eb`, Prods #3).
- `/tnpc adopt` ist nur die Abkürzung im Spiel. Ist die ID schon in einer Datei
  definiert, wird nur markiert; sonst schreibt es `npcs/adopted.yml` inklusive
  `replaces`.
- Die NPC-Definitionen für die Server pflegt die Inhalts-Session; der Server Manager
  kopiert sie nach `plugins/TerranovaLib/npcs/`.

### Jeder NPC hat einen eigenen Skin

Ein NPC ist erst fertig, wenn er einen eigens für ihn gezeichneten Skin hat. Keine
Accountnamen (`skin.name` nur zum kurzen Testen), keine Skins aus fremden
Sammlungen oder Datenbanken.

1. Den Skin mit dem **Aseprite-MCP** zeichnen: 64×64 px, RGBA, Standard-Skin-Layout
   (inklusive zweiter Ebene für Hut, Jacke und Ärmel), Armbreite classic oder slim
   festlegen. Vorher den Stil mit den vorhandenen Terranova-Assets im
   `design`-Repo abgleichen und mit `render_preview` prüfen.
2. Master (`.aseprite`) und Export (`.png`) in `design/Skins/<npc-id>.*` ablegen,
   Dateiname = permanente NPC-ID. Nicht in den Plugin-Ordner auf dem Server.
3. Dem Nutzer den Skin zur Freigabe zeigen.
4. Signieren: Ein Vanilla-Client zeigt nur Skins, die bei Mojang liegen und
   signiert sind. Das PNG wird dafür bei MineSkin (mineskin.org) hochgeladen, das
   liefert *Texture Value* und *Texture Signature*. Das Hochladen veröffentlicht
   den Skin, also **vorher den Nutzer fragen** oder ihn selbst hochladen lassen.
5. Die beiden Werte in die NPC-Definition schreiben (`skin.texture`,
   `skin.signature`) und dort in einem Kommentar die Quelle in `design/Skins/`
   nennen.

Das ist derselbe Weg wie in Nations (`NPCSkins`: MineSkin-Werte, gesetzt mit
Citizens' `setSkinPersistent`). Nur liegen die Werte hier pro NPC in der
YAML-Definition statt im Code, ein neuer Skin braucht also keinen Build.

Signieren ohne Website (nach Zustimmung des Nutzers), Ergebnis wie beim
Hochladen auf mineskin.org:

```bash
curl -s -A "TerranovaLib/1.0" -X POST https://api.mineskin.org/v2/generate \
  -F "file=@C:/…/design/Skins/<id>.png;type=image/png" -F "variant=classic" \
  -F "name=<id>" -F "visibility=unlisted"
```

- Pfad in Windows-Form (`C:/…`) angeben, mit `/c/…` findet curl die Datei nicht
  (HTTP 000).
- Werte stehen in `skin.texture.data.value` und `.signature`; ohne API-Key
  kommt nur die Warnung `no_api_key`.
- Prüfen: `value` base64-dekodieren, die PNG unter `textures.SKIN.url` laden
  und pixelweise mit dem Export vergleichen (Friedolin: 0 Abweichungen).
  Fehlt `metadata.model: slim`, sind die Arme classic.
