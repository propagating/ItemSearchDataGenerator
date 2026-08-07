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
import com.itemsearch.datagen.source.WikiCombatJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WeaknessTest
{
    private static WikiCombatJson defences(String stab, String slash, String crush,
        String magic, String ranged)
    {
        WikiCombatJson w = new WikiCombatJson();
        w.defStab = stab;
        w.defSlash = slash;
        w.defCrush = crush;
        w.defMagic = magic;
        w.defRanged = ranged;
        return w;
    }

    /**
     * The wiki records defence bonuses, not weaknesses: the lower the number, the less it defends.
     * A steel dragon's 0 against stab is the reason everyone brings a stab weapon.
     */
    @Test
    public void theWeakestDefenceIsTheWeakness()
    {
        assertEquals(Arrays.asList("STAB"),
            Weakness.weakestOf(defences("0", "70", "70", "30", "90")));
    }

    /** Two styles it defends equally badly against are both worth naming. */
    @Test
    public void equallyWeakStylesAreAllNamed()
    {
        assertEquals(Arrays.asList("SLASH", "CRUSH"),
            Weakness.weakestOf(defences("50", "10", "10", "60", "70")));
    }

    /** Negative bonuses are weaker still, not stronger. */
    @Test
    public void aNegativeBonusIsTheWeakestOfAll()
    {
        assertEquals(Arrays.asList("MAGIC"),
            Weakness.weakestOf(defences("0", "0", "0", "-45", "50")));
    }

    /**
     * A monster that defends every style identically has no weakness worth naming — saying
     * "weak to everything" would be worse than saying nothing.
     */
    @Test
    public void uniformDefenceIsNoWeakness()
    {
        assertTrue(Weakness.weakestOf(defences("40", "40", "40", "40", "40")).isEmpty());
    }

    @Test
    public void missingOrJunkNumbersYieldNothing()
    {
        assertTrue(Weakness.weakestOf(defences(null, null, null, null, null)).isEmpty());
        assertTrue(Weakness.weakestOf(defences("n/a", "", "?", null, "x")).isEmpty());
        assertTrue(Weakness.weakestOf(null).isEmpty());
    }

    /** Versioned pages number their fields; the first version stands for the source. */
    @Test
    public void aVersionedPageReadsItsFirstVersion()
    {
        WikiCombatJson w = new WikiCombatJson();
        w.defStabs = Arrays.asList("26", "66");
        w.defSlashes = Arrays.asList("108", "126");
        w.defCrushes = Arrays.asList("108", "126");
        w.defMagics = Arrays.asList("240", "204");
        w.defRangeds = Arrays.asList("26", "80");
        // Vorkath: stab and ranged are its 26s, magic its 240
        assertEquals(Arrays.asList("STAB", "RANGED"), Weakness.weakestOf(w));
    }

    /** "+50% from Earth spells" is stated outright rather than inferred. */
    @Test
    public void theElementalWeaknessIsReadStraightOff()
    {
        WikiCombatJson w = new WikiCombatJson();
        w.elementalWeaknessType = "Earth";
        w.elementalWeaknessPercent = "50";
        assertEquals("Earth", Weakness.elementOf(w));
        assertEquals(Integer.valueOf(50), Weakness.elementPercentOf(w));
    }

    @Test
    public void noElementalWeaknessIsNull()
    {
        assertNull(Weakness.elementOf(new WikiCombatJson()));
        assertNull(Weakness.elementPercentOf(new WikiCombatJson()));
        assertNull(Weakness.elementOf(null));
    }
}
