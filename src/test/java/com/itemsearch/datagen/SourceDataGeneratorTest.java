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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.itemsearch.datagen.output.OutputCombat;
import com.itemsearch.datagen.output.OutputSource;
import com.itemsearch.datagen.source.CombatOverlayJson;
import com.itemsearch.datagen.source.WikiCombatJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SourceDataGeneratorTest
{
    /** {@code gap} is what the transform recorded when it parsed this source's own wiki version. */
    private static OutputSource source(int id, String name, String type, boolean gap)
    {
        OutputCombat combat = new OutputCombat(1, 1, 1, 1, 1, 5, new ArrayList<>());
        OutputSource s = new OutputSource(id, Collections.singletonList(id), name, 10, null,
            new ArrayList<>(), new ArrayList<>(), combat, type, null, null, null);
        s.combatGap = gap;
        return s;
    }

    @Test
    public void onlyKillSourcesWhoseOwnParseWasAmbiguousCountAsGaps()
    {
        Set<Integer> gaps = SourceDataGenerator.combatGapIds(Arrays.asList(
            source(1, "Gap Monster", "KILL", true),
            source(2, "Fine Monster", "KILL", false),
            // a chest can carry a gap flag from a shared page; it still has nothing to fight
            source(3, "Loot Chest", "REWARD", true),
            source(4, "Guard (Hosidius) (Bow)", "KILL", false)));

        assertTrue("an ambiguous KILL record is a gap", gaps.contains(1));
        assertFalse("an unambiguous record is not a gap", gaps.contains(2));
        assertFalse("non-KILL sources never enter the curator queue", gaps.contains(3));
        assertFalse("a version split judged by its own version is not a gap", gaps.contains(4));
        assertEquals(1, gaps.size());
    }

    /**
     * A stub seeded by an older, weaker parse must go once the parser can read the page: the
     * curator was asking for numbers the generator already had, and half its queue was that.
     */
    @Test
    public void aPendingStubWhoseGapHasClosedIsDropped()
    {
        CombatOverlayJson stale = new CombatOverlayJson();
        stale.attacks = new ArrayList<>();
        stale.needsReview = true;
        CombatOverlayJson curated = new CombatOverlayJson();
        curated.attacks = new ArrayList<>();
        curated.needsReview = false;
        CombatOverlayJson orphan = new CombatOverlayJson();
        orphan.attacks = new ArrayList<>();
        orphan.needsReview = true;
        Map<Integer, CombatOverlayJson> existing = new HashMap<>();
        existing.put(397, stale);   // Guard (Hosidius): parses cleanly now
        existing.put(2, curated);   // someone's work, gap or not
        existing.put(999, orphan);  // matches no source at all any more

        List<OutputSource> sources = Arrays.asList(
            source(397, "Guard (Hosidius) (Bow)", "KILL", false),
            source(2, "Curated Monster", "KILL", false));
        Map<Integer, CombatOverlayJson> seeded = MonsterTransform.seedStubs(
            sources, existing, SourceDataGenerator.combatGapIds(sources));

        assertFalse("closed gap, stub gone", seeded.containsKey(397));
        assertFalse("no source, stub gone", seeded.containsKey(999));
        assertTrue("curated entries are never pruned", seeded.containsKey(2));
    }

    @Test
    public void gapsSeedReviewStubsWithoutDisturbingCuratedEntries()
    {
        CombatOverlayJson curated = new CombatOverlayJson();
        curated.attacks = new ArrayList<>();
        curated.needsReview = false;
        Map<Integer, CombatOverlayJson> existing = new HashMap<>();
        existing.put(2, curated);

        List<OutputSource> sources = Arrays.asList(
            source(1, "Gap Monster", "KILL", true), source(2, "Curated Monster", "KILL", true));
        Map<Integer, CombatOverlayJson> seeded = MonsterTransform.seedStubs(
            sources, existing, SourceDataGenerator.combatGapIds(sources));

        assertTrue("a new gap becomes a review stub", seeded.get(1).needsReview);
        assertFalse("an already-curated entry is never re-flagged", seeded.get(2).needsReview);
    }
}
