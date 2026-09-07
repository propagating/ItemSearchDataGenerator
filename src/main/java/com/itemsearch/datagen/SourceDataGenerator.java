/*
 * Copyright (c) 2026, propagating <propagating@protonmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.itemsearch.datagen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import com.itemsearch.datagen.output.OutputData;
import com.itemsearch.datagen.output.OutputDrop;
import com.itemsearch.datagen.output.OutputSource;
import com.itemsearch.datagen.output.OutputSpawn;
import com.itemsearch.datagen.source.CombatOverlayJson;
import com.itemsearch.datagen.source.DropLineJson;
import com.itemsearch.datagen.source.DropPageJson;
import com.itemsearch.datagen.source.DropTableJson;
import com.itemsearch.datagen.source.ItemInfoJson;
import com.itemsearch.datagen.source.LocationJson;
import com.itemsearch.datagen.source.MonsterJson;
import com.itemsearch.datagen.source.ShopPageJson;
import com.itemsearch.datagen.source.ShopTableJson;
import com.itemsearch.datagen.source.ShopLineJson;
import com.itemsearch.datagen.source.WikiCombatJson;
import com.itemsearch.datagen.source.WikiPageTextJson;

/**
 * Entry point for the wiki drop sweep: refreshes the snapshots on request, then joins them into the
 * client's {@code sources.json}. Replaces the osrsbox-driven {@link DropDataGenerator} path.
 */
public final class SourceDataGenerator
{
    private static final String DEFAULT_DROPS_SNAPSHOT = "wiki-drops.json";
    private static final String DEFAULT_TABLES_SNAPSHOT = "wiki-drop-tables.json";
    private static final String DEFAULT_ITEMS_SNAPSHOT = "wiki-items.json";
    private static final String DEFAULT_SHOPS_SNAPSHOT = "wiki-shops.json";
    private static final String DEFAULT_MONSTERS = "monsters-complete.json";
    private static final String DEFAULT_SPAWNS = "npc-spawns.json";
    private static final String DEFAULT_SPAWN_OVERLAY = "spawns-overlay.json";
    private static final String DEFAULT_TRANSPORTS = "transports_osrs.json";
    private static final String DEFAULT_LOCATIONS_SNAPSHOT = "wiki-locations.json";
    private static final String DEFAULT_ENTRANCE_OVERLAY = "entrance-overlay.json";
    private static final String DEFAULT_WIKI = "wiki-combat.json";
    // what the curator shows instead of a browser tab: infobox, mechanics, strategy per monster page
    private static final String DEFAULT_WIKI_TEXT = "wiki-monster-text.json";
    // pages the wiki files under discontinued content, Deadman, or a League: nothing a player of
    // the main game can fight, so their sources are dropped unless --keep-retired says otherwise
    private static final String DEFAULT_RETIRED = "wiki-retired.json";
    private static final String DEFAULT_RETIRED_OVERLAY = "retired-overlay.json";
    private static final String DEFAULT_COMBAT_OVERLAY = "combat-overlay.json";
    private static final String DEFAULT_TYPE_OVERLAY = "source-type-overlay.json";
    // same sibling-repo layout as DropDataGenerator.DEFAULT_OUTPUT
    private static final String DEFAULT_OUTPUT =
        "../../JAVA/ItemSearch/src/main/resources/com/itemsearch/sources.json";

    private SourceDataGenerator()
    {
    }

    public static void main(String[] args) throws IOException
    {
        boolean refreshDrops = false;
        boolean refreshItems = false;
        boolean refreshWiki = false;
        boolean refreshLocations = false;
        boolean refreshRetired = false;
        boolean keepRetired = false;
        List<String> positional = new ArrayList<>();
        for (String a : args)
        {
            if ("--refresh-drops".equals(a))
            {
                refreshDrops = true;
            }
            else if ("--refresh-retired".equals(a))
            {
                refreshRetired = true;
            }
            else if ("--keep-retired".equals(a))
            {
                keepRetired = true;
            }
            else if ("--refresh-items".equals(a))
            {
                refreshItems = true;
            }
            else if ("--refresh-wiki".equals(a))
            {
                refreshWiki = true;
            }
            else if ("--refresh-locations".equals(a))
            {
                refreshLocations = true;
            }
            else
            {
                positional.add(a);
            }
        }
        Path dropsSnapshot = Paths.get(DEFAULT_DROPS_SNAPSHOT);
        Path itemsSnapshot = Paths.get(DEFAULT_ITEMS_SNAPSHOT);
        Path output = Paths.get(positional.isEmpty() ? DEFAULT_OUTPUT : positional.get(0));
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        Path tablesSnapshot = Paths.get(DEFAULT_TABLES_SNAPSHOT);
        if (refreshDrops)
        {
            // the shared tables must be in hand before pages are parsed: a monster's herb and
            // rare drops arrive by transcluding one of them, not from its own wikitext
            Map<String, String> tables = WikiDropsFetch.fetchSharedDropTables(gson);
            Files.write(tablesSnapshot,
                (gson.toJson(new TreeMap<>(tables)) + "\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote " + tables.size() + " shared drop tables to " + tablesSnapshot);

            List<String> titles = WikiDropsFetch.fetchDropPageTitles(gson);
            System.out.println("Fetching " + titles.size() + " drop pages...");
            Map<String, DropPageJson> fetched = WikiDropsFetch.fetchDropPages(gson, titles, tables);
            Files.write(dropsSnapshot,
                (gson.toJson(new TreeMap<>(fetched)) + "\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote " + fetched.size() + " drop pages to " + dropsSnapshot);
        }

        Map<String, DropPageJson> pages = readSnapshot(gson, dropsSnapshot, "--refresh-drops",
            new TypeToken<Map<String, DropPageJson>>()
            {
            }.getType());

        Path locationsSnapshot = Paths.get(DEFAULT_LOCATIONS_SNAPSHOT);
        if (refreshLocations)
        {
            System.out.println("Fetching locations for " + pages.size() + " pages...");
            Map<String, List<LocationJson>> found = WikiLocationFetch.fetch(gson, pages.keySet());
            Files.write(locationsSnapshot,
                (gson.toJson(new TreeMap<>(found)) + "\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote locations for " + found.size() + " pages to " + locationsSnapshot);
        }

        if (refreshItems)
        {
            // wiki-items.json is shared with the shop generator — refreshing from drop names alone
            // would drop every shop-only item, so union both snapshots' names before fetching
            Set<String> names = dropItemNames(pages);
            names.addAll(shopItemNames(gson, Paths.get(DEFAULT_SHOPS_SNAPSHOT)));
            System.out.println("Fetching item info for " + names.size() + " item names...");
            Map<String, ItemInfoJson> items = WikiItemFetch.fetch(gson, names);
            Files.write(itemsSnapshot,
                (gson.toJson(new TreeMap<>(items)) + "\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote " + items.size() + " item records to " + itemsSnapshot);
        }

        Path wikiPath = Paths.get(DEFAULT_WIKI);
        if (refreshWiki)
        {
            Set<String> titles = monsterPageTitles(pages);
            System.out.println("Fetching wiki combat for " + titles.size() + " monster pages...");
            Map<String, String> raw = WikiFetch.fetchRaw(titles);
            Map<String, WikiCombatJson> fetched = WikiFetch.infoboxes(raw);
            Files.write(wikiPath,
                (gson.toJson(new TreeMap<>(fetched)) + "\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote " + fetched.size() + " wiki combat records to " + wikiPath);
            // same download, kept for the curator's eyes rather than the parser's
            Map<String, WikiPageTextJson> texts = WikiFetch.fetchPageTexts(raw);
            Path textPath = Paths.get(DEFAULT_WIKI_TEXT);
            Files.write(textPath,
                (gson.toJson(new TreeMap<>(texts)) + "\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote " + texts.size() + " wiki page texts to " + textPath);
        }

        Map<String, ItemInfoJson> itemInfo = readSnapshot(gson, itemsSnapshot, "--refresh-items",
            new TypeToken<Map<String, ItemInfoJson>>()
            {
            }.getType());
        Map<String, WikiCombatJson> wiki = WikiCombat.parse(gson, readIfPresent(wikiPath));
        Map<Integer, CombatOverlayJson> combatOverlay = MonsterTransform.parseCombatOverlay(gson,
            readIfPresent(Paths.get(DEFAULT_COMBAT_OVERLAY)));
        Map<String, String> typeOverlay = parseTypeOverlay(gson,
            readIfPresent(Paths.get(DEFAULT_TYPE_OVERLAY)));
        Map<Integer, List<OutputSpawn>> spawns = mergeSpawns(
            MonsterTransform.parseSpawns(gson, readIfPresent(Paths.get(DEFAULT_SPAWNS))),
            MonsterTransform.parseOverlay(gson, readIfPresent(Paths.get(DEFAULT_SPAWN_OVERLAY))));

        Map<Integer, MonsterJson> monsters = readMonstersById(gson,
            readIfPresent(Paths.get(DEFAULT_MONSTERS)));

        List<OutputSource> sources = SourceTransform.transform(pages, itemInfo, spawns, wiki,
            combatOverlay, typeOverlay, monsters);

        Path retiredSnapshot = Paths.get(DEFAULT_RETIRED);
        if (refreshRetired)
        {
            System.out.println("Fetching categories for " + pages.size() + " pages...");
            Map<String, String> found = WikiRetiredFetch.fetch(pages.keySet());
            Files.write(retiredSnapshot,
                (gson.toJson(new TreeMap<>(found)) + "\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote " + found.size() + " retired pages to " + retiredSnapshot);
        }
        Set<Integer> retiredIds = new HashSet<>();
        String retiredJson = readIfPresent(retiredSnapshot);
        if (retiredJson == null)
        {
            System.out.println("Retired: no " + retiredSnapshot + " yet, keeping every source"
                + " (run with --refresh-retired to drop discontinued, Deadman, and Leagues content)");
        }
        else if (keepRetired)
        {
            System.out.println("Retired: kept on request (--keep-retired)");
        }
        else
        {
            Map<String, String> retired = WikiRetired.applyOverlay(
                gson.fromJson(retiredJson, new TypeToken<Map<String, String>>()
                {
                }.getType()),
                gson, readIfPresent(Paths.get(DEFAULT_RETIRED_OVERLAY)));
            WikiRetired.Filtered filtered = WikiRetired.filter(sources, retired);
            sources = filtered.kept;
            retiredIds = filtered.droppedIds;
            System.out.println("Retired: dropped " + filtered.droppedIds.size() + " sources "
                + filtered.droppedByReason);
        }

        // the spawn dump stops at npc id 11151, so everything added to the game since has no
        // tiles at all. The wiki keeps its own; fill from those, and only where the dump was silent.
        Map<String, List<LocationJson>> locations = new TreeMap<>();
        String locationsJson = readIfPresent(locationsSnapshot);
        if (locationsJson != null)
        {
            Map<String, List<LocationJson>> parsed = gson.fromJson(locationsJson,
                new TypeToken<Map<String, List<LocationJson>>>()
                {
                }.getType());
            if (parsed != null)
            {
                locations.putAll(parsed);
            }
        }
        int fromWiki = 0;
        int placeOnly = 0;
        for (OutputSource s : sources)
        {
            if (s.spawnPoints != null && !s.spawnPoints.isEmpty())
            {
                continue;
            }
            List<LocationJson> found = locations.get(s.wikiPage != null ? s.wikiPage : s.name);
            if (found == null)
            {
                continue;
            }
            List<OutputSpawn> tiles = SourceTransform.tilesFrom(found, s.name);
            if (!tiles.isEmpty())
            {
                s.spawnPoints = tiles;
                fromWiki++;
            }
            else
            {
                // somewhere the wiki has no map for: its numbers are not world tiles, so the name
                // of the place is all that can honestly be offered
                s.locationName = SourceTransform.placeFrom(found, s.name);
                placeOnly += s.locationName != null ? 1 : 0;
            }
        }
        System.out.println("Locations: " + fromWiki + " sources given tiles from the wiki, "
            + placeOnly + " given only a place name");

        // where to go in, for the monsters whose tiles the world map does not draw
        DungeonEntrance entrances = DungeonEntrance.parse(gson,
            readIfPresent(Paths.get(DEFAULT_TRANSPORTS)));
        int withEntrance = 0;
        int undergroundOnly = 0;
        for (OutputSource s : sources)
        {
            if (s.spawnPoints == null || s.spawnPoints.isEmpty())
            {
                continue;
            }
            boolean surfaced = false;
            for (OutputSpawn sp : s.spawnPoints)
            {
                surfaced |= sp.y < DungeonEntrance.UNDERGROUND_Y;
            }
            if (surfaced)
            {
                continue;
            }
            undergroundOnly++;
            s.entrance = entrances.forSpawns(s.spawnPoints);
            withEntrance += s.entrance != null ? 1 : 0;
        }

        // hand-entered entrances win: the transport walk cannot reach instanced content, and some
        // ways in are not transports at all
        Map<Integer, OutputSpawn> curatedEntrances = MonsterTransform.parseEntranceOverlay(gson,
            readIfPresent(Paths.get(DEFAULT_ENTRANCE_OVERLAY)));
        int curated = 0;
        for (OutputSource s : sources)
        {
            OutputSpawn manual = null;
            for (Integer id : s.npcIds == null ? Collections.<Integer>emptyList() : s.npcIds)
            {
                manual = curatedEntrances.get(id);
                if (manual != null)
                {
                    break;
                }
            }
            if (manual != null)
            {
                s.entrance = manual;
                curated++;
            }
        }
        System.out.println("Entrances: " + withEntrance + " of " + undergroundOnly
            + " underground-only sources resolved to a surface way in, " + curated + " curated");

        // count the flat rows before they fold onto shared tables, so the summary keeps
        // describing what a player sees rather than how it is stored
        int flatDrops = 0;
        int unresolved = 0;
        int unknownRarity = 0;
        for (OutputSource s : sources)
        {
            for (OutputDrop d : s.drops)
            {
                flatDrops++;
                unresolved += d.itemId < 0 ? 1 : 0;
                unknownRarity += d.rarityKnown ? 0 : 1;
            }
        }

        OutputData data = DropTableIndex.build(sources);
        Files.write(output, (gson.toJson(data) + "\n").getBytes(StandardCharsets.UTF_8));

        // seed stubs for every combat gap not already curated, then rewrite the overlay — this is
        // what refills the curator's queue, and it must survive the retirement of DropDataGenerator
        Set<Integer> gaps = combatGapIds(sources, wiki);
        Map<Integer, CombatOverlayJson> seeded = WikiRetired.pruneStubs(
            MonsterTransform.seedStubs(sources, combatOverlay, gaps), retiredIds);
        writeOverlay(gson, Paths.get(DEFAULT_COMBAT_OVERLAY), seeded);
        long stubs = seeded.values().stream().filter(o -> o.needsReview).count();

        report(pages, sources, data, output, gaps.size(), stubs,
            flatDrops, unresolved, unknownRarity);
    }

    /**
     * KILL sources whose wiki record exists but will not parse into attacks. Only kills qualify:
     * nothing else has combat data, so nothing else belongs in the curator's review queue.
     */
    static Set<Integer> combatGapIds(List<OutputSource> sources, Map<String, WikiCombatJson> wiki)
    {
        Set<Integer> gaps = new HashSet<>();
        for (OutputSource s : sources)
        {
            if (!"KILL".equals(s.type))
            {
                continue;
            }
            WikiCombatJson w = wiki.get(s.name);
            if (w != null && WikiCombat.toAttacks(w).gap)
            {
                gaps.add(s.id);
            }
        }
        return gaps;
    }

    private static void writeOverlay(Gson gson, Path path, Map<Integer, CombatOverlayJson> overlay)
        throws IOException
    {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent))
        {
            // the default path is repo-root-relative (matching the runSources working directory);
            // skip rather than fail when main() is driven from somewhere else
            return;
        }
        // string keys, sorted numerically, for a stable committed file
        Map<String, CombatOverlayJson> byString = new LinkedHashMap<>();
        overlay.keySet().stream().sorted().forEach(k -> byString.put(String.valueOf(k), overlay.get(k)));
        Files.write(path, (gson.toJson(byString) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Cache spawns win; the curated overlay fills in ids the cache never saw (instanced monsters).
     * Same precedence as {@link MonsterTransform#mergeGroup}, flattened to one map because
     * {@link SourceTransform} joins spawns by npc id alone.
     */
    static Map<Integer, List<OutputSpawn>> mergeSpawns(Map<Integer, List<OutputSpawn>> cache,
        Map<Integer, List<OutputSpawn>> overlay)
    {
        Map<Integer, List<OutputSpawn>> merged = new LinkedHashMap<>();
        if (overlay != null)
        {
            merged.putAll(overlay);
        }
        if (cache != null)
        {
            for (Map.Entry<Integer, List<OutputSpawn>> e : cache.entrySet())
            {
                if (e.getValue() != null && !e.getValue().isEmpty())
                {
                    merged.put(e.getKey(), e.getValue());
                }
            }
        }
        return merged;
    }

    private static Set<String> dropItemNames(Map<String, DropPageJson> pages)
    {
        Set<String> names = new TreeSet<>();
        for (DropPageJson page : pages.values())
        {
            if (page == null || page.tables == null)
            {
                continue;
            }
            for (DropTableJson table : page.tables)
            {
                for (DropLineJson line : table.lines)
                {
                    if (line.name != null && !line.name.isEmpty())
                    {
                        names.add(line.name);
                    }
                }
            }
        }
        return names;
    }

    private static Set<String> shopItemNames(Gson gson, Path shopsSnapshot) throws IOException
    {
        Set<String> names = new TreeSet<>();
        if (!Files.exists(shopsSnapshot))
        {
            return names;
        }
        Type type = new TypeToken<Map<String, ShopPageJson>>()
        {
        }.getType();
        Map<String, ShopPageJson> shops;
        try (BufferedReader reader = Files.newBufferedReader(shopsSnapshot, StandardCharsets.UTF_8))
        {
            shops = gson.fromJson(reader, type);
        }
        if (shops == null)
        {
            return names;
        }
        for (ShopPageJson page : shops.values())
        {
            if (page == null || page.tables == null)
            {
                continue;
            }
            for (ShopTableJson table : page.tables)
            {
                for (ShopLineJson line : table.lines)
                {
                    if (line.name != null && !line.name.isEmpty())
                    {
                        names.add(line.name);
                    }
                }
            }
        }
        return names;
    }

    /** Combat data only exists on Infobox Monster pages, so only those are worth fetching. */
    private static Set<String> monsterPageTitles(Map<String, DropPageJson> pages)
    {
        Set<String> titles = new TreeSet<>();
        for (DropPageJson page : pages.values())
        {
            if (page != null && page.pageTitle != null && "Monster".equalsIgnoreCase(page.infobox))
            {
                titles.add(page.pageTitle);
            }
        }
        return titles;
    }

    static Map<String, String> parseTypeOverlay(Gson gson, String json)
    {
        if (json == null || json.trim().isEmpty())
        {
            return Collections.emptyMap();
        }
        Type type = new TypeToken<Map<String, String>>()
        {
        }.getType();
        Map<String, String> parsed = gson.fromJson(json, type);
        return parsed == null ? Collections.emptyMap() : parsed;
    }

    private static void report(Map<String, DropPageJson> pages, List<OutputSource> sources,
        OutputData data, Path output, int gaps, long stubs,
        int drops, int unresolved, int unknownRarity)
    {
        Map<String, Integer> byType = new TreeMap<>();
        for (OutputSource s : sources)
        {
            byType.merge(s.type, 1, Integer::sum);
        }
        int stored = 0;
        for (com.itemsearch.datagen.output.OutputTable t : data.tables)
        {
            stored += t.drops.size();
        }
        System.out.println("Wrote " + sources.size() + " sources from " + pages.size()
            + " pages to " + output);
        System.out.println("Types: " + byType);
        System.out.println(drops + " drops, " + percent(unresolved, drops) + " without item ids, "
            + percent(unknownRarity, drops) + " with unknown rarity");
        System.out.println(data.tables.size() + " drop tables holding " + stored
            + " rows (" + percent(drops - stored, drops) + " saved by sharing)");
        System.out.println("Combat: " + gaps + " gaps, " + stubs
            + " overlay stubs awaiting review");
    }

    private static String percent(int part, int total)
    {
        return total == 0 ? part + " (0.0%)"
            : String.format("%d (%.1f%%)", part, 100.0 * part / total);
    }

    private static <T> T readSnapshot(Gson gson, Path path, String flag, Type type) throws IOException
    {
        if (!Files.exists(path))
        {
            throw new IOException("Missing snapshot " + path + " — run with " + flag + " first");
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            return gson.fromJson(reader, type);
        }
    }

    /**
     * The monster dump keyed by npc id, for joining combat levels onto page-driven sources.
     *
     * <p>The file keys entries by id as a string; rekeying on the record's own id keeps the two in
     * step and drops any entry whose key and body disagree. A missing dump costs levels, nothing
     * else, so it is read as optional rather than demanded like the wiki snapshots.
     */
    static Map<Integer, MonsterJson> readMonstersById(Gson gson, String json)
    {
        if (json == null || json.trim().isEmpty())
        {
            return Collections.emptyMap();
        }
        Type type = new TypeToken<Map<String, MonsterJson>>()
        {
        }.getType();
        Map<String, MonsterJson> raw = gson.fromJson(json, type);
        if (raw == null)
        {
            return Collections.emptyMap();
        }
        Map<Integer, MonsterJson> byId = new HashMap<>();
        for (MonsterJson m : raw.values())
        {
            if (m != null)
            {
                byId.put(m.id, m);
            }
        }
        return byId;
    }

    private static String readIfPresent(Path path) throws IOException
    {
        return path != null && Files.exists(path)
            ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8) : null;
    }
}
