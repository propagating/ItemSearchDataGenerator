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

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class AttackEffectsTest
{
    /**
     * The wiki records this per monster rather than per attack, in free text:
     * "Yes (6)" for a Saradomin wizard, "Yes ([[venom]])" for Zulrah.
     */
    @Test
    public void readsPoisonAndVenomFromThePoisonousField()
    {
        assertEquals(Collections.singletonList("POISON"), AttackEffects.fromPoisonous("Yes (6)"));
        assertEquals(Collections.singletonList("VENOM"),
            AttackEffects.fromPoisonous("Yes ([[venom]])"));
        assertEquals(Collections.singletonList("POISON"), AttackEffects.fromPoisonous("yes"));
    }

    @Test
    public void aMonsterThatDoesNotPoisonHasNoEffect()
    {
        assertTrue(AttackEffects.fromPoisonous("No").isEmpty());
        assertTrue(AttackEffects.fromPoisonous("").isEmpty());
        assertTrue(AttackEffects.fromPoisonous(null).isEmpty());
    }

    /** What the curator types, so effects the wiki does not record can still be set. */
    @Test
    public void parsesCuratedEffectTokens()
    {
        assertEquals(Arrays.asList("POISON", "DRAIN_PRAYER"),
            AttackEffects.parse("poison, drain prayer"));
        assertEquals(Collections.singletonList("DRAIN_STATS"), AttackEffects.parse("Drain Stats"));
        assertEquals(Collections.singletonList("DISEASE"), AttackEffects.parse("disease"));
        assertTrue("an unknown word is dropped rather than shipped as a label",
            AttackEffects.parse("sunburn").isEmpty());
        assertTrue(AttackEffects.parse("").isEmpty());
    }

    @Test
    public void roundTripsBackToWhatTheCuratorTyped()
    {
        assertEquals("poison, drain prayer",
            AttackEffects.format(Arrays.asList("POISON", "DRAIN_PRAYER")));
        assertEquals("", AttackEffects.format(Collections.emptyList()));
    }
}
