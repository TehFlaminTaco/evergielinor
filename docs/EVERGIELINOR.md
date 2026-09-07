# EverGielinor

A finite, seeded, procedurally generated island for Elvarg, with real terrain —
heights, biomes, coastlines — written into the map files that the client and the
server already read.

One shared world. No instancing: every player walks the same island and the same
dungeons.

## Quick start

```
cd ElvargServer

./gradlew :game:generateWorld -Pseed=847293   # generate, validate and install
./gradlew :game:verifyInstall                 # load it back through RegionManager
./gradlew :game:run                           # play it
```

To roll back to the stock Elvarg map:

```
git checkout -- ElvargServer/data/clipping ElvargClient/Cache
```

## Host tools

| Task | What it does |
|---|---|
| `:game:generateWorld -Pseed=N` | Generate, validate and install a world. Add `-PdryRun=true` to stop before writing. |
| `:game:inspectWorld` | Print the installed world: seed, localities, dungeons, boss assignments. |
| `:game:previewIsland -Pseed=N [-Pout=x.png]` | Render a seed's geography to a PNG without touching game data. |
| `:game:verifyInstall` | Load the installed island through the server's own `RegionManager`. |
| `:game:verifyCodec` | Round-trip every shipped map file through the landscape codec. |
| `:game:verifyDeterminism -Pseed=N` | Generate one seed twice and prove the results are byte-identical. |

In game (owner/developer): `::world`, `::world seed`, `::world here`,
`::world dungeons`, `::world goto <name>`, `::world xp <n>`, `::world reqs on|off`.

## How terrain works

The client loads terrain and objects from its own cache (index 4), resolved
through a `map_index` it also holds. Terrain bytes never cross the wire and the
JagGrab on-demand path is commented out, so a server cannot stream new terrain.

EverGielinor therefore **overwrites the map file payloads of regions that already
exist in `map_index`**, in both stores at once:

- `ElvargServer/data/clipping/maps/<id>.dat`
- `ElvargClient/Cache/main_file_cache.dat` (index 4)

`map_index` itself is never modified, so no cache index rebuild is needed and no
client code changes at all. The island occupies a reserved 12×12 region block at
region (18, 39) — 144 regions whose map file ids are used by no region outside
the block, checked before anything is written.

Both stores must stay in lockstep: the server derives collision from its copy and
validates every object interaction against it, while the client renders and
pathfinds from its own. `MapInstaller` writes the server copy first and reads one
file back from the client cache afterwards to confirm they match.

### Landscape format

`LandscapeCodec` implements the format both parsers read
(`MapRegion#readTile` client-side, `RegionManager#loadMapFiles` server-side):

| Tile type | Meaning |
|---|---|
| `0` | end of tile; client derives the height itself |
| `1, h` | end of tile; height is `-h * 8` world units (`h == 1` reads as `0`) |
| `2..49, id` | overlay; shape `(type-2)/4`, rotation `(type-2)&3` |
| `50..81` | tile flags, `type - 49` (bit 1 blocked, bit 2 bridge) |
| `82..255` | underlay, `type - 81` |

`./gradlew :game:verifyCodec` round-trips all 1,657 shipped terrain files and
1,657 object files (2.9M objects) through it.

Note the server *discards* height — it stores tile flags in an array confusingly
named `heightMap` and has no elevation model. Height is therefore purely visual,
authored by the generator for the client to render.

## Generation pipeline

```
seed
 └─ geography      elevation (warped fBm + ridged noise), coastline, heights
     └─ biomes     14 biomes; volcano, wilderness, desert and swamp placed
     │             deliberately so every world contains them
     └─ localities 80×80 cells, difficulty from distance-from-start + inland depth
         ├─ towns       levelled plaza, services by locality identity, roads
         ├─ resources   weighted to the band, with a rare above-band tail
         ├─ monsters    drawn from a few species per locality
         └─ dungeons    stratified across bands, one boss each, chest in the boss room
             └─ validation → install → world.json
```

Materials are real: every underlay, overlay and object id was read from this
cache's own `flo.dat` and object definitions, or from the enum the relevant skill
already dispatches on (`Mining.Rock`, `Woodcutting.Tree`), so generated resources
are usable rather than scenery.

## Persistent identity vs regenerable layout

```
PERSISTENT   world.json — seed, localities, dungeon ids, BOSS BINDINGS
REGENERABLE  dungeon layout, a pure function of (worldSeed, dungeonId, floor)
```

A dungeon's boss is written to the world record and never recomputed, so it
survives restarts and re-layouts. `GENERATOR_VERSION` is stored with each world;
loading a world made by a different version says so rather than silently
reinterpreting the seed.

## Configuration

`ElvargServer/data/world_config.json`, created on first boot:

```json
{
  "seed": 847293,
  "regularSkillsXpMultiplier": 18.0,
  "combatSkillsXpMultiplier": 6.0,
  "enforceSkillRequirements": true,
  "pvpEnabled": false
}
```

`enforceSkillRequirements` is the level-gated vs resource-gated switch. It gates
the single call at `SkillManager:654` that every skillable passes through, so the
two progression models differ by exactly one condition — deliberately left as a
switch rather than a decision.

## Settlements

Settlements are built from **278 building prefabs harvested out of the original
game map** (`./gradlew :game:extractPrefabs`), not hand-authored from wall ids.
Buildings are found by their roofs - roof objects sit on the plane above the
walls and cover a footprint exactly - and everything inside is captured across
every plane: walls, windows, doors, upper storeys, furniture and floor
materials. Only buildings with a door are kept.

A town levels its ground, paves a square, runs four streets out of it, and lines
those streets with prefabs. Services (bank booth, furnace, anvil, range, altar)
sit around the square, and a shopkeeper runs a shop chosen from the settlement's
identity - a mining camp sells tools, a fishing village sells food.

## Resources are places, not sprinkles

Trees are placed as **groves**: one dominant species with roughly one in five
being something else, denser at the centre. Ore is placed as **mines**: a patch
of ground stripped back to bare earth with a dominant ore and a second mixed in,
the way the Varrock mine reads from above.

Everything placed is vetted against its object definition first, and its true
footprint is reserved. Objects in this cache are not all one tile - a bush is
2x2, a cave entrance 4x2 - and placing them a tile apart made models intersect
and render half-buried.

## Known limits

- **TzTok-Jad cannot be placed.** Its only constructor is
  `(Player, FightCavesArea, int, Location)`; `NPC.create` would fall back to a
  plain NPC — a level 702 monster with no combat method. It is marked
  `MINIGAME_ONLY` in `BossRoster` and never assigned.
- **Dungeons are capped at four floors**, because floors are stacked on planes
  within a single region.
- **`World.objects` is still a `LinkedList`.** Generated content goes into the
  map files rather than the dynamic object list, which sidesteps the problem for
  now, but player construction will hit it.
- **No player construction yet** — beds are claimable where they exist, but
  cannot yet be built.
- **Dungeon interiors are plain rooms and corridors** — no prefab equivalent of
  the surface buildings yet.

## PvP decommission

PvP combat itself is untouched — wilderness, skulling and bounty hunter all
still work. What was removed is the tooling that made playing for anything
optional:

| Removed | Why |
|---|---|
| Spawn tab (`SpawnItemPacketListener`, opcode 187) | **Had no rights check at all** — any client sending the opcode could conjure items into its inventory or bank |
| `GameConstants.ALLOWED_SPAWNS` | Nothing reads it now |
| `::item`, `::runes` | Free items |
| `::master`, `::reset` | Free levels |
| `::bank`, `::copybank` | Bank access from anywhere |
| Preset interface, editor, and death-time re-gear | Re-equipped a player on demand and on every death |
| New-player preset grant (`Player.java`) | **Every new player was handed a random PvP preset — maxed combat stats and a full kit — on first login** |

`Presetables.load` survives as an internal API because the player-bot system
uses it to equip its fighters; it is no longer reachable by a player.
