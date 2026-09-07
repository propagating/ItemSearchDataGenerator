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

/** Asks the live OSRS Wiki which categories each drop page sits in, and keeps the retired verdicts. */
public final class WikiRetiredFetch
{
    private static final int BATCH = 50;

    private WikiRetiredFetch()
    {
    }

    /** Page title to reason, for every title the wiki retires. Live pages are absent. */
    public static Map<String, String> fetch(Collection<String> pageTitles) throws IOException
    {
        Map<String, List<String>> categories = fetchCategories(pageTitles);
        Map<String, String> retired = new TreeMap<>();
        for (String title : pageTitles)
        {
            List<String> cats = categories.get(title);
            String reason = WikiRetired.reasonFor(title, cats == null ? new ArrayList<>() : cats);
            if (reason != null)
            {
                retired.put(title, reason);
            }
        }
        return retired;
    }

    // category names (without the "Category:" prefix) per title; a page with many categories
    // arrives across several continued responses, so lists are appended to, never replaced
    private static Map<String, List<String>> fetchCategories(Collection<String> pageTitles) throws IOException
    {
        Gson gson = new Gson();
        Map<String, List<String>> result = new HashMap<>();
        List<String> titles = new ArrayList<>(pageTitles);
        for (int i = 0; i < titles.size(); i += BATCH)
        {
            List<String> batch = titles.subList(i, Math.min(i + BATCH, titles.size()));
            String baseBatchUrl = WikiHttp.API + "?action=query&prop=categories&cllimit=max&format=json"
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
                    throw new IOException("Unexpected wiki API response for batch [" + String.join(", ", batch)
                        + "]: " + (response.has("error") ? response.get("error").toString() : WikiHttp.snippet(response)));
                }
                for (Map.Entry<String, JsonElement> e : pages.entrySet())
                {
                    JsonObject page = e.getValue().getAsJsonObject();
                    if (!page.has("categories"))
                    {
                        continue;
                    }
                    List<String> cats = result.computeIfAbsent(page.get("title").getAsString(),
                        k -> new ArrayList<>());
                    JsonArray arr = page.getAsJsonArray("categories");
                    for (JsonElement c : arr)
                    {
                        String name = c.getAsJsonObject().get("title").getAsString();
                        cats.add(name.startsWith("Category:") ? name.substring("Category:".length()) : name);
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
