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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.itemsearch.datagen.source.ShopLineJson;
import com.itemsearch.datagen.source.ShopPageJson;
import com.itemsearch.datagen.source.ShopTableJson;

/** Fetches and parses {{StoreTableHead}}/{{StoreLine}} shop tables from the live OSRS Wiki. */
public final class WikiShopFetch
{
    private static final Pattern INFOBOX_SHOP =
        Pattern.compile("\\{\\{Infobox Shop(.*?)\\n\\}\\}", Pattern.DOTALL);
    private static final Pattern STORE_TABLE_HEAD =
        Pattern.compile("\\{\\{StoreTableHead\\|(.*?)\\}\\}");
    // {{StoreLine|...}} is the common row template; {{Tzhaar shop row|...}} is a synonym used
    // exclusively by the TzHaar city shops (Tokkul currency), with the same param names.
    private static final Pattern STORE_LINE =
        Pattern.compile("\\{\\{(?:StoreLine|Tzhaar shop row)\\|(.*?)\\}\\}");
    // locates the raw {{Map|...}} span (up to its first '}') so MAP_ID can be searched within
    // just that template; coordinate/plane extraction itself is delegated to WikiText.mapCoords
    private static final Pattern MAP_COORDS =
        Pattern.compile("\\{\\{Map[^}]*?(\\d{3,5})\\s*,\\s*(\\d{3,5})[^}]*");
    private static final Pattern MAP_ID = Pattern.compile("mapID\\s*=\\s*(-?\\d+)");
    private static final int BATCH = 50;

    private WikiShopFetch()
    {
    }

    public static ShopPageJson extract(String pageTitle, String wikitext)
    {
        if (wikitext == null)
        {
            return null;
        }
        ShopPageJson page = new ShopPageJson();
        page.pageTitle = pageTitle;
        Matcher infobox = INFOBOX_SHOP.matcher(wikitext);
        if (infobox.find())
        {
            String body = infobox.group(1);
            page.location = stripLinks(WikiText.params(body).get("location"));
            // the |map= value nests a {{Map|x,y|...}} template whose pipes defeat params();
            // pull the first coordinate pair (and optional plane) straight from the body
            applyMap(page, body);
        }
        if (page.mapX == null)
        {
            // NPC-page shops have no {{Infobox Shop}}; fall back to the first {{Map}}
            // anywhere on the page — shop locations are static, so any page map points
            // at (or next to) the shop
            applyMap(page, wikitext);
        }
        Matcher head = STORE_TABLE_HEAD.matcher(wikitext);
        while (head.find())
        {
            Map<String, String> headParams = WikiText.params(head.group(1));
            String bucket = headParams.get("bucket");
            if (bucket != null && bucket.trim().equalsIgnoreCase("no"))
            {
                // the wiki's own dedup marker: this table duplicates another page's listing of
                // the same shop (e.g. shown on both the store page and the NPC page) — skip it
                continue;
            }
            ShopTableJson table = new ShopTableJson();
            table.currency = stripLinks(headParams.get("currency"));
            table.sellMultiplier = headParams.get("sellmultiplier");
            table.nameNotes = headParams.get("namenotes");
            table.bucket = bucket;
            int end = wikitext.indexOf("{{StoreTableBottom", head.end());
            String body = wikitext.substring(head.end(), end < 0 ? wikitext.length() : end);
            Matcher line = STORE_LINE.matcher(body);
            while (line.find())
            {
                Map<String, String> p = WikiText.params(line.group(1));
                String name = stripLinks(p.get("name"));
                if (name == null || name.isEmpty())
                {
                    continue; // a store line without an item name is unusable
                }
                ShopLineJson l = new ShopLineJson();
                l.name = name;
                l.stock = emptyToNull(p.get("stock"));
                l.restock = emptyToNull(p.get("restock"));
                l.sell = emptyToNull(p.get("sell"));
                table.lines.add(l);
            }
            if (!table.lines.isEmpty())
            {
                page.tables.add(table);
            }
        }
        return page.tables.isEmpty() ? null : page;
    }

    // "[[Al Kharid|Al-Kharid]]" -> "Al-Kharid", "[[Varrock]]" -> "Varrock"
    static String stripLinks(String s)
    {
        return WikiText.stripLinks(s);
    }

    // locates the first {{Map|...}} template in `text` and, if it contains a coordinate pair,
    // fills in mapX/mapY/mapPlane (via WikiText.mapCoords) and mapId (searched locally, since
    // it isn't part of WikiText's mapCoords contract)
    private static void applyMap(ShopPageJson page, String text)
    {
        Matcher map = MAP_COORDS.matcher(text);
        if (!map.find())
        {
            return;
        }
        int[] coords = WikiText.mapCoords(map.group());
        if (coords != null)
        {
            page.mapX = String.valueOf(coords[0]);
            page.mapY = String.valueOf(coords[1]);
            if (coords[2] != 0)
            {
                page.mapPlane = String.valueOf(coords[2]);
            }
        }
        Matcher mapId = MAP_ID.matcher(map.group());
        if (mapId.find())
        {
            page.mapId = mapId.group(1);
        }
    }

    private static String emptyToNull(String s)
    {
        return s == null || s.isEmpty() ? null : s;
    }

    /**
     * All mainspace page titles that embed {{StoreLine}} or {{Tzhaar shop row}} — i.e. every shop
     * page, including the TzHaar city shops which use the latter, Tokkul-currency-only synonym.
     */
    public static List<String> fetchShopPageTitles(Gson gson) throws IOException
    {
        Set<String> titles = new TreeSet<>();
        titles.addAll(fetchTitlesEmbedding(gson, "Template:StoreLine"));
        titles.addAll(fetchTitlesEmbedding(gson, "Template:Tzhaar shop row"));
        return new ArrayList<>(titles);
    }

    // package-visible so WikiDropsFetch can reuse the same embeddedin pagination loop
    static List<String> fetchTitlesEmbedding(Gson gson, String template) throws IOException
    {
        return fetchTitlesEmbedding(gson, template, 0);
    }

    /** @param namespace 0 for articles, 10 for templates that transclude {@code template}. */
    static List<String> fetchTitlesEmbedding(Gson gson, String template, int namespace)
        throws IOException
    {
        List<String> titles = new ArrayList<>();
        String continueSuffix = "";
        while (true)
        {
            JsonObject response = WikiHttp.request(gson, WikiHttp.API
                + "?action=query&list=embeddedin&eititle=" + URLEncoder.encode(template, "UTF-8")
                + "&einamespace=" + namespace + "&eilimit=500&format=json" + continueSuffix);
            WikiHttp.sleep();
            titles.addAll(titlesFrom(response));
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
        return titles;
    }

    static List<String> titlesFrom(JsonObject response)
    {
        List<String> out = new ArrayList<>();
        JsonObject query = response.getAsJsonObject("query");
        JsonArray list = query == null ? null : query.getAsJsonArray("embeddedin");
        if (list == null)
        {
            return out;
        }
        for (JsonElement e : list)
        {
            out.add(e.getAsJsonObject().get("title").getAsString());
        }
        return out;
    }

    /** Fetches and parses each page's store tables; pages with no usable table are omitted. */
    public static Map<String, ShopPageJson> fetchShops(Gson gson, Collection<String> pageTitles)
        throws IOException
    {
        Map<String, ShopPageJson> result = new TreeMap<>();
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
                    ShopPageJson shop = extract(title, content);
                    if (shop != null)
                    {
                        result.put(title, shop);
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
