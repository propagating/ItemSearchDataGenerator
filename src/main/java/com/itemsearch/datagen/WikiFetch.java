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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.itemsearch.datagen.source.WikiCombatJson;

/** Fetches raw {{Infobox Monster}} combat fields from the live OSRS Wiki. */
public final class WikiFetch
{
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
        w.maxHit = WikiText.field(body, "max hit");
        w.maxHits = WikiText.numberedFields(body, "max hit");
        w.attackStyle = WikiText.field(body, "attack style");
        w.attackStyles = WikiText.numberedFields(body, "attack style");
        w.attackSpeed = WikiText.field(body, "attack speed");
        w.attackSpeeds = WikiText.numberedFields(body, "attack speed");
        w.poisonous = WikiText.field(body, "poisonous");
        // what it defends worst against; see Weakness for why the lowest number is the answer
        w.defStab = WikiText.field(body, "dstab");
        w.defStabs = WikiText.numberedFields(body, "dstab");
        w.defSlash = WikiText.field(body, "dslash");
        w.defSlashes = WikiText.numberedFields(body, "dslash");
        w.defCrush = WikiText.field(body, "dcrush");
        w.defCrushes = WikiText.numberedFields(body, "dcrush");
        w.defMagic = WikiText.field(body, "dmagic");
        w.defMagics = WikiText.numberedFields(body, "dmagic");
        w.defRanged = WikiText.field(body, "dstandard");
        w.defRangeds = WikiText.numberedFields(body, "dstandard");
        w.elementalWeaknessType = WikiText.field(body, "elementalweaknesstype");
        w.elementalWeaknessPercent = WikiText.field(body, "elementalweaknesspercent");
        return w;
    }

    public static Map<String, WikiCombatJson> fetch(Collection<String> pageTitles) throws IOException
    {
        Gson gson = new Gson();
        Map<String, WikiCombatJson> result = new HashMap<>();
        List<String> titles = new ArrayList<>(pageTitles);
        for (int i = 0; i < titles.size(); i += BATCH)
        {
            List<String> batch = titles.subList(i, Math.min(i + BATCH, titles.size()));
            String baseBatchUrl = WikiHttp.API + "?action=query&prop=revisions&rvslots=main&rvprop=content&format=json"
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

}
