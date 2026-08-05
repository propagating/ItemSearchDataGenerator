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

import com.itemsearch.datagen.source.ShopPageJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WikiShopFetchTest
{
    private static final String ZAFF =
        "{{Infobox Shop\n|name = Zaff's Superior Staffs\n|location = [[Varrock]]\n|members = No\n}}\n"
        + "Some prose.\n"
        + "{{StoreTableHead|sellmultiplier=1000|buymultiplier=550|delta=2}}\n"
        + "{{StoreLine|name=Staff|stock=5|restock=100}}\n"
        + "{{StoreLine|name=Magic staff|stock=5|restock=100|sell=200}}\n"
        + "{{StoreLine|name=Battlestaff|stock=∞|restock=N/A}}\n"
        + "{{StoreLine|name=Mystery item}}\n"
        + "{{StoreLine|stock=9}}\n"
        + "{{StoreTableBottom}}\n";

    @Test
    public void parsesInfoboxLocationAndStoreLines()
    {
        ShopPageJson page = WikiShopFetch.extract("Zaff's Superior Staffs", ZAFF);
        assertEquals("Zaff's Superior Staffs", page.pageTitle);
        assertEquals("Varrock", page.location);
        assertEquals(1, page.tables.size());
        assertEquals("1000", page.tables.get(0).sellMultiplier);
        // nameless row dropped: 4 lines survive
        assertEquals(4, page.tables.get(0).lines.size());
        assertEquals("Staff", page.tables.get(0).lines.get(0).name);
        assertEquals("5", page.tables.get(0).lines.get(0).stock);
        assertEquals("100", page.tables.get(0).lines.get(0).restock);
        assertNull(page.tables.get(0).lines.get(0).sell);
        assertEquals("200", page.tables.get(0).lines.get(1).sell);
        assertEquals("∞", page.tables.get(0).lines.get(2).stock);
        assertNull(page.tables.get(0).lines.get(3).stock);
    }

    @Test
    public void defaultsCurrencyNullAndStripsLinkedCurrency()
    {
        ShopPageJson page = WikiShopFetch.extract("Zaff's Superior Staffs", ZAFF);
        assertNull(page.tables.get(0).currency);

        String tokkul = "{{StoreTableHead|sellmultiplier=1000|currency=[[Tokkul]]}}\n"
            + "{{StoreLine|name=Uncut opal|stock=1|restock=25}}\n{{StoreTableBottom}}";
        assertEquals("Tokkul", WikiShopFetch.extract("Shop", tokkul).tables.get(0).currency);
    }

    @Test
    public void splitsMultipleStoreTablesAndKeepsPipesInsideLinks()
    {
        String two = "{{Infobox Shop\n|location = [[Al Kharid|Al-Kharid]]\n}}\n"
            + "{{StoreTableHead|sellmultiplier=1300|namenotes=standard}}\n"
            + "{{StoreLine|name=Pot|stock=5|restock=10}}\n{{StoreTableBottom}}\n"
            + "{{StoreTableHead|sellmultiplier=1000}}\n"
            + "{{StoreLine|name=Jug|stock=2|restock=10}}\n{{StoreTableBottom}}\n";
        ShopPageJson page = WikiShopFetch.extract("General Store", two);
        assertEquals("Al-Kharid", page.location);
        assertEquals(2, page.tables.size());
        assertEquals("standard", page.tables.get(0).nameNotes);
        assertEquals("Jug", page.tables.get(1).lines.get(0).name);
    }

    @Test
    public void parsesTzhaarShopRowSynonymForStoreLine()
    {
        String tzhaar = "{{Infobox Shop\n|location = [[Mor Ul Rek]]\n}}\n"
            + "{{StoreTableHead|sellmultiplier=1500|buymultiplier=150|delta=20|currency=Tokkul}}\n"
            + "{{Tzhaar shop row|name=Toktz-xil-ul|stock=500|restock=10}}\n"
            + "{{Tzhaar shop row|name=Obsidian cape|stock=1|restock=100}}\n"
            + "{{StoreTableBottom}}\n";
        ShopPageJson page = WikiShopFetch.extract("TzHaar-Hur-Tel's Equipment Store", tzhaar);
        assertEquals("Mor Ul Rek", page.location);
        assertEquals(1, page.tables.size());
        assertEquals("Tokkul", page.tables.get(0).currency);
        assertEquals(2, page.tables.get(0).lines.size());
        assertEquals("Toktz-xil-ul", page.tables.get(0).lines.get(0).name);
        assertEquals("500", page.tables.get(0).lines.get(0).stock);
        assertEquals("Obsidian cape", page.tables.get(0).lines.get(1).name);
    }

    @Test
    public void skipsBucketNoTablesAsWikisOwnDedupMarker()
    {
        // one normal table + one bucket=No table (the wiki's marker that this table duplicates
        // the shop's listing elsewhere, e.g. Zaff (NPC page, bucket=No) vs
        // Zaff's Superior Staffs! (store page, no bucket) both listing the same stock)
        String page = "{{Infobox Shop\n|location = [[Varrock]]\n}}\n"
            + "{{StoreTableHead|sellmultiplier=1300|namenotes=standard}}\n"
            + "{{StoreLine|name=Pot|stock=5|restock=10}}\n{{StoreTableBottom}}\n"
            + "{{StoreTableHead|sellmultiplier=1000|bucket=No}}\n"
            + "{{StoreLine|name=Jug|stock=2|restock=10}}\n{{StoreTableBottom}}\n";
        ShopPageJson result = WikiShopFetch.extract("Shop assistant (Varrock)", page);
        assertEquals(1, result.tables.size());
        assertEquals("standard", result.tables.get(0).nameNotes);
        assertEquals("Pot", result.tables.get(0).lines.get(0).name);

        // a page whose only table is bucket=No has nothing left to report: null, like any
        // page with no usable store table
        String onlyBucketed = "{{StoreTableHead|sellmultiplier=1000|bucket=no}}\n"
            + "{{StoreLine|name=Jug|stock=2|restock=10}}\n{{StoreTableBottom}}\n";
        assertNull(WikiShopFetch.extract("Shop assistant (Lumbridge)", onlyBucketed));
    }

    @Test
    public void returnsNullWhenNoStoreTable()
    {
        assertNull(WikiShopFetch.extract("Some Page", "Just prose, no shop."));
        assertNull(WikiShopFetch.extract("Some Page", null));
    }

    @Test
    public void extractsTitlesFromEmbeddedinResponse()
    {
        com.google.gson.JsonObject response = new com.google.gson.Gson().fromJson(
            "{\"query\":{\"embeddedin\":[{\"pageid\":1,\"ns\":0,\"title\":\"Zaff's Superior Staffs\"},"
                + "{\"pageid\":2,\"ns\":0,\"title\":\"Aubury's Rune Shop\"}]}}",
            com.google.gson.JsonObject.class);
        assertEquals(java.util.Arrays.asList("Zaff's Superior Staffs", "Aubury's Rune Shop"),
            WikiShopFetch.titlesFrom(response));
    }
}
