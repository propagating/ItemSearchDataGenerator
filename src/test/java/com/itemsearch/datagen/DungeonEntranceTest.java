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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import com.itemsearch.datagen.output.OutputSpawn;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class DungeonEntranceTest
{
    /**
     * One transport: a rope on the surface at (2760, 3061) leading down into a cave at
     * (2712, 9435). That is Brimhaven Dungeon, where the steel dragons live.
     */
    private static final String ROPE = "["
        + "{\"start\":{\"p\":0,\"x\":2760,\"y\":3061},"
        + " \"destinations\":[{\"p\":0,\"x\":2712,\"y\":9433}],"
        + " \"menuOption\":\"Climb\",\"menuTarget\":\"Rope\"}]";

    private static DungeonEntrance index(String json)
    {
        return DungeonEntrance.parse(new Gson(), json);
    }

    @Test
    public void anUndergroundSpawnResolvesToTheSurfaceEntrance()
    {
        DungeonEntrance entrances = index(ROPE);
        OutputSpawn found = entrances.forSpawns(
            Collections.singletonList(new OutputSpawn(2712, 9435, 0)));
        assertEquals(2760, found.x);
        assertEquals(3061, found.y);
        assertEquals(0, found.plane);
    }

    /** A monster you can walk to needs no entrance; the marker on its own tile is the answer. */
    @Test
    public void aSurfaceSpawnHasNoEntrance()
    {
        assertNull(index(ROPE).forSpawns(
            Collections.singletonList(new OutputSpawn(3200, 3200, 0))));
    }

    @Test
    public void noSpawnsMeansNoEntrance()
    {
        assertNull(index(ROPE).forSpawns(Collections.<OutputSpawn>emptyList()));
        assertNull(index(ROPE).forSpawns(null));
    }

    /**
     * Instanced content — raids, the Nightmare Zone — is entered by means the transport list does
     * not describe, so there is nothing to point at rather than something wrong to point at.
     */
    @Test
    public void anUnreachableSpawnResolvesToNothing()
    {
        assertNull(index(ROPE).forSpawns(
            Collections.singletonList(new OutputSpawn(1200, 12000, 0))));
    }

    /**
     * The first hop out of a dungeon is usually another part of the same dungeon. Following the
     * nearest transport once lands underground again, so the walk keeps going until it surfaces.
     */
    @Test
    public void theWalkContinuesUntilItReachesTheSurface()
    {
        String twoHops = "["
            + "{\"start\":{\"p\":0,\"x\":3000,\"y\":9500},"
            + " \"destinations\":[{\"p\":0,\"x\":3000,\"y\":9700}],"
            + " \"menuOption\":\"Open\",\"menuTarget\":\"Gate\"},"
            + "{\"start\":{\"p\":0,\"x\":3010,\"y\":3300},"
            + " \"destinations\":[{\"p\":0,\"x\":3000,\"y\":9502}],"
            + " \"menuOption\":\"Climb-down\",\"menuTarget\":\"Staircase\"}]";
        OutputSpawn found = index(twoHops).forSpawns(
            Collections.singletonList(new OutputSpawn(3000, 9700, 0)));
        assertEquals("the staircase, not the interior gate", 3010, found.x);
        assertEquals(3300, found.y);
    }

    /** A monster spawning both above and below ground is already findable; leave it alone. */
    @Test
    public void aSourceWithAnySurfaceSpawnNeedsNoEntrance()
    {
        List<OutputSpawn> mixed = Arrays.asList(
            new OutputSpawn(2712, 9435, 0), new OutputSpawn(3200, 3200, 0));
        assertNull(index(ROPE).forSpawns(mixed));
    }

    @Test
    public void malformedDataYieldsAnEmptyIndexRatherThanAnException()
    {
        assertNull(index("{garbage").forSpawns(
            Collections.singletonList(new OutputSpawn(2712, 9435, 0))));
        assertNull(index(null).forSpawns(
            Collections.singletonList(new OutputSpawn(2712, 9435, 0))));
    }
}
