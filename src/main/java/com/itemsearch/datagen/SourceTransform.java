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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import com.itemsearch.datagen.output.OutputAttack;
import com.itemsearch.datagen.output.OutputCombat;
import com.itemsearch.datagen.output.OutputDrop;
import com.itemsearch.datagen.output.OutputSource;
import com.itemsearch.datagen.output.OutputSpawn;
import com.itemsearch.datagen.source.CombatOverlayJson;
import com.itemsearch.datagen.source.DropLineJson;
import com.itemsearch.datagen.source.DropPageJson;
import com.itemsearch.datagen.source.LocationJson;
import com.itemsearch.datagen.source.DropTableJson;
import com.itemsearch.datagen.source.ItemInfoJson;
import com.itemsearch.datagen.source.VersionJson;
import com.itemsearch.datagen.source.WikiCombatJson;

/**
 * Joins the wiki drop-page snapshots (plus item/spawn/combat data) into the client's typed
 * {@code sources.json} shape. One page yields one {@link OutputSource} per distinct source type
 * found on it ("type bucket") — e.g. a page with both kill drops and a pickpocket table yields two
 * sources, the pickpocket one suffixed {@code " (pickpocket)"}.
 */
public final class SourceTransform
{
    private static final Set<String> GENERIC_CATEGORIES = new HashSet<>(
        java.util.Arrays.asList("drops", "rewards", "loot"));
    private static final int FIRST_SYNTHETIC_ID = -1000;
    // matches MonsterTransform.MAX_SPAWNS_PER_NPC: the client draws one map marker per spawn
    private static final int MAX_SPAWNS_PER_SOURCE = 25;

    /**
     * Spawn tiles from a page's {@code {{LocLine}}} blocks, for a source the spawn dump never had.
     *
     * <p>A page describes several monsters, and each block names the one it belongs to, so blocks
     * naming this source are preferred; when none does — the usual case, since a split renames the
     * source and the wiki does not — every block on the page is taken, which is right, because they
     * all describe the monster the page is about.
     */
    public static List<OutputSpawn> tilesFrom(List<LocationJson> locations, String sourceName)
    {
        List<OutputSpawn> out = new ArrayList<>();
        if (locations == null)
        {
            return out;
        }
        List<LocationJson> mine = matching(locations, sourceName);
        Set<String> seen = new LinkedHashSet<>();
        for (LocationJson loc : mine)
        {
            for (LocationJson.Point p : loc.points)
            {
                if (seen.add(p.x + "," + p.y + "," + loc.plane) && out.size() < MAX_SPAWNS_PER_SOURCE)
                {
                    out.add(new OutputSpawn(p.x, p.y, loc.plane));
                }
            }
        }
        return out;
    }

    /** The place a source is found, for one the wiki gives no usable coordinates for. */
    public static String placeFrom(List<LocationJson> locations, String sourceName)
    {
        if (locations == null)
        {
            return null;
        }
        for (LocationJson loc : matching(locations, sourceName))
        {
            if (loc.location != null && !loc.location.isEmpty())
            {
                return loc.location;
            }
        }
        return null;
    }

    private static List<LocationJson> matching(List<LocationJson> locations, String sourceName)
    {
        List<LocationJson> named = new ArrayList<>();
        for (LocationJson loc : locations)
        {
            if (loc.name != null && sourceName != null && loc.name.equalsIgnoreCase(sourceName))
            {
                named.add(loc);
            }
        }
        return named.isEmpty() ? locations : named;
    }

    private SourceTransform()
    {
    }

    public static List<OutputSource> transform(Map<String, DropPageJson> pages,
        Map<String, ItemInfoJson> itemInfo, Map<Integer, List<OutputSpawn>> spawns,
        Map<String, WikiCombatJson> wiki, Map<Integer, CombatOverlayJson> combatOverlay,
        Map<String, String> typeOverlay)
    {
        List<Bucket> buckets = new ArrayList<>();
        if (pages != null)
        {
            for (DropPageJson page : pages.values())
            {
                buckets.addAll(bucketsForPage(page, itemInfo, typeOverlay, wiki));
            }
        }

        assignIds(buckets);

        List<OutputSource> out = new ArrayList<>();
        for (Bucket b : buckets)
        {
            out.add(finish(b, spawns, wiki, combatOverlay));
        }
        out.sort(Comparator
            .comparing((OutputSource s) -> s.name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(s -> s.type));
        return out;
    }

    private static List<Bucket> bucketsForPage(DropPageJson page, Map<String, ItemInfoJson> itemInfo,
        Map<String, String> typeOverlay, Map<String, WikiCombatJson> wiki)
    {
        List<Bucket> result = new ArrayList<>();
        if (page == null || page.tables == null || page.pageTitle == null)
        {
            return result;
        }
        List<Integer> npcIds = parseNpcIds(page.npcIds);
        int combatLevel = parseIntOr(page.combatLevel, 0);
        String override = typeOverlay == null ? null : typeOverlay.get(page.pageTitle);

        // group this page's tables by type, preserving first-seen order
        Map<String, List<DropTableJson>> byType = new LinkedHashMap<>();
        for (DropTableJson table : page.tables)
        {
            if (table == null)
            {
                continue;
            }
            String type = override != null ? override : inferType(page.infobox, table);
            byType.computeIfAbsent(type, k -> new ArrayList<>()).add(table);
        }

        for (Map.Entry<String, List<DropTableJson>> e : byType.entrySet())
        {
            String type = e.getKey();
            Bucket b = new Bucket();
            b.pageTitle = page.pageTitle;
            b.name = "PICKPOCKET".equals(type) ? page.pageTitle + " (pickpocket)" : page.pageTitle;
            b.type = type;
            b.combatLevel = combatLevel;
            b.npcIds = npcIds;
            b.mapX = page.mapX;
            b.mapY = page.mapY;
            b.mapPlane = page.mapPlane;
            b.drops = new ArrayList<>();
            for (DropTableJson table : e.getValue())
            {
                String category = normalizeCategory(table.sectionHeading);
                for (DropLineJson line : table.lines)
                {
                    if (line == null || line.name == null)
                    {
                        continue;
                    }
                    b.drops.add(transformLine(line, category, table.dropTable, itemInfo));
                }
            }
            result.add(b);
        }
        return splitVersions(result, page, wiki);
    }

    /**
     * Splits a page that describes several monsters into one source per distinct version.
     *
     * <p>Killerwatt is Idle (470, stab) and Attacking (469, crush) sharing a drop table; merging
     * them dropped the crush and told the player the thing fighting them stabs. Versions are
     * grouped by what actually distinguishes them — combat level and how they fight — so the 36
     * near-identical Barbarian versions do not each become a search result. A group is named
     * after its first version ("Killerwatt (Attacking)"), and the shared drop table belongs to
     * every version, since any of them drops it.
     */
    private static List<Bucket> splitVersions(List<Bucket> buckets, DropPageJson page,
        Map<String, WikiCombatJson> wiki)
    {
        if (page.versions == null || page.versions.size() < 2)
        {
            return buckets;
        }
        WikiCombatJson combat = wiki == null ? null : wiki.get(page.pageTitle);

        // group version indexes by combat level plus the attacks they parse to
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < page.versions.size(); i++)
        {
            VersionJson v = page.versions.get(i);
            if (v.npcIds.isEmpty())
            {
                continue; // a version with no id of its own cannot be told apart in game
            }
            groups.computeIfAbsent(signature(v, combat, i), k -> new ArrayList<>()).add(i);
        }
        if (groups.size() < 2)
        {
            return buckets; // every version fights the same: one monster after all
        }

        List<Bucket> out = new ArrayList<>();
        for (Bucket b : buckets)
        {
            if (!"KILL".equals(b.type))
            {
                out.add(b); // only a fought monster has versions worth telling apart
                continue;
            }
            for (List<Integer> group : groups.values())
            {
                VersionJson first = page.versions.get(group.get(0));
                Bucket split = new Bucket();
                split.pageTitle = b.pageTitle;
                split.name = b.name + " (" + first.label + ")";
                split.type = b.type;
                split.combatLevel = parseIntOr(first.combatLevel, b.combatLevel);
                split.npcIds = new ArrayList<>();
                for (int i : group)
                {
                    for (Integer id : parseNpcIds(page.versions.get(i).npcIds))
                    {
                        if (!split.npcIds.contains(id))
                        {
                            split.npcIds.add(id);
                        }
                    }
                }
                split.drops = b.drops;
                split.mapX = b.mapX;
                split.mapY = b.mapY;
                split.mapPlane = b.mapPlane;
                split.version = group.get(0);
                out.add(split);
            }
        }
        return out;
    }

    /** What tells two versions apart: their combat level and how they fight. */
    private static String signature(VersionJson v, WikiCombatJson combat, int index)
    {
        StringBuilder sig = new StringBuilder(v.combatLevel == null ? "" : v.combatLevel.trim());
        for (OutputAttack a : WikiCombat.toAttacks(combat, index).attacks)
        {
            sig.append('|').append(a.style).append(':').append(a.prayStyle)
                .append(':').append(a.maxHit).append('-').append(a.maxHitMax)
                .append(':').append(a.speed).append('-').append(a.speedMax);
        }
        return sig.toString();
    }

    private static String inferType(String infobox, DropTableJson table)
    {
        for (DropLineJson line : table.lines)
        {
            if (line != null && line.reward)
            {
                return "REWARD";
            }
        }
        if ("Monster".equals(infobox) || "NPC".equals(infobox))
        {
            if (containsPickpocket(table.sectionHeading) || containsPickpocket(table.topHeading))
            {
                return "PICKPOCKET";
            }
            return "KILL";
        }
        return "GATHER";
    }

    private static boolean containsPickpocket(String heading)
    {
        return heading != null && heading.toLowerCase(Locale.ROOT).contains("pickpocket");
    }

    private static String normalizeCategory(String sectionHeading)
    {
        if (sectionHeading == null)
        {
            return null;
        }
        return GENERIC_CATEGORIES.contains(sectionHeading.toLowerCase(Locale.ROOT)) ? null : sectionHeading;
    }

    private static OutputDrop transformLine(DropLineJson line, String category,
        String dropTable, Map<String, ItemInfoJson> itemInfo)
    {
        ItemInfoJson info = itemInfo == null ? null : itemInfo.get(line.name);
        int itemId = info != null && info.id != null ? info.id : -1;
        Double parsedRarity = Rarity.parse(line.rarity);
        boolean rarityKnown = parsedRarity != null;
        int rolls = parseIntOr(line.rolls, 1);
        double rarity = MonsterTransform.effectiveRarity(parsedRarity, rolls);
        int quantity = MonsterTransform.parseQuantityMax(line.quantity);
        boolean noted = line.quantity != null && line.quantity.contains("(noted)");
        OutputDrop drop = new OutputDrop(itemId, line.name, rarity, quantity, noted, rarityKnown);
        drop.category = category;
        drop.dropTable = dropTable;
        return drop;
    }

    private static List<Integer> parseNpcIds(List<String> raw)
    {
        List<Integer> out = new ArrayList<>();
        if (raw == null)
        {
            return out;
        }
        for (String s : raw)
        {
            if (s == null)
            {
                continue;
            }
            try
            {
                out.add(Integer.parseInt(s.trim()));
            }
            catch (NumberFormatException e)
            {
                // skip unparseable id token
            }
        }
        return out;
    }

    private static int parseIntOr(String raw, int def)
    {
        if (raw == null)
        {
            return def;
        }
        try
        {
            return Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return def;
        }
    }

    // canonical id = first npcId when NPC-backed; otherwise the next synthetic negative id, assigned
    // in sorted-name order (starting at -1000) after every NPC-backed source is numbered
    private static void assignIds(List<Bucket> buckets)
    {
        List<Bucket> needsSynthetic = new ArrayList<>();
        for (Bucket b : buckets)
        {
            if (!b.npcIds.isEmpty())
            {
                b.id = b.npcIds.get(0);
            }
            else
            {
                needsSynthetic.add(b);
            }
        }
        needsSynthetic.sort(Comparator.comparing(bk -> bk.name, String.CASE_INSENSITIVE_ORDER));
        int next = FIRST_SYNTHETIC_ID;
        for (Bucket b : needsSynthetic)
        {
            b.id = next--;
        }
    }

    private static OutputSource finish(Bucket b, Map<Integer, List<OutputSpawn>> spawns,
        Map<String, WikiCombatJson> wiki, Map<Integer, CombatOverlayJson> combatOverlay)
    {
        List<OutputSpawn> spawnPoints;
        Integer x = null;
        Integer y = null;
        Integer plane = null;
        if (!b.npcIds.isEmpty())
        {
            spawnPoints = collectSpawns(b.npcIds, spawns);
        }
        else if (b.mapX != null && b.mapY != null)
        {
            x = b.mapX;
            y = b.mapY;
            plane = b.mapPlane != null ? b.mapPlane : 0;
            spawnPoints = Collections.singletonList(new OutputSpawn(x, y, plane));
        }
        else
        {
            spawnPoints = new ArrayList<>();
        }

        OutputCombat combat = null;
        if ("KILL".equals(b.type))
        {
            CombatParser.ParsedCombat pc = WikiCombat.toAttacks(
                wiki == null ? null : wiki.get(b.pageTitle), b.version);
            combat = new OutputCombat(0, 0, 0, 0, 0, 0, pc.attacks);
            if (combatOverlay != null && combatOverlay.containsKey(b.id))
            {
                combat = MonsterTransform.applyCombatOverlay(combat, combatOverlay.get(b.id));
            }
            // by page, not display name: a split renames the source "Steel dragon (Level 246)",
            // which is not a metallic dragon by any name the check knows
            combat.attacks = MonsterTransform.relabelMetallicDragonfire(
                b.pageTitle != null ? b.pageTitle : b.name, combat.attacks);
            // the wiki records poison per monster, not per attack, so it seeds every attack and
            // the curator narrows it to the one that actually applies it
            WikiCombatJson record = wiki == null ? null : wiki.get(b.pageTitle);
            List<String> poison = AttackEffects.fromPoisonous(record == null ? null : record.poisonous);
            if (!poison.isEmpty())
            {
                for (OutputAttack a : combat.attacks)
                {
                    if (a.effects == null || a.effects.isEmpty())
                    {
                        a.effects = new ArrayList<>(poison);
                    }
                }
            }
        }

        OutputSource source = new OutputSource(b.id, b.npcIds, b.name, b.combatLevel, null,
            spawnPoints, b.drops, combat, b.type, x, y, plane);
        // only worth carrying when the two differ: the client fetches the image by page title, and
        // a split or a pickpocket variant no longer answers to its own display name
        if (b.pageTitle != null && !b.pageTitle.equals(b.name))
        {
            source.wikiPage = b.pageTitle;
        }
        return source;
    }

    /**
     * Spawn tiles for a source, deduplicated and capped. A common monster has hundreds of static
     * spawns (Rat: 731) and the client turns every one into a world-map marker, so shipping them
     * all would let the data stall the UI. Takes the first {@value #MAX_SPAWNS_PER_SOURCE} in npc
     * id order, which keeps the choice stable across regenerations.
     */
    private static List<OutputSpawn> collectSpawns(List<Integer> npcIds, Map<Integer, List<OutputSpawn>> spawns)
    {
        List<OutputSpawn> out = new ArrayList<>();
        if (spawns == null)
        {
            return out;
        }
        Set<String> seen = new LinkedHashSet<>();
        for (Integer id : npcIds)
        {
            for (OutputSpawn sp : spawns.getOrDefault(id, Collections.emptyList()))
            {
                if (out.size() >= MAX_SPAWNS_PER_SOURCE)
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

    /** A page's tables collapsed to one type, before ids/spawns/combat are joined in. */
    private static final class Bucket
    {
        String pageTitle;
        String name;
        String type;
        int combatLevel;
        List<Integer> npcIds;
        List<OutputDrop> drops;
        Integer mapX;
        Integer mapY;
        Integer mapPlane;
        int id;
        // index into the page's versionN list, or -1 when the page describes one monster
        int version = -1;
    }
}
