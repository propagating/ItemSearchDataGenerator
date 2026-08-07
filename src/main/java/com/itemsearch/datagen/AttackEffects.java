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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What an attack does beyond damage: poison, venom, disease, or draining prayer or stats.
 *
 * <p>The wiki records only poison and venom, per monster rather than per attack, in a free-text
 * {@code poisonous} field ("Yes (6)", "Yes ([[venom]])"). That seeds most of it; the curator sets
 * the rest per attack, which is where it belongs — a Saradomin wizard poisons with its melee, not
 * with its spells.
 */
public final class AttackEffects
{
    private static final Map<String, String> TOKENS = new LinkedHashMap<>();

    static
    {
        TOKENS.put("poison", "POISON");
        TOKENS.put("venom", "VENOM");
        TOKENS.put("disease", "DISEASE");
        TOKENS.put("drain prayer", "DRAIN_PRAYER");
        TOKENS.put("drainprayer", "DRAIN_PRAYER");
        TOKENS.put("prayer drain", "DRAIN_PRAYER");
        TOKENS.put("drain stats", "DRAIN_STATS");
        TOKENS.put("drainstats", "DRAIN_STATS");
        TOKENS.put("stat drain", "DRAIN_STATS");
    }

    private AttackEffects()
    {
    }

    /** Effects implied by an {{Infobox Monster}} {@code poisonous} value. */
    public static List<String> fromPoisonous(String field)
    {
        if (field == null)
        {
            return Collections.emptyList();
        }
        String value = field.toLowerCase(Locale.ROOT);
        if (!value.contains("yes"))
        {
            return Collections.emptyList();
        }
        // "Yes ([[venom]])" is venom rather than poison, and the two behave differently in game
        return Collections.singletonList(value.contains("venom") ? "VENOM" : "POISON");
    }

    /** Comma-separated words as the curator types them; unknown words are dropped. */
    public static List<String> parse(String text)
    {
        List<String> out = new ArrayList<>();
        if (text == null)
        {
            return out;
        }
        for (String part : text.split(","))
        {
            String token = TOKENS.get(part.trim().toLowerCase(Locale.ROOT));
            if (token != null && !out.contains(token))
            {
                out.add(token);
            }
        }
        return out;
    }

    /** The inverse of {@link #parse}, for loading a curated attack back into the editor. */
    public static String format(List<String> effects)
    {
        if (effects == null || effects.isEmpty())
        {
            return "";
        }
        List<String> words = new ArrayList<>();
        for (String effect : effects)
        {
            words.add(effect.toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        return String.join(", ", words);
    }

    /** Every token the curator may type, for the placeholder text. */
    public static List<String> known()
    {
        return new ArrayList<>(Arrays.asList("poison", "venom", "disease",
            "drain prayer", "drain stats"));
    }
}
