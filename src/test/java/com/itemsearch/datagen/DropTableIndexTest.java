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
import java.util.List;
import com.itemsearch.datagen.output.OutputData;
import com.itemsearch.datagen.output.OutputDrop;
import com.itemsearch.datagen.output.OutputSource;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;

public class DropTableIndexTest
{
    private static OutputDrop drop(int itemId, String name, String category, String table)
    {
        OutputDrop d = new OutputDrop(itemId, name, 0.5, 1, false, true);
        d.category = category;
        d.dropTable = table;
        return d;
    }

    private static OutputSource source(String name, OutputDrop... drops)
    {
        return new OutputSource(1, new ArrayList<>(), name, 1, null, new ArrayList<>(),
            new ArrayList<>(Arrays.asList(drops)), null, "KILL", null, null, null);
    }

    /** The same shared table on two monsters is written once and referenced twice. */
    @Test
    public void identicalTablesAreStoredOnce()
    {
        OutputSource a = source("Aberrant spectre",
            drop(995, "Coins", "Coins", null),
            drop(199, "Grimy guam leaf", "Herbs", "Herb drop table"));
        OutputSource b = source("Bloodveld",
            drop(526, "Bones", "100%", null),
            drop(199, "Grimy guam leaf", "Herbs", "Herb drop table"));

        OutputData data = DropTableIndex.build(Arrays.asList(a, b));

        assertEquals("own drops differ, herb table is shared", 3, data.tables.size());
        assertEquals(Arrays.asList(0, 1), data.sources.get(0).tables);
        assertEquals(Arrays.asList(2, 1), data.sources.get(1).tables);
        assertEquals("Herb drop table", data.tables.get(1).name);
        assertNull("a source's own drops belong to no shared table", data.tables.get(0).name);
        // the rows themselves are no longer carried on the source
        assertNull(data.sources.get(0).drops);
    }

    /** Version splits repeat their page's whole table; those collapse to one entry too. */
    @Test
    public void versionsSharingATableReferenceTheSameOne()
    {
        OutputDrop bones = drop(526, "Bones", null, null);
        OutputData data = DropTableIndex.build(Arrays.asList(
            source("Killerwatt (Idle)", bones), source("Killerwatt (Attacking)", bones)));

        assertEquals(1, data.tables.size());
        assertEquals(data.sources.get(0).tables, data.sources.get(1).tables);
    }

    /** Same items, different rates — Killerwatt's herb table is not Aberrant spectre's. */
    @Test
    public void tablesDifferingOnlyByRarityStaySeparate()
    {
        OutputDrop cheap = drop(199, "Grimy guam leaf", "Herbs", "Herb drop table");
        OutputDrop dear = drop(199, "Grimy guam leaf", "Herbs", "Herb drop table");
        dear.rarity = 0.0625;

        OutputData data = DropTableIndex.build(Arrays.asList(
            source("Killerwatt", cheap), source("Aberrant spectre", dear)));
        assertEquals(2, data.tables.size());
        assertNotSame(data.sources.get(0).tables, data.sources.get(1).tables);
    }

    @Test
    public void aSourceKeepsItsTablesInOrder()
    {
        OutputData data = DropTableIndex.build(Arrays.asList(source("Monster",
            drop(1, "First", "A", null),
            drop(2, "Shared", null, "Herb drop table"),
            drop(3, "Second", "A", null))));

        // the own-drops block keeps every row that is not from a shared table, in order
        List<OutputDrop> own = data.tables.get(data.sources.get(0).tables.get(0)).drops;
        assertEquals(Arrays.asList("First", "Second"),
            Arrays.asList(own.get(0).itemName, own.get(1).itemName));
        assertEquals(2, data.sources.get(0).tables.size());
    }

    @Test
    public void anEmptySourceReferencesNothing()
    {
        OutputData data = DropTableIndex.build(Arrays.asList(source("Nothing")));
        assertEquals(0, data.sources.get(0).tables.size());
        assertEquals(0, data.tables.size());
    }
}
