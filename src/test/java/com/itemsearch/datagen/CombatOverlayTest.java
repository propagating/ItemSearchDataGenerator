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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.itemsearch.datagen.output.OutputAttack;
import com.itemsearch.datagen.output.OutputCombat;
import com.itemsearch.datagen.output.OutputSource;
import com.itemsearch.datagen.source.CombatOverlayJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CombatOverlayTest
{
    private OutputCombat combat(OutputAttack... attacks)
    {
        return new OutputCombat(1, 1, 1, 1, 1, 10, new ArrayList<>(Arrays.asList(attacks)));
    }

    @Test
    public void parseToleratesEmptyAndBadKeys()
    {
        assertTrue(MonsterTransform.parseCombatOverlay(new Gson(), null).isEmpty());
        assertTrue(MonsterTransform.parseCombatOverlay(new Gson(), "").isEmpty());
        assertTrue(MonsterTransform.parseCombatOverlay(new Gson(), "{\"x\":{}}").isEmpty());
    }

    @Test
    public void nonStubOverrideWins()
    {
        CombatOverlayJson ov = new CombatOverlayJson();
        ov.attacks = Arrays.asList(new OutputAttack("melee", 251, 7), new OutputAttack("ranged", 169, 7));
        ov.needsReview = false;
        OutputCombat result = MonsterTransform.applyCombatOverlay(
            combat(new OutputAttack("typeless", 0, 10)), ov);
        assertEquals(2, result.attacks.size());
        assertEquals(251, result.attacks.get(0).maxHit);
    }

    @Test
    public void stubDoesNotOverride()
    {
        CombatOverlayJson ov = new CombatOverlayJson();
        ov.attacks = Collections.singletonList(new OutputAttack("melee", 999, 1));
        ov.needsReview = true;
        OutputCombat result = MonsterTransform.applyCombatOverlay(
            combat(new OutputAttack("typeless", 0, 10)), ov);
        assertEquals(1, result.attacks.size());
        assertEquals("typeless", result.attacks.get(0).style); // stub ignored
    }

    @Test
    public void seedStubsAppendsOnlyNewGapsPreservingExisting()
    {
        List<OutputSource> npcs = new ArrayList<>();
        npcs.add(new OutputSource(2215, Collections.singletonList(2215), "General Graardor", 624, null,
            new ArrayList<>(), new ArrayList<>(), combat(new OutputAttack("crush", 30, 6)),
            "KILL", null, null, null));
        npcs.add(new OutputSource(7706, Collections.singletonList(7706), "TzKal-Zuk", 1400, null,
            new ArrayList<>(), new ArrayList<>(), combat(new OutputAttack("typeless", 0, 10)),
            "KILL", null, null, null));

        Map<Integer, CombatOverlayJson> existing = new HashMap<>();
        CombatOverlayJson filled = new CombatOverlayJson();
        filled.attacks = Collections.singletonList(new OutputAttack("crush", 60, 6));
        filled.needsReview = false;
        existing.put(2215, filled); // already curated

        Map<Integer, CombatOverlayJson> seeded = MonsterTransform.seedStubs(
            npcs, existing, new java.util.HashSet<>(Arrays.asList(2215, 7706)));

        assertFalse(seeded.get(2215).needsReview);        // existing entry preserved
        assertEquals(60, seeded.get(2215).attacks.get(0).maxHit);
        assertTrue(seeded.get(7706).needsReview);          // new gap stubbed
        assertEquals("typeless", seeded.get(7706).attacks.get(0).style);
    }
    @Test
    public void prayStyleAndVariantsSurviveOverlayParseAndApply()
    {
        String json = "{\"42\":{\"needsReview\":false,\"attacks\":[{"
            + "\"style\":\"magic\",\"prayStyle\":\"ranged\","
            + "\"maxHit\":15,\"maxHitMax\":15,\"speed\":4,\"speedMax\":4,"
            + "\"variants\":[{\"label\":\"Insulated boots\",\"kind\":\"GEAR\","
            + "\"maxHit\":8,\"maxHitMax\":8}]}]}}";
        CombatOverlayJson ov = MonsterTransform.parseCombatOverlay(new Gson(), json).get(42);
        OutputCombat result = MonsterTransform.applyCombatOverlay(
            combat(new OutputAttack("typeless", 0, 10)), ov);
        OutputAttack a = result.attacks.get(0);
        assertEquals("ranged", a.prayStyle);
        assertEquals(1, a.variants.size());
        assertEquals("Insulated boots", a.variants.get(0).label);
        assertEquals("GEAR", a.variants.get(0).kind);
        assertEquals(8, a.variants.get(0).maxHit);
    }

}
