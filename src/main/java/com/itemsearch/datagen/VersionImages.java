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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.itemsearch.datagen.source.WikiPageTextJson;

/**
 * The picture of each version of a monster page.
 *
 * <p>An {{Infobox Monster}} with versions carries image1, image2, … beside version1, version2,
 * …, and a page whose versions look alike carries one plain image for all of them. Read from the
 * page text snapshot the curator uses, so no further fetching is needed.
 */
public final class VersionImages
{
    private static final String IMAGE_BASE = "https://oldschool.runescape.wiki/images/";
    private static final Pattern FILE_LINK = Pattern.compile("\\[\\[\\s*File:([^\\]|]+)");

    private VersionImages()
    {
    }

    /** Page title to its versions' image urls; empty for a page without versions. */
    public static Map<String, List<String>> fromTexts(Map<String, WikiPageTextJson> texts)
    {
        Map<String, List<String>> out = new HashMap<>();
        if (texts == null)
        {
            return out;
        }
        for (Map.Entry<String, WikiPageTextJson> e : texts.entrySet())
        {
            WikiPageTextJson t = e.getValue();
            out.put(e.getKey(), urlsFor(t == null ? null : t.infobox));
        }
        return out;
    }

    /**
     * One url per version, in version order: the version's own numbered image, else the page's
     * plain one, else null. Empty when the page has no versions.
     */
    public static List<String> urlsFor(String infoboxBody)
    {
        List<String> out = new ArrayList<>();
        if (infoboxBody == null)
        {
            return out;
        }
        List<String> versions = WikiText.numberedFields(infoboxBody, "version");
        List<String> images = WikiText.numberedFields(infoboxBody, "image");
        String plain = imageUrl(WikiText.field(infoboxBody, "image"));
        for (int i = 0; i < versions.size(); i++)
        {
            String own = i < images.size() ? imageUrl(images.get(i)) : null;
            out.add(own != null ? own : plain);
        }
        return out;
    }

    /** The wiki's direct path for a file link or bare file name; null when there is none. */
    public static String imageUrl(String fileLink)
    {
        if (fileLink == null || fileLink.trim().isEmpty())
        {
            return null;
        }
        Matcher m = FILE_LINK.matcher(fileLink);
        String name = (m.find() ? m.group(1) : fileLink).trim();
        if (name.isEmpty())
        {
            return null;
        }
        try
        {
            return IMAGE_BASE + URLEncoder.encode(name.replace(' ', '_'), "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new IllegalStateException(e); // UTF-8 is always present
        }
    }
}
