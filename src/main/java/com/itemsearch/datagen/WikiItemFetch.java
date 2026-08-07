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
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.itemsearch.datagen.source.ItemInfoJson;

/** Fetches {{Infobox Item}} id + shop value from the live OSRS Wiki, for shop stock lines. */
public final class WikiItemFetch
{
    private static final Pattern INFOBOX_ITEM =
        Pattern.compile("\\{\\{Infobox Item(.*?)\\n\\}\\}", Pattern.DOTALL);
    private static final int BATCH = 50;

    private WikiItemFetch()
    {
    }

    public static ItemInfoJson extractInfobox(String wikitext)
    {
        if (wikitext == null)
        {
            return null;
        }
        Matcher box = INFOBOX_ITEM.matcher(wikitext);
        if (!box.find())
        {
            return null;
        }
        String body = box.group(1);
        ItemInfoJson info = new ItemInfoJson();
        String id = WikiText.field(body, "id");
        if (id == null)
        {
            id = WikiText.field(body, "id1"); // multi-version items number their ids
        }
        info.id = firstInt(id);
        info.value = firstInt(WikiText.field(body, "value"));
        return info;
    }

    /**
     * Every separately-named item an {{Infobox Item}} declares, by its own name.
     *
     * <p>One wiki page routinely holds several items: Bronze arrow carries the plain arrow and its
     * three poisoned grades, Prayer potion carries all four doses. The page states which id belongs
     * to which — {@code name3 = Bronze arrow(p+)} beside {@code id3 = 5616} — so nothing here has to
     * be inferred from the shape of a name. Returns empty for an ordinary one-item page.
     */
    public static Map<String, ItemInfoJson> extractVersions(String wikitext)
    {
        Map<String, ItemInfoJson> versions = new LinkedHashMap<>();
        if (wikitext == null)
        {
            return versions;
        }
        Matcher box = INFOBOX_ITEM.matcher(wikitext);
        if (!box.find())
        {
            return versions;
        }
        String body = box.group(1);
        for (int n = 1; ; n++)
        {
            String name = WikiText.field(body, "name" + n);
            if (name == null)
            {
                break;
            }
            Integer id = firstInt(WikiText.field(body, "id" + n));
            if (id == null)
            {
                // a named version with no id of its own would otherwise inherit id1's, which is
                // how the poisoned arrows came to claim the unpoisoned arrow's id
                continue;
            }
            ItemInfoJson info = new ItemInfoJson();
            info.id = id;
            Integer value = firstInt(WikiText.field(body, "value" + n));
            info.value = value != null ? value : firstInt(WikiText.field(body, "value"));
            versions.put(name.trim(), info);
        }
        return versions;
    }

    // first integer of a possibly comma-separated value; null on junk
    private static Integer firstInt(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        String first = raw.split(",")[0].trim().replace(" ", "");
        try
        {
            return Integer.parseInt(first);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /**
     * Fetches item info for each name, keyed by the REQUESTED name: the wiki may
     * normalize or redirect a title, so the response's "normalized"/"redirects"
     * mappings are walked back to the original.
     */
    public static Map<String, ItemInfoJson> fetch(Gson gson, Collection<String> itemNames)
        throws IOException
    {
        Map<String, ItemInfoJson> result = new TreeMap<>();
        List<String> names = new ArrayList<>(itemNames);
        for (int i = 0; i < names.size(); i += BATCH)
        {
            List<String> batch = names.subList(i, Math.min(i + BATCH, names.size()));
            JsonObject response = WikiHttp.request(gson, WikiHttp.API
                + "?action=query&prop=revisions&rvslots=main&rvprop=content&redirects=1&format=json"
                + "&titles=" + URLEncoder.encode(String.join("|", batch), "UTF-8"));
            WikiHttp.sleep();
            JsonObject query = response.getAsJsonObject("query");
            if (query == null)
            {
                throw new IOException("Unexpected wiki API response: " + WikiHttp.snippet(response));
            }
            // final title -> every original name that asked for it (normalization, then redirects).
            // Several requested names routinely land on one page — Bronze arrow(p+) redirects to
            // Bronze arrow, which was also requested outright — so this cannot be one-to-one:
            // keeping only the last requester is how plain Bronze arrow came to have no id at all.
            Map<String, List<String>> toOriginal = new HashMap<>();
            for (String n : batch)
            {
                toOriginal.computeIfAbsent(n, k -> new ArrayList<>()).add(n);
            }
            remap(query.getAsJsonArray("normalized"), toOriginal);
            remap(query.getAsJsonArray("redirects"), toOriginal);
            JsonObject pages = query.getAsJsonObject("pages");
            if (pages == null)
            {
                continue;
            }
            for (Map.Entry<String, JsonElement> e : pages.entrySet())
            {
                JsonObject page = e.getValue().getAsJsonObject();
                if (!page.has("revisions"))
                {
                    continue;
                }
                String title = page.get("title").getAsString();
                String content = page.getAsJsonArray("revisions").get(0).getAsJsonObject()
                    .getAsJsonObject("slots").getAsJsonObject("main").get("*").getAsString();
                ItemInfoJson fallback = extractInfobox(content);
                Map<String, ItemInfoJson> versions = extractVersions(content);
                for (String original : toOriginal.getOrDefault(title,
                    Collections.singletonList(title)))
                {
                    // the page names its versions, so each requester gets its own id rather than
                    // whichever one happened to be listed first
                    ItemInfoJson info = versions.getOrDefault(original, fallback);
                    if (info != null)
                    {
                        result.put(original, info);
                    }
                }
            }
        }
        return result;
    }

    // applies a wiki "from"/"to" mapping array so 'to' titles resolve to the original requests.
    // Merges rather than replaces: a page reached both directly and by redirect has two requesters.
    private static void remap(JsonArray mappings, Map<String, List<String>> toOriginal)
    {
        if (mappings == null)
        {
            return;
        }
        for (JsonElement e : mappings)
        {
            JsonObject m = e.getAsJsonObject();
            String from = m.get("from").getAsString();
            String to = m.get("to").getAsString();
            List<String> originals = toOriginal.remove(from);
            if (originals != null)
            {
                toOriginal.computeIfAbsent(to, k -> new ArrayList<>()).addAll(originals);
            }
        }
    }
}
