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

import java.util.List;
import com.itemsearch.datagen.source.RecipeJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class WikiRecipesTest
{
    private static final String SCALES = "==Creation==\n{{Tabber\n|tab1=Sacred eel\n|tab1content=\n"
        + "{{Recipe\n|skill1 = Cooking\n|skill1lvl = 72\n|tools = Knife\n|mat1 = Sacred eel\n"
        + "|output1 = Zulrah's scales\n|output1quantity = 7\n"
        + "|output1quantitynote = Depends on cooking level. Seven is average for cooking level 96-103.\n}}\n"
        + "|tab2=Tanzanite fang\n|tab2content=\n{{Recipe\n|members = Yes\n|mat1 = Tanzanite fang\n"
        + "|mat1cost = {{GEP|Tanzanite fang}}\n|output1 = Zulrah's scales\n|output1quantity = 20000\n}}\n}}\n";

    private static final String DAGGER = "{{Recipe\n|skill1 = Smithing\n|skill1lvl = 1\n|tools = Hammer\n"
        + "|mat1 = Bronze bar\n|output1 = Bronze dagger\n|output1quantity = 1\n}}";

    private static final String CAVIAR = "{{Recipe\n|skill1 = Cooking\n|skill1lvl = 1\n|tools = Knife\n"
        + "|mat1 = Leaping sturgeon\n|output1 = Caviar\n"
        + "|output1quantitynote = Expected quantity scales with the player's [[Cooking]] level.\n"
        + "|output2 = Fish offcuts\n|output2quantity = {{#expr:5/6 round 2}}\n"
        + "|output2quantitynote = There is a 83.3% (5/6) chance of receiving offcuts.\n}}";

    /** Recipes nest inside a Tabber and still read, materials and outputs with links stripped. */
    @Test
    public void everyRecipeOnThePageIsRead()
    {
        List<RecipeJson> recipes = WikiRecipes.extract(SCALES);
        assertEquals(2, recipes.size());
        RecipeJson eel = recipes.get(0);
        assertEquals("Sacred eel", eel.materials.get(0));
        assertEquals("Cooking", eel.skill);
        assertEquals("72", eel.level);
        assertEquals("Knife", eel.tools);
        assertEquals("Zulrah's scales", eel.outputs.get(0).name);
        assertEquals("7", eel.outputs.get(0).quantity);
        RecipeJson fang = recipes.get(1);
        assertNull(fang.skill);
        assertEquals("20000", fang.outputs.get(0).quantity);
        assertTrue(WikiRecipes.extract("no recipes here").isEmpty());
    }

    /**
     * Cutting an eel is breaking it down: one thing in, a yield that depends on level. So is
     * dismantling a fang: no skill at all. Smithing a dagger from a bar is making something.
     */
    @Test
    public void breakingDownIsToldFromMaking()
    {
        List<RecipeJson> scales = WikiRecipes.extract(SCALES);
        assertTrue("a level-dependent yield", WikiRecipes.isBreakdown(scales.get(0)));
        assertTrue("no skill: dismantling", WikiRecipes.isBreakdown(scales.get(1)));
        assertFalse("a fixed count from a skill: crafting", WikiRecipes.isBreakdown(WikiRecipes.extract(DAGGER).get(0)));
        RecipeJson caviar = WikiRecipes.extract(CAVIAR).get(0);
        assertTrue(WikiRecipes.isBreakdown(caviar));
        assertTrue(WikiRecipes.isVariable(caviar.outputs.get(0)));
        assertTrue("an expression is not a count", WikiRecipes.isVariable(caviar.outputs.get(1)));
        assertEquals(1, WikiRecipes.fixedQuantity(caviar.outputs.get(0)));
        assertEquals("an expression is not a count", 1, WikiRecipes.fixedQuantity(caviar.outputs.get(1)));
        assertEquals("a stated average stands", 7, WikiRecipes.fixedQuantity(scales.get(0).outputs.get(0)));
        assertEquals(20000, WikiRecipes.fixedQuantity(scales.get(1).outputs.get(0)));
    }

    /** Filling a bucket has no skill and one thing in, and is still making something. */
    @Test
    public void oneThingInAndOneThingOutWithoutASkillIsNotBreakingDown()
    {
        RecipeJson bucket = WikiRecipes.extract("{{Recipe\n|mat1 = Bucket\n|output1 = Bucket of water\n}}").get(0);
        assertFalse(WikiRecipes.isBreakdown(bucket));
        RecipeJson jar = WikiRecipes.extract("{{Recipe\n|mat1 = Baby impling jar\n|output1 = Chisel\n"
            + "|output2 = Thread\n}}").get(0);
        assertTrue("several things out of one is opening it", WikiRecipes.isBreakdown(jar));
        RecipeJson bone = WikiRecipes.extract("{{Recipe\n|mat1 = Bone in vinegar#Rat bone\n"
            + "|output1 = Rat bone#Polished\n|output2 = Pot\n}}").get(0);
        assertFalse("a section of a page is not an item", WikiRecipes.isBreakdown(bone));
    }

    /** Iron smelting is a matter of chance too, but a furnace makes a bar rather than breaking ore. */
    @Test
    public void aChanceWithASkillCountsOnlyWhenSomethingIsCutUp()
    {
        RecipeJson iron = WikiRecipes.extract("{{Recipe\n|skill1 = Smithing\n|skill1lvl = 15\n|tools = Furnace\n"
            + "|mat1 = Iron ore\n|output1 = Iron bar\n|output1quantitynote = 50% chance without a ring of forging.\n}}")
            .get(0);
        assertFalse(WikiRecipes.isBreakdown(iron));
    }
}
