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
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.itemsearch.datagen.source.LocationJson;

/**
 * Fetches every page's {@code {{LocLine}}} blocks — where its monsters are found.
 *
 * <p>Keyed by the REQUESTED page title, since that is what a source knows itself by; the wiki may
 * answer with a redirect target or a normalised spelling, and several requests can land on one
 * page, so all of them are walked back.
 */
public final class WikiLocationFetch
{
    private static final int BATCH = 50;

    private WikiLocationFetch()
    {
    }

    public static Map<String, List<LocationJson>> fetch(Gson gson, Collection<String> pageTitles)
        throws IOException
    {
        Map<String, List<LocationJson>> result = new TreeMap<>();
        List<String> titles = new ArrayList<>(pageTitles);
        for (int i = 0; i < titles.size(); i += BATCH)
        {
            List<String> batch = titles.subList(i, Math.min(i + BATCH, titles.size()));
            JsonObject response = WikiHttp.request(gson, WikiHttp.API
                + "?action=query&prop=revisions&rvslots=main&rvprop=content&redirects=1&format=json"
                + "&titles=" + URLEncoder.encode(String.join("|", batch), "UTF-8"));
            WikiHttp.sleep();
            JsonObject query = response.getAsJsonObject("query");
            if (query == null)
            {
                throw new IOException("Unexpected wiki API response: " + WikiHttp.snippet(response));
            }
            Map<String, List<String>> toOriginal = new HashMap<>();
            for (String title : batch)
            {
                toOriginal.computeIfAbsent(title, k -> new ArrayList<>()).add(title);
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
                List<LocationJson> found = WikiLocations.parse(content);
                if (found.isEmpty())
                {
                    continue;
                }
                for (String original : toOriginal.getOrDefault(title,
                    java.util.Collections.singletonList(title)))
                {
                    result.put(original, found);
                }
            }
        }
        return result;
    }

    // as WikiItemFetch: a page reached both directly and by redirect has more than one requester
    private static void remap(com.google.gson.JsonArray mappings, Map<String, List<String>> toOriginal)
    {
        if (mappings == null)
        {
            return;
        }
        for (JsonElement e : mappings)
        {
            JsonObject m = e.getAsJsonObject();
            List<String> originals = toOriginal.remove(m.get("from").getAsString());
            if (originals != null)
            {
                toOriginal.computeIfAbsent(m.get("to").getAsString(), k -> new ArrayList<>())
                    .addAll(originals);
            }
        }
    }
}
