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
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.itemsearch.datagen.source.WikiCombatJson;

/** Fetches raw {{Infobox Monster}} combat fields from the live OSRS Wiki. */
public final class WikiFetch
{
    private static final String API = "https://oldschool.runescape.wiki/api.php";
    private static final String UA = "runelite-dropsearch-datagen/1.0 (offline combat-data generator)";
    private static final int BATCH = 50;
    // "|field = value" up to the next "|field" or the closing "}}"
    private static final Pattern INFOBOX =
        Pattern.compile("\\{\\{Infobox Monster(.*?)\\n\\}\\}", Pattern.DOTALL);

    private WikiFetch()
    {
    }

    public static WikiCombatJson extractInfobox(String wikitext)
    {
        if (wikitext == null)
        {
            return null;
        }
        Matcher box = INFOBOX.matcher(wikitext);
        if (!box.find())
        {
            return null;
        }
        String body = box.group(1);
        WikiCombatJson w = new WikiCombatJson();
        w.maxHit = field(body, "max hit");
        w.maxHits = numberedFields(body, "max hit");
        w.attackStyle = field(body, "attack style");
        w.attackStyles = numberedFields(body, "attack style");
        w.attackSpeed = field(body, "attack speed");
        w.attackSpeeds = numberedFields(body, "attack speed");
        return w;
    }

    private static String field(String body, String name)
    {
        Matcher m = Pattern.compile("(?m)^\\s*\\|\\s*" + Pattern.quote(name)
            + "\\s*=\\s*(.*?)\\s*$").matcher(body);
        return m.find() ? m.group(1).trim() : null;
    }

    private static List<String> numberedFields(String body, String name)
    {
        List<String> out = new ArrayList<>();
        for (int i = 1; ; i++)
        {
            String v = field(body, name + i);
            if (v == null)
            {
                break;
            }
            out.add(v);
        }
        return out;
    }

    public static Map<String, WikiCombatJson> fetch(Collection<String> pageTitles) throws IOException
    {
        Gson gson = new Gson();
        Map<String, WikiCombatJson> result = new HashMap<>();
        List<String> titles = new ArrayList<>(pageTitles);
        for (int i = 0; i < titles.size(); i += BATCH)
        {
            List<String> batch = titles.subList(i, Math.min(i + BATCH, titles.size()));
            String baseBatchUrl = API + "?action=query&prop=revisions&rvslots=main&rvprop=content&format=json"
                + "&titles=" + URLEncoder.encode(String.join("|", batch), "UTF-8");
            String continueSuffix = "";
            while (true)
            {
                JsonObject response = request(gson, baseBatchUrl + continueSuffix);
                sleep();
                JsonObject query = response.getAsJsonObject("query");
                JsonObject pages = query == null ? null : query.getAsJsonObject("pages");
                if (pages == null)
                {
                    throw new IOException("Unexpected wiki API response for batch [" + String.join(", ", batch)
                        + "]: " + (response.has("error") ? response.get("error").toString() : snippet(response)));
                }
                for (Map.Entry<String, JsonElement> e : pages.entrySet())
                {
                    JsonObject page = e.getValue().getAsJsonObject();
                    if (!page.has("revisions"))
                    {
                        continue; // missing page
                    }
                    String content = page.getAsJsonArray("revisions").get(0).getAsJsonObject()
                        .getAsJsonObject("slots").getAsJsonObject("main").get("*").getAsString();
                    WikiCombatJson w = extractInfobox(content);
                    if (w != null)
                    {
                        result.put(page.get("title").getAsString(), w);
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

    private static JsonObject request(Gson gson, String url) throws IOException
    {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestProperty("User-Agent", UA);
        conn.setConnectTimeout(15000);
        conn.setReadTimeout(30000);
        try (BufferedReader r = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)))
        {
            return gson.fromJson(r, JsonObject.class);
        }
        finally
        {
            conn.disconnect();
        }
    }

    private static String snippet(JsonObject response)
    {
        String raw = response.toString();
        return raw.length() > 500 ? raw.substring(0, 500) + "..." : raw;
    }

    private static void sleep()
    {
        try
        {
            Thread.sleep(200); // be polite to the wiki
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }
}
