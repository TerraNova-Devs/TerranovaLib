# Ghost blocks

Blocks that only some players see, switched on and off by quests. The world itself
is never changed: each player's client is told about a group's blocks, so one
player sees the rebuilt bridge while another still sees the ruin.

```yaml
# a BetonQuest package
actions:
  showBridge: "ghostblock show harbour_bridge"
  hideBridge: "ghostblock hide harbour_bridge"
  toggleBridge: "ghostblock toggle harbour_bridge"
conditions:
  seesBridge: "ghostblock harbour_bridge"
```

## Groups

One file per group in `plugins/TerranovaLib/ghostblocks/<group>.yml`; the format
is in [`ghostblocks/example.yml`](../src/main/resources/ghostblocks/example.yml). Blocks are any
block state (`minecraft:air` makes a real block disappear), listed one by one or as
boxes; at most 65,536 per group.

The easy way to make one: build it, then

```text
/ghostblocks capture <group> <x1> <y1> <z1> <x2> <y2> <z2>
```

saves every non-air block in the box as a new group (hidden by default), after
which the real blocks can be removed again.

## How it works

- Visibility is stored per player and group in the player's persistent data, so it
  survives relogs and restarts (and travels with HuskSync if it syncs persistent
  data). `default-visible` applies until a quest sets it.
- The client forgets a ghost block whenever the server sends that spot again. That
  happens when a chunk is sent anew (walking away and back, teleport, world
  change, relog): every chunk sent to a player is followed by its ghost blocks
  (`PlayerChunkLoadEvent`). It also happens when the player clicks, digs or places
  near it: the ghost blocks around the player are sent again shortly after.
- Hiding a group sends the real blocks back, read on the thread that owns their
  chunk; where another visible group overlaps, that group's block is kept.

## Limits

Ghost blocks are only drawn. The server still has the real blocks:

- A ghost floor over air cannot be walked on. The client thinks it can, the server
  sees air; the player falls or rubber-bands, and anti-cheat (Vulcan) may flag it.
- A real wall hidden with ghost air cannot be walked through; the server pulls the
  player back.

Use groups for scenery and reveals, not for paths. A real barrier that opens per
player needs a different approach.

## Commands (`ghostblocks.admin`)

| Command | |
|---|---|
| `/ghostblocks reload` | re-read group files and update everyone's view |
| `/ghostblocks list` | groups, worlds, block counts |
| `/ghostblocks show <player> <group>`, `hide …` | switch a group by hand |
| `/ghostblocks capture <group> <x1> <y1> <z1> <x2> <y2> <z2>` | save a built box as a group |

## API status

Uses only BetonQuest's public API (action and condition factories through
`IntegrationService`). BetonQuest is optional: without it, groups are still
switchable with the commands.

## Threads

Written for Folia (from BetonquestAdditions, which declared `folia-supported`;
TerranovaLib does not): visibility is written on the player's thread, real blocks
are read on their region's thread. `capture` reads the box on your own thread and
fails cleanly if part of it belongs to another region.

## History

Started as its own plugin (hence the persistent data namespace
`terranovaghostblocks`, kept so stored visibility survives), then lived in
BetonquestAdditions, now in TerranovaLib. Names in quest packages (`ghostblock`),
the command and the permission did not change.

## Verified, and not yet

Verified: compiles against BetonQuest 3.3.0-DEV-52 (the snapshot in BetonQuest's
repository is that plugin jar); `./gradlew test` checks that chunk keys decode back
to their coordinates, including negative ones.

Smoke-tested in BetonquestAdditions on the test server (no player online): groups with single blocks and
fills load and reload; a package using the `ghostblock` action and condition loads
without errors.

Not yet checked in game: that block changes sent from `PlayerChunkLoadEvent`
arrive after the chunk (so the chunk does not overwrite them), the refresh after
clicking and digging, and the actions and condition in a quest.
