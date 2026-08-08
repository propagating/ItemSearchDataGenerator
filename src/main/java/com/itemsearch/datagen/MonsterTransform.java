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
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.itemsearch.datagen.output.OutputAttack;
import com.itemsearch.datagen.output.OutputCombat;
import com.itemsearch.datagen.output.OutputDrop;
import com.itemsearch.datagen.output.OutputSource;
import com.itemsearch.datagen.output.OutputSpawn;
import com.itemsearch.datagen.source.CombatOverlayJson;
import com.itemsearch.datagen.source.DropJson;
import com.itemsearch.datagen.source.MonsterJson;
import com.itemsearch.datagen.source.NpcSpawnJson;
import com.itemsearch.datagen.source.WikiCombatJson;

public final class MonsterTransform
{
    private static final Pattern INT = Pattern.compile("\\d+");
    // cap markers per monster: enough to find it, few enough to keep the map/file usable
    private static final int MAX_SPAWNS_PER_NPC = 25;

    private MonsterTransform()
    {
    }

    public static int parseQuantityMax(String quantity)
    {
        if (quantity == null)
        {
            return 1;
        }
        Matcher m = INT.matcher(quantity);
        int max = 0;
        boolean found = false;
        while (m.find())
        {
            try
            {
                int v = Integer.parseInt(m.group());
                found = true;
                if (v > max)
                {
                    max = v;
                }
            }
            catch (NumberFormatException e)
            {
                // token too large to be a real quantity; ignore it
            }
        }
        return found && max > 0 ? max : 1;
    }

    public static double effectiveRarity(Double rarity, int rolls)
    {
        if (rarity == null)
        {
            return 0.0;
        }
        int r = Math.max(1, rolls);
        if (r == 1)
        {
            return rarity;
        }
        return 1.0 - Math.pow(1.0 - rarity, r);
    }

    public static OutputDrop transformDrop(DropJson d)
    {
        boolean known = d.rarity != null;
        double rarity = effectiveRarity(d.rarity, d.rolls);
        int qty = parseQuantityMax(d.quantity);
        return new OutputDrop(d.id, d.name, rarity, qty, d.noted, known);
    }

    public static List<OutputDrop> transformDrops(MonsterJson m)
    {
        // group rows by item id: osrsbox lists the same item multiple times (a quantity range split
        // into per-quantity rows, or the item appearing across several drop sub-tables)
        Map<Integer, List<OutputDrop>> byItem = new LinkedHashMap<>();
        if (m.drops != null)
        {
            for (DropJson d : m.drops)
            {
                if (d == null || d.name == null)
                {
                    continue;
                }
                OutputDrop od = transformDrop(d);
                byItem.computeIfAbsent(od.itemId, k -> new ArrayList<>()).add(od);
            }
        }

        List<OutputDrop> drops = new ArrayList<>();
        for (List<OutputDrop> rows : byItem.values())
        {
            drops.add(rows.size() == 1 ? rows.get(0) : mergeDrops(rows));
        }
        drops.sort(Comparator
            .comparingDouble((OutputDrop o) -> o.rarity).reversed()
            .thenComparingInt(o -> o.itemId));
        return drops;
    }

    /**
     * Collapse several rows of the same item into one. Quantity becomes the largest seen; rarity is
     * the union over the <em>distinct</em> per-row rarities (identical rarities are usually one drop
     * split into per-quantity rows, so they count once; different rarities come from separate tables
     * and stack as {@code 1 − ∏(1 − r)}).
     */
    private static OutputDrop mergeDrops(List<OutputDrop> rows)
    {
        OutputDrop first = rows.get(0);
        Set<Double> rarities = new LinkedHashSet<>();
        boolean known = false;
        boolean noted = false;
        int maxQty = 0;
        for (OutputDrop d : rows)
        {
            if (d.rarityKnown)
            {
                known = true;
                rarities.add(d.rarity);
            }
            noted = noted || d.noted;
            maxQty = Math.max(maxQty, d.quantity);
        }
        double miss = 1.0;
        for (double r : rarities)
        {
            miss *= 1.0 - r;
        }
        double rarity = known ? 1.0 - miss : 0.0;
        return new OutputDrop(first.itemId, first.itemName, rarity, maxQty, noted, known);
    }

    public static OutputSource transformMonster(MonsterJson m, Map<Integer, List<OutputSpawn>> overlay)
    {
        return transformMonster(m, overlay, Collections.emptyMap());
    }

    public static OutputSource transformMonster(MonsterJson m, Map<Integer, List<OutputSpawn>> overlay,
        Map<String, WikiCombatJson> wiki)
    {
        List<OutputDrop> drops = transformDrops(m);
        List<OutputSpawn> spawns = new ArrayList<>(overlay.getOrDefault(m.id, Collections.emptyList()));
        return new OutputSource(m.id, new ArrayList<>(Collections.singletonList(m.id)),
            m.name, m.combatLevel, null, spawns, drops, transformCombat(m, wiki),
            "KILL", null, null, null);
    }

    // metallic dragons breathe typeless dragonfire (no Protect from Magic), unlike chromatic dragons
    private static final Set<String> METALLIC_DRAGONS = new HashSet<>(Arrays.asList(
        "bronze dragon", "iron dragon", "steel dragon", "mithril dragon", "adamant dragon", "rune dragon"));

    public static OutputCombat transformCombat(MonsterJson m, Map<String, WikiCombatJson> wiki)
    {
        String title = WikiCombat.pageTitle(m.wikiUrl);
        CombatParser.ParsedCombat pc = WikiCombat.toAttacks(
            title == null ? null : wiki.get(title));
        return new OutputCombat(m.attackLevel, m.strengthLevel, m.defenceLevel, m.magicLevel,
            m.rangedLevel, m.hitpoints, relabelMetallicDragonfire(m.name, pc.attacks));
    }

    /**
     * The wiki tokens every dragon's breath as "dragonfire". Metallic dragons' dragonfire is actually
     * typeless (immune to Protect from Magic), so relabel theirs to "metallic dragonfire" — the client
     * renders that as shield/potion-only, without the partial magic-prayer icon.
     */
    public static List<OutputAttack> relabelMetallicDragonfire(String name, List<OutputAttack> attacks)
    {
        if (name == null || !METALLIC_DRAGONS.contains(name.toLowerCase(Locale.ROOT)))
        {
            return attacks;
        }
        List<OutputAttack> out = new ArrayList<>();
        for (OutputAttack a : attacks)
        {
            OutputAttack copy = new OutputAttack(
                "dragonfire".equals(a.style) ? "metallic dragonfire" : a.style,
                a.maxHit, a.maxHitMax, a.speed, a.speedMax);
            // this runs after the curated overlay is applied and is the only pass that rebuilds
            // an attack, so whatever is not copied here is lost — and lost for metallic dragons
            // alone, which is why it went unnoticed: Steel dragon's breath reached the client
            // without the note or any of the four mitigations that had been curated onto it
            copy.prayStyle = a.prayStyle;
            copy.variants = a.variants;
            copy.notes = a.notes;
            copy.effects = a.effects;
            out.add(copy);
        }
        return out;
    }

    /**
     * Merge same-monster variants (identical name + combat level, e.g. the many
     * spawn/appearance NPC ids the game uses for one Goblin) into a single entry.
     * The entry keeps every underlying id in {@code npcIds} so a kill under any of
     * them still counts; its drops come from the variant with the most drops
     * (tie-break: lowest id), and its spawn tiles are the union of the overlay for
     * any member id. Returns null if the group has no usable drops.
     */
    public static OutputSource mergeGroup(List<MonsterJson> group, Map<Integer, List<OutputSpawn>> overlay,
        Map<Integer, List<OutputSpawn>> cacheSpawns, Map<String, WikiCombatJson> wiki)
    {
        MonsterJson rep = null;
        for (MonsterJson m : group)
        {
            if (rep == null || dropCount(m) > dropCount(rep)
                || (dropCount(m) == dropCount(rep) && m.id < rep.id))
            {
                rep = m;
            }
        }
        if (rep == null)
        {
            return null;
        }

        List<OutputDrop> drops = transformDrops(rep);
        if (drops.isEmpty())
        {
            return null;
        }

        List<Integer> ids = new ArrayList<>();
        for (MonsterJson m : group)
        {
            ids.add(m.id);
        }
        Collections.sort(ids);

        // Prefer real static spawn tiles (mejrs NPCList) unioned across every id, deduped and
        // capped; fall back to the curated overlay for instanced monsters that have no static
        // spawn (e.g. Zulrah, Vorkath).
        List<OutputSpawn> spawns = collectSpawns(ids, cacheSpawns, MAX_SPAWNS_PER_NPC);
        if (spawns.isEmpty())
        {
            spawns = collectSpawns(ids, overlay, Integer.MAX_VALUE);
        }

        return new OutputSource(rep.id, ids, rep.name, rep.combatLevel, null, spawns, drops,
            transformCombat(rep, wiki), "KILL", null, null, null);
    }

    private static List<OutputSpawn> collectSpawns(List<Integer> ids,
        Map<Integer, List<OutputSpawn>> source, int cap)
    {
        List<OutputSpawn> out = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Integer id : ids)
        {
            for (OutputSpawn sp : source.getOrDefault(id, Collections.emptyList()))
            {
                if (out.size() >= cap)
                {
                    return out;
                }
                if (seen.add(sp.x + ":" + sp.y + ":" + sp.plane))
                {
                    out.add(sp);
                }
            }
        }
        return out;
    }

    /**
     * Group the mejrs NPCList spawn dump by NPC id into deduped spawn-tile lists.
     */
    public static Map<Integer, List<OutputSpawn>> parseSpawns(Gson gson, String json)
    {
        Map<Integer, List<OutputSpawn>> result = new HashMap<>();
        if (json == null || json.trim().isEmpty())
        {
            return result;
        }
        Type type = new TypeToken<List<NpcSpawnJson>>()
        {
        }.getType();
        List<NpcSpawnJson> raw = gson.fromJson(json, type);
        if (raw == null)
        {
            return result;
        }
        for (NpcSpawnJson sp : raw)
        {
            if (sp == null)
            {
                continue;
            }
            result.computeIfAbsent(sp.id, k -> new ArrayList<>()).add(new OutputSpawn(sp.x, sp.y, sp.p));
        }
        return result;
    }

    private static int dropCount(MonsterJson m)
    {
        return m.drops == null ? 0 : m.drops.size();
    }

    public static List<OutputSource> transformAll(Map<String, MonsterJson> monsters,
        Map<Integer, List<OutputSpawn>> overlay)
    {
        return transformAll(monsters, overlay, Collections.emptyMap(), Collections.emptyMap());
    }

    public static List<OutputSource> transformAll(Map<String, MonsterJson> monsters,
        Map<Integer, List<OutputSpawn>> overlay, Map<Integer, List<OutputSpawn>> cacheSpawns)
    {
        return transformAll(monsters, overlay, cacheSpawns, Collections.emptyMap());
    }

    public static List<OutputSource> transformAll(Map<String, MonsterJson> monsters,
        Map<Integer, List<OutputSpawn>> overlay, Map<Integer, List<OutputSpawn>> cacheSpawns,
        Map<String, WikiCombatJson> wiki)
    {
        // group droppable monsters by (name, combat level) so cosmetic id variants collapse
        Map<String, List<MonsterJson>> groups = new LinkedHashMap<>();
        if (monsters != null)
        {
            for (MonsterJson m : monsters.values())
            {
                if (m == null || m.name == null || m.drops == null || m.drops.isEmpty())
                {
                    continue;
                }
                // case-insensitive so source casing drift ("Ice Troll" vs "Ice troll") collapses
                String key = m.name.toLowerCase(Locale.ROOT) + ' ' + m.combatLevel;
                groups.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
            }
        }

        List<OutputSource> out = new ArrayList<>();
        for (List<MonsterJson> group : groups.values())
        {
            OutputSource npc = mergeGroup(group, overlay, cacheSpawns, wiki);
            if (npc != null && !npc.drops.isEmpty())
            {
                out.add(npc);
            }
        }
        out.sort(Comparator.comparingInt(o -> o.id));
        return out;
    }

    /**
     * Hand-entered dungeon entrances, npc id -> the surface tile you go in by.
     *
     * <p>{@link DungeonEntrance} works most of them out of the game's transport list, but it cannot
     * reach instanced content, and some ways in are not transports at all. This is where those are
     * written down.
     */
    public static Map<Integer, OutputSpawn> parseEntranceOverlay(Gson gson, String json)
    {
        Map<Integer, OutputSpawn> result = new HashMap<>();
        if (json == null || json.trim().isEmpty())
        {
            return result;
        }
        Map<String, OutputSpawn> raw;
        try
        {
            Type type = new TypeToken<Map<String, OutputSpawn>>()
            {
            }.getType();
            raw = gson.fromJson(json, type);
        }
        catch (Exception e)
        {
            return result; // an unreadable overlay costs curated entrances, not the whole sweep
        }
        if (raw == null)
        {
            return result;
        }
        for (Map.Entry<String, OutputSpawn> e : raw.entrySet())
        {
            if (e.getValue() == null)
            {
                continue;
            }
            try
            {
                result.put(Integer.parseInt(e.getKey().trim()), e.getValue());
            }
            catch (NumberFormatException ex)
            {
                // a key that is not an npc id is a typo, not a monster
            }
        }
        return result;
    }

    public static Map<Integer, List<OutputSpawn>> parseOverlay(Gson gson, String json)
    {
        Map<Integer, List<OutputSpawn>> result = new HashMap<>();
        if (json == null || json.trim().isEmpty())
        {
            return result;
        }
        Type type = new TypeToken<Map<String, List<OutputSpawn>>>()
        {
        }.getType();
        Map<String, List<OutputSpawn>> raw = gson.fromJson(json, type);
        if (raw == null)
        {
            return result;
        }
        for (Map.Entry<String, List<OutputSpawn>> e : raw.entrySet())
        {
            try
            {
                int id = Integer.parseInt(e.getKey().trim());
                result.put(id, e.getValue() == null ? new ArrayList<>() : e.getValue());
            }
            catch (NumberFormatException ex)
            {
                // skip non-numeric overlay key
            }
        }
        return result;
    }

    public static Map<Integer, CombatOverlayJson> parseCombatOverlay(Gson gson, String json)
    {
        Map<Integer, CombatOverlayJson> result = new HashMap<>();
        if (json == null || json.trim().isEmpty())
        {
            return result;
        }
        Type type = new TypeToken<Map<String, CombatOverlayJson>>()
        {
        }.getType();
        Map<String, CombatOverlayJson> raw = gson.fromJson(json, type);
        if (raw == null)
        {
            return result;
        }
        for (Map.Entry<String, CombatOverlayJson> e : raw.entrySet())
        {
            try
            {
                result.put(Integer.parseInt(e.getKey().trim()), e.getValue());
            }
            catch (NumberFormatException ex)
            {
                // skip non-numeric overlay key
            }
        }
        return result;
    }

    public static OutputCombat applyCombatOverlay(OutputCombat base, CombatOverlayJson ov)
    {
        if (ov == null || ov.needsReview || ov.attacks == null)
        {
            return base;
        }
        List<OutputAttack> attacks = new ArrayList<>();
        for (OutputAttack a : ov.attacks)
        {
            attacks.add(resolveHybrid(a));
        }
        return new OutputCombat(base.attackLevel, base.strengthLevel, base.defenceLevel,
            base.magicLevel, base.rangedLevel, base.hitpoints, attacks);
    }

    /**
     * Applies to a curated attack the same hybrid split the wiki parse gets. The curator stores
     * the style text as typed, so "magical melee" arrives as one token; left alone it reaches the
     * client as an unknown style and the combat card claims nothing protects against it. An
     * explicit prayStyle from the curator always wins.
     */
    private static OutputAttack resolveHybrid(OutputAttack a)
    {
        if (a == null || a.style == null || a.prayStyle != null)
        {
            return a;
        }
        String[] hybrid = CombatParser.hybridOf(a.style.toLowerCase(Locale.ROOT).trim());
        if (hybrid == null)
        {
            return a;
        }
        OutputAttack out = new OutputAttack(hybrid[0], a.maxHit, a.maxHitMax, a.speed, a.speedMax);
        out.prayStyle = hybrid[1];
        out.variants = a.variants;
        out.notes = a.notes;
        out.effects = a.effects;
        return out;
    }

    public static Map<Integer, CombatOverlayJson> seedStubs(List<OutputSource> npcs,
        Map<Integer, CombatOverlayJson> existing, Set<Integer> gapIds)
    {
        Map<Integer, CombatOverlayJson> out = new LinkedHashMap<>(existing);
        for (OutputSource npc : npcs)
        {
            if (!gapIds.contains(npc.id) || out.containsKey(npc.id) || npc.combat == null)
            {
                continue;
            }
            CombatOverlayJson stub = new CombatOverlayJson();
            stub.attacks = new ArrayList<>(npc.combat.attacks);
            stub.needsReview = true;
            out.put(npc.id, stub);
        }
        return out;
    }
}
