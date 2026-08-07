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
package com.itemsearch.datagen.output;

import java.util.List;

public class OutputSource
{
    public int id;
    // every source NPC id that maps to this monster (same name + combat level);
    // includes the canonical id. Kills under any of these ids count as this monster.
    public List<Integer> npcIds;
    public String name;
    // the wiki page this source came from, when the display name is no longer that page's title —
    // a version split names itself "Killerwatt (Attacking)", which is not a page anyone can fetch.
    // Null when the name and the title agree, which is most of them.
    public String wikiPage;
    public int combatLevel;
    public String wikiImageUrl;
    public List<OutputSpawn> spawnPoints;
    // the surface tile you enter by, for a monster whose own tiles the world map does not draw.
    // Null when it spawns above ground (its own markers suffice) or when nothing leads there.
    public OutputSpawn entrance;
    // set while building; cleared once the rows move onto shared tables (see DropTableIndex)
    public List<OutputDrop> drops;
    // indexes into OutputData.tables; this source's drops are those tables concatenated
    public List<Integer> tables;
    public OutputCombat combat;
    // KILL, REWARD, PICKPOCKET, or GATHER
    public String type;
    // wiki {{Map}} location for chest/node sources; null for NPC-backed sources
    public Integer x;
    public Integer y;
    public Integer plane;

    public OutputSource(int id, List<Integer> npcIds, String name, int combatLevel, String wikiImageUrl,
        List<OutputSpawn> spawnPoints, List<OutputDrop> drops, OutputCombat combat,
        String type, Integer x, Integer y, Integer plane)
    {
        this.id = id;
        this.npcIds = npcIds;
        this.name = name;
        this.combatLevel = combatLevel;
        this.wikiImageUrl = wikiImageUrl;
        this.spawnPoints = spawnPoints;
        this.drops = drops;
        this.combat = combat;
        this.type = type;
        this.x = x;
        this.y = y;
        this.plane = plane;
    }
}
