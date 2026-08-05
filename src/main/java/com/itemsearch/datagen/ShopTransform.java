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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.itemsearch.datagen.output.OutputShop;
import com.itemsearch.datagen.output.OutputShopItem;
import com.itemsearch.datagen.source.DropJson;
import com.itemsearch.datagen.source.ItemInfoJson;
import com.itemsearch.datagen.source.MonsterJson;
import com.itemsearch.datagen.source.ShopLineJson;
import com.itemsearch.datagen.source.ShopPageJson;
import com.itemsearch.datagen.source.ShopTableJson;

/** Joins the wiki shop + item snapshots into the client's shops.json shape. */
public final class ShopTransform
{
    private ShopTransform()
    {
    }

    public static List<OutputShop> transform(Map<String, ShopPageJson> pages,
        Map<String, ItemInfoJson> itemInfo, Map<String, Integer> fallbackIds)
    {
        List<OutputShop> out = new ArrayList<>();
        for (ShopPageJson page : pages.values())
        {
            for (int t = 0; t < page.tables.size(); t++)
            {
                ShopTableJson table = page.tables.get(t);
                OutputShop shop = new OutputShop();
                // some wiki page titles carry a trailing period ("Diango's Toy Store.")
                String baseName = page.pageTitle.replaceAll("\\.+$", "").trim();
                shop.name = t == 0 ? baseName : baseName + " (" + (t + 1) + ")";
                shop.location = page.location;
                shop.currency = table.currency == null || table.currency.isEmpty()
                    ? "Coins" : table.currency;
                Integer mapX = parseInt(page.mapX);
                Integer mapY = parseInt(page.mapY);
                if (mapX != null && mapY != null)
                {
                    shop.x = mapX;
                    shop.y = mapY;
                    Integer plane = parseInt(page.mapPlane);
                    shop.plane = plane != null ? plane : 0;
                    shop.mapId = parseInt(page.mapId);
                }
                Integer multiplier = parseInt(table.sellMultiplier);
                for (ShopLineJson line : table.lines)
                {
                    OutputShopItem item = new OutputShopItem();
                    item.itemName = line.name;
                    item.itemId = resolveId(line.name, itemInfo, fallbackIds);
                    item.stock = parseStock(line.stock);
                    item.restockSeconds = ticksToSeconds(parseInt(line.restock));
                    item.sellPrice = sellPrice(line.sell, itemInfo.get(line.name), multiplier);
                    shop.items.add(item);
                }
                out.add(shop);
            }
        }
        out.sort(Comparator.comparing(s -> s.name, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** Lower-cased drop item name -> item id, from the monster database (first occurrence wins). */
    public static Map<String, Integer> itemIdsFromMonsters(Map<String, MonsterJson> monsters)
    {
        Map<String, Integer> out = new HashMap<>();
        for (MonsterJson m : monsters.values())
        {
            if (m == null || m.drops == null)
            {
                continue;
            }
            for (DropJson d : m.drops)
            {
                if (d != null && d.name != null)
                {
                    out.putIfAbsent(d.name.toLowerCase(Locale.ROOT), d.id);
                }
            }
        }
        return out;
    }

    static int resolveId(String name, Map<String, ItemInfoJson> itemInfo,
        Map<String, Integer> fallbackIds)
    {
        ItemInfoJson info = itemInfo.get(name);
        if (info != null && info.id != null)
        {
            return info.id;
        }
        Integer fallback = fallbackIds.get(name.toLowerCase(Locale.ROOT));
        return fallback != null ? fallback : -1;
    }

    // -1 = infinite, null = unknown/unparseable
    static Integer parseStock(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.equals("∞") || s.equals("inf"))
        {
            return -1;
        }
        Integer n = parseInt(s);
        return n != null && n >= 0 ? n : null;
    }

    static Integer parseInt(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        try
        {
            return Integer.parseInt(raw.trim().replace(",", ""));
        }
        catch (NumberFormatException e)
        {
            return null; // "N/A", "∞", prose
        }
    }

    // wiki restock values are game ticks (0.6s each); negative means "unknown"
    static Integer ticksToSeconds(Integer ticks)
    {
        if (ticks == null || ticks < 0)
        {
            return null;
        }
        return (int) Math.round(ticks * 0.6);
    }

    static Integer sellPrice(String sellOverride, ItemInfoJson info, Integer sellMultiplier)
    {
        Integer explicit = parseInt(sellOverride);
        if (explicit != null)
        {
            return explicit;
        }
        if (sellOverride != null)
        {
            return null; // an explicit non-numeric override like "N/A" means "no price"
        }
        if (info == null || info.value == null || sellMultiplier == null)
        {
            return null;
        }
        return (int) Math.floor((long) info.value * sellMultiplier / 1000.0);
    }
}
