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
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.itemsearch.datagen.source.ThievingJson;

/**
 * The wiki's one table of everyone who can be pickpocketed, with the Thieving level it takes and
 * the experience a success gives. It lives in the Pickpocketing section of the Thieving page; a
 * row's name cell may name several NPCs ("Man/Woman/Citizen"), and each gets the row's numbers.
 */
public final class WikiThievingFetch
{
    // {{plinkt|Master Farmer|pic=...}} — the first argument is the page name
    private static final Pattern PLINKT = Pattern.compile("\\{\\{plinkt?\\|([^|}]+)");
    // [[Woman]] or [[Citizen (Civitas illa Fortis)|Citizen]] — the display name when piped
    private static final Pattern LINK = Pattern.compile("\\[\\[([^\\]|]+)(?:\\|([^\\]]+))?\\]\\]");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

    private WikiThievingFetch()
    {
    }

    public static Map<String, ThievingJson> fetch(Gson gson) throws IOException
    {
        JsonObject response = WikiHttp.request(gson, WikiHttp.API
            + "?action=parse&page=Thieving&prop=wikitext&format=json");
        JsonObject parse = response.getAsJsonObject("parse");
        if (parse == null || !parse.has("wikitext"))
        {
            throw new IOException("Unexpected wiki API response: " + WikiHttp.snippet(response));
        }
        return parse(parse.getAsJsonObject("wikitext").get("*").getAsString());
    }

    /** NPC name (as displayed, lower-cased) to its row; empty when the table is not found. */
    public static Map<String, ThievingJson> parse(String wikitext)
    {
        Map<String, ThievingJson> out = new LinkedHashMap<>();
        if (wikitext == null)
        {
            return out;
        }
        int section = wikitext.indexOf("Pickpocketing");
        int start = wikitext.indexOf("{|", section < 0 ? 0 : section);
        int end = start < 0 ? -1 : wikitext.indexOf("\n|}", start);
        if (start < 0 || end < 0)
        {
            return out;
        }
        for (String row : wikitext.substring(start, end).split("\\n\\|-"))
        {
            // the first cell names the NPCs; the next holds "level || xp || cloak || stun"
            String[] cells = row.trim().split("\\n");
            if (cells.length < 2 || !cells[0].startsWith("|") || cells[0].startsWith("|}"))
            {
                continue;
            }
            Matcher numbers = NUMBER.matcher(cells[1]);
            if (!numbers.find())
            {
                continue;
            }
            Integer level = (int) Double.parseDouble(numbers.group());
            Double xp = numbers.find() ? Double.parseDouble(numbers.group()) : null;
            for (String name : namesIn(cells[0]))
            {
                out.putIfAbsent(name.toLowerCase(Locale.ROOT), new ThievingJson(level, xp));
            }
        }
        return out;
    }

    private static java.util.List<String> namesIn(String cell)
    {
        java.util.List<String> names = new java.util.ArrayList<>();
        Matcher t = PLINKT.matcher(cell);
        while (t.find())
        {
            names.add(t.group(1).trim());
        }
        Matcher l = LINK.matcher(cell);
        while (l.find())
        {
            // both the page linked to and the name shown: a source is titled by its page
            // ("Citizen (Civitas illa Fortis)") while the table shows the short name
            names.add(l.group(1).trim());
            if (l.group(2) != null)
            {
                names.add(l.group(2).trim());
            }
        }
        return names;
    }
}
