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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.itemsearch.datagen.source.ThievingJson;

/**
 * The wiki's tables of what can be robbed and what it takes: the Pickpocketing and Thievable
 * chests tables of the Thieving page, and the stalls table it transcludes from Stall/Thievable.
 *
 * <p>The three tables are laid out differently — one row per line, or one cell per line, with
 * or without a Location column — so each is read by its header: whichever column says Level is
 * the level, whichever says Experience is the experience, and whichever says Location, if any,
 * is where the thing stands. The first column names what is robbed.
 */
public final class WikiThievingFetch
{
    // {{plinkt|Master Farmer|pic=...}} — the first argument is the page name
    private static final Pattern PLINKT = Pattern.compile("\\{\\{plinkt?\\|([^|}]+)");
    // [[Woman]] or [[Citizen (Civitas illa Fortis)|Citizen]] — the display name when piped
    private static final Pattern LINK = Pattern.compile("\\[\\[([^\\]|]+)(?:\\|([^\\]]+))?\\]\\]");
    private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");
    // a footnote or a reference, which says something about the row rather than naming it
    private static final Pattern ASIDE = Pattern.compile(
        "\\{\\{Refn\\|.*?\\}\\}|<ref[^>]*/>|<ref[^>]*>.*?</ref>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    // a table cell's attributes: the "colspan=2 |" before the text, never the pipe of a template
    private static final Pattern CELL_ATTRIBUTES = Pattern.compile("^[^|\\[{]*\\|");

    private WikiThievingFetch()
    {
    }

    public static Map<String, ThievingJson> fetch(Gson gson) throws IOException
    {
        Map<String, ThievingJson> out = new LinkedHashMap<>();
        out.putAll(parse(wikitext(gson, "Thieving")));
        for (Map.Entry<String, ThievingJson> row : parseStalls(wikitext(gson, "Stall/Thievable")).entrySet())
        {
            out.putIfAbsent(row.getKey(), row.getValue());
        }
        return out;
    }

    private static String wikitext(Gson gson, String page) throws IOException
    {
        JsonObject response = WikiHttp.request(gson, WikiHttp.API
            + "?action=parse&page=" + page + "&prop=wikitext&format=json");
        JsonObject parse = response.getAsJsonObject("parse");
        if (parse == null || !parse.has("wikitext"))
        {
            throw new IOException("Unexpected wiki API response: " + WikiHttp.snippet(response));
        }
        return parse.getAsJsonObject("wikitext").get("*").getAsString();
    }

    /**
     * The Thieving page's tables: the Pickpocketing table, and the Thievable chests one. Names
     * are lower-cased; a row naming several NPCs is filed under each.
     */
    public static Map<String, ThievingJson> parse(String wikitext)
    {
        Map<String, ThievingJson> out = new LinkedHashMap<>();
        if (wikitext == null)
        {
            return out;
        }
        out.putAll(parseTable(tableAfter(wikitext, "Pickpocketing")));
        for (Map.Entry<String, ThievingJson> row : parseTable(tableAfter(wikitext, "Thievable chests")).entrySet())
        {
            out.putIfAbsent(row.getKey(), row.getValue());
        }
        return out;
    }

    /** The stalls table, from the page the Thieving page transcludes it from. */
    public static Map<String, ThievingJson> parseStalls(String wikitext)
    {
        return wikitext == null ? new LinkedHashMap<>() : parseTable(tableAfter(wikitext, null));
    }

    /** The first wikitable after a heading's text, or null when there is none. */
    private static String tableAfter(String wikitext, String heading)
    {
        int section = heading == null ? 0 : wikitext.indexOf(heading);
        if (heading != null && section < 0)
        {
            return null;
        }
        int start = wikitext.indexOf("{|", section);
        int end = start < 0 ? -1 : wikitext.indexOf("\n|}", start);
        return start < 0 || end < 0 ? null : wikitext.substring(start, end);
    }

    /** One wikitable, read by its header row. */
    static Map<String, ThievingJson> parseTable(String table)
    {
        Map<String, ThievingJson> out = new LinkedHashMap<>();
        if (table == null)
        {
            return out;
        }
        String[] rows = table.split("\\n\\|-");
        List<String> headers = headers(rows[0]);
        int level = indexOf(headers, "level");
        int xp = indexOf(headers, "experience", "xp", "exp");
        int location = indexOf(headers, "location");
        if (level < 0 || xp < 0)
        {
            return out;
        }
        for (int r = 1; r < rows.length; r++)
        {
            List<String> cells = cells(rows[r]);
            // a picture and a link written as two cells are one name column: "{{chatlp|Digsite
            // workman}} || [[Digsite workman]]" spans the two columns {{plinkt}} fills by itself
            while (cells.size() > 1 && namesIn(cells.get(0)).isEmpty() && firstNumber(cells.get(1)) == null)
            {
                cells.set(0, cells.get(0) + " " + cells.remove(1));
            }
            if (cells.size() <= Math.max(level, xp))
            {
                continue;
            }
            Integer lvl = firstNumber(cells.get(level));
            if (lvl == null)
            {
                continue;
            }
            Double exp = firstDecimal(cells.get(xp));
            List<String> names = namesIn(cells.get(0));
            if (names.isEmpty())
            {
                continue;
            }
            List<String> places = location >= 0 && location < cells.size()
                ? placesIn(cells.get(location)) : null;
            ThievingJson row = new ThievingJson(lvl, exp, names.get(0), places);
            for (String name : names)
            {
                out.putIfAbsent(name.toLowerCase(Locale.ROOT), row);
            }
        }
        return out;
    }

    /** The header cells' text, lower-cased, in column order. */
    private static List<String> headers(String headerRow)
    {
        List<String> out = new ArrayList<>();
        for (String line : headerRow.split("\\n"))
        {
            line = line.trim();
            if (!line.startsWith("!"))
            {
                continue;
            }
            for (String cell : line.substring(1).split("!!"))
            {
                out.add(CELL_ATTRIBUTES.matcher(cell.trim()).replaceFirst("").trim().toLowerCase(Locale.ROOT));
            }
        }
        return out;
    }

    private static int indexOf(List<String> headers, String... words)
    {
        for (String word : words)
        {
            for (int i = 0; i < headers.size(); i++)
            {
                if (headers.get(i).contains(word))
                {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * A row's cells, whether the table writes them one per line or several to a line joined by
     * "||". Empty cells are dropped: the odd row that opens a cell with "||" would otherwise
     * shift every column after it.
     */
    private static List<String> cells(String row)
    {
        List<String> out = new ArrayList<>();
        for (String line : ASIDE.matcher(row).replaceAll("").split("\\n"))
        {
            line = line.trim();
            if (!line.startsWith("|") || line.startsWith("|}"))
            {
                continue;
            }
            for (String cell : line.substring(1).split("\\|\\|"))
            {
                // a stray "[" before a template is a typo the wiki renders away
                String text = cell.trim().replaceFirst("^\\[(?=\\{\\{)", "");
                if (!text.isEmpty())
                {
                    out.add(text);
                }
            }
        }
        return out;
    }

    private static Integer firstNumber(String cell)
    {
        Matcher m = NUMBER.matcher(cell);
        return m.find() ? (Integer) (int) Double.parseDouble(m.group()) : null;
    }

    private static Double firstDecimal(String cell)
    {
        Matcher m = NUMBER.matcher(cell.replace(",", ""));
        return m.find() ? Double.parseDouble(m.group()) : null;
    }

    private static List<String> namesIn(String cell)
    {
        List<String> names = new ArrayList<>();
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

    /**
     * The places a Location cell links to, as shown: "[[Prifddinas Silver Stall|Prifddinas]]"
     * is Prifddinas, the link's target being the shop there rather than the place.
     */
    private static List<String> placesIn(String cell)
    {
        List<String> places = new ArrayList<>();
        Matcher l = LINK.matcher(cell);
        while (l.find())
        {
            places.add((l.group(2) != null ? l.group(2) : l.group(1)).trim());
        }
        return places;
    }
}
