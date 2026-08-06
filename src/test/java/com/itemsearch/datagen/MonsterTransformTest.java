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
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.itemsearch.datagen.output.OutputDrop;
import com.itemsearch.datagen.output.OutputSource;
import com.itemsearch.datagen.output.OutputSpawn;
import com.itemsearch.datagen.source.DropJson;
import com.itemsearch.datagen.source.MonsterJson;
import com.itemsearch.datagen.source.WikiCombatJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MonsterTransformTest
{
    private Map<String, MonsterJson> sample()
    {
        Type type = new TypeToken<Map<String, MonsterJson>>()
        {
        }.getType();
        return new Gson().fromJson(new InputStreamReader(
            getClass().getResourceAsStream("/monsters-sample.json"), StandardCharsets.UTF_8), type);
    }

    private DropJson drop(int id, String name, double rarity)
    {
        DropJson d = new DropJson();
        d.id = id;
        d.name = name;
        d.quantity = "1";
        d.noted = false;
        d.rarity = rarity;
        d.rolls = 1;
        return d;
    }

    private MonsterJson monster(int id, String name, int level, DropJson... drops)
    {
        MonsterJson m = new MonsterJson();
        m.id = id;
        m.name = name;
        m.combatLevel = level;
        m.size = 1;
        m.drops = new ArrayList<>(Arrays.asList(drops));
        return m;
    }

    @Test
    public void collapsesSameNameAndLevelVariants()
    {
        Map<String, MonsterJson> src = new LinkedHashMap<>();
        src.put("a", monster(500, "Goblin", 2, drop(526, "Bones", 1.0)));
        src.put("b", monster(499, "Goblin", 2, drop(526, "Bones", 1.0), drop(995, "Coins", 0.5)));
        src.put("c", monster(600, "Goblin", 5, drop(526, "Bones", 1.0)));

        List<OutputSource> out = MonsterTransform.transformAll(src, Collections.emptyMap());
        assertEquals(2, out.size()); // lvl-2 variants collapse; lvl-5 stays separate

        OutputSource lvl2 = out.stream().filter(n -> n.combatLevel == 2).findFirst().get();
        assertEquals(499, lvl2.id); // representative = the variant with the most drops
        assertEquals(Arrays.asList(499, 500), lvl2.npcIds); // every underlying id, sorted
        assertEquals(2, lvl2.drops.size());

        OutputSource lvl5 = out.stream().filter(n -> n.combatLevel == 5).findFirst().get();
        assertEquals(Collections.singletonList(600), lvl5.npcIds);
    }

    @Test
    public void collapsesNameCasingVariants()
    {
        Map<String, MonsterJson> src = new LinkedHashMap<>();
        src.put("a", monster(648, "Ice Troll", 124, drop(526, "Bones", 1.0)));
        src.put("b", monster(699, "Ice troll", 124, drop(526, "Bones", 1.0)));

        List<OutputSource> out = MonsterTransform.transformAll(src, Collections.emptyMap());
        assertEquals(1, out.size()); // same monster, differing source casing
        assertEquals(Arrays.asList(648, 699), out.get(0).npcIds);
    }

    @Test
    public void mergedEntryUnionsOverlaySpawnsAcrossMemberIds()
    {
        Map<String, MonsterJson> src = new LinkedHashMap<>();
        src.put("a", monster(500, "Goblin", 2, drop(526, "Bones", 1.0)));
        src.put("b", monster(499, "Goblin", 2, drop(526, "Bones", 1.0)));

        Map<Integer, List<OutputSpawn>> overlay = new HashMap<>();
        overlay.put(500, Collections.singletonList(new OutputSpawn(1, 2, 0))); // overlay on the non-canonical id

        List<OutputSource> out = MonsterTransform.transformAll(src, overlay);
        assertEquals(1, out.size());
        assertEquals(1, out.get(0).spawnPoints.size()); // spawn attaches to the merged entry
    }

    @Test
    public void cacheSpawnsAreDedupedAndCapped()
    {
        Map<String, MonsterJson> src = new LinkedHashMap<>();
        src.put("a", monster(200, "Beast", 50, drop(1, "Thing", 0.5)));

        List<OutputSpawn> many = new ArrayList<>();
        for (int i = 0; i < 30; i++)
        {
            many.add(new OutputSpawn(i, 0, 0));
        }
        many.add(new OutputSpawn(0, 0, 0)); // exact duplicate of the first
        Map<Integer, List<OutputSpawn>> cache = new HashMap<>();
        cache.put(200, many);

        List<OutputSource> out = MonsterTransform.transformAll(src, Collections.emptyMap(), cache);
        assertEquals(25, out.get(0).spawnPoints.size()); // 30 distinct, deduped, capped at 25
    }

    @Test
    public void singleMonsterHasItselfAsOnlyNpcId()
    {
        OutputSource npc = MonsterTransform.transformMonster(
            monster(42, "Lonely", 3, drop(1, "Thing", 0.25)), Collections.emptyMap());
        assertEquals(Collections.singletonList(42), npc.npcIds);
    }

    @Test
    public void quantityTakesMaxInteger()
    {
        assertEquals(1, MonsterTransform.parseQuantityMax("1"));
        assertEquals(35, MonsterTransform.parseQuantityMax("5-35"));
        assertEquals(20, MonsterTransform.parseQuantityMax("10,20"));
        assertEquals(11, MonsterTransform.parseQuantityMax("1-3,5,7-9,11"));
        assertEquals(1, MonsterTransform.parseQuantityMax("(noted)"));
        assertEquals(1, MonsterTransform.parseQuantityMax(null));
    }

    @Test
    public void effectiveRarityCompoundsRolls()
    {
        assertEquals(0.5, MonsterTransform.effectiveRarity(0.5, 1), 1e-9);
        assertEquals(0.271, MonsterTransform.effectiveRarity(0.1, 3), 1e-9);
        assertEquals(0.1, MonsterTransform.effectiveRarity(0.1, 0), 1e-9); // rolls<1 treated as 1
        assertEquals(0.0, MonsterTransform.effectiveRarity(null, 1), 1e-9);
    }

    @Test
    public void nullRarityMarkedUnknown()
    {
        OutputSource multi = MonsterTransform.transformMonster(sample().get("101"), java.util.Collections.emptyMap());
        // drops sorted rarity desc: Rolled thrice (0.271) then Varies (0.0)
        OutputDrop varies = multi.drops.get(1);
        assertEquals(5000, varies.itemId);
        assertFalse(varies.rarityKnown);
        assertEquals(0.0, varies.rarity, 1e-9);
        OutputDrop rolled = multi.drops.get(0);
        assertTrue(rolled.rarityKnown);
        assertTrue(rolled.noted);
        assertEquals(20, rolled.quantity);
        assertEquals(0.271, rolled.rarity, 1e-9);
    }

    @Test
    public void filtersZeroDropMonstersAndSortsById()
    {
        List<OutputSource> npcs = MonsterTransform.transformAll(sample(), java.util.Collections.emptyMap());
        assertEquals(2, npcs.size()); // 102 (no drops) dropped
        assertEquals(100, npcs.get(0).id);
        assertEquals(101, npcs.get(1).id);
    }

    @Test
    public void dropsSortedByRarityDescThenItemId()
    {
        OutputSource goblin = MonsterTransform.transformMonster(sample().get("100"), java.util.Collections.emptyMap());
        assertEquals(526, goblin.drops.get(0).itemId);  // 1.0
        assertEquals(995, goblin.drops.get(1).itemId);  // 0.5, qty 35
        assertEquals(1205, goblin.drops.get(2).itemId); // 0.0234
        assertEquals(35, goblin.drops.get(1).quantity);
    }

    @Test
    public void overlayMergesSpawnsById()
    {
        Gson gson = new Gson();
        Map<Integer, List<OutputSpawn>> overlay =
            MonsterTransform.parseOverlay(gson, "{ \"100\": [ { \"x\": 3246, \"y\": 3235, \"plane\": 0 } ] }");
        List<OutputSource> npcs = MonsterTransform.transformAll(sample(), overlay);
        assertEquals(1, npcs.get(0).spawnPoints.size()); // id 100
        assertEquals(3246, npcs.get(0).spawnPoints.get(0).x);
        assertTrue(npcs.get(1).spawnPoints.isEmpty());   // id 101, no overlay
    }

    @Test
    public void parseOverlayToleratesEmptyAndBadKeys()
    {
        Gson gson = new Gson();
        assertTrue(MonsterTransform.parseOverlay(gson, null).isEmpty());
        assertTrue(MonsterTransform.parseOverlay(gson, "").isEmpty());
        assertTrue(MonsterTransform.parseOverlay(gson, "{ \"notanumber\": [] }").isEmpty());
    }

    @Test
    public void combatLevelsCopiedAndAttacksFromWiki()
    {
        MonsterJson m = monster(700, "Ogre", 53, drop(526, "Bones", 1.0));
        m.wikiUrl = "https://oldschool.runescape.wiki/w/Ogre";
        m.attackLevel = 20;
        m.strengthLevel = 22;
        m.defenceLevel = 18;
        m.magicLevel = 1;
        m.rangedLevel = 1;
        m.hitpoints = 60;

        WikiCombatJson w = new WikiCombatJson();
        w.maxHit = "9";
        w.attackStyle = "[[Crush]]";
        w.attackSpeed = "5";
        Map<String, WikiCombatJson> wiki = Collections.singletonMap("Ogre", w);

        OutputSource npc = MonsterTransform.transformMonster(m, Collections.emptyMap(), wiki);
        assertEquals(20, npc.combat.attackLevel);
        assertEquals(60, npc.combat.hitpoints);
        assertEquals(1, npc.combat.attacks.size());
        assertEquals("crush", npc.combat.attacks.get(0).style);
        assertEquals(9, npc.combat.attacks.get(0).maxHit);
        assertEquals(5, npc.combat.attacks.get(0).speed);
    }

    @Test
    public void metallicDragonsGetTypelessDragonfire()
    {
        WikiCombatJson w = new WikiCombatJson();
        w.maxHit = "50";
        w.attackStyle = "[[Slash]], [[Dragonfire]]";
        w.attackSpeed = "4";

        MonsterJson steel = monster(139, "Steel dragon", 246, drop(1, "X", 0.5));
        steel.wikiUrl = "https://oldschool.runescape.wiki/w/Steel_dragon";
        MonsterJson green = monster(260, "Green dragon", 88, drop(1, "X", 0.5));
        green.wikiUrl = "https://oldschool.runescape.wiki/w/Green_dragon";
        Map<String, WikiCombatJson> wiki = new HashMap<>();
        wiki.put("Steel dragon", w);
        wiki.put("Green dragon", w);

        OutputSource steelNpc = MonsterTransform.transformMonster(steel, Collections.emptyMap(), wiki);
        assertTrue(steelNpc.combat.attacks.stream().anyMatch(a -> "metallic dragonfire".equals(a.style)));
        assertFalse(steelNpc.combat.attacks.stream().anyMatch(a -> "dragonfire".equals(a.style)));

        // a chromatic dragon keeps plain dragonfire
        OutputSource greenNpc = MonsterTransform.transformMonster(green, Collections.emptyMap(), wiki);
        assertTrue(greenNpc.combat.attacks.stream().anyMatch(a -> "dragonfire".equals(a.style)));
    }

    @Test
    public void duplicateItemRowsMergeKeepingRarityAndMaxQuantity()
    {
        // same item, two quantities, same rarity (a split range) -> one row, rarity unchanged, max qty
        DropJson c1 = drop(2011, "Curry", 0.0078125);
        DropJson c2 = drop(2011, "Curry", 0.0078125);
        c2.quantity = "2";
        OutputSource npc = MonsterTransform.transformMonster(
            monster(500, "Dragon", 246, c1, c2), Collections.emptyMap());

        assertEquals(1L, npc.drops.stream().filter(o -> o.itemId == 2011).count());
        OutputDrop curry = npc.drops.stream().filter(o -> o.itemId == 2011).findFirst().get();
        assertEquals(0.0078125, curry.rarity, 1e-9); // union over distinct {1/128} == 1/128
        assertEquals(2, curry.quantity);
    }

    @Test
    public void sameItemAtDifferentRaritiesStacksByUnion()
    {
        DropJson a = drop(995, "Coins", 0.5);
        DropJson b = drop(995, "Coins", 0.25);
        b.quantity = "20";
        OutputSource npc = MonsterTransform.transformMonster(
            monster(501, "Thing", 5, a, b), Collections.emptyMap());

        OutputDrop coins = npc.drops.stream().filter(o -> o.itemId == 995).findFirst().get();
        assertEquals(1.0 - (1.0 - 0.5) * (1.0 - 0.25), coins.rarity, 1e-9); // 0.625
        assertEquals(20, coins.quantity);
    }

    @Test
    public void missingWikiEntryYieldsEmptyAttacks()
    {
        OutputSource npc = MonsterTransform.transformMonster(
            monster(701, "Thing", 1, drop(1, "X", 0.5)), Collections.emptyMap(), Collections.emptyMap());
        assertTrue(npc.combat.attacks.isEmpty()); // no wiki record -> no attack data
    }

    @Test
    public void mergedEntryUsesRepresentativeWikiName()
    {
        MonsterJson a = monster(500, "Goblin", 2, drop(526, "Bones", 1.0));
        a.wikiUrl = "https://oldschool.runescape.wiki/w/Goblin";
        MonsterJson b = monster(499, "Goblin", 2, drop(526, "Bones", 1.0), drop(995, "Coins", 0.5));
        b.wikiUrl = "https://oldschool.runescape.wiki/w/Goblin"; // b has more drops -> representative
        Map<String, MonsterJson> src = new LinkedHashMap<>();
        src.put("a", a);
        src.put("b", b);

        WikiCombatJson w = new WikiCombatJson();
        w.maxHit = "7";
        w.attackStyle = "[[Crush]]";
        w.attackSpeed = "5";

        List<OutputSource> out = MonsterTransform.transformAll(src, Collections.emptyMap(),
            Collections.emptyMap(), Collections.singletonMap("Goblin", w));
        assertEquals(1, out.size());
        assertEquals(7, out.get(0).combat.attacks.get(0).maxHit);
    }
}
