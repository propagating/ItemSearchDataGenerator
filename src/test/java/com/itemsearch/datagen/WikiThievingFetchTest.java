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
        assertEquals("the header row and the other table are not NPCs", 6, rows.size());
    }

    @Test
    public void nothingParsesFromAPageWithoutTheTable()
    {
        assertTrue(WikiThievingFetch.parse("just prose").isEmpty());
        assertTrue(WikiThievingFetch.parse(null).isEmpty());
        assertNull(WikiThievingFetch.parse(TABLE).get("gielinor"));
    }
}
