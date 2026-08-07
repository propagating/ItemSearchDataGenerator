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
     * attack style(i), max hit(i) and attack speed(i). Falls back to the folded reading when the
     * page has no numbered field for that index.
     */
    public static CombatParser.ParsedCombat toAttacks(WikiCombatJson w, int version)
    {
        if (w == null || w.attackStyles == null || version < 0 || version >= w.attackStyles.size())
        {
            return toAttacks(w);
        }
        String maxHit = w.maxHits != null && version < w.maxHits.size()
            ? w.maxHits.get(version) : w.maxHit;
        String speed = w.attackSpeeds != null && version < w.attackSpeeds.size()
            ? w.attackSpeeds.get(version) : w.attackSpeed;
        return CombatParser.assemble(
            CombatParser.parseStyles(w.attackStyles.get(version)),
            CombatParser.parseMaxHits(maxHit),
            CombatParser.parseSpeeds(speed));
    }

    public static CombatParser.ParsedCombat toAttacks(WikiCombatJson w)
    {
        if (w == null)
        {
            return new CombatParser.ParsedCombat(new ArrayList<>(), false);
        }
        // numbered attack-style fields mean the page has multiple monster VERSIONS (not multiple
        // attacks of one monster): collapse them into one attack per distinct style, with ranges.
        if (w.attackStyles != null && !w.attackStyles.isEmpty())
        {
            return CombatParser.collapseVersions(w.attackStyles,
                w.maxHits == null ? new ArrayList<>() : w.maxHits,
                w.attackSpeeds == null ? new ArrayList<>() : w.attackSpeeds);
        }
        // single-version monster: the unnumbered, comma-separated fields describe its own attacks
        List<String> speedFields = new ArrayList<>();
        speedFields.add(w.attackSpeed);
        if (w.attackSpeeds != null)
        {
            speedFields.addAll(w.attackSpeeds);
        }
        List<String> styles = CombatParser.parseStyles(w.attackStyle);
        List<CombatParser.MaxHit> maxHits = CombatParser.parseMaxHits(w.maxHit);
        List<Integer> speeds = CombatParser.parseSpeeds(speedFields.toArray(new String[0]));
        return CombatParser.assemble(styles, maxHits, speeds);
    }
}
