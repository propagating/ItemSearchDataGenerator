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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.itemsearch.datagen.output.OutputSource;
import com.itemsearch.datagen.source.CombatOverlayJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WikiRetiredTest
{
    private static OutputSource source(int id, String name, String wikiPage)
    {
        OutputSource s = new OutputSource(id, Collections.emptyList(), name, 0, null,
            Collections.emptyList(), Collections.emptyList(), null, "KILL", null, null, null);
        s.wikiPage = wikiPage;
        return s;
    }

    // ---- what the wiki's categories say ----

    @Test
    public void discontinuedContentIsDiscontinued()
    {
        assertEquals("discontinued", WikiRetired.reasonFor("High Priest (2013 Goblin Invasion)",
            Arrays.asList("Content released in 2013", "Discontinued content", "Monsters")));
    }

    @Test
    public void anyDeadmanSeasonCategoryIsDeadman()
    {
        assertEquals("deadman", WikiRetired.reasonFor("Cerberus (Deadman)",
            Arrays.asList("Deadman: Armageddon", "Monsters")));
        assertEquals("deadman", WikiRetired.reasonFor("Guard (Deadman Mode)",
            Arrays.asList("Deadman Mode", "Monsters")));
    }

    @Test
    public void anyLeagueCategoryIsLeagues()
    {
        assertEquals("leagues", WikiRetired.reasonFor("Thermonuclear smoke devil (Echo)",
            Arrays.asList("Demonic Pacts League", "Slayer monsters")));
    }

    @Test
    public void aLiveMonsterHasNoReason()
    {
        assertNull(WikiRetired.reasonFor("Zulrah",
            Arrays.asList("Bosses", "Monsters", "Content released in 2015")));
    }

    @Test
    public void discontinuedWinsWhenAPageIsAlsoInALeague()
    {
        // Bloodthirsty monsters were a Deadman season and later a League; they are gone either way
        assertEquals("discontinued", WikiRetired.reasonFor("Bloodthirsty gargoyle",
            Arrays.asList("Trailblazer Reloaded League", "Discontinued content")));
    }

    @Test
    public void theTitleSuffixStandsInWhenCategoriesAreMissing()
    {
        assertEquals("deadman", WikiRetired.reasonFor("Zulrah (Deadman)", Collections.emptyList()));
        assertEquals("leagues", WikiRetired.reasonFor("Cerberus (Echo)", Collections.emptyList()));
        assertNull(WikiRetired.reasonFor("Zulrah", Collections.emptyList()));
    }

    // ---- the hand-kept overrides ----

    @Test
    public void overlayCanForceAPageOutOrKeepItIn()
    {
        Map<String, String> wiki = new HashMap<>();
        wiki.put("Christmas cracker", "discontinued");
        Map<String, String> retired = WikiRetired.applyOverlay(wiki, new Gson(),
            "{\"Christmas cracker\": false, \"Some seasonal boss\": \"discontinued\"}");
        assertFalse(retired.containsKey("Christmas cracker"));
        assertEquals("discontinued", retired.get("Some seasonal boss"));
    }

    @Test
    public void missingOverlayChangesNothing()
    {
        Map<String, String> wiki = new HashMap<>();
        wiki.put("Cerberus (Deadman)", "deadman");
        assertEquals(wiki, WikiRetired.applyOverlay(wiki, new Gson(), null));
    }

    // ---- dropping the sources ----

    @Test
    public void sourcesFromRetiredPagesAreDroppedAndCounted()
    {
        Map<String, String> retired = new HashMap<>();
        retired.put("High Priest (2013 Goblin Invasion)", "discontinued");
        retired.put("Zulrah (Deadman)", "deadman");
        List<OutputSource> sources = Arrays.asList(
            source(1, "Zulrah", null),
            source(2, "High Priest (2013 Goblin Invasion)", null),
            source(3, "Zulrah (Deadman) (Serpentine)", "Zulrah (Deadman)"));
        WikiRetired.Filtered f = WikiRetired.filter(sources, retired);
        assertEquals(1, f.kept.size());
        assertEquals("Zulrah", f.kept.get(0).name);
        assertEquals(Integer.valueOf(1), f.droppedByReason.get("discontinued"));
        assertEquals(Integer.valueOf(1), f.droppedByReason.get("deadman"));
        assertTrue(f.droppedIds.containsAll(Arrays.asList(2, 3)));
    }

    @Test
    public void aVersionSplitIsJudgedByItsPageNotItsDisplayName()
    {
        Map<String, String> retired = new HashMap<>();
        retired.put("Killerwatt", "discontinued");
        List<OutputSource> sources = new ArrayList<>();
        sources.add(source(9, "Killerwatt (Attacking)", "Killerwatt"));
        assertTrue(WikiRetired.filter(sources, retired).kept.isEmpty());
    }

    // ---- the curator queue ----

    @Test
    public void untouchedStubsOfDroppedSourcesArePrunedButCuratedEntriesStay()
    {
        Map<Integer, CombatOverlayJson> overlay = new LinkedHashMap<>();
        CombatOverlayJson stub = new CombatOverlayJson();
        stub.needsReview = true;
        CombatOverlayJson curated = new CombatOverlayJson();
        curated.needsReview = false;
        CombatOverlayJson live = new CombatOverlayJson();
        live.needsReview = true;
        overlay.put(-1076, stub);
        overlay.put(-1130, curated);
        overlay.put(499, live);
        Map<Integer, CombatOverlayJson> pruned =
            WikiRetired.pruneStubs(overlay, new java.util.HashSet<>(Arrays.asList(-1076, -1130)));
        assertEquals(Arrays.asList(-1130, 499), new ArrayList<>(pruned.keySet()));
    }
}
