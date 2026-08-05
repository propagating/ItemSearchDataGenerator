# ItemSearchDataGenerator

Offline generator that turns the osrsbox monster database into the Item Search
RuneLite plugin's `drops.json`. Not part of the plugin build.

The plugin lives at `E:\Projects\JAVA\ItemSearch`; generated output is written
into its `src/main/resources/com/itemsearch/` directory (the default output
path assumes that sibling layout; pass an explicit output path otherwise).

## Source (pinned)

Repo: https://github.com/osrsbox/osrsbox-db (archived, read-only — a stable pin)
Commit: `dae12e3400add0c71465f07a334f9d8f86bebce8`
File: `docs/monsters-complete.json`

The source file (~12 MB) is intentionally **not** committed.

Note: this is the original `osrsbox/osrsbox-db` (~3000 monsters, ~2383 with
drops), which is far more complete than the `0xNeffarion/osrsreboxed-db` fork
originally trialled (that fork's snapshot was missing major bosses such as
Vorkath, Cerberus, and Alchemical Hydra). Because osrsbox-db is archived, its
data is frozen (~2021), so content added later (e.g. Nex) is absent, and raid
rewards obtained from Chambers of Xeric / Theatre of Blood (e.g. Twisted bow)
are not monster drops and so never appear here.

## Spawn source (pinned)

Repo: https://github.com/mejrs/data_osrs
Commit: `6a3ca6f19d65c5609434b51cac8dee9d4af97c02`
File: `NPCList_OSRS.json` (~10 MB, static NPC spawn tiles: id, x, y, plane)

Static spawns cover most world monsters. Instanced monsters (Zulrah, Vorkath,
Graardor, ...) have no static spawn, so `spawns-overlay.json` supplies their
locations as a fallback. The spawn file is **not** committed.

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

Since wiki content can change at any time, the committed `wiki-shops.json` /
`wiki-items.json` snapshots (dated in the commit) are the reproducibility pin
for shop data, the same way `wiki-combat.json` is the pin for combat data.
Normal runs (without the `--refresh-*` flags) reuse the committed snapshots
as-is and never hit the network; `monsters-complete.json` (see above) is
still read as a fallback source of item ids for names the wiki item lookup
couldn't resolve.

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

## Regenerate drops.json

From the repository root:

```bash
# 1. Download the pinned sources (do not commit them)
curl -sL "https://raw.githubusercontent.com/osrsbox/osrsbox-db/dae12e3400add0c71465f07a334f9d8f86bebce8/docs/monsters-complete.json" \
  -o monsters-complete.json
curl -sL "https://raw.githubusercontent.com/mejrs/data_osrs/6a3ca6f19d65c5609434b51cac8dee9d4af97c02/NPCList_OSRS.json" \
  -o npc-spawns.json

# 2. Generate (defaults: spawns = npc-spawns.json, overlay = spawns-overlay.json,
#    output = ../../JAVA/ItemSearch/src/main/resources/com/itemsearch/drops.json)
./gradlew run --args="monsters-complete.json"
```

## Refresh to a newer source

Pick a newer commit SHA (or a more current database), update the pinned SHA
above and the download URL, re-run the two steps, and review the `drops.json`
diff before committing.

## Add curated spawn tiles

Edit `spawns-overlay.json` (`"npcId": [ { "x", "y", "plane" } ]`) and regenerate.
Spawn tiles are not present in the source database; this overlay is how they are
carried across regenerations.

## Combat data (OSRS Wiki)

Per-attack-style combat data (max hit, attack style, attack speed) is not in the
osrsbox source, so it is joined in from the OSRS Wiki's `{{Infobox Monster}}`
wikitext, keyed by each monster's `wiki_name`.

- `--refresh-wiki` fetches live wikitext for every droppable monster's
  `wiki_name` and writes the committed `wiki-combat.json` snapshot. Since the
  wiki content can change at any time, this snapshot (dated in the commit) is
  the reproducibility pin for combat data, the same way the pinned
  `monsters-complete.json` commit is the pin for drop data.
- Normal runs (without `--refresh-wiki`) reuse the committed `wiki-combat.json`
  as-is and never hit the network.
- Monsters whose wiki data can't be parsed into attacks ("gaps") are
  auto-seeded into `combat-overlay.json` as `"needsReview": true` stubs. Fill
  in the real `attacks` for a stub and set `"needsReview": false` (or delete
  the flag) to make that entry authoritative; entries in `combat-overlay.json`
  always take precedence over the wiki join.
- Each run prints a coverage summary: how many NPCs have attacks, how many
  wiki gaps were found, and how many overlay stubs are still awaiting review.

### Curating the stubs

`curate_combat.py` is an interactive helper for filling in the `needsReview`
stubs. It walks each flagged mob, prints its OSRS Wiki page link (with the
`#version` anchor) so you can look up the real numbers, and prompts for the max
hit per attack style. It marks each mob reviewed and saves as you go.

```bash
python curate_combat.py            # curate mobs needing review
python curate_combat.py --list      # just print them + wiki links
python curate_combat.py --all        # revisit every overlay entry
```

It reads `monsters-complete.json` for exact wiki links (falling back to the
committed `drops.json` name if that source isn't present). Re-run the generator
afterwards to bake the curated values into `drops.json`.

To refresh the wiki snapshot and regenerate:

```bash
./gradlew run --args="--refresh-wiki monsters-complete.json"
```
