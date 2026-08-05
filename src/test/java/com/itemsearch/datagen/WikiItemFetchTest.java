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

import com.itemsearch.datagen.source.ItemInfoJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WikiItemFetchTest
{
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

    @Test
    public void returnsNullWithoutInfobox()
    {
        assertNull(WikiItemFetch.extractInfobox("no infobox here"));
        assertNull(WikiItemFetch.extractInfobox(null));
    }
}
