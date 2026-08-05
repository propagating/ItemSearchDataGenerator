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
import java.util.HashMap;
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
        String id = field(body, "id");
        if (id == null)
        {
            id = field(body, "id1"); // multi-version items number their ids
        }
        info.id = firstInt(id);
        info.value = firstInt(field(body, "value"));
        return info;
    }

    // "|field = value" on its own line, same convention as WikiFetch
    private static String field(String body, String name)
    {
        Matcher m = Pattern.compile("(?m)^\\s*\\|\\s*" + Pattern.quote(name)
            + "\\s*=\\s*(.*?)\\s*$").matcher(body);
        return m.find() ? m.group(1).trim() : null;
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
            // final title -> original requested name (walk normalization then redirects)
            Map<String, String> toOriginal = new HashMap<>();
            for (String n : batch)
            {
                toOriginal.put(n, n);
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
                ItemInfoJson info = extractInfobox(content);
                String original = toOriginal.getOrDefault(title, title);
                if (info != null)
                {
                    result.put(original, info);
                }
            }
        }
        return result;
    }

    // applies a wiki "from"/"to" mapping array so 'to' titles resolve to the original request
    private static void remap(JsonArray mappings, Map<String, String> toOriginal)
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
            String original = toOriginal.remove(from);
            if (original != null)
            {
                toOriginal.put(to, original);
            }
        }
    }
}
