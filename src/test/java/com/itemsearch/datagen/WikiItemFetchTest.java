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

import java.util.Map;
import com.itemsearch.datagen.source.ItemInfoJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WikiItemFetchTest
{
    /**
     * The real Bronze arrow page. Four items live on it and the wiki names each one, so which id
     * belongs to which name is stated rather than guessed.
     */
    private static final String BRONZE_ARROW = "{{Infobox Item\n"
        + "|version1 = Unpoisoned\n|version2 = Poison\n|version3 = Poison+\n|version4 = Poison++\n"
        + "|name1 = Bronze arrow\n|name2 = Bronze arrow(p)\n"
        + "|name3 = Bronze arrow(p+)\n|name4 = Bronze arrow(p++)\n"
        + "|id1 = 882\n|id2 = 883\n|id3 = 5616\n|id4 = 5622\n"
        + "|value1 = 1\n|value2 = 2\n|value3 = 3\n|value4 = 4\n}}";

    @Test
    public void everyNamedVersionKeepsItsOwnId()
    {
        Map<String, ItemInfoJson> versions = WikiItemFetch.extractVersions(BRONZE_ARROW);
        assertEquals(Integer.valueOf(882), versions.get("Bronze arrow").id);
        assertEquals(Integer.valueOf(883), versions.get("Bronze arrow(p)").id);
        assertEquals("the poison+ arrow is not the unpoisoned one",
            Integer.valueOf(5616), versions.get("Bronze arrow(p+)").id);
        assertEquals(Integer.valueOf(5622), versions.get("Bronze arrow(p++)").id);
        assertEquals(Integer.valueOf(3), versions.get("Bronze arrow(p+)").value);
    }

    /** Doses work the same way, and the page title is not one of the item names. */
    @Test
    public void dosesAreNamedVersionsToo()
    {
        Map<String, ItemInfoJson> versions = WikiItemFetch.extractVersions("{{Infobox Item\n"
            + "|name1 = Prayer potion(1)\n|name2 = Prayer potion(2)\n"
            + "|name3 = Prayer potion(3)\n|name4 = Prayer potion(4)\n"
            + "|id1 = 143\n|id2 = 141\n|id3 = 139\n|id4 = 2434\n}}");
        assertEquals(Integer.valueOf(2434), versions.get("Prayer potion(4)").id);
        assertEquals(Integer.valueOf(143), versions.get("Prayer potion(1)").id);
        assertNull("the page title itself is not an item here", versions.get("Prayer potion"));
    }

    @Test
    public void aSingleVersionPageDeclaresNoVersions()
    {
        assertTrue(WikiItemFetch.extractVersions(
            "{{Infobox Item\n|name = Staff of air\n|id = 1381\n}}").isEmpty());
        assertTrue(WikiItemFetch.extractVersions("no infobox here").isEmpty());
        assertTrue(WikiItemFetch.extractVersions(null).isEmpty());
    }

    /** A version with a name but no id of its own is not worth an entry claiming id1's. */
    @Test
    public void aVersionWithoutAnIdIsOmitted()
    {
        Map<String, ItemInfoJson> versions = WikiItemFetch.extractVersions(
            "{{Infobox Item\n|name1 = Thing\n|name2 = Thing (broken)\n|id1 = 5\n}}");
        assertEquals(Integer.valueOf(5), versions.get("Thing").id);
        assertNull(versions.get("Thing (broken)"));
    }

    @Test
    public void extractsIdAndValue()
    {
        ItemInfoJson info = WikiItemFetch.extractInfobox(
            "{{Infobox Item\n|name = Staff of air\n|id = 1381\n|value = 1500\n}}");
        assertEquals(Integer.valueOf(1381), info.id);
        assertEquals(Integer.valueOf(1500), info.value);
    }

    @Test
    public void fallsBackToNumberedIdAndFirstOfCommaList()
    {
        ItemInfoJson info = WikiItemFetch.extractInfobox(
            "{{Infobox Item\n|name = Multi\n|id1 = 11840,24564\n|value = 100\n}}");
        assertEquals(Integer.valueOf(11840), info.id);
    }

    @Test
    public void missingFieldsBecomeNull()
    {
        ItemInfoJson info = WikiItemFetch.extractInfobox(
            "{{Infobox Item\n|name = Weird\n|value = free\n}}");
        assertNull(info.id);
        assertNull(info.value);
    }

    /** The examine line is the one wiki fact the item page shows that the client cannot supply. */
    @Test
    public void examineTextIsReadPerVersionAndForThePage()
    {
        String text = "{{Infobox Item\n|name = Bronze arrow\n|id = 882\n|value = 1\n"
            + "|examine = Arrows with [[bronze]] heads.\n}}";
        assertEquals("Arrows with bronze heads.", WikiItemFetch.extractInfobox(text).examine);

        String versioned = "{{Infobox Item\n|name1 = Prayer potion(4)\n|id1 = 2434\n"
            + "|examine1 = 4 doses of prayer restore potion.\n|name2 = Prayer potion(3)\n|id2 = 139\n"
            + "|name3 = Prayer potion(2)\n|id3 = 141\n|examine3 = \n"
            + "|examine = Prayer restore potion.\n}}";
        Map<String, ItemInfoJson> v = WikiItemFetch.extractVersions(versioned);
        assertEquals("4 doses of prayer restore potion.", v.get("Prayer potion(4)").examine);
        assertEquals("a version without its own line takes the page's",
            "Prayer restore potion.", v.get("Prayer potion(3)").examine);
        assertEquals("a blank numbered line falls back too",
            "Prayer restore potion.", v.get("Prayer potion(2)").examine);
        assertNull(WikiItemFetch.extractInfobox("{{Infobox Item\n|id = 1\n}}").examine);
    }

    @Test
    public void returnsNullWithoutInfobox()
    {
        assertNull(WikiItemFetch.extractInfobox("no infobox here"));
        assertNull(WikiItemFetch.extractInfobox(null));
    }
}
