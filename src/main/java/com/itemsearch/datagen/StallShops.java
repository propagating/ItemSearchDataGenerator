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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.itemsearch.datagen.source.ThievingJson;

/**
 * Which shops are the fronts of stalls the player can rob.
 *
 * <p>The wiki keeps the two apart: "Ardougne Baker's Stall" is a shop page run by a Baker, and
 * "Bakery stall" is a Thieving page about the same counter. Neither names the other, so the join
 * is made here from what they do share — the kind of stall in the shop's name, and the place. A
 * shop called "<something> Baker's Stall" in East Ardougne is the Bakery stall, whose row on the
 * Thieving table lists East Ardougne among its places; Nathifa's Bake Stall in Sophanem is not,
 * because no Bakery stall stands in Sophanem. Requiring the place is what keeps a shop that
 * merely sounds like a stall from claiming to be one.
 */
public final class StallShops
{
    // "Ardougne Baker's Stall", "Fortis Fur Stall", "Pelters' Veg Stall": the word before Stall
    // says what kind, less any possessive
    private static final Pattern KIND = Pattern.compile("(?i)(?:^|\\s)([A-Za-z]+?)(?:'s?)?\\s+stall$");
    private static final Pattern WORD = Pattern.compile("[A-Za-z]+");
    // words that say which of a city's stalls, or which side of it, rather than which place
    private static final Set<String> NOISE = new HashSet<>(Arrays.asList(
        "the", "east", "west", "north", "south", "great", "castle", "market", "marketplace",
        "city", "village", "town", "island", "isle", "upper", "lower"));

    private StallShops()
    {
    }

    /**
     * The stall a shop is the front of, by the name of the stall's page, or null when it is not
     * one. A shop qualifies when its name ends in "<kind> Stall", a stall in the tables is of that
     * kind, and the shop's place is one the stall's row lists.
     *
     * @param shopName  the shop's name without any "(2)" numbering
     * @param location  where the shop is, as its infobox says; null when unsaid
     * @param thieving  the Thieving tables by lower-cased name
     */
    public static String stallFor(String shopName, String location, Map<String, ThievingJson> thieving)
    {
        if (shopName == null || thieving == null || thieving.isEmpty())
        {
            return null;
        }
        Matcher m = KIND.matcher(shopName.trim());
        if (!m.find())
        {
            return null;
        }
        String kind = m.group(1).toLowerCase(Locale.ROOT);
        if (kind.length() < 3)
        {
            return null;
        }
        // the shop's place, or failing that the words of its name other than the kind — a
        // "Port Roberts Fish Stall" with no infobox location is still in Port Roberts
        Set<String> place = placeWords(location);
        if (place.isEmpty())
        {
            place = placeWords(shopName.substring(0, m.start(1)));
        }
        for (ThievingJson row : thieving.values())
        {
            if (row == null || row.page == null || row.locations == null)
            {
                continue;
            }
            String page = row.page.trim().toLowerCase(Locale.ROOT);
            if (!page.contains(" stall") || !sameKind(kind, page))
            {
                continue;
            }
            for (String where : row.locations)
            {
                Set<String> there = placeWords(where);
                there.retainAll(place);
                if (!there.isEmpty())
                {
                    return row.page.trim();
                }
            }
        }
        return null;
    }

    /**
     * "Baker" is the kind of a "Bakery stall" and "Veg" of a "Vegetable stall": one is a prefix of
     * the other. The stall's first word is compared, so a "Gem stall (Port Roberts)" is still a
     * gem stall.
     */
    private static boolean sameKind(String kind, String stallPage)
    {
        Matcher first = WORD.matcher(stallPage);
        if (!first.find())
        {
            return false;
        }
        String word = first.group().toLowerCase(Locale.ROOT);
        return word.startsWith(kind) || kind.startsWith(word);
    }

    /** The words of a place name that name the place: "Kourend Castle" and "Great Kourend" share "kourend". */
    private static Set<String> placeWords(String place)
    {
        Set<String> words = new HashSet<>();
        if (place == null)
        {
            return words;
        }
        Matcher m = WORD.matcher(place);
        while (m.find())
        {
            String word = m.group().toLowerCase(Locale.ROOT);
            if (word.length() >= 3 && !NOISE.contains(word))
            {
                words.add(word);
            }
        }
        return words;
    }

    /** For tests: the kinds of stall named in a list of pages, lower-cased. */
    static List<String> kinds(List<String> names)
    {
        List<String> out = new java.util.ArrayList<>();
        for (String name : names)
        {
            Matcher m = KIND.matcher(name.trim());
            out.add(m.find() ? m.group(1).toLowerCase(Locale.ROOT) : null);
        }
        return out;
    }
}
