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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.itemsearch.datagen.source.WikiPageTextJson;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class VersionImagesTest
{
    @Test
    public void aFileLinkBecomesTheWikisDirectImageUrl()
    {
        assertEquals("https://oldschool.runescape.wiki/images/Killerwatt_%28Passive%29.png",
            VersionImages.imageUrl("[[File:Killerwatt (Passive).png|250px]]"));
        assertEquals("https://oldschool.runescape.wiki/images/Zulrah.png",
            VersionImages.imageUrl("Zulrah.png"));
        assertNull(VersionImages.imageUrl(""));
        assertNull(VersionImages.imageUrl(null));
    }

    /**
     * Killerwatt numbers a picture per version; a page that stops numbering part way falls back
     * to its plain image for the rest, so no version is left without a picture the page has.
     */
    @Test
    public void numberedImagesLineUpWithVersionsAndFallBackToThePlainOne()
    {
        String infobox = "{{Infobox Monster\n|version1 = Idle\n|version2 = Attacking\n"
            + "|version3 = Dead\n|image1 = [[File:A.png|250px]]\n|image2 = [[File:B.png|250px]]\n"
            + "|image = [[File:Main.png]]\n|combat = 55\n}}";
        List<String> urls = VersionImages.urlsFor(infobox);
        assertEquals(Arrays.asList(
            "https://oldschool.runescape.wiki/images/A.png",
            "https://oldschool.runescape.wiki/images/B.png",
            "https://oldschool.runescape.wiki/images/Main.png"), urls);
    }

    /** Ankou's five levels share one picture. */
    @Test
    public void aPageWithOnlyAPlainImageGivesItToEveryVersion()
    {
        String infobox = "{{Infobox Monster\n|version1 = Level 75\n|version2 = Level 82\n"
            + "|image = [[File:Ankou.png|110px]]\n}}";
        assertEquals(Arrays.asList(
            "https://oldschool.runescape.wiki/images/Ankou.png",
            "https://oldschool.runescape.wiki/images/Ankou.png"), VersionImages.urlsFor(infobox));
    }

    @Test
    public void textsMapToUrlsByPageTitle()
    {
        Map<String, WikiPageTextJson> texts = new HashMap<>();
        WikiPageTextJson t = new WikiPageTextJson();
        t.infobox = "{{Infobox Monster\n|version1 = A\n|version2 = B\n|image1 = [[File:A.png]]\n"
            + "|image2 = [[File:B.png]]\n}}";
        texts.put("Thing", t);
        texts.put("Empty", new WikiPageTextJson());
        Map<String, List<String>> out = VersionImages.fromTexts(texts);
        assertEquals(2, out.get("Thing").size());
        assertEquals(0, out.get("Empty").size());
        assertEquals(0, VersionImages.fromTexts(null).size());
    }
}
