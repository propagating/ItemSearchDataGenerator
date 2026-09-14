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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.itemsearch.datagen.source.WikiCombatJson;

/**
 * What a monster shrugs off, read into one line each: poison, venom, freezing, cannon, thralls,
 * burns. The wiki writes these three ways — a resistance percentage, a yes/no word, or a phrase
 * about burns — and the client wants one vocabulary: "Immune", "Susceptible", or a degree.
 */
public final class Statuses
{
    private Statuses()
    {
    }

    /**
     * Status name to how the monster takes it, in display order, for the given version of the
     * page (negative for the plain reading). Statuses the page says nothing about are absent.
     */
    public static Map<String, String> of(WikiCombatJson w, int version)
    {
        Map<String, String> out = new LinkedHashMap<>();
        if (w == null)
        {
            return out;
        }
        put(out, "Poison", resistance(at(w.poisonResistances, version, w.poisonResistance)));
        put(out, "Venom", resistance(at(w.venomResistances, version, w.venomResistance)));
        put(out, "Freeze", resistance(at(w.freezeResistances, version, w.freezeResistance)));
        put(out, "Cannon", yesNo(at(w.immuneCannons, version, w.immuneCannon)));
        put(out, "Thralls", yesNo(at(w.immuneThralls, version, w.immuneThrall)));
        put(out, "Burn", burn(at(w.immuneBurns, version, w.immuneBurn)));
        return out;
    }

    private static void put(Map<String, String> out, String status, String reading)
    {
        if (reading != null)
        {
            out.put(status, reading);
        }
    }

    // a percentage: nothing at 0, everything at 100 or more, a degree between
    static String resistance(String raw)
    {
        String s = clean(raw);
        if (s == null)
        {
            return null;
        }
        try
        {
            int percent = Integer.parseInt(s);
            if (percent <= 0)
            {
                return "Susceptible";
            }
            return percent >= 100 ? "Immune" : percent + "% resistant";
        }
        catch (NumberFormatException e)
        {
            return null; // "Poison", or a field that ran into the next line: nothing to say
        }
    }

    // "Yes"/"Immune" against "No"/"Not immune", in whatever case the editor typed
    static String yesNo(String raw)
    {
        String s = clean(raw);
        if (s == null)
        {
            return null;
        }
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.equals("yes") || lower.equals("immune"))
        {
            return "Immune";
        }
        if (lower.equals("no") || lower.equals("not immune"))
        {
            return "Susceptible";
        }
        return null;
    }

    // "Immune to weak burns", "Strong", "Not immune", "No" — a phrase or a shorthand for one
    static String burn(String raw)
    {
        String s = clean(raw);
        if (s == null)
        {
            return null;
        }
        String lower = s.toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
        if (lower.equals("no") || lower.equals("not immune"))
        {
            return "Susceptible";
        }
        for (String strength : new String[]{"weak", "normal", "strong"})
        {
            if (lower.equals(strength) || lower.contains(strength + " burn"))
            {
                return "Immune to " + strength + " burns";
            }
        }
        return lower.equals("yes") || lower.equals("immune") ? "Immune" : null;
    }

    private static String clean(String raw)
    {
        if (raw == null)
        {
            return null;
        }
        String s = WikiText.stripLinks(raw).trim();
        return s.isEmpty() ? null : s;
    }

    // the numbered field for this version when the page has one, else the plain reading
    private static String at(List<String> numbered, int version, String plain)
    {
        if (numbered != null && version >= 0 && version < numbered.size()
            && numbered.get(version) != null && !numbered.get(version).trim().isEmpty())
        {
            return numbered.get(version);
        }
        if (plain != null && !plain.trim().isEmpty())
        {
            return plain;
        }
        return numbered != null && !numbered.isEmpty() ? numbered.get(0) : null;
    }
}
