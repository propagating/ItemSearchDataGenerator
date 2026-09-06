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
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.itemsearch.datagen.output.OutputSpawn;
import com.itemsearch.datagen.source.TransportJson;

/**
 * Where on the surface you go in, for a monster that lives underground.
 *
 * <p>Underground areas use ordinary world coordinates, just at a y the surface map does not draw —
 * so a marker on a steel dragon's tile is correct and completely useless until you are already in
 * Brimhaven Dungeon. What the player wants from the world map is the rope.
 *
 * <p>Nothing states dungeon entrances directly: the wiki has only prose ("Below the Black Knights'
 * Fortress") and a picture. They are recovered instead from the transport list — every ladder,
 * staircase, rope and rift, as a start tile and the tiles it lands on. Walking that graph backwards
 * from a spawn until it surfaces gives the way in. Taking the single nearest transport is not
 * enough: the first hop out of a dungeon is usually another part of the same dungeon.
 */
public final class DungeonEntrance
{
    /**
     * Underground areas are laid out above y=6400 in the same coordinate space as the surface.
     * The world map draws neither them nor the region beyond it.
     */
    public static final int UNDERGROUND_Y = 6400;

    /** How far from a spawn a transport can land and still be its way out. */
    private static final int SEARCH_RADIUS = 64;

    /** Beyond this many hops it is not an entrance so much as a route; give up instead. */
    private static final int MAX_HOPS = 8;

    /** Frontier width, so a badly-connected region cannot turn the walk into a map-wide sweep. */
    private static final int MAX_FRONTIER = 400;

    private static final int CELL = 64;

    /** Destination tiles bucketed by 64x64 cell, each remembering where it can be reached from. */
    private final Map<Long, List<int[]>> byCell = new HashMap<>();

    private DungeonEntrance()
    {
    }

    public static DungeonEntrance parse(Gson gson, String json)
    {
        DungeonEntrance index = new DungeonEntrance();
        if (json == null || json.trim().isEmpty())
        {
            return index;
        }
        List<TransportJson> raw;
        try
        {
            Type type = new TypeToken<List<TransportJson>>()
            {
            }.getType();
            raw = gson.fromJson(json, type);
        }
        catch (Exception e)
        {
            return index; // a transport list we cannot read costs entrance markers, nothing else
        }
        if (raw == null)
        {
            return index;
        }
        for (TransportJson t : raw)
        {
            if (t == null || t.start == null || t.destinations == null)
            {
                continue;
            }
            for (TransportJson.Point dest : t.destinations)
            {
                if (dest == null)
                {
                    continue;
                }
                index.add(dest, t.start);
            }
        }
        return index;
    }

    private void add(TransportJson.Point dest, TransportJson.Point start)
    {
        byCell.computeIfAbsent(cell(dest.p, dest.x, dest.y), k -> new ArrayList<>())
            .add(new int[]{dest.x, dest.y, dest.p, start.x, start.y, start.p});
    }

    private static long cell(int plane, int x, int y)
    {
        return ((long) plane << 40) | ((long) (x / CELL) << 20) | (y / CELL);
    }

    /**
     * The surface entrance for a set of spawn tiles, or null when there is nothing useful to point
     * at — the monster is already on the surface, or it lives somewhere the transport list does not
     * describe (raids and other instances).
     */
    public OutputSpawn forSpawns(List<OutputSpawn> spawns)
    {
        if (spawns == null || spawns.isEmpty())
        {
            return null;
        }
        for (OutputSpawn s : spawns)
        {
            if (isSurface(s.y))
            {
                return null; // already findable; the spawn markers are the answer
            }
        }
        // Every spawn gets a try, not just the first. A blue dragon lives in five dungeons, and the
        // tile the dump lists first is in one nothing connects to — walking only from that one left
        // Taverley, where most people actually go, with no way in marked.
        for (OutputSpawn from : spawns)
        {
            OutputSpawn found = walkFrom(from);
            if (found != null)
            {
                return found;
            }
        }
        return null;
    }

    /** The nearest surface exit reachable from one spawn tile, or null when none is. */
    private OutputSpawn walkFrom(OutputSpawn from)
    {
        Set<Long> seen = new HashSet<>();
        List<int[]> frontier = new ArrayList<>();
        frontier.add(new int[]{from.x, from.y, from.plane});
        for (int hop = 0; hop < MAX_HOPS && !frontier.isEmpty(); hop++)
        {
            List<int[]> next = new ArrayList<>();
            // exits found at this depth, judged together: iteration order decided it before, and a
            // cave mouth on the far side of the map beat the ladder directly overhead
            OutputSpawn best = null;
            long bestDistance = Long.MAX_VALUE;
            for (int[] point : frontier)
            {
                for (int[] link : near(point[2], point[0], point[1]))
                {
                    int sx = link[3];
                    int sy = link[4];
                    int sp = link[5];
                    if (!seen.add(cell(sp, sx, sy) * 31 + sx * 7L + sy))
                    {
                        continue;
                    }
                    if (isSurface(sy))
                    {
                        long distance = distanceToProjection(from, sx, sy);
                        if (distance < bestDistance)
                        {
                            bestDistance = distance;
                            best = new OutputSpawn(sx, sy, sp);
                        }
                        continue;
                    }
                    if (next.size() < MAX_FRONTIER)
                    {
                        next.add(new int[]{sx, sy, sp});
                    }
                }
            }
            if (best != null)
            {
                return best;
            }
            frontier = next;
        }
        return null;
    }

    /**
     * How far a surface tile is from the ground directly above a spawn.
     *
     * <p>An underground region is laid out exactly {@link #UNDERGROUND_Y} north of the surface it
     * sits beneath, so subtracting that offset projects a spawn onto the tile it lives under. The
     * way in is the exit nearest that projection; anything much further is a different route into
     * the same cave system.
     */
    private static long distanceToProjection(OutputSpawn from, int x, int y)
    {
        long dx = (long) x - from.x;
        long dy = (long) y - (from.y - UNDERGROUND_Y);
        return dx * dx + dy * dy;
    }

    /**
     * The surface is a band of the coordinate space rather than everything above the underground
     * line. Transports also start in places that are neither — instance templates and cave
     * interiors sit at coordinates far below the overworld — and pointing the player at one of
     * those is worse than admitting there is no way in.
     */
    private static final int SURFACE_MIN_Y = 2400;

    private static boolean isSurface(int y)
    {
        return y >= SURFACE_MIN_Y && y < UNDERGROUND_Y;
    }

    // every transport landing within SEARCH_RADIUS of the given tile, on the same plane
    private List<int[]> near(int plane, int x, int y)
    {
        List<int[]> out = new ArrayList<>();
        for (int cx = (x - SEARCH_RADIUS) / CELL; cx <= (x + SEARCH_RADIUS) / CELL; cx++)
        {
            for (int cy = (y - SEARCH_RADIUS) / CELL; cy <= (y + SEARCH_RADIUS) / CELL; cy++)
            {
                List<int[]> bucket = byCell.get(((long) plane << 40) | ((long) cx << 20) | cy);
                if (bucket == null)
                {
                    continue;
                }
                for (int[] link : bucket)
                {
                    if (Math.abs(link[0] - x) <= SEARCH_RADIUS && Math.abs(link[1] - y) <= SEARCH_RADIUS)
                    {
                        out.add(link);
                    }
                }
            }
        }
        return out;
    }
}
