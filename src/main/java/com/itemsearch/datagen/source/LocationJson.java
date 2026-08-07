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
package com.itemsearch.datagen.source;

import java.util.ArrayList;
import java.util.List;

/**
 * One {@code {{LocLine}}} block from a monster's wiki page: a named place, and the tiles the
 * monster stands on there.
 *
 * <p>The pinned spawn dump stops at npc id 11151, so everything added to the game since has no
 * tiles at all. The wiki keeps its own, and for any place it has a map for ({@code mapID >= 0})
 * those are ordinary world coordinates — verified against the spawn dump, which agrees with them
 * exactly where both have an npc.
 */
public class LocationJson
{
    /** The version of the monster these tiles belong to, as the page names it. */
    public String name;
    /** Human-readable place, markup stripped: "Brimhaven Dungeon". */
    public String location;
    /** The wiki's map id. -1 means it has no map, and then the tiles are not world coordinates. */
    public Integer mapId;
    public int plane;
    public List<Point> points = new ArrayList<>();

    public static class Point
    {
        public int x;
        public int y;

        public Point()
        {
        }

        public Point(int x, int y)
        {
            this.x = x;
            this.y = y;
        }
    }
}
