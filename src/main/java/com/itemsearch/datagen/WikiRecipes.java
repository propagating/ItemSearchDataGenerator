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
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import com.itemsearch.datagen.source.RecipeJson;

/**
 * The {{Recipe}} templates on an item's page, and which of them break something down.
 *
 * <p>The wiki writes every way of making an item the same way, whether a bronze bar is smithed
 * into a dagger or a sacred eel is cut into Zulrah's scales. The client wants only the second
 * kind for now: one thing turned into others, where the yield is a matter of chance or of level
 * rather than a fixed count, or where no skill is involved at all, as when gear is dismantled.
 */
public final class WikiRecipes
{
    // a yield the wiki has to explain is not a fixed one
    private static final Pattern VARIABLE_NOTE =
        Pattern.compile("(?i)chance|depend|scale|expected|average|random|up to|between");

    private WikiRecipes()
    {
    }

    /** Every recipe on the page, in order; empty when it has none. */
    public static List<RecipeJson> extract(String wikitext)
    {
        List<RecipeJson> out = new ArrayList<>();
        for (String body : recipeBodies(wikitext))
        {
            Map<String, String> p = WikiText.params(body);
            RecipeJson r = new RecipeJson();
            for (int n = 1; ; n++)
            {
                String mat = clean(p.get("mat" + n));
                if (mat == null)
                {
                    break;
                }
                r.materials.add(mat);
            }
            for (int n = 1; ; n++)
            {
                String name = clean(p.get("output" + n));
                if (name == null)
                {
                    break;
                }
                r.outputs.add(new RecipeJson.Output(name, blankToNull(p.get("output" + n + "quantity")),
                    blankToNull(p.get("output" + n + "quantitynote"))));
            }
            r.tools = clean(p.get("tools"));
            r.skill = clean(p.get("skill1"));
            r.level = blankToNull(p.get("skill1lvl"));
            if (!r.materials.isEmpty() && !r.outputs.isEmpty())
            {
                out.add(r);
            }
        }
        return out;
    }

    /**
     * The body of every {{Recipe|...}} on the page, wherever it sits. A page with several ways
     * to make an item tucks each recipe inside a {{Tabber}}, so recipes are found at any depth
     * and each is closed by counting its own braces.
     */
    static List<String> recipeBodies(String wikitext)
    {
        List<String> bodies = new ArrayList<>();
        if (wikitext == null)
        {
            return bodies;
        }
        String open = "{{Recipe";
        int from = 0;
        while (true)
        {
            int start = wikitext.indexOf(open, from);
            if (start < 0)
            {
                return bodies;
            }
            int bodyStart = start + open.length();
            // "{{Recipe|" or "{{Recipe\n|"; not "{{Recipes}}" or some other template
            if (bodyStart >= wikitext.length()
                || (wikitext.charAt(bodyStart) != '|' && !Character.isWhitespace(wikitext.charAt(bodyStart))))
            {
                from = bodyStart;
                continue;
            }
            int depth = 1;
            int i = bodyStart;
            while (i < wikitext.length() && depth > 0)
            {
                if (wikitext.startsWith("{{", i))
                {
                    depth++;
                    i += 2;
                }
                else if (wikitext.startsWith("}}", i))
                {
                    depth--;
                    i += 2;
                }
                else
                {
                    i++;
                }
            }
            if (depth != 0)
            {
                return bodies; // unclosed: nothing more to read
            }
            String body = wikitext.substring(bodyStart, i - 2).trim();
            bodies.add(body.startsWith("|") ? body.substring(1) : body);
            from = i;
        }
    }

    /**
     * Whether the recipe breaks one thing into others: a single material, and either no skill
     * (dismantling, opening) or a yield the wiki has to explain rather than state.
     */
    public static boolean isBreakdown(RecipeJson r)
    {
        if (r.materials.size() != 1 || r.outputs.isEmpty() || r.materials.get(0).contains("#"))
        {
            return false;
        }
        boolean several = r.outputs.size() >= 2;
        for (RecipeJson.Output o : r.outputs)
        {
            // an item that turns into itself (recharging, upgrading) is not broken down, and a
            // name pointing into a section of a page is a quest oddity, not an item
            if (o.name.equalsIgnoreCase(r.materials.get(0)) || o.name.contains("#"))
            {
                return false;
            }
            several |= fixedQuantity(o) > 1;
            // a yield left to chance or level, from cutting something up — smelting iron has a
            // chance too, but a furnace makes bars, it does not break ore down
            if (isVariable(o) && (r.skill == null || cuts(r)))
            {
                return true;
            }
        }
        // no skill and one thing in: dismantling or opening, when several things come out.
        // One thing out is filling a bucket or stringing an amulet, which is making, not breaking
        return r.skill == null && several;
    }

    private static boolean cuts(RecipeJson r)
    {
        return r.tools != null && r.tools.toLowerCase(java.util.Locale.ROOT).contains("knife");
    }

    /** Whether an output's count is left to chance or level rather than fixed. */
    public static boolean isVariable(RecipeJson.Output o)
    {
        if (o.note != null && VARIABLE_NOTE.matcher(o.note).find())
        {
            return true;
        }
        return o.quantity != null && (o.quantity.contains("#expr") || o.quantity.contains("/")
            || o.quantity.contains("–") || o.quantity.contains("-") || o.quantity.contains("."));
    }

    /**
     * The count an output states, or 1 when it states none or only an expression. A stated
     * number stands even when a note says it varies: seven scales from an eel is the average the
     * wiki gives, and the better thing to show than one.
     */
    public static int fixedQuantity(RecipeJson.Output o)
    {
        if (o.quantity == null)
        {
            return 1;
        }
        try
        {
            return Math.max(1, Integer.parseInt(o.quantity.replace(",", "").trim()));
        }
        catch (NumberFormatException e)
        {
            return 1;
        }
    }

    private static String clean(String raw)
    {
        String s = raw == null ? null : WikiText.stripLinks(raw).trim();
        return s == null || s.isEmpty() ? null : s;
    }

    private static String blankToNull(String raw)
    {
        return raw == null || raw.trim().isEmpty() ? null : raw.trim();
    }
}
