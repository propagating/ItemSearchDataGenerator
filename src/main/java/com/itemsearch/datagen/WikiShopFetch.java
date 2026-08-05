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

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
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
    private static final Pattern STORE_LINE = Pattern.compile("\\{\\{StoreLine\\|(.*?)\\}\\}");

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
            page.location = stripLinks(params(infobox.group(1)).get("location"));
        }
        Matcher head = STORE_TABLE_HEAD.matcher(wikitext);
        while (head.find())
        {
            Map<String, String> headParams = params(head.group(1));
            ShopTableJson table = new ShopTableJson();
            table.currency = stripLinks(headParams.get("currency"));
            table.sellMultiplier = headParams.get("sellmultiplier");
            table.nameNotes = headParams.get("namenotes");
            int end = wikitext.indexOf("{{StoreTableBottom", head.end());
            String body = wikitext.substring(head.end(), end < 0 ? wikitext.length() : end);
            Matcher line = STORE_LINE.matcher(body);
            while (line.find())
            {
                Map<String, String> p = params(line.group(1));
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

    // splits template params on '|' pipes that are not inside a [[wiki link]]
    private static Map<String, String> params(String body)
    {
        Map<String, String> out = new HashMap<>();
        for (String part : body.split("\\|(?![^\\[]*?\\]\\])"))
        {
            int eq = part.indexOf('=');
            if (eq > 0)
            {
                out.put(part.substring(0, eq).trim().toLowerCase(Locale.ROOT),
                    part.substring(eq + 1).trim());
            }
        }
        return out;
    }

    // "[[Al Kharid|Al-Kharid]]" -> "Al-Kharid", "[[Varrock]]" -> "Varrock"
    static String stripLinks(String s)
    {
        return s == null ? null
            : s.replaceAll("\\[\\[(?:[^\\]|]*\\|)?([^\\]]*)\\]\\]", "$1").trim();
    }

    private static String emptyToNull(String s)
    {
        return s == null || s.isEmpty() ? null : s;
    }
}
