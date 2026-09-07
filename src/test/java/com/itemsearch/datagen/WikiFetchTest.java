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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import com.itemsearch.datagen.source.WikiCombatJson;
import com.itemsearch.datagen.source.WikiPageTextJson;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WikiFetchTest
{
    private String fixture(String name) throws IOException
    {
        try (InputStream in = getClass().getResourceAsStream("/" + name);
             Scanner s = new Scanner(in, StandardCharsets.UTF_8.name()))
        {
            return s.useDelimiter("\\A").hasNext() ? s.next() : "";
        }
    }

    @Test
    public void extractsInfoboxCombatFields() throws IOException
    {
        WikiCombatJson w = WikiFetch.extractInfobox(fixture("graardor-wikitext.txt"));
        assertEquals("60 (melee), 35 (ranged)", w.maxHit);
        assertEquals("[[Crush]], [[Ranged]]", w.attackStyle);
        assertEquals("6", w.attackSpeed);
    }

    @Test
    public void returnsNullWhenNoInfobox()
    {
        assertNull(WikiFetch.extractInfobox("Just some prose with no template."));
    }

    @Test
    public void extractsNumberedVersionFields()
    {
        String wikitext = "{{Infobox Monster\n|attack style1 = [[Stab]]\n|attack style2 = [[Crush]]\n"
            + "|max hit1 = 1\n|max hit2 = 5\n|attack speed1 = 4\n|attack speed2 = 6\n}}";
        WikiCombatJson w = WikiFetch.extractInfobox(wikitext);
        assertEquals(java.util.Arrays.asList("[[Stab]]", "[[Crush]]"), w.attackStyles);
        assertEquals(java.util.Arrays.asList("1", "5"), w.maxHits);
        assertEquals(java.util.Arrays.asList("4", "6"), w.attackSpeeds);
    }

    @Test
    public void pageTextsAreExtractedPerTitle()
    {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("Zulrah", "{{Infobox Monster\n|max hit = 41\n}}\n==Mechanics==\nrotates\n");
        raw.put("Chicken", "");
        Map<String, WikiPageTextJson> texts = WikiFetch.pageTexts(raw);
        assertEquals("==Mechanics==\nrotates", texts.get("Zulrah").mechanics);
        assertEquals(1, texts.size());
    }

    @Test
    public void strategySubpagesAreAskedForOnlyWhenThePageSaidNothing()
    {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("Zulrah", "{{Infobox Monster\n|max hit = 41\n}}\n");
        raw.put("Chicken", "{{Infobox Monster\n|max hit = 1\n}}\n==Strategy==\nhit it\n");
        Map<String, WikiPageTextJson> texts = WikiFetch.pageTexts(raw);
        assertEquals(Arrays.asList("Zulrah/Strategies"), WikiFetch.strategySubpagesFor(texts));
    }
}
