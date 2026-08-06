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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import com.itemsearch.datagen.output.OutputDrop;
import com.itemsearch.datagen.output.OutputSource;
import com.itemsearch.datagen.output.OutputSpawn;
import com.itemsearch.datagen.source.CombatOverlayJson;
import com.itemsearch.datagen.source.DropLineJson;
import com.itemsearch.datagen.source.DropPageJson;
import com.itemsearch.datagen.source.DropTableJson;
import com.itemsearch.datagen.source.ItemInfoJson;
import com.itemsearch.datagen.source.ShopPageJson;
import com.itemsearch.datagen.source.ShopTableJson;
import com.itemsearch.datagen.source.ShopLineJson;
import com.itemsearch.datagen.source.WikiCombatJson;

/**
 * Entry point for the wiki drop sweep: refreshes the snapshots on request, then joins them into the
 * client's {@code sources.json}. Replaces the osrsbox-driven {@link DropDataGenerator} path.
 */
public final class SourceDataGenerator
{
    private static final String DEFAULT_DROPS_SNAPSHOT = "wiki-drops.json";
    private static final String DEFAULT_ITEMS_SNAPSHOT = "wiki-items.json";
    private static final String DEFAULT_SHOPS_SNAPSHOT = "wiki-shops.json";
    private static final String DEFAULT_SPAWNS = "npc-spawns.json";
    private static final String DEFAULT_SPAWN_OVERLAY = "spawns-overlay.json";
    private static final String DEFAULT_WIKI = "wiki-combat.json";
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
        List<String> positional = new ArrayList<>();
        for (String a : args)
        {
            if ("--refresh-drops".equals(a))
            {
                refreshDrops = true;
            }
            else if ("--refresh-items".equals(a))
            {
                refreshItems = true;
            }
            else if ("--refresh-wiki".equals(a))
            {
                refreshWiki = true;
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

        if (refreshDrops)
        {
            List<String> titles = WikiDropsFetch.fetchDropPageTitles(gson);
            System.out.println("Fetching " + titles.size() + " drop pages...");
            Map<String, DropPageJson> fetched = WikiDropsFetch.fetchDropPages(gson, titles);
            Files.write(dropsSnapshot,
                (gson.toJson(new TreeMap<>(fetched)) + "\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote " + fetched.size() + " drop pages to " + dropsSnapshot);
        }

        Map<String, DropPageJson> pages = readSnapshot(gson, dropsSnapshot, "--refresh-drops",
            new TypeToken<Map<String, DropPageJson>>()
            {
            }.getType());

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
            Map<String, WikiCombatJson> fetched = WikiFetch.fetch(titles);
            Files.write(wikiPath,
                (gson.toJson(new TreeMap<>(fetched)) + "\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote " + fetched.size() + " wiki combat records to " + wikiPath);
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

        List<OutputSource> sources =
            SourceTransform.transform(pages, itemInfo, spawns, wiki, combatOverlay, typeOverlay);
        Files.write(output, (gson.toJson(sources) + "\n").getBytes(StandardCharsets.UTF_8));

        report(pages, sources, wiki, output);
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
        Map<String, WikiCombatJson> wiki, Path output)
    {
        Map<String, Integer> byType = new TreeMap<>();
        int drops = 0;
        int unresolved = 0;
        int unknownRarity = 0;
        for (OutputSource s : sources)
        {
            byType.merge(s.type, 1, Integer::sum);
            for (OutputDrop d : s.drops)
            {
                drops++;
                if (d.itemId < 0)
                {
                    unresolved++;
                }
                if (!d.rarityKnown)
                {
                    unknownRarity++;
                }
            }
        }
        Set<String> gaps = new HashSet<>();
        for (OutputSource s : sources)
        {
            WikiCombatJson w = "KILL".equals(s.type) ? wiki.get(s.name) : null;
            if (w != null && WikiCombat.toAttacks(w).gap)
            {
                gaps.add(s.name);
            }
        }
        System.out.println("Wrote " + sources.size() + " sources from " + pages.size()
            + " pages to " + output);
        System.out.println("Types: " + byType);
        System.out.println(drops + " drops, " + percent(unresolved, drops) + " without item ids, "
            + percent(unknownRarity, drops) + " with unknown rarity");
        System.out.println("Combat: " + gaps.size() + " KILL sources with unparsed wiki combat");
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

    private static String readIfPresent(Path path) throws IOException
    {
        return path != null && Files.exists(path)
            ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8) : null;
    }
}
