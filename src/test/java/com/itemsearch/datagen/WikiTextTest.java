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

import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WikiTextTest
{
    @Test
    public void templatesAreBraceBalanced()
    {
        String wikitext = "{{DropsLine|name=Flax|quantity=1000 (noted)|rarity=10/249"
            + "|raritynotes={{Refn|group=d|Exact rarity is 10/249 * 5244/5264}}}}\n"
            + "{{DropsLine|name=Bones|quantity=1|rarity=Always}}";
        List<WikiText.Found> found = WikiText.templates(wikitext, "DropsLine");
        assertEquals(2, found.size());
        assertTrue(found.get(0).body.contains("{{Refn|group=d"));
        assertEquals("DropsLine", found.get(1).name);
    }

    @Test
    public void templatesRecordDocumentOffsetOfOpeningBraces()
    {
        String wikitext = "prose {{DropsLine|name=Bones}}";
        List<WikiText.Found> found = WikiText.templates(wikitext, "DropsLine");
        assertEquals(1, found.size());
        assertEquals(6, found.get(0).start);
    }

    @Test
    public void templatesFiltersByNameAndIgnoresUnmatchedTemplates()
    {
        String wikitext = "{{OtherTemplate|foo=bar}}{{DropsLine|name=Bones}}";
        List<WikiText.Found> found = WikiText.templates(wikitext, "DropsLine");
        assertEquals(1, found.size());
        assertEquals("name=Bones", found.get(0).body);
    }

    @Test
    public void paramsIgnorePipesInsideNestedTemplatesAndLinks()
    {
        Map<String, String> p = WikiText.params(
            "name=[[Al Kharid|Flax]]|rarity=10/249|raritynotes={{Refn|group=d|note}}|rolls=2");
        assertEquals("[[Al Kharid|Flax]]", p.get("name"));
        assertEquals("10/249", p.get("rarity"));
        assertEquals("{{Refn|group=d|note}}", p.get("raritynotes"));
        assertEquals("2", p.get("rolls"));
    }

    @Test
    public void paramsLowerCasesKeysAndIgnoresPositionalPieces()
    {
        Map<String, String> p = WikiText.params("positional|Name = Bones |Stock=5");
        assertEquals("Bones", p.get("name"));
        assertEquals("5", p.get("stock"));
        assertEquals(2, p.size());
    }

    @Test
    public void stripCommentsRemovesHtmlComments()
    {
        assertEquals("100-299", WikiText.stripComments("100-299<!--note: excluded-->"));
    }

    @Test
    public void stripCommentsRemovesMultipleSpans()
    {
        assertEquals("ab", WikiText.stripComments("a<!--one-->b<!--two-->"));
    }

    @Test
    public void stripLinksMatchesExistingBehavior()
    {
        // same fixture as WikiShopFetchTest's splitsMultipleStoreTablesAndKeepsPipesInsideLinks
        assertEquals("Al-Kharid", WikiText.stripLinks("[[Al Kharid|Al-Kharid]]"));
        assertEquals("Varrock", WikiText.stripLinks("[[Varrock]]"));
        assertNull(WikiText.stripLinks(null));
    }

    @Test
    public void fieldMatchesExistingWikiFetchBehavior()
    {
        // same fixture as WikiFetchTest#extractsInfoboxCombatFields
        String body = "\n|max hit1 = 60 (melee)\n|max hit2 = 35 (ranged)\n|attack speed = 6\n";
        assertEquals("6", WikiText.field(body, "attack speed"));
    }

    @Test
    public void numberedFieldsMatchesExistingWikiFetchBehavior()
    {
        // same fixture as WikiFetchTest#extractsNumberedVersionFields
        String body = "\n|attack style1 = [[Stab]]\n|attack style2 = [[Crush]]\n"
            + "|max hit1 = 1\n|max hit2 = 5\n|attack speed1 = 4\n|attack speed2 = 6\n";
        assertEquals(java.util.Arrays.asList("[[Stab]]", "[[Crush]]"),
            WikiText.numberedFields(body, "attack style"));
    }

    @Test
    public void mapCoordsMatchesExistingWikiShopFetchBehavior()
    {
        // same fixture as WikiShopFetchTest's parsesMapPlaneWhenPresent
        int[] coords = WikiText.mapCoords("{{Map|3100,3300|plane=1}}");
        assertEquals(3100, coords[0]);
        assertEquals(3300, coords[1]);
        assertEquals(1, coords[2]);
    }

    @Test
    public void mapCoordsDefaultsPlaneToZeroWhenAbsent()
    {
        int[] coords = WikiText.mapCoords("{{Map|3203,3434|mtype=rectangle}}");
        assertEquals(3203, coords[0]);
        assertEquals(3434, coords[1]);
        assertEquals(0, coords[2]);
    }

    @Test
    public void mapCoordsReturnsNullWhenNoCoordinatePair()
    {
        assertNull(WikiText.mapCoords("{{Map|mtype=rectangle}}"));
        assertNull(WikiText.mapCoords(null));
    }
}
