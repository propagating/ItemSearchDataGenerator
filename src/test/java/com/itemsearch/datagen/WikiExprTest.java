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

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WikiExprTest
{
    private static Map<String, String> args(String... pairs)
    {
        Map<String, String> m = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
        {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }

    @Test
    public void evaluatesArithmetic()
    {
        assertEquals("5", WikiExpr.render("{{#expr:2+3}}", args()));
        assertEquals("6", WikiExpr.render("{{#expr:2*3}}", args()));
        assertEquals("0.5", WikiExpr.render("{{#expr:1/2}}", args()));
        assertEquals("8", WikiExpr.render("{{#expr:2^3}}", args()));
        assertEquals("7", WikiExpr.render("{{#expr:1+2*3}}", args()));
        assertEquals("9", WikiExpr.render("{{#expr:(1+2)*3}}", args()));
        assertEquals("-4", WikiExpr.render("{{#expr:-4}}", args()));
    }

    @Test
    public void evaluatesFunctionsAndRounding()
    {
        assertEquals("3", WikiExpr.render("{{#expr:floor(3.7)}}", args()));
        assertEquals("4", WikiExpr.render("{{#expr:ceil(3.2)}}", args()));
        assertEquals("4", WikiExpr.render("{{#expr:sqrt(16)}}", args()));
        assertEquals("3.7", WikiExpr.render("{{#expr:3.66 round 1}}", args()));
        // from the revenant template: sqrt(135) is 11.6, floored to 11, and 2200/11 is 200
        assertEquals("200", WikiExpr.render("{{#expr:floor(2200/floor(sqrt(135)))}}", args()));
    }

    @Test
    public void substitutesTemplateParameters()
    {
        assertEquals("34/140", WikiExpr.render("{{{1}}}", args("1", "34/140")));
        assertEquals("135", WikiExpr.render("{{{combat|135}}}", args()));
        assertEquals("200", WikiExpr.render("{{{combat|135}}}", args("combat", "200")));
        // an unsupplied parameter with no default renders empty, as MediaWiki does
        assertEquals("", WikiExpr.render("{{{missing}}}", args()));
    }

    @Test
    public void carriesVariablesAcrossTheDocument()
    {
        String text = "{{#vardefine:base|{{#expr:2*4}}}}value is {{#var:base}}";
        assertEquals("value is 8", WikiExpr.render(text, args()));
    }

    @Test
    public void resolvesConditionals()
    {
        assertEquals("yes", WikiExpr.render("{{#if:x|yes|no}}", args()));
        assertEquals("no", WikiExpr.render("{{#if:|yes|no}}", args()));
        assertEquals("", WikiExpr.render("{{#if:|yes}}", args()));
    }

    /**
     * The real Killerwatt case: the page writes {{HerbDropLines|34/140}}, and the template turns
     * that into a per-herb rate. Guam is 32/128 of the herb table, so its true rate is
     * (34/140) * (32/128) = 1/16.5.
     */
    @Test
    public void computesTheHerbTableRateTheWikiShows()
    {
        String template = "{{#vardefine:herbbase|{{#expr:{{{1}}}/128}}}}"
            + "rarity=1/{{#expr:1/(32*{{#var:herbbase}}) round 1}}";
        assertEquals("rarity=1/16.5", WikiExpr.render(template, args("1", "34/140")));
    }

    @Test
    public void unparseableExpressionsBecomeEmptyRatherThanThrowing()
    {
        assertEquals("", WikiExpr.render("{{#expr:not a number}}", args()));
        assertEquals("", WikiExpr.render("{{#expr:}}", args()));
        assertEquals("", WikiExpr.render("{{#expr:1/0}}", args()));
        // an unknown parser function is dropped rather than left as markup
        assertEquals("", WikiExpr.render("{{#invoke:Something|foo}}", args()));
    }

    @Test
    public void leavesOrdinaryTextAndTemplatesAlone()
    {
        String text = "{{DropsLine|name=Grimy guam leaf|rarity=1/25}}";
        assertEquals(text, WikiExpr.render(text, args()));
    }

    @Test
    public void parsesTransclusionArguments()
    {
        Map<String, String> a = WikiExpr.arguments("34/140|1-3|rolls=2");
        assertEquals("34/140", a.get("1"));
        assertEquals("1-3", a.get("2"));
        assertEquals("2", a.get("rolls"));
        assertNull(a.get("3"));
        assertEquals(0, WikiExpr.arguments("").size());
    }
}
