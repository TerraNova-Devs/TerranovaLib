# NPCs as code

Citizens NPCs written down in YAML instead of placed by hand, each with a permanent
id that BetonQuest can refer to.

```yaml
# plugins/TerranovaLib/npcs/harbour.yml
rotbart:
  name: "<red>Rotbart"
  location: "100.5;64;200.5;world;90;0"
  skin:
    name: Notch
```

```yaml
# a BetonQuest package
npcs:
  rotbart: "tnpc rotbart"
conversations: ...     # use the NPC id "rotbart" as usual
```

## Why

BetonQuest's `citizens 12` points at Citizens' numeric id. That number changes when
an NPC is deleted and placed again, and differs between servers. `tnpc rotbart`
points at an id that is written in a file and never changes, so quests keep
working through rebuilds, and the NPC files can live in git with the quests.

## How it works

- Every `.yml` under `plugins/TerranovaLib/npcs/` (subfolders allowed) is read; each
  top-level key is one NPC. All fields are in [`npcs/example.yml`](../src/main/resources/npcs/example.yml).
- On start (once Citizens has loaded its NPCs), on `/citizens reload` and on
  `/tnpc reload`, Citizens is brought in line with the files: missing NPCs are
  created, name, type, location, skin, look-close and protection are corrected.
- The NPCs are ordinary NPCs in Citizens' registry, tagged with their id in the
  NPC's persistent data (`terranova-id`). Citizens saves them; `/npc` commands work;
  BetonQuest's own Citizens click handling reports clicks on them, and this plugin's
  reverse identifier tells BetonQuest which `tnpc` entries match.
- The files are the source of truth: in-game changes to a managed NPC are
  overwritten on the next reload.
- An NPC whose definition is deleted is destroyed (`npcs.remove-orphans` in
  TerranovaLib's `config.yml`, on by default), but never in a reload where any file
  failed to load or where no definition was found at all (folder missing or not
  moved yet), so neither a typo nor a missing folder can wipe NPCs. Only managed
  NPCs (with `terranova-id`) are ever removed; NPCs placed by hand are left alone.
- An NPC copied with `/npc copy` carries the id along; the copy is detected and
  released from management.
- `replaces: <Citizens UUID>` names an NPC that existed before the definition,
  e.g. on Prod. Where that NPC exists, it is taken over and keeps its Citizens id,
  instead of a second one being created. If a copy was already created before the
  definition named its original, the copy is removed and the original takes its
  place. A server without that NPC (the dev world) simply gets a new one.
  UUIDs are unique across servers, so this can never grab the wrong NPC.

## Commands (`terranovanpcs.admin`)

| Command | |
|---|---|
| `/tnpc reload` | re-read the files and sync |
| `/tnpc list` | managed NPCs with their Citizens id |
| `/tnpc adopt <citizens id> <id>` | take an existing NPC under management on this server |

Taking over existing NPCs is normally done with `replaces` in the definition, which
works on every server without a command. `adopt` is the in-game shortcut: if a file
already defines the id, the NPC is only tagged and brought in line with that file;
otherwise its current state is written to `npcs/adopted.yml`, including
`replaces`, so other servers holding the same NPC take it over on their own. Then
change the package entries from `citizens <n>` to `tnpc <id>`; both forms keep
working side by side.

## API status

Uses BetonQuest's public NPC API (`NpcFactory`, `NpcWrapper`, `NpcReverseIdentifier`,
`IntegrationService`), plus BetonQuest's own `CitizensAdapter` so these NPCs behave
exactly like `citizens` ones in every BetonQuest feature; that class is internal.
BetonQuest is optional: without it the lib still manages the NPCs. Without Citizens
the feature stays off.

## Threads

Written for Folia (from BetonquestAdditions, which declared `folia-supported`;
TerranovaLib does not): each NPC is changed on the region thread that owns its
location.

## Web map

With HeroicMap 0.17.0 or newer, `NpcMapLayer` shows every Citizens NPC, not only
the managed ones, on the layer `terranovalib:npcs` of the web map and the mod: a
pin with the NPC's name at its stored spot, with its face if it has a player skin.

- **Every 30 s** and 1 s after the start the layer is compared with the Citizens
  registry: `put` for what changed, `remove` for what is gone. Citizens has no
  event for a new skin, and `NPCMoveEvent` fires on every step.
- **Head:** the texture hash (`SkinHash`, the part of `textures.SKIN.url` after
  `/texture/`) from the skin trait, else from the profile of the spawned NPC.
  Only `textures.minecraft.net`, never a URL; HeroicMap fetches the skin itself.
- **Left out:** NPCs without a name or stored spot, with the name tag switched
  off, or despawned on purpose. Mob NPCs get a plain pin.
- **Limits of HeroicMap:** 1000 pins and 256 different heads per layer; above
  that, one log line, and the rest stay off or get a plain pin.
- Without HeroicMap or Citizens the layer stays off. When the lib is disabled,
  HeroicMap drops the layer itself.

## History

Lived in BetonquestAdditions, now in TerranovaLib. `tnpc`, `/tnpc`,
`terranovanpcs.admin` and the Citizens key `terranova-id` did not change.

## Verified, and not yet

Verified: compiles against BetonQuest 3.3.0-DEV-52 and Citizens 2.0.44-SNAPSHOT;
`./gradlew test` checks definition
parsing and that malformed ids, locations, types and skins are rejected before
anything touches Citizens.

Smoke-tested in BetonquestAdditions on the test server (no player online): a definition added and
`/tnpc reload` created and spawned the NPC; Citizens saved its id in `saves.yml`;
a package using `tnpc <id>` loaded without errors; deleting the definition and
reloading removed the NPC again.

Tested in BetonquestAdditions by server starts on the prod mirror (Prod world and data, no player online),
1.0.3: with `replaces`, prod's Friedolin (Citizens #3) was taken over instead of a
second one being created ("1 taken over", Citizens' id counter unchanged); an NPC
standing elsewhere (moved by hand with `/npc tphere`) was moved back to its
defined spot ("Moving 'friedolin' (#3) from … to …", spot confirmed in
`saves.yml`). On dev the same build created nothing and moved nothing.

Not yet checked in game: clicking a `tnpc` NPC starts its conversation; reconcile
after `/citizens reload` (needs a confirmation that RCON cannot give); `adopt` on a
live NPC.
