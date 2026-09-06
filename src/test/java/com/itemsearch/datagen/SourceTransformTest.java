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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.itemsearch.datagen.output.OutputDrop;
import com.itemsearch.datagen.output.OutputSource;
import com.itemsearch.datagen.output.OutputSpawn;
import com.itemsearch.datagen.source.CombatOverlayJson;
import com.itemsearch.datagen.source.DropLineJson;
import com.itemsearch.datagen.source.DropPageJson;
import com.itemsearch.datagen.source.DropTableJson;
import com.itemsearch.datagen.source.ItemInfoJson;
import com.itemsearch.datagen.source.MonsterJson;
import com.itemsearch.datagen.source.VersionJson;
import com.itemsearch.datagen.source.WikiCombatJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SourceTransformTest
{
    private static DropLineJson line(boolean reward, String name, String quantity, String rarity, String rolls)
    {
        DropLineJson l = new DropLineJson();
        l.reward = reward;
        l.name = name;
        l.quantity = quantity;
        l.rarity = rarity;
        l.rolls = rolls;
        return l;
    }

    private static DropTableJson table(String sectionHeading, String topHeading, DropLineJson... lines)
    {
        DropTableJson t = new DropTableJson();
        t.sectionHeading = sectionHeading;
        t.topHeading = topHeading;
        for (DropLineJson l : lines)
        {
            t.lines.add(l);
        }
        return t;
    }

    private static DropPageJson page(String title, String infobox, List<String> npcIds, String combatLevel,
        DropTableJson... tables)
    {
        DropPageJson p = new DropPageJson();
        p.pageTitle = title;
        p.infobox = infobox;
        if (npcIds != null)
        {
            p.npcIds = new ArrayList<>(npcIds);
        }
        p.combatLevel = combatLevel;
        for (DropTableJson t : tables)
        {
            p.tables.add(t);
        }
        return p;
    }

    private static ItemInfoJson item(Integer id)
    {
        ItemInfoJson i = new ItemInfoJson();
        i.id = id;
        return i;
    }

    /**
     * A common monster has hundreds of static spawn tiles (Rat: 731). Every one becomes a marker
     * on the in-game world map when the player opens it, so the data must not carry more than the
     * client can reasonably draw.
     */
    @Test
    public void spawnPointsAreCappedPerSource()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        pages.put("Rat", page("Rat", "Monster", Collections.singletonList("500"), "1",
            table("Drops", "Drops", line(false, "Bones", "1", "Always", null))));

        List<OutputSpawn> many = new ArrayList<>();
        for (int i = 0; i < 400; i++)
        {
            many.add(new OutputSpawn(3000 + i, 3000, 0));
        }
        Map<Integer, List<OutputSpawn>> spawns = new HashMap<>();
        spawns.put(500, many);

        OutputSource rat = SourceTransform.transform(pages, Collections.emptyMap(), spawns,
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap()).get(0);

        assertEquals(25, rat.spawnPoints.size());
        // deterministic across regenerations: the first N in id order, not an arbitrary subset
        assertEquals(3000, rat.spawnPoints.get(0).x);
        assertEquals(3024, rat.spawnPoints.get(24).x);
    }

    private static VersionJson version(String label, String combatLevel, String... ids)
    {
        VersionJson v = new VersionJson();
        v.label = label;
        v.combatLevel = combatLevel;
        v.npcIds.addAll(Arrays.asList(ids));
        return v;
    }

    private static WikiCombatJson versioned(List<String> styles, List<String> maxHits,
        List<String> speeds)
    {
        WikiCombatJson w = new WikiCombatJson();
        w.attackStyles = styles;
        w.maxHits = maxHits;
        w.attackSpeeds = speeds;
        return w;
    }

    /**
     * Killerwatt is Idle (470, stab) and Attacking (469, crush) sharing one drop table. Folding
     * them into one source lost the crush outright, and told the player the thing fighting them
     * stabs.
     */
    @Test
    public void versionsWithDifferentCombatBecomeSeparateSources()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        DropPageJson p = page("Killerwatt", "Monster", Arrays.asList("470", "469"), "55",
            table("Drops", "Drops", line(false, "Bones", "1", "Always", null)));
        p.versions.add(version("Idle", "55", "470"));
        p.versions.add(version("Attacking", "55", "469"));
        pages.put("Killerwatt", p);

        Map<String, WikiCombatJson> wiki = Collections.singletonMap("Killerwatt",
            versioned(Arrays.asList("[[Stab]]", "[[Crush]]"), Arrays.asList("6", "6"),
                Arrays.asList("2", "2")));

        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), wiki, Collections.emptyMap(), Collections.emptyMap());

        assertEquals(2, out.size());
        OutputSource attacking = byName(out, "Killerwatt (Attacking)");
        OutputSource idle = byName(out, "Killerwatt (Idle)");
        assertNotNull("each version is its own source", attacking);
        assertNotNull(idle);
        assertEquals(Collections.singletonList(469), attacking.npcIds);
        assertEquals("crush", attacking.combat.attacks.get(0).style);
        assertEquals(Collections.singletonList(470), idle.npcIds);
        assertEquals("stab", idle.combat.attacks.get(0).style);
        // the shared drop table belongs to both
        assertEquals(1, attacking.drops.size());
        assertEquals(1, idle.drops.size());
        assertEquals("the page is carried so the client can still fetch its image",
            "Killerwatt", attacking.wikiPage);
    }

    /** A source named after its own page has nothing extra to carry. */
    @Test
    public void anUnsplitSourceCarriesNoSeparatePage()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        pages.put("Steel dragon", page("Steel dragon", "Monster",
            Collections.singletonList("139"), "246",
            table("Drops", "Drops", line(false, "Bones", "1", "Always", null))));
        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyMap());
        assertNull(out.get(0).wikiPage);
    }

    /**
     * Splitting renames sources — "Steel dragon (Level 246)" — and the metallic-dragon check
     * matched whole names, so every split metallic dragon fell back to plain dragonfire and the
     * client told the player to pray Magic against a breath that ignores prayer entirely.
     */
    @Test
    public void aSplitMetallicDragonStillBreathesTypelessFire()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        DropPageJson p = page("Steel dragon", "Monster", Arrays.asList("139", "8085"), "246",
            table("Drops", "Drops", line(false, "Bones", "1", "Always", null)));
        p.versions.add(version("Level 246", "246", "139"));
        p.versions.add(version("Level 274", "274", "8085"));
        pages.put("Steel dragon", p);

        Map<String, WikiCombatJson> wiki = Collections.singletonMap("Steel dragon",
            versioned(Arrays.asList("[[Dragonfire]]", "[[Dragonfire]]"),
                Arrays.asList("50", "60"), Arrays.asList("4", "4")));

        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), wiki, Collections.emptyMap(), Collections.emptyMap());

        assertEquals(2, out.size());
        for (OutputSource s : out)
        {
            assertEquals(s.name + " breathes typeless fire", "metallic dragonfire",
                s.combat.attacks.get(0).style);
        }
    }

    /**
     * Barbarian lists 36 versions that mostly fight identically. One source per version would
     * bury the search results, so versions that are actually the same monster stay together.
     */
    @Test
    public void versionsThatFightTheSameStayOneSource()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        DropPageJson p = page("Barbarian", "Monster", Arrays.asList("1", "2", "3"), "10",
            table("Drops", "Drops", line(false, "Bones", "1", "Always", null)));
        p.versions.add(version("Level 10 (Fafner)", "10", "1"));
        p.versions.add(version("Level 10 (Hagen)", "10", "2"));
        p.versions.add(version("Level 9 (Sieglinde)", "9", "3"));
        pages.put("Barbarian", p);

        Map<String, WikiCombatJson> wiki = Collections.singletonMap("Barbarian",
            versioned(Arrays.asList("[[Crush]]", "[[Crush]]", "[[Crush]]"),
                Arrays.asList("3", "3", "2"), Arrays.asList("4", "4", "4")));

        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), wiki, Collections.emptyMap(), Collections.emptyMap());

        assertEquals("the two identical level 10 barbarians merge", 2, out.size());
        OutputSource lvl10 = byName(out, "Barbarian (Level 10 (Fafner))");
        assertNotNull(lvl10);
        assertEquals(Arrays.asList(1, 2), lvl10.npcIds);
        assertNotNull(byName(out, "Barbarian (Level 9 (Sieglinde))"));
    }

    @Test
    public void aPageWithOneVersionKeepsItsPlainName()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        pages.put("Ogre", page("Ogre", "Monster", Arrays.asList("500"), "53",
            table("Drops", "Drops", line(false, "Bones", "1", "Always", null))));
        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyMap());
        assertEquals(1, out.size());
        assertEquals("Ogre", out.get(0).name);
    }

    private static OutputSource byName(List<OutputSource> sources, String name)
    {
        for (OutputSource s : sources)
        {
            if (name.equals(s.name))
            {
                return s;
            }
        }
        return null;
    }

    @Test
    public void killPageJoinsIdsCombatLevelAndCombat()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        pages.put("Ogre", page("Ogre", "Monster", Arrays.asList("500", "501"), "53",
            table("Drops", "Drops", line(false, "Bones", "1", "Always", null))));

        WikiCombatJson w = new WikiCombatJson();
        w.maxHit = "9";
        w.attackStyle = "[[Crush]]";
        w.attackSpeed = "5";
        Map<String, WikiCombatJson> wiki = Collections.singletonMap("Ogre", w);

        Map<Integer, List<OutputSpawn>> spawns = new HashMap<>();
        spawns.put(500, Collections.singletonList(new OutputSpawn(3200, 3200, 0)));

        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(), spawns, wiki,
            Collections.emptyMap(), Collections.emptyMap());

        assertEquals(1, out.size());
        OutputSource ogre = out.get(0);
        assertEquals("Ogre", ogre.name);
        assertEquals("KILL", ogre.type);
        assertEquals(500, ogre.id); // canonical id = first npcId
        assertEquals(Arrays.asList(500, 501), ogre.npcIds);
        assertEquals(53, ogre.combatLevel);
        assertEquals(1, ogre.spawnPoints.size());
        assertEquals(3200, ogre.spawnPoints.get(0).x);
        assertNotNull(ogre.combat);
        assertEquals(1, ogre.combat.attacks.size());
        assertEquals("crush", ogre.combat.attacks.get(0).style);
        assertEquals(9, ogre.combat.attacks.get(0).maxHit);
        assertNull(ogre.x); // NPC source: no synthetic map coords
    }

    @Test
    public void rewardChestGetsSyntheticIdAndSingleSpawnPoint()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        DropPageJson chest = page("Lunar Chest", "Scenery", null, null,
            table("Rewards", "Rewards", line(true, "Blood moon helm", "1", "1/224", null)));
        chest.mapX = 1512;
        chest.mapY = 9581;
        chest.mapPlane = 0;
        pages.put("Lunar Chest", chest);

        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

        assertEquals(1, out.size());
        OutputSource lunar = out.get(0);
        assertEquals("REWARD", lunar.type);
        assertEquals(-1000, lunar.id); // only source needing a synthetic id
        assertTrue(lunar.npcIds.isEmpty());
        assertEquals(0, lunar.combatLevel); // unparseable/absent combatLevel -> 0
        assertEquals(Integer.valueOf(1512), lunar.x);
        assertEquals(Integer.valueOf(9581), lunar.y);
        assertEquals(Integer.valueOf(0), lunar.plane);
        assertEquals(1, lunar.spawnPoints.size());
        assertEquals(1512, lunar.spawnPoints.get(0).x);
        assertEquals(9581, lunar.spawnPoints.get(0).y);
        assertNull(lunar.combat); // non-KILL -> no combat
    }

    @Test
    public void pickpocketSectionBecomesSeparateSuffixedSource()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        pages.put("Master Farmer", page("Master Farmer", "NPC", Collections.singletonList("3257"), null,
            table("Drops", "Drops", line(false, "Coins", "5", "Always", null)),
            table("Pickpocketing loot", "Pickpocketing loot",
                line(false, "Potato seed", "1-4", "1/2.2", null))));

        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

        assertEquals(2, out.size());
        OutputSource kill = out.stream().filter(s -> "KILL".equals(s.type)).findFirst().get();
        OutputSource pickpocket = out.stream().filter(s -> "PICKPOCKET".equals(s.type)).findFirst().get();
        assertEquals("Master Farmer", kill.name);
        assertEquals("Master Farmer (pickpocket)", pickpocket.name);
        assertEquals(1, kill.drops.size());
        assertEquals("Coins", kill.drops.get(0).itemName);
        assertEquals(1, pickpocket.drops.size());
        assertEquals("Potato seed", pickpocket.drops.get(0).itemName);
        assertEquals(3257, kill.id);
        assertEquals(3257, pickpocket.id); // same npcIds -> same canonical id
    }

    @Test
    public void typeOverlayForcesAllTablesToOverlaidType()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        pages.put("Weird Tree", page("Weird Tree", "Monster", Collections.singletonList("999"), "1",
            table("Drops", "Drops", line(false, "Bird nest", "1", "1/8", null))));

        Map<String, String> typeOverlay = Collections.singletonMap("Weird Tree", "GATHER");

        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), typeOverlay);

        assertEquals(1, out.size());
        assertEquals("GATHER", out.get(0).type);
        assertEquals("Weird Tree", out.get(0).name); // no pickpocket suffix
        assertNull(out.get(0).combat); // non-KILL -> no combat despite Monster infobox
    }

    @Test
    public void rarityKnownAndUnknownPathsComputeCorrectly()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        pages.put("Thing", page("Thing", "Monster", Collections.singletonList("1"), "1",
            table("Drops", "Drops",
                line(false, "Known item", "1", "1/128", null),
                line(false, "Unknown item", "1", "Common", null))));

        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

        OutputDrop known = out.get(0).drops.get(0);
        OutputDrop unknown = out.get(0).drops.get(1);
        assertTrue(known.rarityKnown);
        assertEquals(1.0 / 128, known.rarity, 1e-9);
        assertFalse(unknown.rarityKnown);
        assertEquals(0.0, unknown.rarity, 1e-9);
    }

    @Test
    public void notedQuantityDetected()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        pages.put("Thing", page("Thing", "Monster", Collections.singletonList("1"), "1",
            table("Drops", "Drops", line(false, "Flax", "1000 (noted)", "10/249", null))));

        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

        OutputDrop flax = out.get(0).drops.get(0);
        assertTrue(flax.noted);
        assertEquals(1000, flax.quantity);
    }

    @Test
    public void itemIdResolvedFromItemInfoOrFallsBackToNegativeOne()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        pages.put("Thing", page("Thing", "Monster", Collections.singletonList("1"), "1",
            table("Drops", "Drops",
                line(false, "Known item", "1", "1/2", null),
                line(false, "Mystery item", "1", "1/2", null))));
        Map<String, ItemInfoJson> itemInfo = new HashMap<>();
        itemInfo.put("Known item", item(4151));

        List<OutputSource> out = SourceTransform.transform(pages, itemInfo,
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

        assertEquals(4151, out.get(0).drops.get(0).itemId);
        assertEquals(-1, out.get(0).drops.get(1).itemId);
    }

    @Test
    public void categoryNulledForGenericHeadingsKeptForSpecificOnes()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        pages.put("Kalphite Queen", page("Kalphite Queen", "Monster", Collections.singletonList("963"), "333",
            table("Drops", "Drops", line(false, "Bones", "1", "Always", null)),
            table("Rare drop table", "Drops", line(false, "Loop half of key", "1", "1/128", null))));

        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

        assertEquals(1, out.size()); // both tables are KILL -> merged into one source
        List<OutputDrop> drops = out.get(0).drops;
        OutputDrop bones = drops.stream().filter(d -> "Bones".equals(d.itemName)).findFirst().get();
        OutputDrop key = drops.stream().filter(d -> "Loop half of key".equals(d.itemName)).findFirst().get();
        assertNull(bones.category); // generic "Drops" heading nulled
        assertEquals("Rare drop table", key.category);
    }

    @Test
    public void outputSortedByNameCaseInsensitiveRegardlessOfInputOrder()
    {
        Map<String, DropPageJson> pages = new LinkedHashMap<>();
        pages.put("Zulu Thing", page("Zulu Thing", "Monster", Collections.singletonList("2"), "1",
            table("Drops", "Drops", line(false, "X", "1", "1/2", null))));
        pages.put("alpha thing", page("alpha thing", "Monster", Collections.singletonList("1"), "1",
            table("Drops", "Drops", line(false, "Y", "1", "1/2", null))));

        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

        assertEquals("alpha thing", out.get(0).name);
        assertEquals("Zulu Thing", out.get(1).name);
    }

    @Test
    public void combatOverlayAppliesToKillSourceByCanonicalId()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        pages.put("Goblin", page("Goblin", "Monster", Collections.singletonList("100"), "2",
            table("Drops", "Drops", line(false, "Bones", "1", "Always", null))));

        CombatOverlayJson overlay = new CombatOverlayJson();
        overlay.attacks = Collections.singletonList(new com.itemsearch.datagen.output.OutputAttack("crush", 99, 5));
        overlay.needsReview = false;
        Map<Integer, CombatOverlayJson> combatOverlay = Collections.singletonMap(100, overlay);

        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), combatOverlay, Collections.emptyMap());

        assertEquals(99, out.get(0).combat.attacks.get(0).maxHit); // overlay wins
    }

    private static MonsterJson monster(int id, String name, int attack, int strength, int defence,
        int magic, int ranged, int hitpoints)
    {
        MonsterJson m = new MonsterJson();
        m.id = id;
        m.name = name;
        m.attackLevel = attack;
        m.strengthLevel = strength;
        m.defenceLevel = defence;
        m.magicLevel = magic;
        m.rangedLevel = ranged;
        m.hitpoints = hitpoints;
        return m;
    }

    /**
     * The stats panel is read to decide what to bring, which is answered by the levels rather than
     * by the attacks alone. The page-driven transform has no levels of its own — the wiki infobox
     * does not carry them — so they are joined in from the monster dump by npc id.
     */
    @Test
    public void combatLevelsComeFromTheMonsterDump()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        pages.put("Blue dragon", page("Blue dragon", "Monster", Collections.singletonList("265"), "111",
            table("Drops", "Drops", line(false, "Bones", "1", "Always", null))));

        Map<Integer, MonsterJson> monsters = Collections.singletonMap(
            265, monster(265, "Blue dragon", 95, 95, 95, 1, 1, 105));

        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyMap(), monsters);

        assertEquals(95, out.get(0).combat.attackLevel);
        assertEquals(95, out.get(0).combat.strengthLevel);
        assertEquals(95, out.get(0).combat.defenceLevel);
        assertEquals(1, out.get(0).combat.magicLevel);
        assertEquals(1, out.get(0).combat.rangedLevel);
        assertEquals(105, out.get(0).combat.hitpoints);
    }

    /**
     * A page split into versions names its own npc ids per version, so each split source must find
     * the monster its own ids point at rather than the first one on the page.
     */
    @Test
    public void combatLevelsFollowTheVersionsOwnNpcIds()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        DropPageJson p = page("Hellhound", "Monster", null, null,
            table("Drops", "Drops", line(false, "Bones", "1", "Always", null)));
        p.versions.add(version("Level 122", "122", "104"));
        p.versions.add(version("Level 127", "127", "105"));
        pages.put("Hellhound", p);

        Map<Integer, MonsterJson> monsters = new HashMap<>();
        monsters.put(104, monster(104, "Hellhound", 60, 60, 20, 1, 1, 116));
        monsters.put(105, monster(105, "Hellhound", 70, 70, 30, 1, 1, 124));

        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyMap(), monsters);

        assertEquals(2, out.size());
        OutputSource first = out.get(0).npcIds.contains(104) ? out.get(0) : out.get(1);
        OutputSource second = first == out.get(0) ? out.get(1) : out.get(0);
        assertEquals(116, first.combat.hitpoints);
        assertEquals(124, second.combat.hitpoints);
    }

    /** A source with no matching monster keeps zeros rather than failing the whole generation. */
    @Test
    public void combatLevelsAreZeroWhenTheMonsterDumpHasNoMatch()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        pages.put("Goblin", page("Goblin", "Monster", Collections.singletonList("100"), "2",
            table("Drops", "Drops", line(false, "Bones", "1", "Always", null))));

        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap());

        assertEquals(0, out.get(0).combat.attackLevel);
        assertEquals(0, out.get(0).combat.hitpoints);
    }

    /**
     * A brimstone key is only on the table while Konar assigned the task, so an off-task kill was
     * never a chance at one. The wiki says so in the rarity itself — the drop is priced with a
     * template no other kind of drop uses — which is the only machine-readable statement of it.
     */
    @Test
    public void aKonarOnlyDropIsMarkedConditional()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        pages.put("Aberrant spectre", page("Aberrant spectre", "Monster",
            Collections.singletonList("2"), "96",
            table("Tertiary", "Drops",
                line(false, "Brimstone key", "1", "{{Brimstone rarity|96|bonus=yes}}", null),
                line(false, "Bones", "1", "Always", null))));

        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap());

        OutputDrop key = out.get(0).drops.stream()
            .filter(d -> "Brimstone key".equals(d.itemName)).findFirst().orElse(null);
        OutputDrop bones = out.get(0).drops.stream()
            .filter(d -> "Bones".equals(d.itemName)).findFirst().orElse(null);

        assertNotNull(key);
        assertEquals("KONAR_TASK", key.condition);
        // an ordinary drop stays unconditional, or every counter would stop moving
        assertNotNull(bones);
        assertNull(bones.condition);
    }

    /**
     * A superior slayer monster lists the same key as a guaranteed drop, because within the task it
     * is. Off task it is not on the table at all, so the "Always" must not be taken at face value —
     * the item decides, not the rarity it happens to be written with.
     */
    @Test
    public void aGuaranteedKonarOnlyDropIsStillConditional()
    {
        Map<String, DropPageJson> pages = new TreeMap<>();
        pages.put("Choke devil", page("Choke devil", "Monster",
            Collections.singletonList("7404"), "290",
            table("100%", "Drops", line(false, "Brimstone key", "1", "Always", null))));

        List<OutputSource> out = SourceTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(),
            Collections.emptyMap(), Collections.emptyMap());

        assertEquals("KONAR_TASK", out.get(0).drops.get(0).condition);
    }
}
