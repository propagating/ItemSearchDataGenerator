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
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import com.itemsearch.datagen.output.OutputSource;
import com.itemsearch.datagen.source.CombatOverlayJson;

/**
 * Which wiki pages describe something a player can no longer fight, or can only fight in another
 * game mode, and the dropping of their sources.
 *
 * <p>The wiki's categories are the reliable marker. Templates and infobox fields vary from page to
 * page and season to season, but a removed page always lands in "Discontinued content", a Deadman
 * page in a category starting with "Deadman" (the seasons have names: Apocalypse, Armageddon,
 * Annihilation), and a Leagues page in one ending in " League".
 */
public final class WikiRetired
{
    public static final String DISCONTINUED = "discontinued";
    public static final String DEADMAN = "deadman";
    public static final String LEAGUES = "leagues";

    // when categories are missing the title suffix still says which mode a page belongs to
    private static final Pattern DEADMAN_TITLE = Pattern.compile("\\((Deadman|Deadman Mode)\\)$");
    private static final Pattern LEAGUES_TITLE = Pattern.compile("\\((Echo|Leagues)\\)$");

    private WikiRetired()
    {
    }

    /** The reason a page is retired, or null when it is live content. */
    public static String reasonFor(String title, Collection<String> categories)
    {
        String reason = null;
        for (String c : categories)
        {
            if (c.equals("Discontinued content"))
            {
                return DISCONTINUED; // gone outranks every mode it was once part of
            }
            if (c.startsWith("Deadman"))
            {
                reason = DEADMAN;
            }
            else if (c.endsWith(" League") && reason == null)
            {
                reason = LEAGUES;
            }
        }
        if (reason != null)
        {
            return reason;
        }
        if (DEADMAN_TITLE.matcher(title).find())
        {
            return DEADMAN;
        }
        if (LEAGUES_TITLE.matcher(title).find())
        {
            return LEAGUES;
        }
        return null;
    }

    /**
     * The wiki's verdicts with the hand-kept overrides on top: a title mapped to a reason is
     * forced out, a title mapped to {@code false} is kept in whatever the wiki says.
     */
    public static Map<String, String> applyOverlay(Map<String, String> wiki, Gson gson, String overlayJson)
    {
        Map<String, String> out = new TreeMap<>(wiki);
        if (overlayJson == null || overlayJson.trim().isEmpty())
        {
            return out;
        }
        JsonObject overlay = gson.fromJson(overlayJson, JsonObject.class);
        for (Map.Entry<String, JsonElement> e : overlay.entrySet())
        {
            JsonElement v = e.getValue();
            if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isBoolean() && !v.getAsBoolean())
            {
                out.remove(e.getKey());
            }
            else if (v.isJsonPrimitive() && v.getAsJsonPrimitive().isString())
            {
                out.put(e.getKey(), v.getAsString());
            }
        }
        return out;
    }

    /** What {@link #filter} kept, and what it dropped and why. */
    public static final class Filtered
    {
        public final List<OutputSource> kept = new ArrayList<>();
        public final Map<String, Integer> droppedByReason = new TreeMap<>();
        public final Set<Integer> droppedIds = new HashSet<>();
    }

    public static Filtered filter(List<OutputSource> sources, Map<String, String> retired)
    {
        Filtered f = new Filtered();
        for (OutputSource s : sources)
        {
            String reason = retired.get(pageOf(s));
            if (reason == null)
            {
                f.kept.add(s);
                continue;
            }
            f.droppedIds.add(s.id);
            f.droppedByReason.merge(reason, 1, Integer::sum);
        }
        return f;
    }

    /**
     * The overlay without the untouched stubs of dropped sources, so the curator's queue does not
     * ask for the max hit of a monster nobody can fight. Entries somebody curated stay: the work
     * is not thrown away because a season ended, and they show as stale rather than pending.
     */
    public static Map<Integer, CombatOverlayJson> pruneStubs(Map<Integer, CombatOverlayJson> overlay,
        Set<Integer> droppedIds)
    {
        Map<Integer, CombatOverlayJson> out = new LinkedHashMap<>();
        for (Map.Entry<Integer, CombatOverlayJson> e : overlay.entrySet())
        {
            if (droppedIds.contains(e.getKey()) && e.getValue().needsReview)
            {
                continue;
            }
            out.put(e.getKey(), e.getValue());
        }
        return out;
    }

    // a version split names itself after the monster, not the page; the page is what is retired
    private static String pageOf(OutputSource s)
    {
        return s.wikiPage != null ? s.wikiPage : s.name;
    }
}
