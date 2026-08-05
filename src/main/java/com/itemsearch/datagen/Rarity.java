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

/**
 * Parses rarity strings from wiki text into Double values in the range (0, 1].
 * Returns null for unknown or invalid formats.
 */
public final class Rarity
{
    private Rarity()
    {
    }

    /**
     * Parses a rarity string into a Double value in the range (0, 1].
     * Returns null if the string cannot be parsed or represents an unknown rarity.
     *
     * Rules (applied in order after stripping links/comments/whitespace):
     * - "Always" → 1.0
     * - "a/b" where a and b parse as doubles (commas stripped; b may be fractional) → a/b
     * - "~a/b" treated as "a/b"
     * - Multiplication suffixes and anything else ("Common", "Varies", "Unknown", empty, null) → null
     * - Result must be in (0, 1] else null
     *
     * @param raw the raw rarity string (may be null)
     * @return the parsed rarity as a Double in (0, 1], or null if unknown
     */
    public static Double parse(String raw)
    {
        if (raw == null)
        {
            return null;
        }

        // Strip wiki links and comments, then trim and convert to lowercase
        String cleaned = WikiText.stripLinks(raw);
        cleaned = WikiText.stripComments(cleaned);
        cleaned = cleaned == null ? null : cleaned.trim();

        if (cleaned == null || cleaned.isEmpty())
        {
            return null;
        }

        String lowerCased = cleaned.toLowerCase();

        // "Always" → 1.0
        if ("always".equals(lowerCased))
        {
            return 1.0;
        }

        // Try parsing as "a/b" or "~a/b"
        String ratioStr = lowerCased;
        if (ratioStr.startsWith("~"))
        {
            ratioStr = ratioStr.substring(1);
        }

        // Check if this looks like a ratio
        if (ratioStr.contains("/"))
        {
            try
            {
                String[] parts = ratioStr.split("/");
                if (parts.length != 2)
                {
                    return null;
                }

                // Remove commas from both parts
                String numeratorStr = parts[0].replaceAll(",", "").trim();
                String denominatorStr = parts[1].replaceAll(",", "").trim();

                if (numeratorStr.isEmpty() || denominatorStr.isEmpty())
                {
                    return null;
                }

                double numerator = Double.parseDouble(numeratorStr);
                double denominator = Double.parseDouble(denominatorStr);

                // Calculate the ratio
                double result = numerator / denominator;

                // Verify result is in (0, 1]
                if (result > 0.0 && result <= 1.0)
                {
                    return result;
                }
            }
            catch (NumberFormatException e)
            {
                // Not a valid ratio format
            }
        }

        // All other formats (Common, Varies, Unknown, etc.) return null
        return null;
    }
}
