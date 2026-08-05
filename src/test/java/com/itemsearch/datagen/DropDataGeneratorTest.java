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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import com.itemsearch.datagen.output.OutputNpc;
import com.itemsearch.datagen.source.MonsterJson;
import org.junit.Assume;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.Rule;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DropDataGeneratorTest
{
    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private Path copyResource(String resource, String name) throws Exception
    {
        Path dest = tmp.newFile(name).toPath();
        try (InputStream in = getClass().getResourceAsStream(resource))
        {
            Files.copy(in, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return dest;
    }

    private Path missing(String name)
    {
        return tmp.getRoot().toPath().resolve(name); // path that does not exist
    }

    @Test
    public void generatesSortedMergedNpcs() throws Exception
    {
        Path input = copyResource("/monsters-sample.json", "monsters.json");
        Path overlay = copyResource("/spawns-overlay-sample.json", "overlay.json");
        List<OutputNpc> npcs = DropDataGenerator.generate(new Gson(), input, missing("nospawns.json"), overlay);

        assertEquals(2, npcs.size());
        assertEquals(100, npcs.get(0).id);
        assertEquals(1, npcs.get(0).spawnPoints.size()); // overlay fallback (no cache spawns)
        assertTrue(npcs.get(1).spawnPoints.isEmpty());
        assertEquals(526, npcs.get(0).drops.get(0).itemId);
    }

    @Test
    public void cacheSpawnsPreferredOverOverlay() throws Exception
    {
        Path input = copyResource("/monsters-sample.json", "monsters.json");
        Path overlay = copyResource("/spawns-overlay-sample.json", "overlay.json");
        Path spawns = copyResource("/npc-spawns-sample.json", "spawns.json");
        List<OutputNpc> npcs = DropDataGenerator.generate(new Gson(), input, spawns, overlay);

        OutputNpc n100 = npcs.stream().filter(n -> n.id == 100).findFirst().get();
        OutputNpc n101 = npcs.stream().filter(n -> n.id == 101).findFirst().get();
        assertEquals(1, n100.spawnPoints.size());          // no cache spawn -> overlay fallback
        assertEquals(1, n101.spawnPoints.size());          // two identical cache spawns deduped to one
        assertEquals(50, n101.spawnPoints.get(0).x);
    }

    @Test
    public void mainWritesParseableJson() throws Exception
    {
        Path input = copyResource("/monsters-sample.json", "monsters.json");
        Path overlay = copyResource("/spawns-overlay-sample.json", "overlay.json");
        Path out = tmp.newFile("drops.json").toPath();
        DropDataGenerator.main(new String[]{
            input.toString(), missing("nospawns.json").toString(), overlay.toString(), out.toString()});

        String written = new String(Files.readAllBytes(out), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(written.contains("\"combatLevel\""));
        assertTrue(written.contains("\"rarityKnown\""));
        assertFalse(written.contains("combat_level")); // output uses the client's camelCase schema
    }

    @Test
    public void combatOverlayOverridesWikiAttacks() throws Exception
    {
        Path input = copyResource("/monsters-sample.json", "monsters.json");
        Path wiki = tmp.newFile("wiki-combat.json").toPath();
        Files.write(wiki, ("{\"Test Goblin\":{\"maxHit\":\"30\",\"attackStyle\":\"[[Crush]]\","
            + "\"attackSpeed\":\"5\"}}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        Path combatOverlay = tmp.newFile("combat-overlay.json").toPath();
        Files.write(combatOverlay, ("{\"100\":{\"attacks\":[{\"style\":\"crush\",\"maxHit\":99,"
            + "\"speed\":5}],\"needsReview\":false}}").getBytes(java.nio.charset.StandardCharsets.UTF_8));

        List<OutputNpc> npcs = DropDataGenerator.generate(new Gson(), input,
            missing("nospawns.json"), missing("nooverlay.json"), wiki, combatOverlay);

        OutputNpc goblin = npcs.stream().filter(n -> n.id == 100).findFirst().get();
        assertEquals(99, goblin.combat.attacks.get(0).maxHit); // overlay wins over the wiki's 30
    }

    // Opt-in: only runs when the developer has downloaded the pinned sources locally.
    @Test
    public void smokeTestRealSource() throws Exception
    {
        Path real = Paths.get("monsters-complete.json");
        Assume.assumeTrue("skipped: pinned monsters-complete.json not present", Files.exists(real));
        List<OutputNpc> npcs = DropDataGenerator.generate(new Gson(), real,
            Paths.get("npc-spawns.json"), Paths.get("spawns-overlay.json"));
        assertTrue("expected many monsters, got " + npcs.size(), npcs.size() > 1000);
        assertTrue("expected many monsters to have spawns",
            npcs.stream().filter(n -> !n.spawnPoints.isEmpty()).count() > 100);
    }
}
