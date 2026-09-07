# ItemSearchDataGenerator

Offline generator that turns OSRS Wiki sweeps into the Item Search RuneLite
plugin's data files. Not part of the plugin build.

The plugin lives at `E:\Projects\JAVA\ItemSearch`; generated output is written
into its `src/main/resources/com/itemsearch/` directory (the default output
path assumes that sibling layout; pass an explicit output path otherwise).

| Output | Entry point | Gradle task |
| --- | --- | --- |
| `sources.json` | `SourceDataGenerator` | `runSources` |
| `shops.json` | `ShopDataGenerator` | `runShops` |
| `drops.json` (legacy, retiring) | `DropDataGenerator` | `run` |

## Snapshots are the reproducibility pin

Wiki content can change at any time, so every generator reads **committed
snapshots** and never hits the network unless you pass a `--refresh-*` flag.
The snapshot files (dated by their commit) are what makes a regeneration
reproducible.

| Snapshot | Refreshed by | Holds |
| --- | --- | --- |
| `wiki-drops.json` | `--refresh-drops` | every page embedding `{{DropsLine}}`/`{{DropsLineReward}}`: infobox kind, npc ids, combat level, map coords, drop tables with their section headings |
| `wiki-items.json` | `--refresh-items` | item id + shop value per item name (shared with the shop generator) |
| `wiki-combat.json` | `--refresh-wiki` | `{{Infobox Monster}}` combat wikitext, keyed by page title |
| `wiki-monster-text.json` | `--refresh-wiki` | per monster page: the raw infobox, the Mechanics section, and the Strategy section (or `Name/Strategies` subpage) — read by the curator, not the generator |
| `wiki-shops.json` | `--refresh-shops` (`runShops`) | shop store tables |
| `wiki-retired.json` | `--refresh-retired` | page title -> reason (`discontinued`, `deadman`, `leagues`) for every drop page the wiki files under discontinued content, a Deadman season, or a League |

`wiki-items.json` is **shared**. `runSources --refresh-items` fetches the union
of drop-line names and shop-line names, so refreshing from either generator
keeps the other's items resolvable.

## Regenerate sources.json

From the repository root:

```bash
# full sweep: enumerate + fetch drop pages, then refresh the shared item lookup
./gradlew runSources --args="--refresh-drops --refresh-items"

# reuse the committed snapshots (no network)
./gradlew runSources
```

Flags may be combined; `--refresh-wiki` additionally re-fetches combat wikitext
for every Monster-infobox page in the drop snapshot, and keeps the parts a
curator reads (infobox, Mechanics, Strategy) in `wiki-monster-text.json` so
the curator can show them beside the editor instead of opening a browser tab.
`--refresh-retired` asks the wiki which categories each drop page sits in and
records the ones nobody in the main game can fight (see "Retired content"). A positional argument
overrides the output path. A missing snapshot raises an `IOException` naming
the flag that produces it.

Each run prints page/source counts, a per-type breakdown, the share of drops
without item ids and with unknown rarity, and how many KILL sources have wiki
combat that wouldn't parse.

### Sanity gates

After a refresh, check the run summary and the output before committing:

- `Lunar Chest` present as `REWARD`, including `Blood moon helm`
- `Zulrah`, `Vorkath`, and `Nex` present as `KILL` with non-empty `npcIds`
- `Master Farmer (pickpocket)` present as `PICKPOCKET`
- unresolved-item rate < 5%
- source count and per-type counts within a few percent of the last run

Baseline from the 2026-08-05 sweep, for comparing future runs against:

| Measure | Value |
| --- | --- |
| shared wiki table templates | 30 |
| drop tables in sources.json | 2144 holding 28397 rows |
| pages enumerated / parsed | 2375 / 2285 |
| sources | 2748 |
| drops (as a player sees them) | 52442 |
| unresolved items | 2376 (4.5%) |
| unknown rarity | 4114 (7.8%) |
| combat gaps / overlay stubs | 72 / 103 |

An earlier draft of these gates expected ≥3000 sources. That was an estimate
made before the sweep; the wiki only has ~2375 pages carrying drop tables, so
~2280 sources is full coverage of what the templates actually mark up. Judge
future runs against the table above, not the old estimate.

A systematic parsing gap (a whole template variant missed, a heading shape
mishandled) should be fixed in the parser with a regression test **before**
committing regenerated data — not papered over with an overlay entry.

### Retired content

The wiki keeps drop tables for things that are gone (the 2013 Goblin Invasion,
past holiday events, the Bloodthirsty slayer monsters) and for other game modes
(every `(Deadman)` boss, the Leagues `(Echo)` bosses). None of it belongs in a
search result for the main game, so the generator drops every source whose page
is in `wiki-retired.json`, prints the count per reason, and prunes the untouched
curator stubs for them. Pass `--keep-retired` to ship them anyway.

The verdict comes from the wiki's categories rather than titles or templates:
"Discontinued content", any category starting with "Deadman" (the seasons are
named: Apocalypse, Armageddon, Annihilation), or any ending in " League". The
`(Deadman)` and `(Echo)` title suffixes stand in when a page has no categories.
Live seasonal content — Forestry events, random events, Hard Mode raids — is in
none of those and stays.

`retired-overlay.json` overrides the wiki per page title: a reason string forces
a page out, `false` keeps one the wiki mislabels.

```json
{ "Some seasonal boss": "discontinued", "Christmas cracker": false }
```

Without the snapshot the generator keeps everything and says so; it does not
fail, because retirement is a refinement rather than an input.

### Shared drop tables

A monster's herb, seed, and rare drops usually are not on its own page: the page
transcludes one of the wiki's shared table templates. `--refresh-drops` first
fetches those templates into `wiki-drop-tables.json` (found by asking which
Template-namespace pages transclude a drop line template) and expands them while
parsing, following nesting and stopping on cycles. Each expanded table records
which shared table it came from, which is what the client's "group drops by drop
table" setting groups on.

Before this, Aberrant spectre parsed to 10 drops with no herbs at all, and every
revenant was missing.

**Shared tables compute their rates rather than stating them.** Killerwatt writes
`{{HerbDropLines|35/140}}`, and the template derives each herb from it through
`{{{1}}}` substitution, `#vardefine`/`#var`, and `#expr` arithmetic. `WikiExpr`
evaluates enough of that template language to read the result: a 35/140 chance of
the herb table times guam's 32/128 share gives 1/16, matching the wiki.

It is deliberately not a MediaWiki implementation — parameters, `#expr`,
`#vardefine`/`#var`, `#if`, and the arithmetic those need. Any other parser
function evaluates to empty rather than being left as markup for the drop parser
to trip over, and a malformed expression yields no rarity rather than throwing.

`Rarity`, `CalculateRDTNaked`, and `DropsLineEcumenical` are excluded: they
transclude a drop line without being shared tables, and expanding them would
inject placeholder rows into thousands of pages.

## The shape of sources.json

Drop tables are stored once and referenced by the sources that have them:

```json
{
  "tables":  [ { "name": "Herb drop table", "drops": [ ... ] }, { "drops": [ ... ] } ],
  "sources": [ { "id": 470, "name": "Killerwatt (Idle)", "tables": [1, 0] } ]
}
```

A source's drops are its referenced tables concatenated, in order. Most tables are shared — 47
monsters roll the same herb table, 84 the same wilderness slayer table, and every version of a
monster repeats its page's — so storing each once removes 46% of the rows (52442 down to 28397)
and takes the file from 13.4 MB to 8.7 MB.

Tables are matched on content, not on name: Killerwatt's herb table and Aberrant spectre's hold
the same items at different rates, because the wiki parameterises them per monster. They are two
tables that share a name. A table names itself once rather than every row repeating it.

The client resolves the references at load time, so a `SourceData` still just has a list of
drops and no view had to change. The resolved lists are shared rather than copied, so the
sharing saves heap as well as file size.

## Monster versions

A page can describe several monsters that share a drop table. Killerwatt is Idle (470, stab) and
Attacking (469, crush); merging them lost the crush and described the wrong attack for the one
actually fighting you. Each version becomes its own source, named `Killerwatt (Attacking)`.

Versions are grouped by what tells them apart — combat level and how they fight — so Barbarian's
36 versions become 8 sources rather than 36. The shared drop table belongs to every version.

Note this also splits bosses whose "versions" are phases of one fight rather than separately
encounterable monsters: Zulrah becomes its three colour phases, Vorkath its two quest states.
Nothing in the wiki data distinguishes the two cases.

## Source types

Every source is one of `KILL`, `REWARD`, `PICKPOCKET`, or `GATHER`, inferred
per drop table:

- `{{DropsLineReward}}` rows → `REWARD`
- `{{DropsLine}}` under an `Infobox Monster`/`Infobox NPC` page → `KILL`,
  unless the table's section or top-level heading mentions pickpocketing →
  `PICKPOCKET`
- any other infobox (Scenery, Object, ...) or none → `GATHER`

A page carrying more than one type yields one source per type; the pickpocket
one is suffixed ` (pickpocket)` so the two don't collide.

`source-type-overlay.json` (page title → type string) overrides the inference
and forces **all** of that page's tables to the given type. Use it for pages
the heuristics genuinely can't read.

Sources with no npc id (chests, gathering nodes) get a synthetic negative id
assigned from -1000 in sorted-name order. These are **not stable across
regenerations** — nothing persists against them.

## Add curated spawn tiles

Edit `spawns-overlay.json` (`"npcId": [ { "x", "y", "plane" } ]`) and
regenerate. Static spawns from `npc-spawns.json` win where they exist; the
overlay covers instanced monsters (Zulrah, Vorkath, Graardor, ...) that have no
static spawn tile. Chest and node pages with `{{Map}}` coordinates get a single
synthetic spawn point so the map-marker flow works for them too.

### Spawn source (pinned)

Repo: https://github.com/mejrs/data_osrs
Commit: `6a3ca6f19d65c5609434b51cac8dee9d4af97c02`
File: `NPCList_OSRS.json` (~10 MB, static NPC spawn tiles: id, x, y, plane)

Download to `npc-spawns.json`; it is **not** committed.

```bash
curl -sL "https://raw.githubusercontent.com/mejrs/data_osrs/6a3ca6f19d65c5609434b51cac8dee9d4af97c02/NPCList_OSRS.json" \
  -o npc-spawns.json
```

Note the dump stops at npc id 11151, so anything added to the game since it was
taken has no spawn tiles — Elite Black Knights (13463) among them. The newer
upstream commit is byte-identical, so re-pinning does not help.

### Dungeon entrances (pinned)

Same repo, commit `7777c539839b`, file `transports_osrs.json` (~7 MB): every
ladder, staircase, rope and rift, as a start tile and the tiles it lands on.

```bash
curl -sL "https://raw.githubusercontent.com/mejrs/data_osrs/7777c539839b/transports_osrs.json" \
  -o transports_osrs.json
```

Underground areas use ordinary world coordinates at y >= 6400, which the surface
world map does not draw — so a marker on a steel dragon's tile is correct and
useless until you are already in Brimhaven Dungeon. Nothing states dungeon
entrances directly (the wiki has only prose and a picture), so `DungeonEntrance`
walks this transport graph backwards from a spawn until it surfaces. Taking the
single nearest transport is not enough: the first hop out of a dungeon is
usually another part of the same dungeon.

224 of 241 underground-only sources resolve. Without the file the sweep still
runs; sources simply get no `entrance`.

### Crowd-sourced spawns

Around 1457 sources can be placed by nothing automatic: the spawn dump stops at
npc id 11151, and the wiki has no map of a good many places. Players can record
them from inside the game instead.

1. In the plugin: enable **Record spawn locations yourself**, open a monster, and
   use **+ Spawn here** / **+ Entrance** while standing there.
2. Enable **Export recorded spawns**. It writes
   `.runelite/itemsearch/spawn-export.json` and switches itself back off.
3. Send that file in.
4. In `curate_gui.py`, the Location card has **Import mine** (your own export) and
   **Import contributed files…** (any number of files other people sent).
5. Re-run `runSources` to bake them in.

Contributions **merge**. Two players standing in different corners of the same
dungeon are both right, so a later file never erases an earlier one; identical
tiles collapse, so the same spot from ten people stays one tile. An entrance is a
single place and a file that states one replaces it, but a file that does not
mention an entrance leaves whatever someone else supplied alone.

Imports are filtered on the way in: coordinates outside the playable map, planes
outside 0–3 and non-numeric junk are dropped, and no monster may exceed
`MAX_IMPORTED_TILES` (60) however many files arrive.

## Combat data (OSRS Wiki)

Per-attack-style combat data (max hit, attack style, attack speed) comes from
the OSRS Wiki's `{{Infobox Monster}}` wikitext, joined to `KILL` sources by page
title. Non-KILL sources have no combat block.

- Monsters whose wiki data can't be parsed into attacks ("gaps") are seeded
  into `combat-overlay.json` as `"needsReview": true` stubs. Fill in the real
  `attacks` and set `"needsReview": false` to make an entry authoritative;
  `combat-overlay.json` always takes precedence over the wiki join.
- An attack may carry a `prayStyle` (hybrid attacks: the accuracy roll is
  `style`, but it is prayed against as `prayStyle` — "magic ranged" rolls magic
  accuracy and is blocked by Protect from Missiles).
- An attack may carry `variants`: conditional max hits, each with a `label`,
  a `kind` (`GEAR`/`PRAYER`/`CONSUMABLE`/`COMBO`), and its own max hit — e.g.
  Killerwatt hitting 15, or 8 with Insulated boots.

### Curating the stubs

`curate_gui.py` is the desktop editor for filling in `needsReview` stubs. It
lists the pending mobs, links each one's wiki page, and gives you a style
dropdown plus max hit, attack speed, and the three conditional-damage columns.
An "In the plugin" panel renders your input the way the combat card will, so
you can see the result before saving.

A "From the wiki" column beside the editor shows the monster's page as four
blocks (infobox, overview, mechanics, strategy) from `wiki-monster-text.json`,
with a find box that highlights every hit and steps through them on Enter.
It stays empty until a `--refresh-wiki` run has produced that snapshot.

```bash
python curate_gui.py
```

Conditional damage uses `N @ Label`, semicolon-separated, with `lo-hi` ranges
allowed and a `(combo)` suffix for combined mitigations:

```
8 @ Insulated boots;  12-14 @ Antifire;  3 @ Antifire + shield (combo)
```

The column you type in sets the kind. Shortcuts: `Ctrl+S` save, `Ctrl+Enter`
save & next, `Ctrl+N` add attack, `Ctrl+F` search the queue, `Ctrl+Shift+F`
find in the wiki text.

`curate_combat.py` is the terminal equivalent (and the shared data layer):

```bash
python curate_combat.py            # curate mobs needing review
python curate_combat.py --list      # just print them + wiki links
python curate_combat.py --all        # revisit every overlay entry
```

Re-run the generator afterwards to bake the curated values into `sources.json`.

## Shop data (OSRS Wiki)

`ShopDataGenerator` produces `shops.json` for the plugin's "buy from" lookups
by joining two OSRS Wiki snapshots:

- `wiki-shops.json` — every shop page's `{{StoreTableHead}}`/`{{StoreLine}}`
  tables (and the TzHaar-only `{{Tzhaar shop row}}` synonym), keyed by wiki
  page title: location, currency, sell multiplier, and each stocked item's
  name/stock/restock/optional sell override.
- `wiki-items.json` — item id + shop value (the GE-independent base value
  used to derive shop prices) for every item name referenced by a shop line.

Regenerate with:

```bash
./gradlew runShops --args="--refresh-shops --refresh-items"
```

**Derived price.** Each shop line's sell price is `floor(value * sellmultiplier
/ 1000)`, where `value` is the item's shop value from `wiki-items.json` and
`sellmultiplier` comes from that table's `{{StoreTableHead}}`. An explicit
`sell=` override on the `{{StoreLine}}`/`{{Tzhaar shop row}}` itself always
wins over the derived price. If neither is available the price is omitted
(`sellPrice: null`).

**Restock.** Wiki restock values are game ticks (0.6s each); they're
converted to whole seconds (`round(ticks * 0.6)`) in `shops.json`. A negative
or unparseable tick value (e.g. "N/A") is written as `null` (unknown).

**Stock encoding.** `stock` is `-1` for infinite ("∞"), `null` when the wiki
value couldn't be parsed (unknown), and `0` for a wiki-listed but currently
out-of-stock line — `0` is kept in `shops.json` for completeness but the
plugin's item-to-shop join excludes `stock == 0` lines, since a shop that
never stocks an item isn't a place to buy it.

**Duplicate-listing dedup.** The same shop is sometimes documented on two
wiki pages (its own store page and the owning NPC's page). The wiki's own
convention for this is a `bucket=No` param on the duplicate `{{StoreTableHead}}`
table; the parser skips any table so marked, keeping just the other copy. A
page whose only table is `bucket=No` (nothing else survives) is dropped
entirely, same as a page with no store table at all. Some genuinely distinct
shops still end up with identical item lists after this (e.g. every F2P
general store, or every Farming Guild supplies NPC, legitimately sells the
same stock) — that's real game content, not a dedup miss.

## Legacy: the osrsbox path (retiring)

`DropDataGenerator` (`./gradlew run`) built the plugin's older `drops.json`
from the archived osrsbox monster database. The wiki sweep replaces it:
osrsbox's data is frozen (~2021), so content added later (Nex) is absent, and
non-monster sources — raid reward chests, pickpocketing, gathering nodes —
were never representable at all.

It is kept only until the wiki-swept `sources.json` is confirmed in-game, then
deleted along with `MonsterTransform`'s osrsbox-driven paths. Its pinned input:

Repo: https://github.com/osrsbox/osrsbox-db (archived, read-only)
Commit: `dae12e3400add0c71465f07a334f9d8f86bebce8`
File: `docs/monsters-complete.json` (~12 MB, **not** committed)

`monsters-complete.json` is still read opportunistically by the shop generator
as a fallback source of item ids, and by `curate_combat.py` for exact wiki
links.
