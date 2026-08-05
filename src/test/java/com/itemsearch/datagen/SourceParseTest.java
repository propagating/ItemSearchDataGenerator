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
import com.itemsearch.datagen.source.MonsterJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SourceParseTest
{
    private Map<String, MonsterJson> parseSample()
    {
        Gson gson = new Gson();
        Type type = new TypeToken<Map<String, MonsterJson>>()
        {
        }.getType();
        return gson.fromJson(new InputStreamReader(
            getClass().getResourceAsStream("/monsters-sample.json"), StandardCharsets.UTF_8), type);
    }

    @Test
    public void mapsSnakeCaseFields()
    {
        MonsterJson goblin = parseSample().get("100");
        assertEquals(100, goblin.id);
        assertEquals("Test Goblin", goblin.name);
        assertEquals(2, goblin.combatLevel);
        assertEquals(3, goblin.drops.size());
    }

    @Test
    public void keepsNullRarityAsNull()
    {
        MonsterJson multi = parseSample().get("101");
        assertNull(multi.drops.get(0).rarity);
        assertEquals(3, multi.drops.get(1).rolls);
        assertTrue(multi.drops.get(1).noted);
        assertFalse(multi.drops.get(0).noted);
    }
}
