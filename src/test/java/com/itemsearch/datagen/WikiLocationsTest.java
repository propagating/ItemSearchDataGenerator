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

import java.util.List;
import com.itemsearch.datagen.source.LocationJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class WikiLocationsTest
{
    /** Steel dragons in Brimhaven Dungeon: a real map, so its coordinates are world tiles. */
    private static final String BRIMHAVEN = "{{LocLine\n"
        + "|name = Steel dragon\n"
        + "|location = [[Brimhaven Dungeon]]\n"
        + "|levels = 246\n"
        + "|mapID = 46\n"
        + "|plane = 0\n"
        + "|x:2712,y:9435\n"
        + "|x:2702,y:9447\n"
        + "|mtype = pin\n"
        + "}}";

    @Test
    public void readsTilesPlaneAndTheNameTheyBelongTo()
    {
        List<LocationJson> found = WikiLocations.parse(BRIMHAVEN);
        assertEquals(1, found.size());
        LocationJson loc = found.get(0);
        assertEquals("Steel dragon", loc.name);
        assertEquals("Brimhaven Dungeon", loc.location);
        assertEquals(Integer.valueOf(46), loc.mapId);
        assertEquals(0, loc.plane);
        assertEquals(2, loc.points.size());
        assertEquals(2712, loc.points.get(0).x);
        assertEquals(9435, loc.points.get(0).y);
        assertEquals(2702, loc.points.get(1).x);
    }

    /**
     * mapID -1 means the wiki has no map for the place, and its coordinates are not world tiles —
     * checked against the game's own transport list, which has nothing within 80 tiles of them.
     * The name of the place is still worth keeping; the numbers are not.
     */
    @Test
    public void aPlaceWithNoMapKeepsItsNameButNotItsCoordinates()
    {
        List<LocationJson> found = WikiLocations.parse("{{LocLine\n"
            + "|name = Elite Black Knight\n"
            + "|location = [[Black Knight Catacombs]]\n"
            + "|mapID = -1\n|plane = 1\n"
            + "|x:4124,y:4692\n|x:4112,y:4706\n}}");
        assertEquals(1, found.size());
        assertEquals("Black Knight Catacombs", found.get(0).location);
        assertEquals(Integer.valueOf(-1), found.get(0).mapId);
        assertTrue("coordinates off the world grid are not kept",
            found.get(0).points.isEmpty());
    }

    @Test
    public void everyBlockOnAPageIsRead()
    {
        List<LocationJson> found = WikiLocations.parse(BRIMHAVEN + "\nfiller\n" + "{{LocLine\n"
            + "|name = Steel dragon\n|location = [[Catacombs of Kourend]]\n"
            + "|mapID = 32\n|plane = 0\n|x:1607,y:10057\n}}");
        assertEquals(2, found.size());
        assertEquals("Catacombs of Kourend", found.get(1).location);
        assertEquals(1607, found.get(1).points.get(0).x);
    }

    /** A location line with a heading but no tiles has nothing to mark. */
    @Test
    public void aBlockWithoutTilesYieldsNoPoints()
    {
        List<LocationJson> found = WikiLocations.parse(
            "{{LocLine\n|name = Thing\n|location = [[Somewhere]]\n|mapID = 0\n}}");
        assertEquals(1, found.size());
        assertTrue(found.get(0).points.isEmpty());
    }

    @Test
    public void aPageWithoutLocationLinesIsEmpty()
    {
        assertTrue(WikiLocations.parse("just prose").isEmpty());
        assertTrue(WikiLocations.parse(null).isEmpty());
    }

    /** Wiki links, templates and footnotes all show up in the location field. */
    @Test
    public void theLocationNameIsStrippedOfMarkup()
    {
        List<LocationJson> found = WikiLocations.parse("{{LocLine\n"
            + "|name = X\n"
            + "|location = [[Taverley Dungeon|the dungeon]] - Upper level<ref>note</ref>\n"
            + "|mapID = 12\n|plane = 0\n|x:3000,y:9500\n}}");
        assertEquals("the dungeon - Upper level", found.get(0).location);
    }
}
