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
import com.itemsearch.datagen.source.DropTableJson;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import java.util.Map;
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

    /**
     * A monster's herb/seed/rare drops live in a shared template the page transcludes, not in the
     * page's own wikitext. Without expanding them Aberrant spectre ships 10 drops and no herbs.
     */
    @Test
    public void transcludedDropTablesAreExpandedAndAttributed()
    {
        Map<String, String> templates = new HashMap<>();
        templates.put("HerbDropLines",
            "{{DropsLine|name=Grimy guam leaf|quantity=1|rarity=1/25}}\n"
            + "{{DropsLine|name=Grimy ranarr weed|quantity=1|rarity=1/128}}\n");

        String page = "{{Infobox Monster\n|name = Aberrant spectre\n|id = 2}}\n"
            + "==Drops==\n===Herbs===\n{{HerbDropLines}}\n"
            + "===Other===\n{{DropsTableHead}}\n"
            + "{{DropsLine|name=Coins|quantity=100|rarity=1/2}}\n{{DropsTableBottom}}\n";

        DropPageJson p = WikiDropsFetch.extract("Aberrant spectre", page, templates);
        List<String> all = new ArrayList<>();
        for (DropTableJson t : p.tables)
        {
            for (DropLineJson l : t.lines)
            {
                all.add(l.name);
            }
        }
        assertTrue("herbs from the shared table must be present", all.contains("Grimy ranarr weed"));
        assertTrue("the page's own drops must survive", all.contains("Coins"));

        DropTableJson herbs = tableContaining(p, "Grimy ranarr weed");
        assertEquals("Herb drop table", herbs.dropTable);
        assertEquals("the heading introducing the transclusion still applies",
            "Herbs", herbs.sectionHeading);
        assertNull("a page's own table belongs to no shared table",
            tableContaining(p, "Coins").dropTable);
    }

    // Revenant pages transclude {{Revenants}}, which transcludes {{Revenants/Drops}}; one level
    // of expansion is not enough to reach the lines.
    @Test
    public void nestedTransclusionsAreFollowed()
    {
        Map<String, String> templates = new HashMap<>();
        templates.put("Revenants", "Some prose.\n{{Revenants/Drops}}\n");
        templates.put("Revenants/Drops",
            "{{DropsLine|name=Revenant cave teleport|quantity=1|rarity=1/50}}\n");

        String page = "{{Infobox Monster\n|name = Revenant goblin\n|id = 7931}}\n"
            + "==Drops==\n{{Revenants}}\n";
        DropPageJson p = WikiDropsFetch.extract("Revenant goblin", page, templates);
        assertNotNull("the page must now yield a drop table", p);
        assertEquals("Revenant cave teleport", p.tables.get(0).lines.get(0).name);
    }

    /**
     * A big shared table is itself sectioned — the revenant template carries ==Drops== plus
     * ===100%===, ===Unique===, ===Ancient statuettes=== and more. Flattening it into one table
     * filed under whatever heading preceded the transclusion loses all of that.
     */
    @Test
    public void aSharedTableKeepsItsOwnInternalSections()
    {
        Map<String, String> templates = new HashMap<>();
        templates.put("Revenants/Drops",
            "==Drops==\n===100%===\n{{DropsTableHead}}\n"
            + "{{DropsLine|name=Revenant ether|quantity=1-10|rarity=Always}}\n"
            + "{{DropsTableBottom}}\n"
            + "===Ancient statuettes===\n{{DropsTableHead}}\n"
            + "{{DropsLine|name=Ancient emblem|quantity=1|rarity=1/1000}}\n"
            + "{{DropsTableBottom}}\n");

        String page = "{{Infobox Monster\n|name = Revenant hobgoblin\n|id = 7934}}\n"
            + "==Prayer info==\nsome prose\n{{Revenants/Drops}}\n";
        DropPageJson p = WikiDropsFetch.extract("Revenant hobgoblin", page, templates);

        assertEquals("each section of the shared table is its own table", 2, p.tables.size());
        assertEquals("100%", p.tables.get(0).sectionHeading);
        assertEquals("Revenant ether", p.tables.get(0).lines.get(0).name);
        assertEquals("Ancient statuettes", p.tables.get(1).sectionHeading);
        for (DropTableJson t : p.tables)
        {
            assertEquals("Revenant drop table", t.dropTable);
        }
    }

    @Test
    public void aHeadingIntroducingATransclusionIsInherited()
    {
        Map<String, String> templates = new HashMap<>();
        templates.put("HerbDropLines",
            "{{DropsLine|name=Grimy guam leaf|quantity=1|rarity=1/25}}\n");
        // the heading sits directly above the transclusion, so it names it
        String page = "{{Infobox Monster\n|name = Spectre\n|id = 2}}\n"
            + "==Drops==\n===Herbs===\n{{HerbDropLines}}\n";
        DropPageJson p = WikiDropsFetch.extract("Spectre", page, templates);
        assertEquals("Herbs", p.tables.get(0).sectionHeading);
        assertEquals("Herb drop table", p.tables.get(0).dropTable);
    }

    /**
     * The two fields answer different questions and must not collapse into one: the section is
     * where the table sits on the page, the drop table is what it is. Revenant pages carry the
     * wilderness slayer table under a "Prayer info" heading — odd, but that is where it is, and
     * the client's grouping setting is what lets the player pick which answer they want.
     */
    @Test
    public void sectionAndDropTableStayDistinct()
    {
        Map<String, String> templates = new HashMap<>();
        templates.put("WildernessSlayerDropTable",
            "{{DropsLine|name=Larran's key|quantity=1|rarity=1/100}}\n");
        String page = "{{Infobox Monster\n|name = Revenant hobgoblin\n|id = 7934}}\n"
            + "==Prayer info==\nsome prose about prayer\n\n{{WildernessSlayerDropTable}}\n";
        DropPageJson p = WikiDropsFetch.extract("Revenant hobgoblin", page, templates);
        assertEquals("Prayer info", p.tables.get(0).sectionHeading);
        assertEquals("Wilderness slayer drop table", p.tables.get(0).dropTable);
    }

    @Test
    public void aTemplateCycleTerminates()
    {
        Map<String, String> templates = new HashMap<>();
        templates.put("A", "{{B}}");
        templates.put("B", "{{A}}\n{{DropsLine|name=Bones|quantity=1|rarity=Always}}\n");
        DropPageJson p = WikiDropsFetch.extract("Loopy",
            "{{Infobox Monster\n|name = Loopy\n|id = 3}}\n==Drops==\n{{A}}\n", templates);
        assertEquals("Bones", p.tables.get(0).lines.get(0).name);
    }

    private static DropTableJson tableContaining(DropPageJson page, String itemName)
    {
        for (DropTableJson t : page.tables)
        {
            for (DropLineJson l : t.lines)
            {
                if (itemName.equals(l.name))
                {
                    return t;
                }
            }
        }
        return null;
    }

    /**
     * A monster page can describe several versions, each its own npc id with its own combat.
     * Killerwatt is Idle (470, stab) and Attacking (469, crush); folding them together loses the
     * crush entirely.
     */
    @Test
    public void infoboxVersionsAreCaptured()
    {
        String page = "{{Infobox Monster\n|name = Killerwatt\n"
            + "|version1 = Idle\n|version2 = Attacking\n"
            + "|id1 = 470\n|id2 = 469\n"
            + "|combat1 = 55\n|combat2 = 55\n}}\n"
            + "==Drops==\n{{DropsTableHead}}\n"
            + "{{DropsLine|name=Nothing interesting|quantity=1|rarity=Always}}\n"
            + "{{DropsTableBottom}}\n";
        DropPageJson p = WikiDropsFetch.extract("Killerwatt", page);

        assertEquals(2, p.versions.size());
        assertEquals("Idle", p.versions.get(0).label);
        assertEquals(Collections.singletonList("470"), p.versions.get(0).npcIds);
        assertEquals("55", p.versions.get(0).combatLevel);
        assertEquals("Attacking", p.versions.get(1).label);
        assertEquals(Collections.singletonList("469"), p.versions.get(1).npcIds);
        // the flat id list still carries every id, as the rest of the pipeline expects
        assertEquals(Arrays.asList("470", "469"), p.npcIds);
    }

    // one version can hold several ids: Dagannoth writes "id1 = 970,971,972"
    @Test
    public void aVersionCanHoldSeveralIds()
    {
        String page = "{{Infobox Monster\n|name = Dagannoth\n"
            + "|version1 = Level 74\n|version2 = Level 92\n"
            + "|id1 = 970,971,972\n|id2 = 973\n"
            + "|combat1 = 74\n|combat2 = 92\n}}\n"
            + "==Drops==\n{{DropsTableHead}}\n"
            + "{{DropsLine|name=Bones|quantity=1|rarity=Always}}\n{{DropsTableBottom}}\n";
        DropPageJson p = WikiDropsFetch.extract("Dagannoth", page);
        assertEquals(Arrays.asList("970", "971", "972"), p.versions.get(0).npcIds);
        assertEquals("74", p.versions.get(0).combatLevel);
        assertEquals(Collections.singletonList("973"), p.versions.get(1).npcIds);
    }

    @Test
    public void aSingleVersionPageRecordsNoVersions()
    {
        DropPageJson p = WikiDropsFetch.extract("Zulrah", ZULRAH_LIKE);
        assertTrue("nothing to split when the page describes one monster", p.versions.isEmpty());
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
