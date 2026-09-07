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

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.itemsearch.datagen.source.WikiPageTextJson;

/**
 * Pulls out of a monster page the three things a curator reads on the wiki before writing an
 * overlay entry, so the curator can show them instead of sending them to a browser.
 */
public final class WikiPageText
{
    private static final String INFOBOX_OPEN = "{{Infobox Monster";
    // "== Title ==" on its own line; group 1 is the run of '=' that sets the level
    private static final Pattern HEADING = Pattern.compile("(?m)^(={2,6})\\s*(.+?)\\s*\\1\\s*$");
    // the wiki has no one heading for "how it fights": bosses get a fight overview, slayer
    // monsters strengths and weaknesses or prayer info, and a few just tips
    private static final List<String> MECHANICS = Arrays.asList("mechanics", "attacks", "abilities",
        "fight overview", "strengths and weaknesses", "prayer info", "tips");
    private static final List<String> STRATEGY = Arrays.asList("strategy", "strategies");

    private WikiPageText()
    {
    }

    /** The infobox and the mechanics/strategy sections, or null when the page has none of them. */
    public static WikiPageTextJson extract(String wikitext)
    {
        if (wikitext == null)
        {
            return null;
        }
        WikiPageTextJson t = new WikiPageTextJson();
        t.infobox = infobox(wikitext);
        t.lead = lead(wikitext, t.infobox);
        t.mechanics = sections(wikitext, MECHANICS);
        t.strategy = sections(wikitext, STRATEGY);
        return t.infobox == null && t.lead == null && t.mechanics == null && t.strategy == null ? null : t;
    }

    /**
     * Bosses keep their strategy on a {@code Name/Strategies} subpage rather than a section, so
     * the whole subpage stands in when the page itself said nothing.
     */
    public static void addStrategySubpage(WikiPageTextJson t, String subpageWikitext)
    {
        if (t.strategy == null && subpageWikitext != null && !subpageWikitext.trim().isEmpty())
        {
            t.strategy = subpageWikitext.trim();
        }
    }

    // the infobox template with its braces balanced, since a field can hold a template of its own
    private static String infobox(String wikitext)
    {
        int start = wikitext.indexOf(INFOBOX_OPEN);
        if (start < 0)
        {
            return null;
        }
        int depth = 0;
        for (int i = start; i + 1 < wikitext.length(); i++)
        {
            if (wikitext.startsWith("{{", i))
            {
                depth++;
                i++;
            }
            else if (wikitext.startsWith("}}", i))
            {
                depth--;
                if (depth == 0)
                {
                    return wikitext.substring(start, i + 2);
                }
                i++;
            }
        }
        return null; // never closed
    }

    // what comes after the infobox and before the first heading
    private static String lead(String wikitext, String infobox)
    {
        int start = infobox == null ? 0 : wikitext.indexOf(infobox) + infobox.length();
        Matcher first = HEADING.matcher(wikitext);
        int end = first.find(start) ? first.start() : wikitext.length();
        String body = wikitext.substring(start, end).trim();
        return body.isEmpty() ? null : body;
    }

    // every section whose heading has one of the names, each with its heading line so a reader
    // knows which it is, up to the next heading at the same level or higher; deeper subsections
    // stay part of the body
    private static String sections(String wikitext, List<String> names)
    {
        StringBuilder out = new StringBuilder();
        Matcher m = HEADING.matcher(wikitext);
        while (m.find())
        {
            if (!names.contains(m.group(2).toLowerCase(Locale.ROOT)))
            {
                continue;
            }
            int level = m.group(1).length();
            int bodyEnd = wikitext.length();
            Matcher next = HEADING.matcher(wikitext);
            next.region(m.end(), wikitext.length());
            while (next.find())
            {
                if (next.group(1).length() <= level)
                {
                    bodyEnd = next.start();
                    break;
                }
            }
            String body = wikitext.substring(m.end(), bodyEnd).trim();
            if (body.isEmpty())
            {
                continue;
            }
            if (out.length() > 0)
            {
                out.append("\n\n");
            }
            out.append(m.group().trim()).append('\n').append(body);
        }
        return out.length() == 0 ? null : out.toString();
    }
}
