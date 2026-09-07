# EverGielinor — Phase 0: Forensic Reconnaissance

Reverse-engineering report on the Elvarg client/server pair, establishing what a
finite, procedurally generated, persistent island would actually cost to build on
this codebase.

Base commit: `769f82c` (RSPSApp/elvarg-rsps).
Scope: investigation only — no functional code was changed by this report.

Full report (formatted): <https://claude.ai/code/artifact/d84d89e3-4b78-4e81-923a-cb7462c9feff>

**Evidence marking.** `[VERIFIED]` means it was executed, or the exact line is
quoted. `[INFERRED]` means reasoning from verified facts, not itself demonstrated.

---

## 1. The three findings that reorder the plan

### 1.1 The client already implements dynamic region construction `[VERIFIED]`

`ElvargClient/.../Client.java:14489` handles opcode **241** and stitches the
104×104 scene from arbitrary 8×8 chunks pulled from anywhere in the cache, each
with its own source plane and rotation (`loadRegion()`, `Client.java:2264-2325`).
Terrain, objects and collision all follow the stitched layout.

Wire format, read at `Client.java:14417-14426` — per chunk, `readBits(1)` for
visible, then `readBits(26)`:

| Field    | Bits    | Client extraction   |
|----------|---------|---------------------|
| plane    | 24–25   | `>> 24 & 3`         |
| chunk X  | 14–23   | `>> 14 & 0x3ff`     |
| chunk Y  | 3–13    | `>> 3  & 0x7ff`     |
| rotation | 1–2     | `>> 1  & 3`         |

### 1.2 The server's counterpart exists as dead code, with a matching layout `[VERIFIED]`

`ElvargServer/.../net/packet/PacketSender.java:1064-1081` carries a commented-out
`sendConstructMapRegion(Palette)` writing `z(2) · x(10) · y(11) · rot(2) · pad(1)`
= 26 bits. **This matches the client's decoder exactly.**

`PacketBuilder.putBits` (`:147`) and `AccessType.BIT` (`:182`) are present and
working. The only missing piece is a ~40-line `Palette` / `PaletteTile` holder.

### 1.3 Terrain bytes never cross the wire `[VERIFIED]`

The client resolves map files via `ResourceProvider.resolve()` against a
`map_index` read from its own cache, then loads them from `main_file_cache.idx4`
(`loadMandatory()`). The JagGrab on-demand path that would let a server serve map
files — `ResourceProvider.request()` — is **entirely commented out**.

Terrain is a client-side asset.

**Headline:** procedural geography is reachable without touching the client;
procedural *terrain* is not. Opcode 241 can compose an island from the 106,048
authored chunks already in the cache, but cannot invent a hillside that does not
already exist somewhere in Gielinor.

---

## 2. Feasibility ratings

GREEN = straightforward · YELLOW = significant engineering · ORANGE = difficult ·
RED = major architectural modification

| Capability | Rating | Why |
|---|---|---|
| Procedural localities | GREEN | Pure server-side data |
| Procedural resources | GREEN | `ObjectManager.register()` + skills dispatch on object ID |
| Skill infrastructure | GREEN | `ObjectActionPacketListener` switches on named IDs; generated bank/anvil/furnace work on placement |
| Beds / spawnpoints | GREEN | Respawn hardcoded at exactly two sites (`PlayerDeathTask.java:192,240`) |
| Persistent boss identity | GREEN | Seeded `Map<dungeonId,npcId>` stored with the world |
| Boss chests | GREEN | `NPCDropGenerator` + 742 existing drop tables |
| Transportation (teleports) | GREEN | `TeleportHandler.teleport(player, Location, …)` already takes any destination |
| Procedural biomes | YELLOW | Needs a chunk-palette classifier; 1,153 signatures measured |
| Procedural towns | YELLOW | Placement free; templates, roads, validation are the work |
| Persistent overworld | YELLOW | No world persistence exists — new subsystem, but unobstructed |
| Player construction | YELLOW | Placement + validation exist; ownership and delta log are new |
| Procedural terrain (chunk-stitched) | YELLOW | Opcode 241, both ends verified; bounded by the authored palette |
| Dynamic dungeons | ORANGE | Needs 241 *and* rework of instancing (see §4) |
| Object scale to 1e5 objects | ORANGE | `World.objects` is a `LinkedList` (see §6) |
| Dynamic terrain (arbitrary) | RED | Client reads terrain only from local cache |
| Server-side elevation | RED | Server has no height concept anywhere |

---

## 3. Client/server boundary

| Subsystem | Server representation | Network | Client representation | Verdict |
|---|---|---|---|---|
| Heightmap | none — discarded | none | `tileHeights[4][105][105]` | RED |
| Underlays / overlays | parsed, discarded | none | cache idx4 only | RED |
| Terrain tile flags | → clipping only | none | `tileFlags[4][104][104]` | RED |
| Region composition | `Region(id,terrain,obj)` | **241 palette** | `constructRegionData[4][13][13]` | YELLOW |
| Map objects | `MapObjects` | from cache | `SceneGraph` | GREEN |
| Dynamic objects | `ObjectManager` | 151 / 101 / 153 | `SceneGraph` mutation | GREEN |
| NPCs | `World.getNpcs()` | NPC update block | `npcs[16384]` | GREEN |
| Ground items | `ItemOnGroundManager` | 44 / 156 / 84 | `groundArray` | GREEN |
| Collision | `Region.clips` (authoritative) | none | `CollisionMap` (advisory) | GREEN |
| Persistence | player only | n/a | none | YELLOW |

### The mirroring invariant `[VERIFIED]`

`tools/verify_cache_sync.py` extracts `map_index` from the client's versionlist
archive (idx0 file 5, bzip2) and diffs it against
`ElvargServer/data/clipping/map_index`:

```
client map_index entries   1657
server map_index entries   1657
file-id disagreements      0
client/server map data:    IN SYNC
```

**Any change to map data must be applied to both sides atomically**, or object
interaction desynchronises. Collision is derived independently on each side;
there is no reconciliation mechanism.

---

## 4. Structural constraints found

### Instancing is not spatial `[VERIFIED]`

`PrivateArea` is a *visibility overlay*: instanced entities live at the same world
coordinates as everyone else and are filtered per-player in
`ObjectManager.perform()` and `MapObjects.get()`, with a per-area
`Map<Location,Integer>` of clip overrides. It does **not** allocate a separate
coordinate space.

Two parties in different generated dungeons would occupy the same tiles. Genuine
dungeon instancing needs either opcode 241 (each instance gets its own stitched
scene) or a coordinate-space allocator handing out disjoint unused regions. This
is the largest unresolved design question. `[INFERRED]` — neither was prototyped.

### The server discards height `[VERIFIED]`

In `RegionManager.loadMapFiles`, the `tileType == 1` branch reads the height byte
and throws it away. The array named `heightMap` actually stores tile *settings*
(bit 1 = blocked, bit 2 = bridge) from `tileType` 50–81. The server is strictly
2.5D: four planes, no elevation.

Consequence for Phase 10: chunk stitching produces visible height discontinuities
at seams unless the palette classifier records each chunk's edge heights and the
layout generator matches them. That edge-matching constraint is the main technical
content of the terrain work.

---

## 5. Terrain: what stitching buys

`tools/verify_map_format.py` decodes every terrain file with an independent
parser and asserts byte-exact consumption:

```
map_index entries          1657
terrain files decoded      1657 (byte-exact, 0 failures)
8x8 chunks on plane 0      106048
  >=75% blocked            60709  (ocean / void)
  usable land chunks       45339
distinct biome signatures  1153
```

Tile encoding (mirrors `RegionManager.loadMapFiles`):

| Type   | Meaning                                            |
|--------|----------------------------------------------------|
| 0      | end of tile                                        |
| 1      | height byte follows, end of tile                   |
| 2–49   | overlay id byte follows; shape/rotation in the type |
| 50–81  | tile settings, `type - 49`                          |
| 82+    | underlay, `type - 81`, no payload byte              |

### Ranked approaches

| Approach | Client change | Cache change | Ceiling | Rank |
|---|---|---|---|---|
| Existing terrain + generated objects | none | none | Geography fixed, everything above generated | 1st — do this first |
| Chunk stitching (opcode 241) | none | none | Any island of authored 8×8 chunks, rotated | 2nd — the real target |
| Regenerated cache shipped to players | none | full rebuild | Arbitrary terrain | 3rd — re-roll costs a redistribution |
| Revive JagGrab on-demand | uncomment + file server | server-authored | Arbitrary, streamed, re-rollable | 4th — best long-term |
| New terrain-streaming packet | substantial | bypassed | Arbitrary, fully dynamic | 5th |
| Client-side terrain synthesis | major | bypassed | Unbounded | 6th |

---

## 6. Hazards

### Object storage will not scale `[VERIFIED]`

`World.objects` is a `LinkedList<GameObject>` (`World.java:62`).
`ObjectManager.register()` linearly scans the entire list on every registration,
and `ObjectManager.onRegionChange()` iterates *all* world objects and, for each,
loops over *all* players. A world with 1e5 objects makes boot O(n²) and every
region change O(objects × players).

Fix before the object count grows: a `Map<regionId, List<GameObject>>`, and a
region-change path that sends only the changing player's nearby regions.

### Presets are load-bearing for PlayerBot `[VERIFIED]`

`Presetables` looks like pure PvP infrastructure but is called from
`PlayerBot.java:165`, `playerbot/interaction/CombatInteraction.java:227` and
`playerbot/commands/LoadPreset.java` to equip bots. It is also opened on death at
`PlayerDeathTask.java:195`.

Decommission order: sever the death-time `Presetables.open()` call (which
conflicts with bed respawn anyway) → give `PlayerBot` its own loadout source →
remove the player-facing preset UI → delete `Presetables`. The same care applies
to `GameConstants.ALLOWED_SPAWNS`, read by both `SpawnItemPacketListener` and
`Presetables`.

### Definition loaders run concurrently `[VERIFIED]`

`GameBuilder.createBackgroundTasks()` hands a queue to a `BackgroundLoader` that
may use multiple threads; the comment states interdependent utilities must live in
the *same* task. World generation depends on item, NPC, drop and object
definitions.

`WorldLoader` must therefore run **after** `backgroundLoader.awaitCompletion()`,
not as another background task. Getting this wrong produces intermittent
null-definition failures that appear only under specific thread timings.

### A slur in vendored client code `[VERIFIED]`

`ResourceProvider.resolve()` (~line 445) contains local variables named with a
racial slur, inherited from upstream Elvarg. It sits in the exact method the
terrain work touches. Rename in the first commit that touches this file.

### Build pins an unavailable toolchain `[VERIFIED]`

`ElvargServer/build.gradle.kts:39-45` requests a Java 17 toolchain with no
download repository configured; on a JDK 21 host the build fails at configuration
time. Changing both `JavaLanguageVersion.of(17)` occurrences to 21 compiles the
server cleanly (`:game:compileJava` succeeds, warnings only).

---

## 7. Boss roster: what actually exists `[VERIFIED]`

Eight NPCs have dedicated combat methods in
`game/content/combat/method/impl/npcs/`: Callisto, Chaos Elemental, Chaos Fanatic,
Crazy Archaeologist, TzTok-Jad, King Black Dragon, Venenatis, Vet'ion.

Content inventory:

| Metric | Count |
|---|---|
| NPCs with a combat level | 3,038 |
| NPCs with drop tables | 1,627 |
| Drop tables defined | 742 |
| NPCs level ≥100 with drops | 365 |
| Mining rock types (30 object IDs) | 11 |
| Woodcutting tree types | 11 |

Others with definitions and loot but generic AI include Corporeal Beast (785),
Zulrah (725), K'ril Tsutsaroth (650), General Graardor (624), Commander Zilyana
(596).

This sizes the boss system: **8 fully-realised boss dungeons at launch**,
extensible to ~20 by promoting NPCs that have definitions and drop tables. Every
other generated dungeon must be boss-less — which is the correct default, not a
compromise.

Note: `npc_defs.json` contains junk combat levels (1337, 12500) that any
difficulty-banding generator must filter.

### Identity vs. layout

```
PERSISTENT   (world.json — survives everything)
  dungeonId 12 -> entrance, biome=VOLCANIC, difficulty=HIGH
  dungeonId 12 -> bossNpcId = 50 (King Black Dragon)
  dungeonId 12 -> floorCount = 4

REGENERABLE  f(worldSeed, dungeonId, floorIndex)
  rooms, corridors, traps, spawn points, treasure

EPHEMERAL    discarded on restart; players ejected to the entrance
  the live instance, its NPCs, its loot on the floor

Restart => layout may differ, the boss never does.
```

---

## 8. Key insertion points

| Concern | File / line |
|---|---|
| World-gen boot hook | `GameBuilder.java:35-55`, after `awaitCompletion()` |
| Object spawning | `ObjectManager.register()` (`:37`) |
| Object existence check | `MapObjects.get()` (`:36`) |
| Collision mutation | `RegionManager.addClipping` / `removeClip` |
| XP multiplier | `GameConstants.java:91-92` → `SkillManager.java:166-168` |
| Skill-requirement toggle | `SkillManager.java:654` — single chokepoint |
| Respawn location | `PlayerDeathTask.java:192, 240` |
| Dynamic teleport | `TeleportHandler.java:27` |
| Opcode 241 | `PacketSender.java:1064` (commented) |
| Persistence pattern to copy | `game/entity/impl/player/persistence/` |
| Generated-content loader pattern | `ObjectSpawnDefinitionLoader.java:14` |

---

## 9. Reproducing the proofs

From the repository root:

```
python3 tools/verify_map_format.py   # decodes all 1657 terrain files byte-exactly
python3 tools/verify_cache_sync.py   # diffs client cache map_index vs server's
```

Both are dependency-free Python 3. `verify_cache_sync.py` exits non-zero if the
client and server map data ever drift — worth wiring into CI once map generation
begins.

---

## 10. Next steps

The two things to prototype next, both explicitly untested here:

1. **The instancing decision** (§4) — opcode 241 per-instance scenes vs. a
   disjoint-region allocator. Blocks all dungeon work.
2. **The JagGrab path** (§5) — the client scaffolding exists in commented form and
   would make worlds re-rollable without redistributing a cache. The file-server
   side does not exist in this repo; `Client.openSocket` and `JagGrabConstants`
   need review before committing to it.

Everything in Phases 1–9 of the plan runs on existing terrain and is unblocked by
both.
