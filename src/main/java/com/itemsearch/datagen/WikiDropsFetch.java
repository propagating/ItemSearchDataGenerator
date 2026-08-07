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
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.itemsearch.datagen.source.DropLineJson;
import com.itemsearch.datagen.source.DropPageJson;
import com.itemsearch.datagen.source.DropTableJson;

/**
 * Fetches and parses {{DropsLine}}/{{DropsLineReward}} drop tables (and their
 * {{Infobox ...}} header) from the live OSRS Wiki.
 */
public final class WikiDropsFetch
{
    // captures the word immediately after "{{Infobox " (e.g. "Monster", "NPC", "Scenery");
    // the brace-balanced body itself is extracted separately since it may nest templates
    private static final Pattern INFOBOX_NAME = Pattern.compile("\\{\\{Infobox\\s+([A-Za-z]+)");
    // "==heading==" / "===heading===" / ... (2-6 '=' fences, matching on both sides)
    private static final Pattern HEADING = Pattern.compile("(?m)^(={2,6})\\s*(.*?)\\s*\\1\\s*$");
    private static final String DROPS_TABLE_HEAD = "{{DropsTableHead";
    private static final String[] LINE_TEMPLATES =
        {"DropsLine", "DropsLineReward", "DropsLineSkill"};
    // Revenant goblin -> {{Revenants}} -> {{Revenants/Drops}} is two; a little headroom beyond
    private static final int MAX_TEMPLATE_DEPTH = 4;
    // template names whose canonical title cannot be derived from the name itself
    private static final Map<String, String> TABLE_ALIASES = new HashMap<>();
    // transclude a drop line but are not shared tables: Rarity is a formatting helper used on
    // thousands of pages, CalculateRDTNaked is a calculator, and Ecumenical is a line template
    // whose body is parameter placeholders rather than real drops
    private static final Set<String> NON_TABLE_TEMPLATES = new HashSet<>(
        Arrays.asList("Rarity", "CalculateRDTNaked", "DropsLineEcumenical"));

    static
    {
        TABLE_ALIASES.put("GWDRDT", "God Wars Dungeon rare drop table");
        TABLE_ALIASES.put("Revenants/Drops", "Revenant drop table");
        TABLE_ALIASES.put("CalculateRDTNaked", "Rare drop table");
    }
    private static final int BATCH = 50;

    private WikiDropsFetch()
    {
    }

    public static DropPageJson extract(String pageTitle, String wikitext)
    {
        return extract(pageTitle, wikitext, Collections.emptyMap());
    }

    /**
     * Parses a page, expanding the wiki's shared drop-table templates.
     *
     * @param sharedTables bare template name (no "Template:" prefix) to that template's wikitext.
     *                     A monster's herb, seed, and rare drops live in these rather than on the
     *                     page, so without them Aberrant spectre parses to ten drops and no herbs.
     */
    public static DropPageJson extract(String pageTitle, String wikitext,
        Map<String, String> sharedTables)
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
        page.tables.addAll(parseTables(text));
        appendSharedTables(page, text, headings, sharedTables);
        return page.tables.isEmpty() ? null : page;
    }

    /**
     * Every drop table in a stretch of wikitext, with the headings standing above each. Used for a
     * page's own text and, unchanged, for the body of a shared table template: a big shared table
     * is itself sectioned (the revenant one has 100%, Unique, Ancient statuettes and more), and
     * those sections are what the client groups by.
     */
    private static List<DropTableJson> parseTables(String text)
    {
        List<HeadingMatch> headings = findHeadings(text);
        List<Integer> headOffsets = findHeadOffsets(text);
        // DropsLineSkill is the wiki's line template for skilling loot — it is how every
        // pickpocketing table is written, and carries the same name/quantity/rarity params
        List<WikiText.Found> lineFounds = WikiText.templates(text, LINE_TEMPLATES);

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

        List<DropTableJson> tables = new ArrayList<>();
        for (List<WikiText.Found> group : grouped.values())
        {
            DropTableJson table = new DropTableJson();
            for (WikiText.Found found : group)
            {
                DropLineJson line = toLine(found);
                if (line != null)
                {
                    table.lines.add(line);
                }
            }
            if (table.lines.isEmpty())
            {
                continue;
            }
            int firstLineStart = group.get(0).start;
            table.sectionHeading = headingBefore(headings, firstLineStart, false);
            table.topHeading = headingBefore(headings, firstLineStart, true);
            tables.add(table);
        }
        return tables;
    }

    /** One {{DropsLine...}} template call as a parsed line, or null when it names no item. */
    private static DropLineJson toLine(WikiText.Found found)
    {
        Map<String, String> p = WikiText.params(found.body);
        String name = itemName(WikiText.stripLinks(p.get("name")));
        if (name == null)
        {
            return null; // no usable item name, or a "no drop" placeholder
        }
        DropLineJson line = new DropLineJson();
        line.reward = found.name.equals("DropsLineReward");
        line.name = name;
        line.quantity = p.get("quantity");
        line.rarity = p.get("rarity");
        line.rolls = p.get("rolls");
        return line;
    }

    /**
     * Adds a table for every shared drop-table template the page transcludes, attributed to that
     * table and filed under whatever heading the transclusion sits beneath.
     */
    private static void appendSharedTables(DropPageJson page, String text,
        List<HeadingMatch> headings, Map<String, String> sharedTables)
    {
        if (sharedTables.isEmpty())
        {
            return;
        }
        String[] names = sharedTables.keySet().toArray(new String[0]);
        for (WikiText.Found found : WikiText.templates(text, names))
        {
            List<DropTableJson> tables = new ArrayList<>();
            collectTemplateTables(found.name, WikiExpr.arguments(found.body), sharedTables,
                tables, new HashSet<>(), 0);
            String canonical = canonicalTableName(found.name);
            String pageTop = headingBefore(headings, found.start, true);
            // The two groupings answer different questions and each keeps its own answer:
            // sectionHeading is where the table sits on the page (revenant pages carry the
            // wilderness slayer table under "Prayer info", oddly but truthfully), dropTable is
            // what the table actually is. Collapsing them left the client's grouping toggle
            // doing nothing on most sources.
            String pageSection = headingBefore(headings, found.start, false);
            for (DropTableJson table : tables)
            {
                if (table.sectionHeading == null)
                {
                    table.sectionHeading = pageSection;
                }
                if (table.topHeading == null)
                {
                    table.topHeading = pageTop;
                }
                table.dropTable = canonical;
                page.tables.add(table);
            }
        }
    }


    /**
     * Lines contributed by one shared template, following its own transclusions. Revenant pages
     * reach their drops through {{Revenants}} -> {{Revenants/Drops}}, so one level is not enough.
     * The visited set stops a template cycle, and the depth limit stops a deep chain.
     */
    private static void collectTemplateTables(String name, Map<String, String> arguments,
        Map<String, String> sharedTables, List<DropTableJson> out, Set<String> visited, int depth)
    {
        String body = sharedTables.get(name);
        if (body == null || depth > MAX_TEMPLATE_DEPTH || !visited.add(name))
        {
            return;
        }
        // Substitute the transclusion's arguments and evaluate the template's parser functions
        // before parsing. Shared tables compute their rates rather than stating them, so skipping
        // this leaves every herb and seed drop with an unreadable rarity.
        String text = WikiExpr.render(WikiText.stripComments(body), arguments);
        out.addAll(parseTables(text));
        for (WikiText.Found nested
            : WikiText.templates(text, sharedTables.keySet().toArray(new String[0])))
        {
            collectTemplateTables(nested.name, WikiExpr.arguments(nested.body), sharedTables,
                out, visited, depth + 1);
        }
    }

    /**
     * Human name for a shared table template: "HerbDropLines" -> "Herb drop table". The wiki's
     * template names are camel-cased and inconsistently suffixed, so the suffix is stripped and
     * the remaining words spaced out.
     */
    static String canonicalTableName(String template)
    {
        String alias = TABLE_ALIASES.get(template);
        if (alias != null)
        {
            return alias;
        }
        String base = template.replace("/Drops", "")
            .replaceAll("(DropLines|DropTable|DropLine)$", "");
        String spaced = base.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ").trim();
        if (spaced.isEmpty())
        {
            return template;
        }
        String lowered = spaced.substring(0, 1).toUpperCase(Locale.ROOT)
            + spaced.substring(1).toLowerCase(Locale.ROOT);
        return lowered + " drop table";
    }

    /**
     * A drop line's item name, or null when the line names no obtainable item.
     *
     * <p>"Nothing" is the wiki's placeholder for a roll that yields no item, so it is not a drop.
     * A "#section" fragment ("Bird nest (egg)#Red egg") points at part of an item's page; it is a
     * display detail, and keeping it leaves the name matching no item id at all.
     */
    private static String itemName(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        int anchor = raw.indexOf('#');
        String name = (anchor >= 0 ? raw.substring(0, anchor) : raw).trim();
        return name.isEmpty() || "nothing".equalsIgnoreCase(name) ? null : name;
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

    /**
     * All mainspace page titles that embed {{DropsLine}}, {{DropsLineReward}} or {{DropsLineSkill}}
     * — i.e. every page with a drop table, reward table, or skilling/pickpocket loot table.
     */
    public static List<String> fetchDropPageTitles(Gson gson) throws IOException
    {
        Set<String> titles = new TreeSet<>();
        titles.addAll(WikiShopFetch.fetchTitlesEmbedding(gson, "Template:DropsLine"));
        titles.addAll(WikiShopFetch.fetchTitlesEmbedding(gson, "Template:DropsLineReward"));
        titles.addAll(WikiShopFetch.fetchTitlesEmbedding(gson, "Template:DropsLineSkill"));
        return new ArrayList<>(titles);
    }

    /**
     * The wiki's shared drop-table templates, keyed by bare name, with their wikitext.
     *
     * <p>Found by asking which Template-namespace pages transclude a drop line template: those are
     * exactly the shared tables (herb, seed, superior, revenant, ...). A monster's herb and rare
     * drops live in one of these rather than on its own page, so without them Aberrant spectre
     * parses to ten drops and no herbs.
     */
    public static Map<String, String> fetchSharedDropTables(Gson gson) throws IOException
    {
        Set<String> titles = new TreeSet<>();
        for (String line : LINE_TEMPLATES)
        {
            for (String t : WikiShopFetch.fetchTitlesEmbedding(gson, "Template:" + line, 10))
            {
                String bare = bareTemplateName(t);
                if (!bare.contains("/doc") && !bare.contains("/Sandbox")
                    && !NON_TABLE_TEMPLATES.contains(bare))
                {
                    titles.add(t);
                }
            }
        }
        Map<String, String> out = new TreeMap<>();
        List<String> list = new ArrayList<>(titles);
        for (int i = 0; i < list.size(); i += BATCH)
        {
            List<String> batch = list.subList(i, Math.min(i + BATCH, list.size()));
            JsonObject response = WikiHttp.request(gson, WikiHttp.API
                + "?action=query&prop=revisions&rvslots=main&rvprop=content&format=json"
                + "&titles=" + URLEncoder.encode(String.join("|", batch), "UTF-8"));
            WikiHttp.sleep();
            JsonObject query = response.getAsJsonObject("query");
            JsonObject pages = query == null ? null : query.getAsJsonObject("pages");
            if (pages == null)
            {
                continue;
            }
            for (Map.Entry<String, JsonElement> e : pages.entrySet())
            {
                JsonObject page = e.getValue().getAsJsonObject();
                if (page.has("revisions"))
                {
                    out.put(bareTemplateName(page.get("title").getAsString()),
                        page.getAsJsonArray("revisions").get(0).getAsJsonObject()
                            .getAsJsonObject("slots").getAsJsonObject("main").get("*").getAsString());
                }
            }
        }
        return out;
    }

    private static String bareTemplateName(String title)
    {
        return title.startsWith("Template:") ? title.substring("Template:".length()) : title;
    }

    /** Fetches and parses each page's drop tables; pages with no usable table are omitted. */
    public static Map<String, DropPageJson> fetchDropPages(Gson gson, Collection<String> pageTitles)
        throws IOException
    {
        return fetchDropPages(gson, pageTitles, Collections.emptyMap());
    }

    public static Map<String, DropPageJson> fetchDropPages(Gson gson, Collection<String> pageTitles,
        Map<String, String> sharedTables) throws IOException
    {
        Map<String, DropPageJson> result = new TreeMap<>();
        List<String> titles = new ArrayList<>(pageTitles);
        for (int i = 0; i < titles.size(); i += BATCH)
        {
            List<String> batch = titles.subList(i, Math.min(i + BATCH, titles.size()));
            String baseBatchUrl = WikiHttp.API
                + "?action=query&prop=revisions&rvslots=main&rvprop=content&format=json"
                + "&titles=" + URLEncoder.encode(String.join("|", batch), "UTF-8");
            String continueSuffix = "";
            while (true)
            {
                JsonObject response = WikiHttp.request(gson, baseBatchUrl + continueSuffix);
                WikiHttp.sleep();
                JsonObject query = response.getAsJsonObject("query");
                JsonObject pages = query == null ? null : query.getAsJsonObject("pages");
                if (pages == null)
                {
                    throw new IOException("Unexpected wiki API response for batch ["
                        + String.join(", ", batch) + "]: "
                        + (response.has("error") ? response.get("error").toString()
                            : WikiHttp.snippet(response)));
                }
                for (Map.Entry<String, JsonElement> e : pages.entrySet())
                {
                    JsonObject page = e.getValue().getAsJsonObject();
                    if (!page.has("revisions"))
                    {
                        continue; // missing page
                    }
                    String title = page.get("title").getAsString();
                    String content = page.getAsJsonArray("revisions").get(0).getAsJsonObject()
                        .getAsJsonObject("slots").getAsJsonObject("main").get("*").getAsString();
                    DropPageJson drops = extract(title, content, sharedTables);
                    if (drops != null)
                    {
                        result.put(title, drops);
                    }
                }
                JsonObject cont = response.getAsJsonObject("continue");
                if (cont == null)
                {
                    break;
                }
                StringBuilder next = new StringBuilder();
                for (Map.Entry<String, JsonElement> e : cont.entrySet())
                {
                    next.append('&').append(e.getKey()).append('=')
                        .append(URLEncoder.encode(e.getValue().getAsString(), "UTF-8"));
                }
                continueSuffix = next.toString();
            }
        }
        return result;
    }
}
