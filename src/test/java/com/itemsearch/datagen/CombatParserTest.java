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
import java.util.List;
import com.itemsearch.datagen.output.OutputAttack;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CombatParserTest
{
    /**
     * "Magical melee" is one attack that rolls magic accuracy but is blocked by Protect from
     * Melee. Left unresolved it reaches the client as an unknown style, which renders as
     * "no prayer protects against this attack" — the opposite of the truth.
     */
    @Test
    public void hybridStylesSplitIntoAccuracyAndPrayerStyle()
    {
        OutputAttack a = CombatParser.assemble(
            CombatParser.parseStyles("[[Magical melee]]"),
            CombatParser.parseMaxHits("30"), Arrays.asList(4)).attacks.get(0);
        assertEquals("accuracy roll", "magic", a.style);
        assertEquals("prayed against as", "melee", a.prayStyle);

        OutputAttack r = CombatParser.assemble(
            CombatParser.parseStyles("[[Magical ranged]]"),
            CombatParser.parseMaxHits("30"), Arrays.asList(4)).attacks.get(0);
        assertEquals("magic", r.style);
        assertEquals("ranged", r.prayStyle);

        // word order carries the meaning: first is the roll, second the defence
        OutputAttack rm = CombatParser.assemble(
            CombatParser.parseStyles("Ranged magic"),
            CombatParser.parseMaxHits("30"), Arrays.asList(4)).attacks.get(0);
        assertEquals("ranged", rm.style);
        assertEquals("magic", rm.prayStyle);
    }

    @Test
    public void plainStylesKeepNoPrayerStyle()
    {
        for (String plain : new String[]{"[[Crush]]", "[[Magic]]", "Melee", "[[Dragonfire]]"})
        {
            OutputAttack a = CombatParser.assemble(CombatParser.parseStyles(plain),
                CombatParser.parseMaxHits("10"), Arrays.asList(4)).attacks.get(0);
            assertNull(plain + " is not a hybrid", a.prayStyle);
        }
    }

    @Test
    public void hybridMixesWithPlainStylesOnOnePage()
    {
        CombatParser.ParsedCombat pc = CombatParser.assemble(
            CombatParser.parseStyles("[[Stab]], [[Magical ranged]]"),
            CombatParser.parseMaxHits("20 (melee), 30 (magic)"), Arrays.asList(4));
        assertEquals(2, pc.attacks.size());
        assertEquals("stab", pc.attacks.get(0).style);
        assertNull(pc.attacks.get(0).prayStyle);
        assertEquals("magic", pc.attacks.get(1).style);
        assertEquals("ranged", pc.attacks.get(1).prayStyle);
    }

    @Test
    public void parsesWikiLinkedStylesLowercased()
    {
        assertEquals(Arrays.asList("crush", "ranged"),
            CombatParser.parseStyles("[[Crush]], [[Ranged]]"));
    }

    @Test
    public void normalisesTypelessAndConcatenatesNumberedStyleFields()
    {
        assertEquals(Arrays.asList("typeless", "typeless"),
            CombatParser.parseStyles("Typeless Magic, Typeless Ranged"));
        assertEquals(Arrays.asList("stab", "magic"),
            CombatParser.parseStyles("[[Stab]]", "[[Magic]]"));
    }

    @Test
    public void parsesAnnotatedMaxHits()
    {
        List<CombatParser.MaxHit> hits = CombatParser.parseMaxHits("60 (melee), 35 (ranged)");
        assertEquals(2, hits.size());
        assertEquals(60, hits.get(0).value);
        assertEquals("melee", hits.get(0).annotation);
        assertEquals(35, hits.get(1).value);
        assertEquals("ranged", hits.get(1).annotation);
    }

    @Test
    public void parsesSingleUnannotatedMaxHit()
    {
        List<CombatParser.MaxHit> hits = CombatParser.parseMaxHits("12");
        assertEquals(1, hits.size());
        assertEquals(12, hits.get(0).value);
        assertEquals(null, hits.get(0).annotation);
    }

    @Test
    public void oversizedDigitRunsAreSkippedNotCrashing()
    {
        // real wiki data had a max-hit field with a 19-digit token that overflows int
        assertTrue(CombatParser.parseMaxHits("1706977187717320812").isEmpty());
        List<CombatParser.MaxHit> mixed = CombatParser.parseMaxHits("9 1706977187717320812");
        assertEquals(1, mixed.size());
        assertEquals(9, mixed.get(0).value);
    }

    @Test
    public void parsesNumberedSpeeds()
    {
        assertEquals(Arrays.asList(10, 7), CombatParser.parseSpeeds("10", "7"));
        assertEquals(Arrays.asList(6), CombatParser.parseSpeeds("6", null, ""));
    }

    @Test
    public void oversizedSpeedTokensAreSkipped()
    {
        // a speed field whose first digit-run overflows int must not crash; skip it
        assertTrue(CombatParser.parseSpeeds("1706977187717320812").isEmpty());
        assertEquals(java.util.Arrays.asList(6), CombatParser.parseSpeeds("6", "1706977187717320812"));
    }

    @Test
    public void assemblesGraardorPerStyleNotAGap()
    {
        CombatParser.ParsedCombat pc = CombatParser.assemble(
            Arrays.asList("crush", "ranged"),
            CombatParser.parseMaxHits("60 (melee), 35 (ranged)"),
            Arrays.asList(6));
        assertFalse(pc.gap);
        assertEquals(2, pc.attacks.size());
        OutputAttack crush = pc.attacks.get(0);
        assertEquals("crush", crush.style);
        assertEquals(60, crush.maxHit);   // "melee" annotation matches the crush (melee-class) style
        assertEquals(6, crush.speed);     // single speed applies to all styles
        assertEquals(35, pc.attacks.get(1).maxHit);
    }

    @Test
    public void assemblesPerStyleSpeedsPositionally()
    {
        CombatParser.ParsedCombat pc = CombatParser.assemble(
            Arrays.asList("magic", "ranged"),
            CombatParser.parseMaxHits("128 (magic), 169 (ranged)"),
            Arrays.asList(10, 7));
        assertEquals(10, pc.attacks.get(0).speed);
        assertEquals(7, pc.attacks.get(1).speed);
    }

    @Test
    public void singleStyleSingleHitIsNotAGap()
    {
        CombatParser.ParsedCombat pc = CombatParser.assemble(
            Arrays.asList("crush"), CombatParser.parseMaxHits("9"), Arrays.asList(5));
        assertFalse(pc.gap);
        assertEquals(9, pc.attacks.get(0).maxHit);
    }

    @Test
    public void multiStyleSingleUnannotatedHitIsAGapButStillAssigns()
    {
        CombatParser.ParsedCombat pc = CombatParser.assemble(
            Arrays.asList("crush", "magic"), CombatParser.parseMaxHits("30"), Arrays.asList(4));
        assertTrue(pc.gap);
        assertEquals(30, pc.attacks.get(0).maxHit); // best-guess: single value under both
        assertEquals(30, pc.attacks.get(1).maxHit);
    }

    @Test
    public void proseOnlyMismatchedAnnotationIsAGapWithZeroHits()
    {
        // Zuk: two typeless styles, infobox max hit "148 (projectile attack)"
        CombatParser.ParsedCombat pc = CombatParser.assemble(
            Arrays.asList("typeless", "typeless"),
            CombatParser.parseMaxHits("148 (projectile attack)"),
            Arrays.asList(10, 7));
        assertTrue(pc.gap);
        assertEquals(0, pc.attacks.get(0).maxHit); // "projectile" (ranged-class) matches neither typeless style
    }

    @Test
    public void wikiLinkedMaxHitAnnotationsMatchStyles()
    {
        CombatParser.ParsedCombat pc = CombatParser.assemble(
            java.util.Arrays.asList("stab", "magic", "ranged"),
            CombatParser.parseMaxHits("97 ([[Melee]]), 95 ([[Magic]]), 97 ([[Ranged]])"),
            java.util.Arrays.asList(8));
        assertFalse(pc.gap);
        // stab is melee-class -> takes the [[Melee]] hit (97); magic -> 95; ranged -> 97
        assertEquals(97, pc.attacks.get(0).maxHit);
        assertEquals(95, pc.attacks.get(1).maxHit);
        assertEquals(97, pc.attacks.get(2).maxHit);
    }

    @Test
    public void collapseVersionsFoldsEachStyleIntoAMinMaxRange()
    {
        // three versions: stab(mh 1, spd 4), crush(mh 5, spd 6), stab(mh 3, spd 5)
        CombatParser.ParsedCombat pc = CombatParser.collapseVersions(
            Arrays.asList("[[Stab]]", "[[Crush]]", "[[Stab]]"),
            Arrays.asList("1", "5", "3"),
            Arrays.asList("4", "6", "5"));
        assertFalse(pc.gap);
        assertEquals(2, pc.attacks.size()); // the two stab versions collapse to one style
        OutputAttack stab = pc.attacks.get(0);
        assertEquals("stab", stab.style);
        assertEquals(1, stab.maxHit);
        assertEquals(3, stab.maxHitMax);
        assertEquals(4, stab.speed);
        assertEquals(5, stab.speedMax);
        OutputAttack crush = pc.attacks.get(1);
        assertEquals(5, crush.maxHit);
        assertEquals(5, crush.maxHitMax); // single version -> low == high
    }

    @Test
    public void collapseVersionsWithoutMaxHitsIsAGap()
    {
        CombatParser.ParsedCombat pc = CombatParser.collapseVersions(
            Arrays.asList("[[Stab]]", "[[Stab]]"),
            Arrays.asList(),
            Arrays.asList("4", "6"));
        assertTrue(pc.gap);
        assertEquals(1, pc.attacks.size());
        assertEquals(0, pc.attacks.get(0).maxHit);
        assertEquals(4, pc.attacks.get(0).speed);
        assertEquals(6, pc.attacks.get(0).speedMax);
    }
}
