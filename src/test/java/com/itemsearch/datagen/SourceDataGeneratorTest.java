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
    private static OutputSource source(int id, String name, String type)
    {
        OutputCombat combat = new OutputCombat(1, 1, 1, 1, 1, 5, new ArrayList<>());
        return new OutputSource(id, Collections.singletonList(id), name, 10, null,
            new ArrayList<>(), new ArrayList<>(), combat, type, null, null, null);
    }

    /**
     * A gap: several attack styles, but a single unattributed max hit, so the parser cannot say
     * which style hits how hard. Resolving that is exactly what the curator is for.
     */
    private static WikiCombatJson ambiguous()
    {
        WikiCombatJson w = new WikiCombatJson();
        w.attackStyle = "Melee, Magic";
        w.maxHit = "20";
        w.attackSpeed = "4";
        return w;
    }

    /** One style with its own max hit — nothing to disambiguate. */
    private static WikiCombatJson unambiguous()
    {
        WikiCombatJson w = new WikiCombatJson();
        w.attackStyle = "Melee";
        w.maxHit = "12";
        w.attackSpeed = "4";
        return w;
    }

    @Test
    public void onlyKillSourcesWithUnparseableCombatCountAsGaps()
    {
        Map<String, WikiCombatJson> wiki = new HashMap<>();
        wiki.put("Gap Monster", ambiguous());
        wiki.put("Fine Monster", unambiguous());
        // a chest can share a page name with wiki combat data; it still has nothing to fight
        wiki.put("Loot Chest", ambiguous());

        Set<Integer> gaps = SourceDataGenerator.combatGapIds(Arrays.asList(
            source(1, "Gap Monster", "KILL"),
            source(2, "Fine Monster", "KILL"),
            source(3, "Loot Chest", "REWARD"),
            source(4, "Unlisted Monster", "KILL")), wiki);

        assertTrue("an ambiguous KILL record is a gap", gaps.contains(1));
        assertFalse("an unambiguous record is not a gap", gaps.contains(2));
        assertFalse("non-KILL sources never enter the curator queue", gaps.contains(3));
        assertFalse("a source with no wiki record at all is not a gap", gaps.contains(4));
        assertEquals(1, gaps.size());
    }

    @Test
    public void gapsSeedReviewStubsWithoutDisturbingCuratedEntries()
    {
        Map<String, WikiCombatJson> wiki = new HashMap<>();
        wiki.put("Gap Monster", ambiguous());
        wiki.put("Curated Monster", ambiguous());

        CombatOverlayJson curated = new CombatOverlayJson();
        curated.attacks = new ArrayList<>();
        curated.needsReview = false;
        Map<Integer, CombatOverlayJson> existing = new HashMap<>();
        existing.put(2, curated);

        List<OutputSource> sources = Arrays.asList(
            source(1, "Gap Monster", "KILL"), source(2, "Curated Monster", "KILL"));
        Map<Integer, CombatOverlayJson> seeded = MonsterTransform.seedStubs(
            sources, existing, SourceDataGenerator.combatGapIds(sources, wiki));

        assertTrue("a new gap becomes a review stub", seeded.get(1).needsReview);
        assertFalse("an already-curated entry is never re-flagged", seeded.get(2).needsReview);
    }
}
