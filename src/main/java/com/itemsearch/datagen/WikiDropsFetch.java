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
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.itemsearch.datagen.source.DropLineJson;
import com.itemsearch.datagen.source.DropPageJson;
import com.itemsearch.datagen.source.DropTableJson;

/**
 * Pure wikitext parsing of {{DropsLine}}/{{DropsLineReward}} drop tables (and their
 * {{Infobox ...}} header) from an OSRS Wiki page. No network I/O lives here.
 */
public final class WikiDropsFetch
{
    // captures the word immediately after "{{Infobox " (e.g. "Monster", "NPC", "Scenery");
    // the brace-balanced body itself is extracted separately since it may nest templates
    private static final Pattern INFOBOX_NAME = Pattern.compile("\\{\\{Infobox\\s+([A-Za-z]+)");
    // "==heading==" / "===heading===" / ... (2-6 '=' fences, matching on both sides)
    private static final Pattern HEADING = Pattern.compile("(?m)^(={2,6})\\s*(.*?)\\s*\\1\\s*$");
    private static final String DROPS_TABLE_HEAD = "{{DropsTableHead";

    private WikiDropsFetch()
    {
    }

    public static DropPageJson extract(String pageTitle, String wikitext)
    {
        if (wikitext == null)
        {
            return null;
        }
        String text = WikiText.stripComments(wikitext);

        DropPageJson page = new DropPageJson();
        page.pageTitle = pageTitle;

        String infoboxBody = null;
        Matcher infobox = INFOBOX_NAME.matcher(text);
        if (infobox.find())
        {
            page.infobox = infobox.group(1);
            infoboxBody = braceBalancedBody(text, infobox.start());
        }

        if (infoboxBody != null)
        {
            addIdTokens(page.npcIds, WikiText.field(infoboxBody, "id"));
            for (String v : WikiText.numberedFields(infoboxBody, "id"))
            {
                addIdTokens(page.npcIds, v);
            }
            page.combatLevel = WikiText.field(infoboxBody, "combat");
        }

        int[] coords = infoboxBody != null ? WikiText.mapCoords(infoboxBody) : null;
        if (coords == null)
        {
            coords = WikiText.mapCoords(text);
        }
        if (coords != null)
        {
            page.mapX = coords[0];
            page.mapY = coords[1];
            page.mapPlane = coords[2];
        }

        List<HeadingMatch> headings = findHeadings(text);
        List<Integer> headOffsets = findHeadOffsets(text);
        List<WikiText.Found> lineFounds = WikiText.templates(text, "DropsLine", "DropsLineReward");

        // group lines by how many {{DropsTableHead occurrences preceded them: a head between
        // two lines starts a new table; lines before any head form their own (leading) table
        Map<Integer, List<WikiText.Found>> grouped = new TreeMap<>();
        for (WikiText.Found found : lineFounds)
        {
            int tableIndex = 0;
            for (int headOffset : headOffsets)
            {
                if (headOffset < found.start)
                {
                    tableIndex++;
                }
                else
                {
                    break;
                }
            }
            grouped.computeIfAbsent(tableIndex, k -> new ArrayList<>()).add(found);
        }

        for (List<WikiText.Found> group : grouped.values())
        {
            DropTableJson table = new DropTableJson();
            for (WikiText.Found found : group)
            {
                Map<String, String> p = WikiText.params(found.body);
                String name = WikiText.stripLinks(p.get("name"));
                if (name == null || name.isEmpty())
                {
                    continue; // a drop line without an item name is unusable
                }
                DropLineJson line = new DropLineJson();
                line.reward = found.name.equals("DropsLineReward");
                line.name = name;
                line.quantity = p.get("quantity");
                line.rarity = p.get("rarity");
                line.rolls = p.get("rolls");
                table.lines.add(line);
            }
            if (table.lines.isEmpty())
            {
                continue;
            }
            int firstLineStart = group.get(0).start;
            table.sectionHeading = headingBefore(headings, firstLineStart, false);
            table.topHeading = headingBefore(headings, firstLineStart, true);
            page.tables.add(table);
        }

        return page.tables.isEmpty() ? null : page;
    }

    // returns the raw content between the "{{" at `start` and its brace-balanced matching "}}"
    private static String braceBalancedBody(String text, int start)
    {
        int n = text.length();
        int depth = 0;
        int i = start;
        int bodyStart = start + 2;
        while (i < n)
        {
            if (text.startsWith("{{", i))
            {
                depth++;
                i += 2;
            }
            else if (text.startsWith("}}", i))
            {
                depth--;
                i += 2;
                if (depth == 0)
                {
                    return text.substring(bodyStart, i - 2);
                }
            }
            else
            {
                i++;
            }
        }
        return text.substring(bodyStart); // unterminated; take the rest of the page
    }

    // splits a raw id/idN field value on commas, trims, drops empties, and dedups (preserving order)
    private static void addIdTokens(List<String> ids, String raw)
    {
        if (raw == null)
        {
            return;
        }
        for (String token : raw.split(","))
        {
            String t = token.trim();
            if (!t.isEmpty() && !ids.contains(t))
            {
                ids.add(t);
            }
        }
    }

    private static final class HeadingMatch
    {
        final int start;
        final int level;
        final String text;

        HeadingMatch(int start, int level, String text)
        {
            this.start = start;
            this.level = level;
            this.text = text;
        }
    }

    private static List<HeadingMatch> findHeadings(String text)
    {
        List<HeadingMatch> out = new ArrayList<>();
        Matcher m = HEADING.matcher(text);
        while (m.find())
        {
            out.add(new HeadingMatch(m.start(), m.group(1).length(), m.group(2)));
        }
        return out;
    }

    private static List<Integer> findHeadOffsets(String text)
    {
        List<Integer> out = new ArrayList<>();
        int idx = 0;
        while ((idx = text.indexOf(DROPS_TABLE_HEAD, idx)) >= 0)
        {
            out.add(idx);
            idx += DROPS_TABLE_HEAD.length();
        }
        return out;
    }

    // last heading before `offset` (topOnly restricts to exactly-level-2 "==heading=="); null if none
    private static String headingBefore(List<HeadingMatch> headings, int offset, boolean topOnly)
    {
        String result = null;
        for (HeadingMatch h : headings)
        {
            if (h.start >= offset)
            {
                break;
            }
            if (!topOnly || h.level == 2)
            {
                result = WikiText.stripLinks(h.text);
            }
        }
        return result;
    }
}
