# Dialogue HUD

Shows BetonQuest conversations in the actionbar, right above the hotbar, instead of
in the chat. Vanilla client, no mods; the frame comes from Nexo glyphs in the server
resource pack.

```text
[▌ ⚓ Rotbart │ Die See birgt viele Geheimnisse … ▶ 1/3 ▐]
```

BetonQuest keeps everything a conversation means: texts, answers, conditions,
actions, placeholders, translations and quest progress. This plugin only displays
what BetonQuest hands it and reports back the number of the answer picked.

## Setup

1. Install TerranovaLib next to BetonQuest (and Nexo) and start the server once. The HUD
   only starts where BetonQuest is installed.
2. Copy the contents of [`nexo/`](../nexo) into `plugins/Nexo/`: it mirrors that folder,
   with the glyph definitions in `glyphs/terranova_dialogue.yml`, the frame
   textures in `pack/assets/minecraft/textures/terranova/dialogue/` and the text
   row fonts in `pack/assets/terranova/`. Run `/nexo reload all`, then
   `/dialoguehud reload`. The log line "Dialogue lines per page: N" says how many
   lines the pack allows.

   The frame is a 9-slice whose master lives in the design repo
   (`design/DialogueHUD/dialogue.aseprite`, exported by `export.py` there): 8 px
   caps and middle tiles of 1 to 128 px, assembled without seams to any width,
   with a 3 px border and 4 px of fill around the text. There is one frame per line
   count, 10 · lines + 12 px high (22 px for one line), named like the one-line
   pieces with `_2` … `_6` appended.
3. Use it per conversation, or for all of them in BetonQuest's `config.yml`:

   ```yaml
   conversationIO: terranova_hud            # in a conversation
   interceptor: none                        # keep the chat usable, see below
   conversation.default_io: terranova_hud,menu,tellraw   # globally
   ```

   **Always pair it with `interceptor: none`.** BetonQuest's interceptor holds
   back all chat during a conversation (made for conversations shown in chat).
   The HUD draws in the actionbar, so with an interceptor the chat is simply dead
   while an NPC talks. Once every conversation uses the HUD,
   `conversation.interceptor.default: none` in BetonQuest's `config.yml` does it
   for all of them.

4. `/dialoguehud preview [text]` shows one line in your actionbar, to check the frame
   at your GUI scale.

All settings are in `plugins/TerranovaLib/dialogue-hud.yml` ([default](../src/main/resources/dialogue-hud.yml)).

## Controls

| State | Input (default) | Effect |
|---|---|---|
| Reading | right click, left click, F, jump | finish the typed page, then next page, then the answers |
| Answers | mouse wheel / hotbar keys | previous / next answer (the held slot stays) |
| Answers | right click, left click, F, jump | pick the answer |
| Any | sneak | end the conversation, unless it blocks movement (`stop: true`) |

The server cannot see arbitrary keys. These are the vanilla actions a client
reports anyway: clicks, the hotbar slot, swapping hands, and since 1.21.2 the
movement keys (`PlayerInputEvent`). A right click into the air only reaches the
server while holding an item; clicking the NPC or a block always works. Nothing is
intercepted outside a running conversation, and inside one an event is only
cancelled when the dialogue used it.

## How it fits together

| Class | Role |
|---|---|
| `DialogueHud` | the feature: config, one session per player, `/dialoguehud`, public `isInDialogue(Player)` |
| `ConversationIntegration` | registers `terranova_hud` with BetonQuest, picks HUD or fallback per conversation |
| `DialogueSession` | the `ConversationIO`: one player's dialogue state, ticking on the player's scheduler |
| `ActionbarRenderer` | formats, pagination by real font width, typing effect |
| `GlyphIntegration` | Nexo glyphs, their measured widths, the frame |
| `DialogueInputHandler` | vanilla events to dialogue input |

There is no renderer interface: the actionbar is the only renderer, and the
fallbacks are BetonQuest's own IOs.

## What the actionbar can and cannot do

- It is one line, horizontally centered, at a fixed height above the hotbar (higher
  in survival, where hearts and food sit between). The client offers no way to move
  it, so this HUD is not freely positionable.
- Up to 6 lines are stacked upwards from that line, 10 px apart (`hud.max-lines`).
  Below it there is no room: the hotbar and hearts are there. A font can only lift
  a glyph by its own height (Minecraft rejects an ascent above the height), so each
  row above the actionbar line has its own copy of the vanilla font whose
  character cells are padded below, `terranova:dialogue_row1` … `row5`.
  [`tools/row_fonts.py`](../tools/row_fonts.py) builds them from the official client
  jar (`python -I tools/row_fonts.py <client.jar> nexo/pack/assets/terranova`); run it
  again if Mojang changes the font. These are modified Mojang textures shipped in
  the server pack. Characters outside the vanilla bitmap fonts (rare scripts that
  fall back to Unifont) are not in the row fonts.
- Pages start with the NPC's name; answers become a list with a header line,
  scrolling when there are more answers than lines. Without the pack, or with only
  the one-line frame, every page is a single line as before.
- Horizontal layout uses negative spaces (Nexo shifts). Every width comes from
  Minecraft's own glyph widths (via BetonQuest's line wrapper) or, for glyphs, from
  the very texture Nexo packs, computed the way the client computes a bitmap
  glyph's advance. GUI scale changes the size, not the layout.
- The client fades an actionbar message after about three seconds, so the current
  line is re-sent every `keepalive-ticks`.
- Long NPC text is split into pages at word boundaries. Colors, formatting and any
  Unicode text survive; a single word longer than a line is split.

## Fallbacks

Chosen once per conversation, all configurable under `fallback`:

| Situation | Default |
|---|---|
| Resource pack declined, failed or still loading | BetonQuest's native `dialog` |
| Nexo missing, glyphs disabled, a frame glyph or texture missing | `plain`: this HUD in vanilla text |
| More answers than `selection.max-hud-options` | that turn in `dialog`, then back to the HUD |

Without the pack no glyph character is ever sent, so nobody sees boxes.

The pack check trusts only a status this server received. After a server switch
behind NexoProxy there is none: the client kept the pack, and the proxy does not
send it again. No status therefore counts as loaded. That is safe only because the
pack is mandatory (Nexo `Pack.dispatch.mandatory: true`, sent before joining), so a
player who declines it never gets onto the server. Without a mandatory pack, a
player who declined it would see boxes after switching servers.

## The actionbar is shared

Vanilla shows one actionbar message at a time, and the last sender wins. Without a
packet library there is no way to hold other plugins' messages back, so:

- While a dialogue runs, it is re-sent every `keepalive-ticks`; a message from
  another plugin shows until then.
- Other plugins can call `DialogueHud.isInDialogue(player)` and skip
  their own actionbar messages meanwhile.
- When a dialogue ends, the actionbar is cleared once; nothing is overwritten after.

## BetonQuest API status

BetonQuest 3.3 has no public API for conversation IOs yet; its docs list them as a
future feature. The IO is registered the way BetonQuest's own integrations do it:
from inside a public `IntegrationService` integration (so it exists before
conversations are parsed), through the internal `ConversationIORegistry`. The
classes `Conversation`, `ConversationIO`, `ConversationIOFactory` and
`ConversationIORegistry` are internal. Verified against 3.3.0-DEV-52; expect to
adjust when BetonQuest publishes the API.

Nexo's glyph lookup (`NexoPlugin#fontManager`) and `Shift` are outside Nexo's `api`
package as well; verified against Nexo 1.29.1.

## Folia

The HUD schedules as Folia needs it: one repeating task
per dialogue on the player's entity scheduler, cancelled when the dialogue ends or
the player leaves, and no global tasks. All dialogue state is guarded by the
session's monitor, because BetonQuest calls in from its own async threads.

It cannot run on Folia today: BetonQuest 3.3.0-DEV-52 does not declare Folia
support and uses the Bukkit scheduler, so Folia does not load it, and the HUD
requires it.

## Verified, and not yet

Verified: compiles against BetonQuest 3.3.0-DEV-52 (the snapshot artifact that is
byte-identical to the server jar) and Nexo 1.29.1; `./gradlew test` checks the typing
effect (no split characters, styles kept), the texture width measurement, and
that the frame is gapless and advances exactly its width for exact and overlapping
tile sets.

Smoke-tested on the test server (`servers/main`, no player online): the plugin
enables, `terranova_hud` is registered before BetonQuest parses its packages, a
package with `conversationIO: terranova_hud` loads without errors (also after
`/bq reload`), the frame glyphs are found and measured (caps 8 px, tiles 1–128 px),
and Nexo's generated pack contains the `terranova:dialogue` font with the expected
characters, ascent and height.

The HUD came to TerranovaLib from BetonquestAdditions 1.1.0 (Terranova-Content,
commit a7ca3dc). In the lib it hooks into BetonQuest when BetonQuest enables, since
the lib loads before Nexo and BetonQuest; otherwise the code is unchanged. The log
lines are the same, with the prefix `[TerraNovaLib]`.

Not yet checked in game, to be done on the test server with a vanilla client:

1. HUD shows on a vanilla client, at GUI scales 2, 3 and 4.
2. The frame is drawn behind the text, not over it. This depends on how the client
   orders glyphs from different fonts in one line; `/dialoguehud preview` shows it.
3. An NPC opens the HUD; texts and answers match BetonQuest's.
4. Picking each answer follows the right branch; conditions and actions unchanged.
5. Long text pages correctly; typing and skipping work.
6. Two players talk to NPCs at the same time.
7. Ending (sneak, walking away, relog, `/bq reload`) leaves nothing on screen.
8. Declining the resource pack gives the `dialog` fallback (with the mandatory pack:
   the client is disconnected instead).
9. Behaviour next to other actionbar senders on the server (MythicMobs skills, Nexo).
10. After a server switch behind NexoProxy (main → build → main) the HUD still shows
    instead of `dialog` (fixed in 1.0.2; 1.0.1 fell back, reported on Prod).
