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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import com.itemsearch.datagen.output.OutputCombat;
import com.itemsearch.datagen.output.OutputNpc;
import com.itemsearch.datagen.output.OutputSpawn;
import com.itemsearch.datagen.source.CombatOverlayJson;
import com.itemsearch.datagen.source.MonsterJson;
import com.itemsearch.datagen.source.WikiCombatJson;

public final class DropDataGenerator
{
    private static final String DEFAULT_SPAWNS = "npc-spawns.json";
    private static final String DEFAULT_OVERLAY = "spawns-overlay.json";
    private static final String DEFAULT_WIKI = "wiki-combat.json";
    private static final String DEFAULT_COMBAT_OVERLAY = "combat-overlay.json";
    private static final String DEFAULT_OUTPUT =
        "../../JAVA/ItemSearch/src/main/resources/com/itemsearch/drops.json";

    private DropDataGenerator()
    {
    }

    public static void main(String[] args) throws IOException
    {
        List<String> positional = new ArrayList<>();
        boolean refreshWiki = false;
        for (String a : args)
        {
            if ("--refresh-wiki".equals(a))
            {
                refreshWiki = true;
            }
            else
            {
                positional.add(a);
            }
        }
        if (positional.isEmpty())
        {
            System.err.println("Usage: DropDataGenerator [--refresh-wiki] <monsters-complete.json> "
                + "[npc-spawns.json] [spawns-overlay.json] [output.json]");
            System.exit(2);
            return;
        }
        Path input = Paths.get(positional.get(0));
        Path spawns = Paths.get(positional.size() >= 2 ? positional.get(1) : DEFAULT_SPAWNS);
        Path overlay = Paths.get(positional.size() >= 3 ? positional.get(2) : DEFAULT_OVERLAY);
        Path output = Paths.get(positional.size() >= 4 ? positional.get(3) : DEFAULT_OUTPUT);
        Path wikiPath = Paths.get(DEFAULT_WIKI);
        Path combatOverlayPath = Paths.get(DEFAULT_COMBAT_OVERLAY);

        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        if (refreshWiki)
        {
            refreshWiki(gson, input, wikiPath);
        }

        List<OutputNpc> npcs = generate(gson, input, spawns, overlay, wikiPath, combatOverlayPath);
        Files.write(output, (gson.toJson(npcs) + "\n").getBytes(StandardCharsets.UTF_8));

        // seed stubs for any gap not already curated, then rewrite the overlay
        Map<Integer, CombatOverlayJson> overlayMap =
            MonsterTransform.parseCombatOverlay(gson, readIfPresent(combatOverlayPath));
        Map<String, WikiCombatJson> wiki = WikiCombat.parse(gson, readIfPresent(wikiPath));
        Set<Integer> gaps = gapIds(npcs, wiki);
        Map<Integer, CombatOverlayJson> seeded = MonsterTransform.seedStubs(npcs, overlayMap, gaps);
        writeOverlay(gson, combatOverlayPath, seeded);

        long stubs = seeded.values().stream().filter(o -> o.needsReview).count();
        long withAttacks = npcs.stream().filter(n -> n.combat != null && !n.combat.attacks.isEmpty()).count();
        System.out.println("Wrote " + npcs.size() + " NPCs to " + output);
        System.out.println("Combat: " + withAttacks + " with attacks, " + gaps.size()
            + " gaps, " + stubs + " overlay stubs awaiting review");
    }

    private static void refreshWiki(Gson gson, Path input, Path wikiPath) throws IOException
    {
        Map<String, MonsterJson> monsters = readMonsters(gson, input);
        Set<String> titles = new HashSet<>();
        for (MonsterJson m : monsters.values())
        {
            if (m == null || m.drops == null || m.drops.isEmpty())
            {
                continue;
            }
            String title = WikiCombat.pageTitle(m.wikiUrl);
            if (title != null && !title.isEmpty())
            {
                titles.add(title);
            }
        }
        System.out.println("Fetching wiki combat for " + titles.size() + " pages...");
        Map<String, WikiCombatJson> fetched = WikiFetch.fetch(titles);
        // sort keys for a stable, reviewable committed snapshot
        Files.write(wikiPath, gson.toJson(new TreeMap<>(fetched)).getBytes(StandardCharsets.UTF_8));
        System.out.println("Wrote " + fetched.size() + " wiki combat records to " + wikiPath);
    }

    /** Backward-compatible 4-arg form (no wiki join, no combat overlay). */
    public static List<OutputNpc> generate(Gson gson, Path input, Path spawnsPath, Path overlayPath)
        throws IOException
    {
        return generate(gson, input, spawnsPath, overlayPath, null, null);
    }

    public static List<OutputNpc> generate(Gson gson, Path input, Path spawnsPath, Path overlayPath,
        Path wikiCombatPath, Path combatOverlayPath) throws IOException
    {
        Map<String, MonsterJson> monsters = readMonsters(gson, input);
        Map<Integer, List<OutputSpawn>> cacheSpawns = MonsterTransform.parseSpawns(gson, readIfPresent(spawnsPath));
        Map<Integer, List<OutputSpawn>> overlay = MonsterTransform.parseOverlay(gson, readIfPresent(overlayPath));
        Map<String, WikiCombatJson> wiki = WikiCombat.parse(gson,
            wikiCombatPath == null ? null : readIfPresent(wikiCombatPath));
        Map<Integer, CombatOverlayJson> combatOverlay = MonsterTransform.parseCombatOverlay(gson,
            combatOverlayPath == null ? null : readIfPresent(combatOverlayPath));

        List<OutputNpc> npcs = MonsterTransform.transformAll(monsters, overlay, cacheSpawns, wiki);
        for (OutputNpc npc : npcs)
        {
            if (npc.combat == null)
            {
                continue;
            }
            if (combatOverlay.containsKey(npc.id))
            {
                npc.combat = MonsterTransform.applyCombatOverlay(npc.combat, combatOverlay.get(npc.id));
            }
            // normalize after overlay: metallic dragons always breathe typeless dragonfire, even if a
            // curated override still calls it plain "dragonfire"
            npc.combat.attacks = MonsterTransform.relabelMetallicDragonfire(npc.name, npc.combat.attacks);
        }
        return npcs;
    }

    // an NPC is a gap when its representative wiki record parses as a gap (see CombatParser.assemble)
    private static Set<Integer> gapIds(List<OutputNpc> npcs, Map<String, WikiCombatJson> wiki)
    {
        Set<Integer> gaps = new HashSet<>();
        for (OutputNpc npc : npcs)
        {
            WikiCombatJson w = wiki.get(npc.name);
            if (w != null && WikiCombat.toAttacks(w).gap)
            {
                gaps.add(npc.id);
            }
        }
        return gaps;
    }

    private static Map<String, MonsterJson> readMonsters(Gson gson, Path input) throws IOException
    {
        Type monstersType = new TypeToken<Map<String, MonsterJson>>()
        {
        }.getType();
        try (BufferedReader reader = Files.newBufferedReader(input, StandardCharsets.UTF_8))
        {
            return gson.fromJson(reader, monstersType);
        }
    }

    private static void writeOverlay(Gson gson, Path path, Map<Integer, CombatOverlayJson> overlay)
        throws IOException
    {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent))
        {
            // best-effort: DEFAULT_COMBAT_OVERLAY is repo-root-relative (matching the "run" task's
            // working directory); skip silently if invoked from elsewhere (e.g. unit tests driving
            // main() from the module directory) rather than failing the whole run.
            return;
        }
        // string keys, sorted numerically, for a stable committed file
        Map<String, CombatOverlayJson> byString = new LinkedHashMap<>();
        overlay.keySet().stream().sorted().forEach(k -> byString.put(String.valueOf(k), overlay.get(k)));
        Files.write(path, (gson.toJson(byString) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String readIfPresent(Path path) throws IOException
    {
        return path != null && Files.exists(path)
            ? new String(Files.readAllBytes(path), StandardCharsets.UTF_8) : null;
    }
}
