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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Set;
import com.itemsearch.datagen.output.OutputAttack;
import com.itemsearch.datagen.output.OutputCombat;
import com.itemsearch.datagen.output.OutputDrop;
import com.itemsearch.datagen.output.OutputPhase;
import com.itemsearch.datagen.output.OutputSource;
import com.itemsearch.datagen.output.OutputSpawn;
import com.itemsearch.datagen.output.OutputVariant;
import com.itemsearch.datagen.source.CombatOverlayJson;
import com.itemsearch.datagen.source.DropLineJson;
import com.itemsearch.datagen.source.DropPageJson;
import com.itemsearch.datagen.source.LocationJson;
import com.itemsearch.datagen.source.DropTableJson;
import com.itemsearch.datagen.source.ItemInfoJson;
import com.itemsearch.datagen.source.MonsterJson;
import com.itemsearch.datagen.source.RecipeJson;
import com.itemsearch.datagen.source.ThievingJson;
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

    /** Backward-compatible 6-arg form (no monster dump, so combat levels stay zero). */
    public static List<OutputSource> transform(Map<String, DropPageJson> pages,
        Map<String, ItemInfoJson> itemInfo, Map<Integer, List<OutputSpawn>> spawns,
        Map<String, WikiCombatJson> wiki, Map<Integer, CombatOverlayJson> combatOverlay,
        Map<String, String> typeOverlay)
    {
        return transform(pages, itemInfo, spawns, wiki, combatOverlay, typeOverlay,
            Collections.emptyMap());
    }

    public static List<OutputSource> transform(Map<String, DropPageJson> pages,
        Map<String, ItemInfoJson> itemInfo, Map<Integer, List<OutputSpawn>> spawns,
        Map<String, WikiCombatJson> wiki, Map<Integer, CombatOverlayJson> combatOverlay,
        Map<String, String> typeOverlay, Map<Integer, MonsterJson> monsters)
    {
        return transform(pages, itemInfo, spawns, wiki, combatOverlay, typeOverlay, monsters,
            Collections.emptyMap());
    }

    /**
     * @param versionImages page title to one image url per wiki version, as
     *                      {@link VersionImages#fromTexts} reads them; a phase's picture
     */
    public static List<OutputSource> transform(Map<String, DropPageJson> pages,
        Map<String, ItemInfoJson> itemInfo, Map<Integer, List<OutputSpawn>> spawns,
        Map<String, WikiCombatJson> wiki, Map<Integer, CombatOverlayJson> combatOverlay,
        Map<String, String> typeOverlay, Map<Integer, MonsterJson> monsters,
        Map<String, List<String>> versionImages)
    {
        return transform(pages, itemInfo, spawns, wiki, combatOverlay, typeOverlay, monsters,
            versionImages, Collections.emptyMap());
    }

    /**
     * @param thieving lower-cased NPC name to its row of the wiki's Pickpocketing table, as
     *                 {@link WikiThievingFetch#parse} reads it; what a pickpocket source costs
     */
    public static List<OutputSource> transform(Map<String, DropPageJson> pages,
        Map<String, ItemInfoJson> itemInfo, Map<Integer, List<OutputSpawn>> spawns,
        Map<String, WikiCombatJson> wiki, Map<Integer, CombatOverlayJson> combatOverlay,
        Map<String, String> typeOverlay, Map<Integer, MonsterJson> monsters,
        Map<String, List<String>> versionImages, Map<String, ThievingJson> thieving)
    {
        return transform(pages, itemInfo, spawns, wiki, combatOverlay, typeOverlay, monsters,
            versionImages, thieving, Collections.emptyMap());
    }

    /**
     * @param recipes every {{Recipe}} on the item pages, by page, as {@link WikiRecipes#extract}
     *                reads them; the ones that break something down become PROCESS sources
     */
    public static List<OutputSource> transform(Map<String, DropPageJson> pages,
        Map<String, ItemInfoJson> itemInfo, Map<Integer, List<OutputSpawn>> spawns,
        Map<String, WikiCombatJson> wiki, Map<Integer, CombatOverlayJson> combatOverlay,
        Map<String, String> typeOverlay, Map<Integer, MonsterJson> monsters,
        Map<String, List<String>> versionImages, Map<String, ThievingJson> thieving,
        Map<String, List<RecipeJson>> recipes)
    {
        List<Bucket> buckets = new ArrayList<>();
        if (pages != null)
        {
            for (DropPageJson page : pages.values())
            {
                buckets.addAll(bucketsForPage(page, itemInfo, typeOverlay, wiki));
            }
        }
        if (recipes != null)
        {
            buckets.addAll(processBuckets(recipes, itemInfo));
        }

        assignIds(buckets);

        List<OutputSource> out = new ArrayList<>();
        for (Bucket b : buckets)
        {
            OutputSource source = finish(b, spawns, wiki, combatOverlay, monsters, versionImages);
            ThievingJson row = thieving == null ? null : thievingRow(thieving, b.pageTitle, b.type);
            if (row != null)
            {
                source.thievingLevel = row.level;
                source.thievingXp = row.xp;
            }
            out.add(source);
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
        return splitVersions(result, page);
    }

    /**
     * One PROCESS source per item that is broken down into others: a sacred eel yields Zulrah's
     * scales, a leaping sturgeon caviar and offcuts, a tanzanite fang scales. The source is named
     * for the material, and every breakdown recipe with that material contributes its outputs.
     */
    private static List<Bucket> processBuckets(Map<String, List<RecipeJson>> recipes,
        Map<String, ItemInfoJson> itemInfo)
    {
        Map<String, Bucket> byMaterial = new LinkedHashMap<>();
        for (List<RecipeJson> pageRecipes : recipes.values())
        {
            for (RecipeJson r : pageRecipes)
            {
                if (!WikiRecipes.isBreakdown(r))
                {
                    continue;
                }
                String material = r.materials.get(0);
                Bucket b = byMaterial.computeIfAbsent(material, m ->
                {
                    Bucket fresh = new Bucket();
                    fresh.pageTitle = m;
                    fresh.name = m;
                    fresh.type = "PROCESS";
                    fresh.combatLevel = 0;
                    fresh.npcIds = new ArrayList<>();
                    fresh.drops = new ArrayList<>();
                    return fresh;
                });
                for (RecipeJson.Output o : r.outputs)
                {
                    boolean listed = false;
                    for (OutputDrop existing : b.drops)
                    {
                        listed |= existing.itemName.equalsIgnoreCase(o.name);
                    }
                    if (listed)
                    {
                        continue;
                    }
                    ItemInfoJson info = itemInfo == null ? null : itemInfo.get(o.name);
                    int itemId = info != null && info.id != null ? info.id : -1;
                    // a fixed yield is certain; one the wiki has to explain has no rate to give
                    boolean variable = WikiRecipes.isVariable(o);
                    b.drops.add(new OutputDrop(itemId, o.name, variable ? 0 : 1.0,
                        WikiRecipes.fixedQuantity(o), false, !variable));
                }
            }
        }
        return new ArrayList<>(byMaterial.values());
    }

    /**
     * Splits a page that describes several monsters into one source per distinct version.
     *
     * <p>What makes a version its own monster is its combat level: Ankou comes at 75, 82 and
     * 86, and a player hunting one wants the one they can fight. Versions at the same level are
     * one monster in different poses or outfits — Killerwatt's Idle and Attacking are the same
     * spark, and one id turns into the other when it notices you — so they share a source, with
     * every id and every attack any of them has. That also keeps the 36 near-identical Barbarian
     * versions from each becoming a search result. A lone version keeps the wiki's label for it
     * ("Steel dragon (Level 274)"); a merged group is named for the level its members share,
     * since the first member's label would describe only that one. The shared drop table belongs
     * to every version, since any of them drops it.
     */
    private static List<Bucket> splitVersions(List<Bucket> buckets, DropPageJson page)
    {
        if (page.versions == null || page.versions.size() < 2)
        {
            return buckets;
        }

        // group version indexes by difficulty mode, then by combat level within the mode. A mode
        // is a different fight whatever the level says: Nylocas Vasilias is level 800 in Normal
        // and in Hard, hitting 70 in one and 105 in the other.
        Map<String, Map<String, List<Integer>>> modes = new LinkedHashMap<>();
        int groupCount = 0;
        for (int i = 0; i < page.versions.size(); i++)
        {
            VersionJson v = page.versions.get(i);
            if (v.npcIds.isEmpty())
            {
                continue; // a version with no id of its own cannot be told apart in game
            }
            String level = v.combatLevel == null ? "" : v.combatLevel.trim();
            Map<String, List<Integer>> levels = modes.computeIfAbsent(modeOf(v.label),
                k -> new LinkedHashMap<>());
            if (levels.computeIfAbsent(level, k -> new ArrayList<>()).isEmpty())
            {
                groupCount++;
            }
            levels.get(level).add(i);
        }
        if (groupCount < 2)
        {
            // every version is the same level: one monster after all, standing for all of them
            for (Bucket b : buckets)
            {
                if ("KILL".equals(b.type) && groupCount == 1)
                {
                    b.versions = modes.values().iterator().next().values().iterator().next();
                    b.pageVersions = page.versions;
                }
            }
            return buckets;
        }

        List<Bucket> out = new ArrayList<>();
        for (Bucket b : buckets)
        {
            if (!"KILL".equals(b.type))
            {
                out.add(b); // only a fought monster has versions worth telling apart
                continue;
            }
            for (Map.Entry<String, Map<String, List<Integer>>> m : modes.entrySet())
            {
                for (Map.Entry<String, List<Integer>> e : m.getValue().entrySet())
                {
                    List<Integer> group = e.getValue();
                    VersionJson first = page.versions.get(group.get(0));
                    Bucket split = new Bucket();
                    split.pageTitle = b.pageTitle;
                    split.pageVersions = page.versions;
                    split.name = b.name + suffixFor(page, group, e.getKey(), m.getKey(),
                        m.getValue().size() > 1);
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
                    split.versions = group;
                    out.add(split);
                }
            }
        }
        return out;
    }

    // the difficulty modes the wiki names in a version label: Hard at the Theatre of Blood,
    // Challenge Mode at the Chambers of Xeric, Normal Mode and Hard Mode in the Nightmare Zone.
    // A bare "Normal" is not one — the wiki calls the plain version of anything Normal, enraged
    // or not — and "Enraged" is a phase of the same fight. Longest phrase first, so "Hard Mode"
    // is kept whole and the name reads as the wiki wrote it.
    private static final Pattern MODE =
        Pattern.compile("\\b(Normal Mode|Hard Mode|Challenge Mode|Hard)\\b");

    /** The mode a version label names, or "" for the ordinary fight. */
    static String modeOf(String label)
    {
        if (label == null)
        {
            return "";
        }
        Matcher m = MODE.matcher(label);
        return m.find() ? m.group(1) : "";
    }

    /** The label with its mode taken off: "Normal (Challenge Mode)" is the Normal phase. */
    static String withoutMode(String label)
    {
        if (label == null)
        {
            return null;
        }
        String bare = MODE.matcher(label).replaceAll("").replaceAll("\\(\\s*\\)", "").trim();
        return bare.isEmpty() ? label : bare;
    }

    /**
     * What a split source adds to its page's name: " (Hard)" for the only group of a mode,
     * " (Level 800)" for one of several at the ordinary difficulty, and both when a mode has
     * several levels of its own. The ordinary fight with one level adds nothing, so the pages
     * that only gained a mode group keep the name they had.
     */
    private static String suffixFor(DropPageJson page, List<Integer> group, String level,
        String mode, boolean severalLevels)
    {
        List<String> parts = new ArrayList<>();
        if (!mode.isEmpty())
        {
            parts.add(mode);
        }
        if (severalLevels)
        {
            parts.add(groupLabel(page, group, level));
        }
        return parts.isEmpty() ? "" : " (" + String.join(", ", parts) + ")";
    }

    /**
     * The Thieving tables' row for a page, or null. A pickpocket is found by its title, else by
     * its title without the place in brackets — "Farmer (Kastori)" is robbed like any Farmer, and
     * the table has one row for all of them. A stall or a chest is found by its title alone: the
     * stalls of Port Roberts have rows of their own, and a chest that is not in the table is not
     * picked. Monsters and the rest are not robbed at all.
     */
    private static ThievingJson thievingRow(Map<String, ThievingJson> thieving, String pageTitle,
        String type)
    {
        if (pageTitle == null)
        {
            return null;
        }
        String title = pageTitle.trim().toLowerCase(Locale.ROOT);
        if ("PICKPOCKET".equals(type))
        {
            ThievingJson row = thieving.get(title);
            if (row == null && title.endsWith(")") && title.contains(" ("))
            {
                row = thieving.get(title.substring(0, title.lastIndexOf(" (")).trim());
            }
            return row;
        }
        if ("GATHER".equals(type) || "REWARD".equals(type))
        {
            return thieving.get(title);
        }
        return null;
    }

    // a wiki label's trailing counter: "God Wars Dungeon (2)", "Melee, 3", "Level 79 (1)"
    private static final Pattern LABEL_COUNTER = Pattern.compile("\\s*(?:\\(\\d+\\)|,\\s*\\d+)$");

    /**
     * What to call a group of versions: a lone version's own label; for several, the label they
     * all share once the wiki's counters come off ("God Wars Dungeon (1)" and "(2)" are the God
     * Wars Dungeon werewolves), and failing that the level they share, which is the one thing
     * known to be true of all of them.
     */
    private static String groupLabel(DropPageJson page, List<Integer> group, String level)
    {
        String shared = null;
        for (int i : group)
        {
            String label = withoutMode(page.versions.get(i).label);
            String bare = label == null ? "" : LABEL_COUNTER.matcher(label.trim()).replaceFirst("");
            if (shared == null)
            {
                shared = bare;
            }
            else if (!shared.equals(bare))
            {
                shared = level.isEmpty() ? bare : "Level " + level;
                break;
            }
        }
        return shared;
    }

    private static String inferType(String infobox, DropTableJson table)
    {
        // something the player takes part in — Golem crafting, a minigame — rather than a thing
        // they kill, open, rob, or gather from. Its rewards are earned, so it is not a container
        // even when the wiki marks the lines as rewards.
        if ("Activity".equals(infobox))
        {
            return "ACTIVITY";
        }
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
        drop.condition = conditionOf(line.name, line.rarity);
        return drop;
    }

    /** Items that exist only under a condition, wherever on the wiki they are listed. */
    private static final Map<String, String> CONDITIONAL_ITEMS = new HashMap<>();

    static
    {
        // brimstone keys are handed out by Konar's tasks and by nothing else in the game
        CONDITIONAL_ITEMS.put("brimstone key", "KONAR_TASK");
    }

    /**
     * What has to hold before a drop is on the table at all, or null when it always is.
     *
     * <p>Nothing states conditions outright. A drop that only exists under one is usually priced
     * with a template of its own — a brimstone key's chance is meaningless off a Konar task, so the
     * wiki gives it {@code Brimstone rarity} instead of a number — and that template is the
     * statement. Superior slayer monsters break the pattern by listing the same key as "Always",
     * because within the task it is guaranteed, so the item's own name has the final say.
     */
    static String conditionOf(String itemName, String rarity)
    {
        if (itemName != null)
        {
            String byItem = CONDITIONAL_ITEMS.get(itemName.trim().toLowerCase(Locale.ROOT));
            if (byItem != null)
            {
                return byItem;
            }
        }
        if (rarity == null)
        {
            return null;
        }
        return rarity.toLowerCase(Locale.ROOT).contains("{{brimstone rarity") ? "KONAR_TASK" : null;
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

    /**
     * The monster the dump holds for this source, or null when it holds none.
     *
     * <p>Matched on the bucket's own npc ids rather than the page's, so a page split into versions
     * ("Hellhound (Level 122)") picks up the levels of the version it describes.
     */
    private static MonsterJson monsterFor(List<Integer> npcIds, Map<Integer, MonsterJson> monsters)
    {
        if (monsters == null || monsters.isEmpty() || npcIds == null)
        {
            return null;
        }
        for (Integer id : npcIds)
        {
            MonsterJson m = monsters.get(id);
            if (m != null)
            {
                return m;
            }
        }
        return null;
    }

    private static OutputSource finish(Bucket b, Map<Integer, List<OutputSpawn>> spawns,
        Map<String, WikiCombatJson> wiki, Map<Integer, CombatOverlayJson> combatOverlay,
        Map<Integer, MonsterJson> monsters, Map<String, List<String>> versionImages)
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
        boolean combatGap = false;
        List<OutputPhase> phases = null;
        if ("KILL".equals(b.type))
        {
            boolean[] gap = new boolean[1];
            combat = combatFor(b.pageTitle != null ? b.pageTitle : b.name, b.npcIds, b.id,
                b.versions, -1, wiki, combatOverlay, monsters, gap);
            // judged here, on the versions this source is built from: a page read whole can be
            // ambiguous (two styles, one max hit) while each of its versions is perfectly clear
            combatGap = gap[0];
            phases = phasesFor(b, wiki, combatOverlay, monsters, versionImages);
        }

        OutputSource source = new OutputSource(b.id, b.npcIds, b.name, b.combatLevel, null,
            spawnPoints, b.drops, combat, b.type, x, y, plane);
        source.combatGap = combatGap;
        source.phases = phases;
        // only worth carrying when the two differ: the client fetches the image by page title, and
        // a split or a pickpocket variant no longer answers to its own display name
        if (b.pageTitle != null && !b.pageTitle.equals(b.name))
        {
            source.wikiPage = b.pageTitle;
        }
        return source;
    }

    /**
     * How a monster fights, from every source the generator has: the wiki's attack fields for the
     * given versions of its page (all of them when null), levels from the monster dump under its
     * npc ids, the curator's write-up under any of those ids, and the page's own weakness and
     * poison. Serves the source itself and each of its phases alike; a phase asks for its one
     * version and its own defence row ({@code weaknessVersion}), the source asks for the fold
     * and the plain row (-1). {@code gapOut[0]} receives whether the wiki read was ambiguous.
     */
    private static OutputCombat combatFor(String pageTitle, List<Integer> npcIds, int syntheticId,
        List<Integer> versions, int weaknessVersion, Map<String, WikiCombatJson> wiki,
        Map<Integer, CombatOverlayJson> combatOverlay, Map<Integer, MonsterJson> monsters,
        boolean[] gapOut)
    {
        WikiCombatJson record = wiki == null ? null : wiki.get(pageTitle);
        CombatParser.ParsedCombat pc = WikiCombat.toAttacks(record, versions);
        gapOut[0] = pc.gap;
        // the wiki infobox publishes defence bonuses and max hits but not levels, so the
        // levels come from the monster dump, joined on npc id
        MonsterJson m = monsterFor(npcIds, monsters);
        OutputCombat combat = m == null
            ? new OutputCombat(0, 0, 0, 0, 0, 0, pc.attacks)
            : new OutputCombat(m.attackLevel, m.strengthLevel, m.defenceLevel, m.magicLevel,
                m.rangedLevel, m.hitpoints, pc.attacks);
        if (combatOverlay != null)
        {
            // under every id the source answers to: a version merged in may have been
            // curated on its own while it was a source of its own
            List<CombatOverlayJson> curated = new ArrayList<>();
            for (Integer id : npcIds.isEmpty() ? Collections.singletonList(syntheticId) : npcIds)
            {
                if (combatOverlay.containsKey(id))
                {
                    curated.add(combatOverlay.get(id));
                }
            }
            combat = MonsterTransform.applyCombatOverlays(combat, curated);
        }
        // by page, not display name: a split renames the source "Steel dragon (Level 246)",
        // which is not a metallic dragon by any name the check knows
        combat.attacks = MonsterTransform.relabelMetallicDragonfire(pageTitle, combat.attacks);
        // what to bring: the styles it defends worst against, and the spell school it burns to
        List<String> weak = weaknessVersion >= 0
            ? Weakness.weakestOf(record, weaknessVersion) : Weakness.weakestOf(record);
        combat.weakTo = weak.isEmpty() ? null : weak;
        combat.elementalWeakness = Weakness.elementOf(record);
        combat.elementalWeaknessPercent = Weakness.elementPercentOf(record);
        Map<String, String> statuses = Statuses.of(record, weaknessVersion);
        combat.statuses = statuses.isEmpty() ? null : statuses;
        // the wiki records poison per monster, not per attack, so it seeds every attack and
        // the curator narrows it to the one that actually applies it
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
        return combat;
    }

    /**
     * The versions a source stands for, each with its own picture and its own combat, when there
     * are at least two worth telling apart. Versions that look and fight alike are one phase —
     * twenty Canifis werewolves are one werewolf — and a source left with one phase carries none.
     */
    private static List<OutputPhase> phasesFor(Bucket b, Map<String, WikiCombatJson> wiki,
        Map<Integer, CombatOverlayJson> combatOverlay, Map<Integer, MonsterJson> monsters,
        Map<String, List<String>> versionImages)
    {
        if (b.versions == null || b.versions.size() < 2 || b.pageVersions == null)
        {
            return null;
        }
        List<String> images = versionImages == null ? null : versionImages.get(b.pageTitle);
        List<OutputPhase> phases = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (int i : b.versions)
        {
            VersionJson v = b.pageVersions.get(i);
            OutputCombat combat = combatFor(b.pageTitle, parseNpcIds(v.npcIds), b.id,
                Collections.singletonList(i), i, wiki, combatOverlay, monsters, new boolean[1]);
            String image = images != null && i < images.size() ? images.get(i) : null;
            // the mode is in the source's name already; the phase says what is left
            OutputPhase phase = new OutputPhase(withoutMode(v.label), image, combat);
            if (seen.add(phaseKey(phase)))
            {
                phases.add(phase);
            }
        }
        return phases.size() >= 2 ? phases : null;
    }

    // everything the client would show for a phase, so two that read the same are one
    private static String phaseKey(OutputPhase p)
    {
        StringBuilder key = new StringBuilder(String.valueOf(p.image));
        OutputCombat c = p.combat;
        key.append('|').append(c.attackLevel).append(',').append(c.strengthLevel).append(',')
            .append(c.defenceLevel).append(',').append(c.magicLevel).append(',')
            .append(c.rangedLevel).append(',').append(c.hitpoints)
            .append('|').append(c.weakTo).append('|').append(c.elementalWeakness)
            .append('|').append(c.elementalWeaknessPercent).append('|').append(c.statuses);
        for (OutputAttack a : c.attacks)
        {
            key.append('|').append(a.style).append(':').append(a.prayStyle).append(':')
                .append(a.maxHit).append('-').append(a.maxHitMax).append(':')
                .append(a.speed).append('-').append(a.speedMax).append(':')
                .append(a.notes).append(':').append(a.effects);
            if (a.variants != null)
            {
                for (OutputVariant variant : a.variants)
                {
                    key.append(':').append(variant.label).append('=').append(variant.maxHit)
                        .append('-').append(variant.maxHitMax);
                }
            }
        }
        return key.toString();
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
        // indexes into the page's versionN list that this source is built from, or null when it
        // is built from the whole page
        List<Integer> versions;
        // the page's versionN list itself, for naming and picturing the phases
        List<VersionJson> pageVersions;
    }
}
