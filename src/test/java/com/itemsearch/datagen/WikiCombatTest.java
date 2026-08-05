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
import com.google.gson.reflect.TypeToken;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import com.itemsearch.datagen.source.WikiCombatJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WikiCombatTest
{
    private String sample()
    {
        Type type = new TypeToken<Map<String, WikiCombatJson>>()
        {
        }.getType();
        return new Gson().toJson(new Gson().fromJson(new InputStreamReader(
            getClass().getResourceAsStream("/wiki-combat-sample.json"), StandardCharsets.UTF_8), type), type);
    }

    @Test
    public void parseIsTolerantOfEmpty()
    {
        assertTrue(WikiCombat.parse(new Gson(), null).isEmpty());
        assertTrue(WikiCombat.parse(new Gson(), "").isEmpty());
    }

    @Test
    public void graardorParsesToDistinctPerStyleHits()
    {
        Map<String, WikiCombatJson> map = WikiCombat.parse(new Gson(), sample());
        CombatParser.ParsedCombat pc = WikiCombat.toAttacks(map.get("General Graardor"));
        assertFalse(pc.gap);
        assertEquals(60, pc.attacks.get(0).maxHit);
        assertEquals("crush", pc.attacks.get(0).style);
        assertEquals(35, pc.attacks.get(1).maxHit);
    }

    @Test
    public void zukIsAGapWithNumberedSpeeds()
    {
        Map<String, WikiCombatJson> map = WikiCombat.parse(new Gson(), sample());
        CombatParser.ParsedCombat pc = WikiCombat.toAttacks(map.get("TzKal-Zuk"));
        assertTrue(pc.gap);
        assertEquals(10, pc.attacks.get(0).speed);
        assertEquals(7, pc.attacks.get(1).speed);
    }

    @Test
    public void pageTitleStripsAnchorAndDecodesPath()
    {
        assertEquals("General Graardor",
            WikiCombat.pageTitle("https://oldschool.runescape.wiki/w/General_Graardor#Normal"));
        assertEquals("Goblin (Goblin Village)",
            WikiCombat.pageTitle("https://oldschool.runescape.wiki/w/Goblin_(Goblin_Village)#Red"));
        assertEquals("Cerberus",
            WikiCombat.pageTitle("https://oldschool.runescape.wiki/w/Cerberus"));
        assertNull(WikiCombat.pageTitle(null));
    }
}
