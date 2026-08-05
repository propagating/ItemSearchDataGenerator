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

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import com.itemsearch.datagen.output.OutputShop;
import com.itemsearch.datagen.source.ItemInfoJson;
import com.itemsearch.datagen.source.ShopLineJson;
import com.itemsearch.datagen.source.ShopPageJson;
import com.itemsearch.datagen.source.ShopTableJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ShopTransformTest
{
    private static ShopLineJson line(String name, String stock, String restock, String sell)
    {
        ShopLineJson l = new ShopLineJson();
        l.name = name;
        l.stock = stock;
        l.restock = restock;
        l.sell = sell;
        return l;
    }

    private static ShopPageJson page(String title, String location, ShopTableJson... tables)
    {
        ShopPageJson p = new ShopPageJson();
        p.pageTitle = title;
        p.location = location;
        for (ShopTableJson t : tables)
        {
            p.tables.add(t);
        }
        return p;
    }

    private static ShopTableJson table(String currency, String sellMultiplier, ShopLineJson... lines)
    {
        ShopTableJson t = new ShopTableJson();
        t.currency = currency;
        t.sellMultiplier = sellMultiplier;
        for (ShopLineJson l : lines)
        {
            t.lines.add(l);
        }
        return t;
    }

    private static ItemInfoJson info(Integer id, Integer value)
    {
        ItemInfoJson i = new ItemInfoJson();
        i.id = id;
        i.value = value;
        return i;
    }

    @Test
    public void transformsValuesStockRestockAndDerivedPrice()
    {
        Map<String, ShopPageJson> pages = new TreeMap<>();
        pages.put("Zaff's Superior Staffs", page("Zaff's Superior Staffs", "Varrock",
            table(null, "1300",
                line("Staff", "5", "100", null),        // derived: floor(200*1300/1000)=260
                line("Magic staff", "0", "N/A", "500"), // explicit sell override, 0 stock kept
                line("Battlestaff", "∞", null, null),   // infinite stock, no value -> null price
                line("Unknown thing", "junk", "N/A", "N/A"))));
        Map<String, ItemInfoJson> items = new HashMap<>();
        items.put("Staff", info(1379, 200));
        items.put("Magic staff", info(1389, 300));
        Map<String, Integer> fallback = new HashMap<>();
        fallback.put("battlestaff", 1391);

        List<OutputShop> shops = ShopTransform.transform(pages, items, fallback);
        assertEquals(1, shops.size());
        OutputShop shop = shops.get(0);
        assertEquals("Zaff's Superior Staffs", shop.name);
        assertEquals("Varrock", shop.location);
        assertEquals("Coins", shop.currency); // null currency defaults
        assertEquals(4, shop.items.size());   // 0-stock line KEPT in data

        assertEquals(1379, shop.items.get(0).itemId);
        assertEquals(Integer.valueOf(5), shop.items.get(0).stock);
        assertEquals(Integer.valueOf(60), shop.items.get(0).restockSeconds); // 100 ticks * 0.6
        assertEquals(Integer.valueOf(260), shop.items.get(0).sellPrice);

        assertEquals(Integer.valueOf(500), shop.items.get(1).sellPrice); // override wins
        assertEquals(Integer.valueOf(0), shop.items.get(1).stock);
        assertNull(shop.items.get(1).restockSeconds);

        assertEquals(1391, shop.items.get(2).itemId); // fallback id, case-insensitive
        assertEquals(Integer.valueOf(-1), shop.items.get(2).stock); // ∞ -> -1
        assertNull(shop.items.get(2).sellPrice);

        assertEquals(-1, shop.items.get(3).itemId); // unresolvable
        assertNull(shop.items.get(3).stock);        // junk -> unknown
    }

    @Test
    public void multiTablePagesBecomeNumberedShopsAndSortByName()
    {
        Map<String, ShopPageJson> pages = new TreeMap<>();
        pages.put("Zeta", page("Zeta", null, table(null, null, line("Pot", "1", null, null)),
            table("Tokkul", null, line("Jug", "1", null, null))));
        pages.put("Alpha", page("Alpha", null, table(null, null, line("Pot", "1", null, null))));

        List<OutputShop> shops = ShopTransform.transform(pages,
            Collections.emptyMap(), Collections.emptyMap());
        assertEquals(3, shops.size());
        assertEquals("Alpha", shops.get(0).name);
        assertEquals("Zeta", shops.get(1).name);
        assertEquals("Zeta (2)", shops.get(2).name);
        assertEquals("Tokkul", shops.get(2).currency);
    }
}
