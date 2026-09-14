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

import java.util.Map;
import com.itemsearch.datagen.source.ThievingJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WikiThievingFetchTest
{
    private static final String TABLE = "== Pickpocketing ==\nSome prose first.\n"
        + "{| class=\"wikitable sortable align-center-1\"\n"
        + "!colspan=\"2\"| Name\n"
        + "!{{SCP|Thieving}} Level\n"
        + "![[Experience|XP]]\n"
        + "!{{plinkp|Ardougne cloak 3}} 100% success lvl\n"
        + "![[Stun (status)|Stun]] damage \n"
        + "!Notes\n"
        + "|-\n"
        + "|{{plinkt|Man|pic=Thief Man}}/[[Woman]]/[[Citizen (Civitas illa Fortis)|Citizen]]\n"
        + "| 1 || 8 || 85 || 1\n"
        + "| Found all around [[Gielinor]].\n"
        + "|-\n"
        + "|{{plinkt|Master Farmer|pic=Thief Master farmer}}\n"
        + "|38 || 43 || 94 || 3\n"
        + "| Notes\n"
        + "|-\n"
        + "|{{plinkt|Guard|pic=Thief Guard}}\n"
        + "|40 || 46.8 || 95 || 2\n"
        + "|-\n"
        + "|{{chatlp|Digsite workman|pic=Digsite workman (1) chathead}} || [[Digsite workman]]\n"
        + "|25 || 10.4 || ''Unknown'' || 1\n"
        + "|Pickpocketing workmen is twice as slow.\n"
        + "|}\n"
        + "\n== Something else ==\n{| class=\"wikitable\"\n|-\n| not this\n|}\n";

    /** A row that names three NPCs gives each the row's level and experience. */
    @Test
    public void everyNameInARowGetsItsLevelAndXp()
    {
        Map<String, ThievingJson> rows = WikiThievingFetch.parse(TABLE);
        assertEquals(Integer.valueOf(1), rows.get("man").level);
        assertEquals(Double.valueOf(8), rows.get("woman").xp);
        assertEquals("a piped link's display name is a name the NPC goes by",
            Integer.valueOf(1), rows.get("citizen").level);
        assertEquals("and so is the page it links to, which is what a source is titled by",
            Integer.valueOf(1), rows.get("citizen (civitas illa fortis)").level);
        assertEquals(Integer.valueOf(38), rows.get("master farmer").level);
        assertEquals(Double.valueOf(43), rows.get("master farmer").xp);
        assertEquals(Double.valueOf(46.8), rows.get("guard").xp);
        assertEquals("a picture in a cell of its own does not push the name out of the name column",
            Integer.valueOf(25), rows.get("digsite workman").level);
        assertEquals(Double.valueOf(10.4), rows.get("digsite workman").xp);
        assertEquals("the header row and the other table are not NPCs", 7, rows.size());
    }

    @Test
    public void nothingParsesFromAPageWithoutTheTable()
    {
        assertTrue(WikiThievingFetch.parse("just prose").isEmpty());
        assertTrue(WikiThievingFetch.parse(null).isEmpty());
        assertNull(WikiThievingFetch.parse(TABLE).get("gielinor"));
    }

    // the stalls table writes one cell per line, with an Items column before Location
    private static final String STALLS = "{| class=\"wikitable sortable\"\n"
        + "! colspan=2 | Stall\n"
        + "!{{SCP|Thieving}}Level\n"
        + "!Exp.\n"
        + "!Items\n"
        + "!Location\n"
        + "!Respawn Time\n"
        + "! Max XP/Hr <!--Calculated-->\n"
        + "|-\n"
        + "|{{plinkt|Bakery stall|pic=Bread}}\n"
        + "|5\n"
        + "|16\n"
        + "|[[Cake]], [[chocolate slice]] or [[bread]]\n"
        + "|[[East Ardougne]], [[Keldagrim]], [[Great Kourend]], [[Civitas illa Fortis]]\n"
        + "|2.4 seconds (9.6 seconds in [[Keldagrim]])\n"
        + "| 24,000 (6,000 in [[Keldagrim]])\n"
        + "|-\n"
        + "|[{{plinkt|Silk stall|pic=Silk}}\n"
        + "|20\n"
        + "|24\n"
        + "|[[Silk]]\n"
        + "|[[East Ardougne]], [[Prifddinas Silk Stall|Prifddinas]]\n"
        + "|4.8 seconds\n"
        + "| 18,000\n"
        + "|-\n"
        + "|{{plinkt|Gem stall (Port Roberts)|pic=Sapphire}}\n"
        + "|75\n"
        + "|129.5\n"
        + "||[[Sapphire|Cut]] or [[uncut sapphire]]\n"
        + "|[[Port Roberts]]\n"
        + "|2.4 seconds\n"
        + "|194,250<ref name=\"marketplace-thieving\"/>\n"
        + "|}\n\n{{Reflist}}\n";

    /** A stall's row says what it takes, and where the stall stands. */
    @Test
    public void aStallRowCarriesItsLevelXpPageAndPlaces()
    {
        Map<String, ThievingJson> rows = WikiThievingFetch.parseStalls(STALLS);
        ThievingJson bakery = rows.get("bakery stall");
        assertEquals(Integer.valueOf(5), bakery.level);
        assertEquals(Double.valueOf(16), bakery.xp);
        assertEquals("Bakery stall", bakery.page);
        assertEquals(java.util.Arrays.asList("East Ardougne", "Keldagrim", "Great Kourend", "Civitas illa Fortis"),
            bakery.locations);
        assertEquals("a stray bracket before the template is not part of the name",
            Integer.valueOf(20), rows.get("silk stall").level);
        assertEquals("a place shown under a piped link is the place shown, not the shop linked",
            java.util.Arrays.asList("East Ardougne", "Prifddinas"), rows.get("silk stall").locations);
        assertEquals("an empty cell does not shift the columns after it",
            java.util.Collections.singletonList("Port Roberts"), rows.get("gem stall (port roberts)").locations);
        assertEquals(Double.valueOf(129.5), rows.get("gem stall (port roberts)").xp);
        assertEquals("the items are not places or names", 3, rows.size());
    }

    // the chests table writes a row to a line, and its places are the fourth column
    private static final String CHESTS = "== Chests ==\n===Thievable chests===\n''Loot is on their pages.''\n"
        + "{| class=\"wikitable sortable align-center-1\"\n"
        + "! colspan=2 |Chest!!{{SCP|Thieving}} Level!![[Experience]]!!Location(s)!!Respawn Time\n"
        + "|-\n"
        + "|{{plinkt|Chest (10 coins)|pic=Coins|txt=10 coin chest}}||13||7.8||[[East Ardougne]], [[Rellekka]]||3.5 seconds\n"
        + "|-\n"
        + "|{{plinkt|Chest (nature runes)|pic=Nature rune}}{{Refn|Disabled in [[Deadman Mode|seasonal]].|group=t}}||28||25||[[East Ardougne]]||8 seconds\n"
        + "|}\n";

    @Test
    public void theThievingPageGivesPickpocketsAndChestsTogether()
    {
        Map<String, ThievingJson> rows = WikiThievingFetch.parse(TABLE + CHESTS);
        assertEquals(Integer.valueOf(38), rows.get("master farmer").level);
        assertNull("a pickpocket row has no places", rows.get("master farmer").locations);
        ThievingJson coins = rows.get("chest (10 coins)");
        assertEquals(Integer.valueOf(13), coins.level);
        assertEquals(Double.valueOf(7.8), coins.xp);
        assertEquals(java.util.Arrays.asList("East Ardougne", "Rellekka"), coins.locations);
        assertEquals("a footnote's link is not a name", Integer.valueOf(28), rows.get("chest (nature runes)").level);
        assertNull(rows.get("seasonal"));
        assertNull(rows.get("deadman mode"));
    }
}
