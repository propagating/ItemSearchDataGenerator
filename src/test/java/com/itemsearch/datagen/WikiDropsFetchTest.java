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

import com.itemsearch.datagen.source.DropLineJson;
import com.itemsearch.datagen.source.DropPageJson;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WikiDropsFetchTest
{
    private static final String ZULRAH_LIKE =
        "{{Infobox Monster\n|name = Zulrah\n|combat = 725\n|id1 = 2042\n|id2 = 2043\n|id3 = 2044\n}}\n"
        + "==Drops==\n"
        + "{{DropsTableHead}}\n"
        + "{{DropsLine|name=Tanzanite fang|quantity=1|rarity=1/1024|rolls=2}}\n"
        + "{{DropsLine|name=Zulrah's scales|quantity=100-299<!--separate drop excluded-->|rarity=Always}}\n"
        + "{{DropsLine|name=Flax|quantity=1000 (noted)|rarity=10/249|raritynotes={{Refn|group=d|note}}}}\n"
        + "{{DropsTableBottom}}\n";

    @Test
    public void parsesMonsterPageWithNumberedIdsAndNestedTemplates()
    {
        DropPageJson page = WikiDropsFetch.extract("Zulrah", ZULRAH_LIKE);
        assertEquals("Monster", page.infobox);
        assertEquals(Arrays.asList("2042", "2043", "2044"), page.npcIds);
        assertEquals("725", page.combatLevel);
        assertEquals(1, page.tables.size());
        assertEquals("Drops", page.tables.get(0).sectionHeading);
        assertEquals("Drops", page.tables.get(0).topHeading);
        List<DropLineJson> lines = page.tables.get(0).lines;
        assertEquals(3, lines.size());
        assertEquals("2", lines.get(0).rolls);
        assertEquals("100-299", lines.get(1).quantity); // comment stripped
        assertEquals("10/249", lines.get(2).rarity);    // nested Refn did not break the split
        assertFalse(lines.get(0).reward);
        // no {{Map}} anywhere on this fixture -> all three coordinate fields stay null
        assertNull(page.mapX);
        assertNull(page.mapY);
        assertNull(page.mapPlane);
    }

    @Test
    public void mapCoordsFromInfoboxBodyIncludePlane()
    {
        String page = "{{Infobox Monster\n|name = Cave dweller\n"
            + "|map = {{Map|3100,3300|plane=1}}\n}}\n"
            + "==Drops==\n{{DropsTableHead}}\n"
            + "{{DropsLine|name=Bones|quantity=1|rarity=Always}}\n{{DropsTableBottom}}\n";
        DropPageJson result = WikiDropsFetch.extract("Cave dweller", page);
        assertEquals(Integer.valueOf(3100), result.mapX);
        assertEquals(Integer.valueOf(3300), result.mapY);
        assertEquals(Integer.valueOf(1), result.mapPlane);
    }

    @Test
    public void mapCoordsFallBackToAnyMapTemplateOnThePageWhenInfoboxHasNone()
    {
        String page = "{{Infobox NPC\n|name = Cave goblin guard\n}}\n"
            + "The cave entrance is here: {{Map|3160,9500}}\n"
            + "==Drops==\n{{DropsTableHead}}\n"
            + "{{DropsLine|name=Bones|quantity=1|rarity=Always}}\n{{DropsTableBottom}}\n";
        DropPageJson result = WikiDropsFetch.extract("Cave goblin guard", page);
        assertEquals(Integer.valueOf(3160), result.mapX);
        assertEquals(Integer.valueOf(9500), result.mapY);
        // WikiText.mapCoords defaults plane to 0 when absent; DropPageJson keeps it as a
        // boxed 0, not null, since coordinates were found
        assertEquals(Integer.valueOf(0), result.mapPlane);
    }

    @Test
    public void rewardChestPageParsesRewardLinesUnderSceneryInfobox()
    {
        String chest = "{{Infobox Scenery\n|name = Lunar Chest\n}}\n"
            + "==Rewards==\n{{DropsTableHead}}\n"
            + "{{DropsLineReward|name=Blood moon helm|quantity=1|rarity=1/224}}\n"
            + "{{DropsTableBottom}}\n";
        DropPageJson page = WikiDropsFetch.extract("Lunar Chest", chest);
        assertEquals("Scenery", page.infobox);
        assertTrue(page.npcIds.isEmpty());
        assertTrue(page.tables.get(0).lines.get(0).reward);
        assertEquals("Rewards", page.tables.get(0).sectionHeading);
    }

    @Test
    public void pickpocketSectionHeadingIsCaptured()
    {
        String farmer = "{{Infobox NPC\n|name = Master Farmer\n|id = 3257\n}}\n"
            + "==Pickpocketing loot==\n{{DropsTableHead}}\n"
            + "{{DropsLine|name=Potato seed|quantity=1-4|rarity=1/2.2}}\n{{DropsTableBottom}}\n";
        DropPageJson page = WikiDropsFetch.extract("Master Farmer", farmer);
        assertEquals("NPC", page.infobox);
        assertEquals(Collections.singletonList("3257"), page.npcIds);
        assertEquals("Pickpocketing loot", page.tables.get(0).sectionHeading);
    }

    // The real Master Farmer page: skilling loot uses {{DropsLineSkill}}, and the tables sit
    // three levels under the ==Pickpocketing== section that classifies them.
    @Test
    public void skillDropLinesAreParsedAndKeepTheirTopLevelSection()
    {
        String farmer = "{{Infobox NPC\n|name = Master Farmer\n|id = 3257\n}}\n"
            + "==Pickpocketing==\n===Loot===\n====Allotments====\n"
            + "{{DropsTableHead}}\n"
            + "{{DropsLineSkill|name=Potato seed|quantity=1-4|rarity=1/5.65|skill=Thieving}}\n"
            + "{{DropsLineSkill|name=Onion seed|quantity=1-3|rarity=1/7.53|skill=Thieving}}\n"
            + "{{DropsTableBottom}}\n";
        DropPageJson page = WikiDropsFetch.extract("Master Farmer", farmer);
        assertNotNull("a page whose only drop lines are skill lines must still parse", page);
        assertEquals("NPC", page.infobox);
        assertEquals(1, page.tables.size());
        assertEquals("Allotments", page.tables.get(0).sectionHeading);
        assertEquals("Pickpocketing", page.tables.get(0).topHeading);
        List<DropLineJson> lines = page.tables.get(0).lines;
        assertEquals(2, lines.size());
        assertEquals("Potato seed", lines.get(0).name);
        assertEquals("1/5.65", lines.get(0).rarity);
        assertFalse("skill loot is not a reward-chest line", lines.get(0).reward);
    }

    // guards the shared prefix: {{DropsLineSkill}} must never be read as {{DropsLine}}, and
    // {{DropsLineReward}} must keep its reward flag when all three names are scanned together
    @Test
    public void similarlyPrefixedTemplateNamesDoNotCollide()
    {
        String mixed = "{{Infobox Monster\n|name = Mixed\n|id = 1\n}}\n==Drops==\n"
            + "{{DropsTableHead}}\n"
            + "{{DropsLine|name=Bones|quantity=1|rarity=Always}}\n"
            + "{{DropsLineSkill|name=Herb|quantity=1|rarity=1/10|skill=Thieving}}\n"
            + "{{DropsLineReward|name=Prize|quantity=1|rarity=1/50}}\n"
            + "{{DropsTableBottom}}\n";
        List<DropLineJson> lines = WikiDropsFetch.extract("Mixed", mixed).tables.get(0).lines;
        assertEquals(3, lines.size());
        assertFalse(lines.get(0).reward);
        assertFalse(lines.get(1).reward);
        assertTrue("only DropsLineReward marks a reward line", lines.get(2).reward);
    }

    // "Nothing" is the wiki's placeholder for a roll that yields no item — it is not a drop,
    // and rendering it would tell the player an NPC drops something called "Nothing"
    @Test
    public void placeholderNothingLinesAreNotDrops()
    {
        String page = "{{Infobox Monster\n|name = Rat\n|id = 1\n}}\n==Drops==\n"
            + "{{DropsTableHead}}\n"
            + "{{DropsLine|name=Nothing|quantity=1|rarity=1/2}}\n"
            + "{{DropsLine|name=nothing|quantity=1|rarity=1/3}}\n"
            + "{{DropsLine|name=Bones|quantity=1|rarity=Always}}\n"
            + "{{DropsTableBottom}}\n";
        List<DropLineJson> lines = WikiDropsFetch.extract("Rat", page).tables.get(0).lines;
        assertEquals(1, lines.size());
        assertEquals("Bones", lines.get(0).name);
    }

    @Test
    public void aTableOfOnlyPlaceholdersIsDropped()
    {
        String page = "{{Infobox Monster\n|name = Rat\n|id = 1\n}}\n==Drops==\n"
            + "{{DropsTableHead}}\n{{DropsLine|name=Nothing|quantity=1|rarity=1/2}}\n"
            + "{{DropsTableBottom}}\n";
        assertNull("a page whose only line is a placeholder has no drop table",
            WikiDropsFetch.extract("Rat", page));
    }

    // the wiki links to a section of an item page ("Bird nest (egg)#Red egg"); the fragment is
    // a display detail, and keeping it makes the name unresolvable to any item id
    @Test
    public void anchorFragmentsAreStrippedFromItemNames()
    {
        String page = "{{Infobox Monster\n|name = Bird\n|id = 1\n}}\n==Drops==\n"
            + "{{DropsTableHead}}\n"
            + "{{DropsLine|name=Bird nest (egg)#Red egg|quantity=1|rarity=1/50}}\n"
            + "{{DropsTableBottom}}\n";
        List<DropLineJson> lines = WikiDropsFetch.extract("Bird", page).tables.get(0).lines;
        assertEquals("Bird nest (egg)", lines.get(0).name);
    }

    @Test
    public void subsectionHeadingsBecomeCategoriesWithTopLevelSectionKept()
    {
        String kalphite = "{{Infobox Monster\n|name = Kalphite Queen\n|id = 963\n}}\n"
            + "==Drops==\n===100%===\n{{DropsTableHead}}\n"
            + "{{DropsLine|name=Bones|quantity=1|rarity=Always}}\n{{DropsTableBottom}}\n"
            + "===Rare drop table===\n{{DropsTableHead}}\n"
            + "{{DropsLine|name=Loop half of key|quantity=1|rarity=1/128}}\n{{DropsTableBottom}}\n";
        DropPageJson page = WikiDropsFetch.extract("Kalphite Queen", kalphite);
        assertEquals(2, page.tables.size());
        assertEquals("100%", page.tables.get(0).sectionHeading);
        assertEquals("Drops", page.tables.get(0).topHeading);
        assertEquals("Rare drop table", page.tables.get(1).sectionHeading);
        assertEquals("Drops", page.tables.get(1).topHeading);
    }

    @Test
    public void pagesWithoutDropLinesReturnNull()
    {
        assertNull(WikiDropsFetch.extract("Prose", "Just text."));
        assertNull(WikiDropsFetch.extract("Prose", null));
    }
}
