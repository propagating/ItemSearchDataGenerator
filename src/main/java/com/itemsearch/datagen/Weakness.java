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
import java.util.List;
import com.itemsearch.datagen.source.WikiCombatJson;

/**
 * What a monster is worst at defending against.
 *
 * <p>The wiki states defence bonuses, not weaknesses: a steel dragon's {@code dstab = 0} beside
 * {@code dslash = 70} is why everyone brings a stab weapon. The lowest number is therefore the
 * answer, and a monster that defends everything equally has no weakness worth naming.
 *
 * <p>Elemental weakness is stated outright ({@code elementalweaknesstype = Earth},
 * {@code elementalweaknesspercent = 50}) and needs no working out.
 */
public final class Weakness
{
    private static final List<String> STYLES =
        Collections.unmodifiableList(Arrays.asList("STAB", "SLASH", "CRUSH", "MAGIC", "RANGED"));

    private Weakness()
    {
    }

    /** The attack styles this monster defends worst against; empty when it makes no difference. */
    public static List<String> weakestOf(WikiCombatJson w)
    {
        if (w == null)
        {
            return new ArrayList<>();
        }
        return weakest(new Integer[]{
            firstOf(w.defStab, w.defStabs),
            firstOf(w.defSlash, w.defSlashes),
            firstOf(w.defCrush, w.defCrushes),
            firstOf(w.defMagic, w.defMagics),
            firstOf(w.defRanged, w.defRangeds),
        });
    }

    /**
     * As {@link #weakestOf(WikiCombatJson)}, read from one version's own numbered fields. A
     * version the page does not number falls back to the plain reading.
     */
    public static List<String> weakestOf(WikiCombatJson w, int version)
    {
        if (w == null)
        {
            return new ArrayList<>();
        }
        return weakest(new Integer[]{
            at(w.defStabs, version, w.defStab),
            at(w.defSlashes, version, w.defSlash),
            at(w.defCrushes, version, w.defCrush),
            at(w.defMagics, version, w.defMagic),
            at(w.defRangeds, version, w.defRanged),
        });
    }

    // the numbered field for this version when the page has one, else the plain reading
    private static Integer at(List<String> numbered, int version, String plain)
    {
        if (numbered != null && version >= 0 && version < numbered.size())
        {
            Integer value = parse(numbered.get(version));
            if (value != null)
            {
                return value;
            }
        }
        return firstOf(plain, numbered);
    }

    // the styles whose bonus is the lowest, in STYLES order
    private static List<String> weakest(Integer[] values)
    {
        List<String> out = new ArrayList<>();
        Integer lowest = null;
        Integer highest = null;
        for (Integer v : values)
        {
            if (v == null)
            {
                continue;
            }
            lowest = lowest == null || v < lowest ? v : lowest;
            highest = highest == null || v > highest ? v : highest;
        }
        if (lowest == null || lowest.equals(highest))
        {
            // nothing stated, or it defends everything alike: "weak to all five" helps nobody
            return out;
        }
        for (int i = 0; i < values.length; i++)
        {
            if (lowest.equals(values[i]))
            {
                out.add(STYLES.get(i));
            }
        }
        return out;
    }

    public static String elementOf(WikiCombatJson w)
    {
        if (w == null || w.elementalWeaknessType == null)
        {
            return null;
        }
        String value = w.elementalWeaknessType.trim();
        return value.isEmpty() ? null : value;
    }

    public static Integer elementPercentOf(WikiCombatJson w)
    {
        return w == null ? null : parse(w.elementalWeaknessPercent);
    }

    // the plain field, else the first numbered one: a versioned page states the versions in order
    // and the first stands for the source, as it does for max hit and attack speed
    private static Integer firstOf(String plain, List<String> numbered)
    {
        Integer value = parse(plain);
        if (value != null)
        {
            return value;
        }
        if (numbered != null)
        {
            for (String candidate : numbered)
            {
                Integer parsed = parse(candidate);
                if (parsed != null)
                {
                    return parsed;
                }
            }
        }
        return null;
    }

    private static Integer parse(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        try
        {
            return Integer.valueOf(raw.trim());
        }
        catch (NumberFormatException e)
        {
            return null; // "n/a", "?", blank — the wiki is not always numeric here
        }
    }
}
