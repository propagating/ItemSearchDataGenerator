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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared wikitext scanning helpers used by the various Wiki*Fetch classes. Template/param
 * scanning is done with character-by-character brace/bracket depth tracking (not regex) so it
 * survives nested templates such as {{Refn|...}} inside a param value.
 */
public final class WikiText
{
    private static final Pattern COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern LINK = Pattern.compile("\\[\\[(?:[^\\]|]*\\|)?([^\\]]*)\\]\\]");
    private static final Pattern COORDS = Pattern.compile("(\\d{3,5})\\s*,\\s*(\\d{3,5})");
    private static final Pattern PLANE = Pattern.compile("plane\\s*=\\s*(\\d+)");

    private WikiText()
    {
    }

    /** One `{{Name|body}}` match: {@code name}, its raw {@code body}, and the document offset of the opening `{{`. */
    public static final class Found
    {
        public final String name;
        public final String body;
        public final int start;

        Found(String name, String body, int start)
        {
            this.name = name;
            this.body = body;
            this.start = start;
        }
    }

    /** Removes {@code <!--...-->} spans (non-greedy, handles multiple occurrences). */
    public static String stripComments(String s)
    {
        return s == null ? null : COMMENT.matcher(s).replaceAll("");
    }

    // "[[Al Kharid|Al-Kharid]]" -> "Al-Kharid", "[[Varrock]]" -> "Varrock"
    public static String stripLinks(String s)
    {
        return s == null ? null : LINK.matcher(s).replaceAll("$1").trim();
    }

    /**
     * Splits a template body on '|' pipes that are at brace depth 0 (not inside a nested
     * {{template}}) and bracket depth 0 (not inside a [[link]]). Each piece is split at its
     * first '='; pieces without '=' (positional params) are ignored. Keys are lower-cased and
     * trimmed; values are trimmed.
     */
    public static Map<String, String> params(String templateBody)
    {
        Map<String, String> out = new HashMap<>();
        if (templateBody == null)
        {
            return out;
        }
        int n = templateBody.length();
        int braceDepth = 0;
        int bracketDepth = 0;
        int pieceStart = 0;
        int i = 0;
        while (i < n)
        {
            if (templateBody.startsWith("{{", i))
            {
                braceDepth++;
                i += 2;
            }
            else if (templateBody.startsWith("}}", i))
            {
                if (braceDepth > 0)
                {
                    braceDepth--;
                }
                i += 2;
            }
            else if (templateBody.startsWith("[[", i))
            {
                bracketDepth++;
                i += 2;
            }
            else if (templateBody.startsWith("]]", i))
            {
                if (bracketDepth > 0)
                {
                    bracketDepth--;
                }
                i += 2;
            }
            else if (templateBody.charAt(i) == '|' && braceDepth == 0 && bracketDepth == 0)
            {
                addParam(out, templateBody.substring(pieceStart, i));
                i++;
                pieceStart = i;
            }
            else
            {
                i++;
            }
        }
        addParam(out, templateBody.substring(pieceStart));
        return out;
    }

    private static void addParam(Map<String, String> out, String piece)
    {
        int eq = piece.indexOf('=');
        if (eq > 0)
        {
            out.put(piece.substring(0, eq).trim().toLowerCase(Locale.ROOT), piece.substring(eq + 1).trim());
        }
    }

    /**
     * Finds every {@code {{Name|...}}} template (for any of the given names) at brace depth 0,
     * capturing the body between the name and its brace-balanced closing {@code }}}. Bracket
     * depth ([[links]]) is not tracked here since a link cannot itself contain {@code }}}.
     * Results are returned in document order.
     */
    public static List<Found> templates(String wikitext, String... names)
    {
        List<Found> out = new ArrayList<>();
        if (wikitext == null || names.length == 0)
        {
            return out;
        }
        int n = wikitext.length();
        int depth = 0;
        String matchName = null;
        int matchStart = -1;
        int bodyStart = -1;
        int i = 0;
        while (i < n)
        {
            if (wikitext.startsWith("{{", i))
            {
                if (depth == 0)
                {
                    String nm = matchNameAt(wikitext, i + 2, names);
                    if (nm != null)
                    {
                        matchName = nm;
                        matchStart = i;
                        int nameEnd = i + 2 + nm.length();
                        bodyStart = wikitext.charAt(nameEnd) == '|' ? nameEnd + 1 : -1;
                    }
                }
                depth++;
                i += 2;
            }
            else if (wikitext.startsWith("}}", i))
            {
                if (depth > 0)
                {
                    depth--;
                }
                i += 2;
                if (depth == 0 && matchName != null)
                {
                    String body = bodyStart == -1 ? "" : wikitext.substring(bodyStart, i - 2);
                    out.add(new Found(matchName, body, matchStart));
                    matchName = null;
                }
            }
            else
            {
                i++;
            }
        }
        return out;
    }

    // returns the template name at position `pos` if it exactly matches one of `names` and is
    // immediately followed by '|' or '}}'; null otherwise
    private static String matchNameAt(String s, int pos, String[] names)
    {
        for (String name : names)
        {
            int end = pos + name.length();
            if (end >= s.length() || !s.regionMatches(pos, name, 0, name.length()))
            {
                continue;
            }
            char c = s.charAt(end);
            if (c == '|' || (c == '}' && end + 1 < s.length() && s.charAt(end + 1) == '}'))
            {
                return name;
            }
        }
        return null;
    }

    // "|field = value" up to end of line, e.g. within an infobox body
    public static String field(String body, String name)
    {
        // spaces and tabs only around the value: a blank "|field = " must read as blank rather
        // than swallow the newline and hand back the next line
        Matcher m = Pattern.compile("(?m)^\\s*\\|\\s*" + Pattern.quote(name)
            + "[ \\t]*=[ \\t]*(.*?)[ \\t]*$").matcher(body);
        return m.find() ? m.group(1).trim() : null;
    }

    // "field1", "field2", ... until one is missing
    public static List<String> numberedFields(String body, String name)
    {
        List<String> out = new ArrayList<>();
        for (int i = 1; ; i++)
        {
            String v = field(body, name + i);
            if (v == null)
            {
                break;
            }
            out.add(v);
        }
        return out;
    }

    /**
     * Extracts the first "x,y" coordinate pair and optional "plane=" value from a template
     * body (e.g. a {{Map|...}} body). Returns {@code {x, y, plane}} (plane defaults to 0 when
     * absent), or null when no coordinate pair is found.
     */
    public static int[] mapCoords(String body)
    {
        if (body == null)
        {
            return null;
        }
        Matcher m = COORDS.matcher(body);
        if (!m.find())
        {
            return null;
        }
        int x = Integer.parseInt(m.group(1));
        int y = Integer.parseInt(m.group(2));
        int plane = 0;
        Matcher p = PLANE.matcher(body);
        if (p.find())
        {
            plane = Integer.parseInt(p.group(1));
        }
        return new int[] {x, y, plane};
    }
}
