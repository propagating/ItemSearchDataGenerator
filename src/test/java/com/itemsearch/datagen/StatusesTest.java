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
import java.util.Map;
import com.itemsearch.datagen.source.WikiCombatJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StatusesTest
{
    /** Zulrah: poison and venom at 100, cannon and thralls "No". One vocabulary for all four. */
    @Test
    public void everyWayTheWikiStatesAnImmunityReadsTheSame()
    {
        WikiCombatJson w = new WikiCombatJson();
        w.poisonResistance = "100";
        w.venomResistance = "100";
        w.freezeResistance = "50";
        w.immuneCannon = "No";
        w.immuneThrall = "Not immune";
        w.immuneBurn = "Immune to weak burns";
        Map<String, String> s = Statuses.of(w, -1);
        assertEquals("Immune", s.get("Poison"));
        assertEquals("Immune", s.get("Venom"));
        assertEquals("50% resistant", s.get("Freeze"));
        assertEquals("Susceptible", s.get("Cannon"));
        assertEquals("Susceptible", s.get("Thralls"));
        assertEquals("Immune to weak burns", s.get("Burn"));
        assertEquals("in display order", Arrays.asList("Poison", "Venom", "Freeze", "Cannon", "Thralls", "Burn"),
            new java.util.ArrayList<>(s.keySet()));
    }

    @Test
    public void shorthandAndJunkAreReadOrLeftOut()
    {
        assertEquals("Immune to strong burns", Statuses.burn("Strong"));
        assertEquals("Immune to normal burns", Statuses.burn("Immune to normal burn."));
        assertEquals("Susceptible", Statuses.burn("No"));
        assertEquals("Immune", Statuses.yesNo("yes"));
        assertEquals("Susceptible", Statuses.resistance("0"));
        assertEquals("Immune", Statuses.resistance("200"));
        assertNull("a word where a number belongs says nothing", Statuses.resistance("Poison"));
        assertNull(Statuses.yesNo(""));
        assertTrue(Statuses.of(null, -1).isEmpty());
        assertTrue("a page that says nothing yields nothing", Statuses.of(new WikiCombatJson(), -1).isEmpty());
    }

    @Test
    public void aVersionReadsItsOwnRowAndFallsBackToThePage()
    {
        WikiCombatJson w = new WikiCombatJson();
        w.immuneCannons = Arrays.asList("No", "Yes");
        w.poisonResistance = "100";
        assertEquals("Susceptible", Statuses.of(w, 0).get("Cannon"));
        assertEquals("Immune", Statuses.of(w, 1).get("Cannon"));
        assertEquals("the plain field serves every version", "Immune", Statuses.of(w, 1).get("Poison"));
        assertEquals("out of range takes the first row", "Susceptible", Statuses.of(w, 5).get("Cannon"));
    }
}
