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
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Type;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.itemsearch.datagen.source.WikiCombatJson;

/** Loads the extracted wiki-combat snapshot and feeds each record through {@link CombatParser}. */
public final class WikiCombat
{
    private WikiCombat()
    {
    }

    /**
     * Derive the real wiki page title from a {@code wiki_url}. osrsbox's {@code wiki_name} appends
     * a version qualifier (e.g. "General Graardor (Normal)") that is NOT the page title; the actual
     * title lives in the URL path, with the version as the {@code #anchor}. Some page titles
     * legitimately contain parens (e.g. "Goblin (Goblin Village)"), so the title must be derived
     * from the URL path rather than by stripping parens from {@code wiki_name}.
     */
    public static String pageTitle(String wikiUrl)
    {
        if (wikiUrl == null)
        {
            return null;
        }
        int idx = wikiUrl.indexOf("/w/");
        if (idx < 0)
        {
            return null;
        }
        String path = wikiUrl.substring(idx + 3);
        int hash = path.indexOf('#');
        if (hash >= 0)
        {
            path = path.substring(0, hash);
        }
        try
        {
            path = URLDecoder.decode(path, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            // UTF-8 is always supported; unreachable
            throw new AssertionError(e);
        }
        return path.replace('_', ' ');
    }

    public static Map<String, WikiCombatJson> parse(Gson gson, String json)
    {
        if (json == null || json.trim().isEmpty())
        {
            return new HashMap<>();
        }
        Type type = new TypeToken<Map<String, WikiCombatJson>>()
        {
        }.getType();
        Map<String, WikiCombatJson> map = gson.fromJson(json, type);
        return map == null ? new HashMap<>() : map;
    }

    /**
     * Attacks for one {{Infobox Monster}} version, rather than every version folded together.
     * The numbered fields line up by index with the page's versionN list, so version i reads
     * attack style(i), max hit(i) and attack speed(i). A page numbers only the fields that
     * differ between its versions — Dessous in the Nightmare Zone numbers its max hits and not
     * its style — so each field is read from its numbered form where the page has one and from
     * the plain form where it does not. Falls back to the folded reading when no field is
     * numbered for that index.
     */
    public static CombatParser.ParsedCombat toAttacks(WikiCombatJson w, int version)
    {
        if (w == null || version < 0 || version >= versionCount(w))
        {
            return toAttacks(w);
        }
        String style = numbered(w.attackStyles, w.attackStyle, version);
        return CombatParser.assemble(
            CombatParser.parseStyles(style),
            CombatParser.parseStyleNotes(style),
            CombatParser.parseMaxHits(numbered(w.maxHits, w.maxHit, version)),
            CombatParser.parseSpeeds(numbered(w.attackSpeeds, w.attackSpeed, version)));
    }

    // how many versions the page numbers any field for
    private static int versionCount(WikiCombatJson w)
    {
        return Math.max(size(w.attackStyles), Math.max(size(w.maxHits), size(w.attackSpeeds)));
    }

    private static int size(List<String> fields)
    {
        return fields == null ? 0 : fields.size();
    }

    private static String numbered(List<String> fields, String plain, int version)
    {
        return fields != null && version < fields.size() ? fields.get(version) : plain;
    }

    /** Every version of the page folded into one attack table. */
    public static CombatParser.ParsedCombat toAttacks(WikiCombatJson w)
    {
        return toAttacks(w, (List<Integer>) null);
    }

    /**
     * The given versions of the page, by index into its numbered fields, folded into one attack
     * table; null for all of them. Versions at one combat level are one monster to the player, so
     * the source built from them fights every way any of them does.
     */
    public static CombatParser.ParsedCombat toAttacks(WikiCombatJson w, List<Integer> versions)
    {
        if (w == null)
        {
            return new CombatParser.ParsedCombat(new ArrayList<>(), false);
        }
        // particular versions, each read on its own and then folded together
        if (versions != null)
        {
            List<CombatParser.ParsedCombat> read = new ArrayList<>();
            for (int i : versions)
            {
                if (i >= 0 && i < versionCount(w))
                {
                    read.add(toAttacks(w, i));
                }
            }
            if (!read.isEmpty())
            {
                return CombatParser.fold(read);
            }
            // asked for nothing the page numbers, so the whole page is the honest answer
        }
        // numbered attack-style fields mean the page has multiple monster VERSIONS (not multiple
        // attacks of one monster): read each on its own, then fold them together. Numbered
        // speeds alone are one monster's attacks in turn (TzKal-Zuk), read below.
        if (w.attackStyles != null && !w.attackStyles.isEmpty())
        {
            List<CombatParser.ParsedCombat> read = new ArrayList<>();
            for (int i = 0; i < w.attackStyles.size(); i++)
            {
                read.add(toAttacks(w, i));
            }
            return CombatParser.fold(read);
        }
        // single-version monster: the unnumbered, comma-separated fields describe its own attacks
        List<String> speedFields = new ArrayList<>();
        speedFields.add(w.attackSpeed);
        if (w.attackSpeeds != null)
        {
            speedFields.addAll(w.attackSpeeds);
        }
        List<String> styles = CombatParser.parseStyles(w.attackStyle);
        List<String> notes = CombatParser.parseStyleNotes(w.attackStyle);
        List<CombatParser.MaxHit> maxHits = CombatParser.parseMaxHits(w.maxHit);
        List<Integer> speeds = CombatParser.parseSpeeds(speedFields.toArray(new String[0]));
        return CombatParser.assemble(styles, notes, maxHits, speeds);
    }
}
