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
| `wiki-shops.json` | `--refresh-shops` (`runShops`) | shop store tables |

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
for every Monster-infobox page in the drop snapshot. A positional argument
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
| pages enumerated / parsed | 2375 / 2226 |
| sources | 2277 |
| by type | KILL 1187, REWARD 627, GATHER 388, PICKPOCKET 75 |
| drops | 27038 |
| unresolved items | 1341 (5.0%) |
| unknown rarity | 3001 (11.1%) |
| combat gaps / overlay stubs | 83 / 105 |

An earlier draft of these gates expected ≥3000 sources. That was an estimate
made before the sweep; the wiki only has ~2375 pages carrying drop tables, so
~2280 sources is full coverage of what the templates actually mark up. Judge
future runs against the table above, not the old estimate.

A systematic parsing gap (a whole template variant missed, a heading shape
mishandled) should be fixed in the parser with a regression test **before**
committing regenerated data — not papered over with an overlay entry.

### Known gap: transcluded drop tables

149 of the 2375 enumerated pages (6.3%) produce no drop table. They embed a
line template only *indirectly* — the table lives in a shared template the page
transcludes (`{{Revenants}}`, the Deadman and elf variants) — and the fetch
reads raw wikitext, which does not expand transclusions. Every revenant is
missing for this reason. Fixing it means resolving those transclusions before
parsing, which is a change to the fetch strategy rather than the parser.

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

```bash
python curate_gui.py
```

Conditional damage uses `N @ Label`, semicolon-separated, with `lo-hi` ranges
allowed and a `(combo)` suffix for combined mitigations:

```
8 @ Insulated boots;  12-14 @ Antifire;  3 @ Antifire + shield (combo)
```

The column you type in sets the kind. Shortcuts: `Ctrl+S` save, `Ctrl+Enter`
save & next, `Ctrl+N` add attack, `Ctrl+F` search.

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
