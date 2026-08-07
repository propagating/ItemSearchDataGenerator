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
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.itemsearch.datagen.source.LocationJson;

/**
 * Reads {@code {{LocLine}}} blocks — where a monster is found — out of a wiki page.
 *
 * <p>The pinned spawn dump stops at npc id 11151, leaving 1595 sources with no tiles at all. The
 * wiki records its own, as a place plus a run of {@code |x:NNNN,y:NNNN} pairs.
 *
 * <p>A block's {@code mapID} decides whether the numbers mean anything to the game. Where the wiki
 * has a map for the place the tiles are ordinary world coordinates — Killerwatt's agree with the
 * spawn dump exactly, and Brimhaven's sit in a dense neighbourhood of the game's own transports.
 * Where it does not ({@code mapID = -1}, as for the Black Knight Catacombs) they are coordinates in
 * a drawing, with no transport within eighty tiles of them, so only the name of the place is kept.
 */
public final class WikiLocations
{
    private static final Pattern LOC_LINE =
        Pattern.compile("\\{\\{LocLine(.*?)\\}\\}", Pattern.DOTALL);
    private static final Pattern TILE =
        Pattern.compile("\\|\\s*x:\\s*(\\d+)\\s*,\\s*y:\\s*(\\d+)");
    private static final Pattern FIELD_NAME = field("name");
    private static final Pattern FIELD_LOCATION = field("location");
    private static final Pattern FIELD_MAP_ID = field("mapID");
    private static final Pattern FIELD_PLANE = field("plane");

    // to end of line, not to the next "|": LocLine puts one parameter per line, and a value can
    // legitimately contain a pipe — "[[Taverley Dungeon|the dungeon]]" is a link, not two fields
    private static Pattern field(String name)
    {
        return Pattern.compile("\\|\\s*" + name + "\\s*=\\s*([^\\n]*)", Pattern.CASE_INSENSITIVE);
    }

    private WikiLocations()
    {
    }

    public static List<LocationJson> parse(String wikitext)
    {
        List<LocationJson> out = new ArrayList<>();
        if (wikitext == null)
        {
            return out;
        }
        Matcher blocks = LOC_LINE.matcher(wikitext);
        while (blocks.find())
        {
            String body = blocks.group(1);
            LocationJson loc = new LocationJson();
            loc.name = clean(first(FIELD_NAME, body));
            loc.location = clean(first(FIELD_LOCATION, body));
            loc.mapId = intOrNull(first(FIELD_MAP_ID, body));
            Integer plane = intOrNull(first(FIELD_PLANE, body));
            loc.plane = plane == null ? 0 : plane;
            // a place the wiki has no map for is drawn rather than surveyed; its numbers are not
            // world tiles, so the name is all that survives
            if (loc.mapId != null && loc.mapId < 0)
            {
                out.add(loc);
                continue;
            }
            Matcher tiles = TILE.matcher(body);
            while (tiles.find())
            {
                loc.points.add(new LocationJson.Point(
                    Integer.parseInt(tiles.group(1)), Integer.parseInt(tiles.group(2))));
            }
            out.add(loc);
        }
        return out;
    }

    private static String first(Pattern pattern, String body)
    {
        Matcher m = pattern.matcher(body);
        return m.find() ? m.group(1) : null;
    }

    /** Wiki markup out of a place name: "[[Taverley Dungeon|the dungeon]] - Upper<ref>x</ref>". */
    private static String clean(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        String text = raw.replaceAll("<ref[^>]*>.*?</ref>", "")
            .replaceAll("<ref[^>]*/>", "")
            .replaceAll("\\[\\[[^\\]|]*\\|", "")   // piped link keeps the label
            .replace("[[", "")
            .replace("]]", "")
            .replaceAll("\\{\\{[^}]*\\}\\}", "")
            .replaceAll("'''?", "")
            .trim();
        return text.isEmpty() ? null : text;
    }

    private static Integer intOrNull(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        try
        {
            return Integer.valueOf(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }
}
