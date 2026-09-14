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
import com.itemsearch.datagen.source.ThievingJson;
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

    /** The shopkeeper rides along, so the client can join the shop to the NPC it belongs to. */
    @Test
    public void theOwnerCarriesOntoEveryShopOfThePage()
    {
        Map<String, ShopPageJson> pages = new TreeMap<>();
        ShopPageJson p = page("Zaff's Superior Staffs", "Varrock",
            table(null, "1000", line("Staff", "5", "100", null)),
            table("Tokkul", "1000", line("Staff", "5", "100", null)));
        p.owner = "Zaff";
        pages.put(p.pageTitle, p);
        List<OutputShop> shops = ShopTransform.transform(pages, Collections.emptyMap(),
            Collections.emptyMap());
        assertEquals(2, shops.size());
        assertEquals("Zaff", shops.get(0).owner);
        assertEquals("Zaff", shops.get(1).owner);
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
    public void trailingPeriodsAreStrippedFromShopNames()
    {
        Map<String, ShopPageJson> pages = new TreeMap<>();
        pages.put("Diango's Toy Store.", page("Diango's Toy Store.", "Draynor",
            table(null, null, line("Toy horsey", "5", null, null)),
            table(null, null, line("Toy soldier", "5", null, null))));

        List<OutputShop> shops = ShopTransform.transform(pages,
            Collections.emptyMap(), Collections.emptyMap());
        assertEquals("Diango's Toy Store", shops.get(0).name);
        assertEquals("Diango's Toy Store (2)", shops.get(1).name); // suffix after stripping
    }

    @Test
    public void mapCoordinatesCarryThroughToOutput()
    {
        Map<String, ShopPageJson> pages = new TreeMap<>();
        ShopPageJson ground = page("Zaff", "Varrock", table(null, null, line("Pot", "1", null, null)));
        ground.mapX = "3203";
        ground.mapY = "3434";
        pages.put("Zaff", ground);
        ShopPageJson upstairs = page("Upstairs", null, table(null, null, line("Jug", "1", null, null)));
        upstairs.mapX = "3100";
        upstairs.mapY = "3300";
        upstairs.mapPlane = "1";
        upstairs.mapId = "31";
        pages.put("Upstairs", upstairs);
        pages.put("Nowhere", page("Nowhere", null, table(null, null, line("Pot", "1", null, null))));

        List<OutputShop> shops = ShopTransform.transform(pages,
            Collections.emptyMap(), Collections.emptyMap());
        assertEquals("Nowhere", shops.get(0).name);
        assertNull(shops.get(0).x);
        assertNull(shops.get(0).plane);
        assertEquals("Upstairs", shops.get(1).name);
        assertEquals(Integer.valueOf(3100), shops.get(1).x);
        assertEquals(Integer.valueOf(3300), shops.get(1).y);
        assertEquals(Integer.valueOf(1), shops.get(1).plane);
        assertEquals(Integer.valueOf(31), shops.get(1).mapId);
        assertEquals("Zaff", shops.get(2).name);
        assertEquals(Integer.valueOf(3203), shops.get(2).x);
        assertEquals(Integer.valueOf(0), shops.get(2).plane); // coords present -> ground level
        assertNull(shops.get(2).mapId);
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

    private static Map<String, ThievingJson> stalls()
    {
        Map<String, ThievingJson> rows = new HashMap<>();
        rows.put("bakery stall", new ThievingJson(5, 16.0, "Bakery stall",
            java.util.Arrays.asList("East Ardougne", "Keldagrim", "Great Kourend", "Civitas illa Fortis")));
        rows.put("fish stall", new ThievingJson(42, 42.0, "Fish stall",
            java.util.Arrays.asList("Etceteria", "Rellekka")));
        rows.put("fish stall (port roberts)", new ThievingJson(42, 49.0, "Fish stall (Port Roberts)",
            java.util.Collections.singletonList("Port Roberts")));
        rows.put("master farmer", new ThievingJson(38, 43.0, "Master Farmer", null));
        return rows;
    }

    private static OutputShop stallShop(String title, String location, String owner)
    {
        Map<String, ShopPageJson> pages = new TreeMap<>();
        ShopPageJson p = page(title, location, table(null, "1000", line("Bread", "10", "100", null)));
        p.owner = owner;
        pages.put(title, p);
        return ShopTransform.transform(pages, Collections.emptyMap(), Collections.emptyMap(), stalls()).get(0);
    }

    /**
     * The wiki keeps a stall's shop page and its Thieving page apart. A shop whose name ends in
     * a kind of stall, in a place where the stalls table puts such a stall, is that stall.
     */
    @Test
    public void aShopThatIsAStallIsMarkedWithTheStall()
    {
        assertEquals("Bakery stall", stallShop("Ardougne Baker's Stall.", "East Ardougne", "Baker").stall);
        assertEquals("Kourend Castle is in Great Kourend",
            "Bakery stall", stallShop("Kourend Castle Baker's Stall", "Kourend Castle", "Baker").stall);
        assertEquals("the stalls of Port Roberts have rows of their own",
            "Fish stall (Port Roberts)", stallShop("Port Roberts Fish Stall", "Port Roberts", "Gregory").stall);
        assertEquals("with no place given, the shop's name says where it is",
            "Fish stall (Port Roberts)", stallShop("Port Roberts Fish Stall", null, null).stall);
        assertEquals("the owner still rides along", "Baker",
            stallShop("Ardougne Baker's Stall.", "East Ardougne", "Baker").owner);
    }

    @Test
    public void aShopThatOnlySoundsLikeAStallIsNot()
    {
        assertNull("no Bakery stall stands in Sophanem",
            stallShop("Nathifa's Bake Stall", "Sophanem", "Nathifa").stall);
        assertNull("a fishing supplies stall is not a fish stall anywhere the fish stalls are",
            stallShop("Elder Krill's Fishing Stall", "The Summer Shore", "Elder Krill").stall);
        assertNull("not a stall at all", stallShop("Zaff's Superior Staffs", "Varrock", "Zaff").stall);
        assertNull("without the tables nothing is a stall",
            ShopTransform.transform(Collections.singletonMap("Ardougne Baker's Stall",
                page("Ardougne Baker's Stall", "East Ardougne", table(null, "1000", line("Bread", "1", "1", null)))),
                Collections.emptyMap(), Collections.emptyMap()).get(0).stall);
    }
}
