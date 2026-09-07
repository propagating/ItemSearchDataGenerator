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

import com.itemsearch.datagen.source.WikiPageTextJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class WikiPageTextTest
{
    private static final String PAGE = "{{Infobox Monster\n"
        + "|name = General Graardor\n"
        + "|max hit = 60 (melee), 35 (ranged)\n"
        + "|attack style = [[Crush]], [[Ranged]]\n"
        + "}}\n"
        + "'''General Graardor''' is the [[Bandos]] general.\n"
        + "\n"
        + "==Mechanics==\n"
        + "He has two attacks.\n"
        + "===Melee===\n"
        + "A crush attack hitting up to 60.\n"
        + "===Ranged===\n"
        + "A ranged attack hitting up to 35.\n"
        + "\n"
        + "==Strategy==\n"
        + "Pray melee and tank the ranged.\n"
        + "\n"
        + "==Drops==\n"
        + "{{DropsTableHead}}\n";

    @Test
    public void keepsTheInfoboxBlock()
    {
        WikiPageTextJson t = WikiPageText.extract(PAGE);
        assertEquals("{{Infobox Monster\n|name = General Graardor\n|max hit = 60 (melee), 35 (ranged)\n"
            + "|attack style = [[Crush]], [[Ranged]]\n}}", t.infobox);
    }

    @Test
    public void keepsTheMechanicsSectionWithItsSubsections()
    {
        WikiPageTextJson t = WikiPageText.extract(PAGE);
        assertEquals("==Mechanics==\nHe has two attacks.\n===Melee===\nA crush attack hitting up to 60.\n"
            + "===Ranged===\nA ranged attack hitting up to 35.", t.mechanics);
    }

    @Test
    public void keepsTheStrategySection()
    {
        WikiPageTextJson t = WikiPageText.extract(PAGE);
        assertEquals("==Strategy==\nPray melee and tank the ranged.", t.strategy);
    }

    @Test
    public void sectionsThePageDoesNotHaveAreNull()
    {
        WikiPageTextJson t = WikiPageText.extract("{{Infobox Monster\n|max hit = 1\n}}\nJust a monster.\n==Drops==\n");
        assertEquals("{{Infobox Monster\n|max hit = 1\n}}", t.infobox);
        assertNull(t.mechanics);
        assertNull(t.strategy);
    }

    @Test
    public void headingsMatchLooselyOnCaseSpacingAndPlural()
    {
        WikiPageTextJson t = WikiPageText.extract("== mechanics ==\nspins\n== Strategies ==\nrun\n");
        assertEquals("== mechanics ==\nspins", t.mechanics);
        assertEquals("== Strategies ==\nrun", t.strategy);
    }

    @Test
    public void attacksHeadingCountsAsMechanics()
    {
        WikiPageTextJson t = WikiPageText.extract("==Attacks==\nbites\n==Drops==\n");
        assertEquals("==Attacks==\nbites", t.mechanics);
    }

    @Test
    public void infoboxWithNestedTemplateStillEndsAtItsOwnBraces()
    {
        String page = "{{Infobox Monster\n|max hit = {{Nowrap|10}}\n|attack speed = 4\n}}\nprose";
        WikiPageTextJson t = WikiPageText.extract(page);
        assertEquals("{{Infobox Monster\n|max hit = {{Nowrap|10}}\n|attack speed = 4\n}}", t.infobox);
    }

    @Test
    public void nothingRecognisableGivesNull()
    {
        assertNull(WikiPageText.extract(""));
        assertNull(WikiPageText.extract("   \n\n"));
        assertNull(WikiPageText.extract(null));
    }

    @Test
    public void aStrategySubpageFillsAnEmptyStrategy()
    {
        WikiPageTextJson t = WikiPageText.extract("{{Infobox Monster\n|max hit = 1\n}}\n");
        WikiPageText.addStrategySubpage(t, "==Gear==\nBring a crossbow.\n");
        assertEquals("==Gear==\nBring a crossbow.", t.strategy);
    }

    @Test
    public void aStrategySubpageDoesNotReplaceAStrategyThePageAlreadyHas()
    {
        WikiPageTextJson t = WikiPageText.extract("==Strategy==\nown words\n");
        WikiPageText.addStrategySubpage(t, "subpage words");
        assertEquals("==Strategy==\nown words", t.strategy);
    }

    @Test
    public void everyCombatSectionThePageHasIsKeptTogether()
    {
        WikiPageTextJson t = WikiPageText.extract("==Locations==\nhere\n==Strengths and weaknesses==\nweak to slash\n"
            + "==Prayer info==\npray melee\n==Drops==\nbones\n");
        assertEquals("==Strengths and weaknesses==\nweak to slash\n\n==Prayer info==\npray melee", t.mechanics);
    }

    @Test
    public void aBossFightOverviewIsMechanics()
    {
        WikiPageTextJson t = WikiPageText.extract("==Fight overview==\nfour phases\n==Drops==\n");
        assertEquals("==Fight overview==\nfour phases", t.mechanics);
    }

    @Test
    public void theLeadIsWhatSitsBetweenTheInfoboxAndTheFirstHeading()
    {
        WikiPageTextJson t = WikiPageText.extract(PAGE);
        assertEquals("'''General Graardor''' is the [[Bandos]] general.", t.lead);
    }

    @Test
    public void aPageWithNoInfoboxLeadsFromItsTop()
    {
        WikiPageTextJson t = WikiPageText.extract("Just words.\n==Drops==\nbones\n");
        assertEquals("Just words.", t.lead);
    }

    @Test
    public void anEmptyLeadIsNull()
    {
        WikiPageTextJson t = WikiPageText.extract("{{Infobox Monster\n|max hit = 1\n}}\n\n==Drops==\nbones\n");
        assertNull(t.lead);
    }
}
