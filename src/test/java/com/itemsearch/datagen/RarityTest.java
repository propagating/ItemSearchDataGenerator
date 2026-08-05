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

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class RarityTest
{
    @Test
    public void parsesKnownForms()
    {
        assertEquals(1.0, Rarity.parse("Always"), 1e-12);
        assertEquals(1.0 / 1024, Rarity.parse("1/1024"), 1e-12);
        assertEquals(10.0 / 249, Rarity.parse("10/249"), 1e-12);
        assertEquals(1.0 / 127.5, Rarity.parse("1/127.5"), 1e-12);
        assertEquals(3.0 / 128, Rarity.parse("~3/128"), 1e-12);
        assertEquals(1.0 / 5000, Rarity.parse("1/5,000"), 1e-12);
    }

    @Test
    public void unknownFormsReturnNull()
    {
        assertNull(Rarity.parse("Common"));
        assertNull(Rarity.parse("Varies"));
        assertNull(Rarity.parse(null));
        assertNull(Rarity.parse(""));
        assertNull(Rarity.parse("2 x 1/128"));
        assertNull(Rarity.parse("5/4")); // > 1 is nonsense
        assertNull(Rarity.parse("0/128"));
    }
}
