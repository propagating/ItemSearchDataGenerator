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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Just enough of the MediaWiki template language to read a shared drop table's rarities.
 *
 * <p>Shared tables do not state their rates, they compute them. The Killerwatt page writes
 * {@code {{HerbDropLines|34/140}}} and the template turns that into a rate per herb through
 * {@code {{{1}}}} substitution, {@code #vardefine}/{@code #var}, and {@code #expr} arithmetic.
 * Without evaluating those the drop still parses but its rarity is unreadable, which is how
 * thousands of herb and seed drops ended up marked "rate unknown".
 *
 * <p>This is deliberately not a MediaWiki implementation: it covers parameters, the three parser
 * functions the drop templates actually use, and the arithmetic those need. Anything else
 * evaluates to empty rather than being left as markup for the drop parser to trip over.
 */
public final class WikiExpr
{
    private WikiExpr()
    {
    }

    /**
     * Positional and named arguments of one transclusion, keyed "1", "2", ... for positional.
     *
     * @param body the text between the template name and its closing braces, without the
     *             leading pipe — exactly what {@code WikiText.Found.body} holds
     */
    public static Map<String, String> arguments(String body)
    {
        Map<String, String> out = new LinkedHashMap<>();
        if (body == null || body.trim().isEmpty())
        {
            return out;
        }
        int positional = 0;
        for (String part : splitTopLevel(body))
        {
            int eq = indexOfTopLevel(part, '=');
            if (eq >= 0)
            {
                out.put(part.substring(0, eq).trim(), part.substring(eq + 1).trim());
            }
            else
            {
                out.put(String.valueOf(++positional), part.trim());
            }
        }
        return out;
    }

    /** Substitutes the given arguments into a template body and evaluates its parser functions. */
    public static String render(String text, Map<String, String> arguments)
    {
        if (text == null)
        {
            return null;
        }
        String substituted = substituteParameters(text, arguments == null ? new HashMap<>() : arguments);
        return evaluateFunctions(substituted);
    }

    // {{{1}}}, {{{1|default}}}, {{{combat|135}}} -> the supplied value, else the default, else ""
    private static String substituteParameters(String text, Map<String, String> arguments)
    {
        StringBuilder out = new StringBuilder();
        int i = 0;
        while (i < text.length())
        {
            int start = text.indexOf("{{{", i);
            if (start < 0)
            {
                out.append(text, i, text.length());
                break;
            }
            int end = matchingClose(text, start, "{{{", "}}}");
            if (end < 0)
            {
                out.append(text, i, text.length());
                break;
            }
            out.append(text, i, start);
            String inner = substituteParameters(text.substring(start + 3, end), arguments);
            int pipe = indexOfTopLevel(inner, '|');
            String name = (pipe < 0 ? inner : inner.substring(0, pipe)).trim();
            String fallback = pipe < 0 ? "" : inner.substring(pipe + 1);
            String value = arguments.get(name);
            out.append(value != null ? value : fallback);
            i = end + 3;
        }
        return out.toString();
    }

    /**
     * Resolves {@code {{#name:...}}} calls leftmost-innermost, so a nested {@code #expr} is a
     * number before the {@code #vardefine} around it stores it, and a variable is defined before
     * a later {@code #var} reads it.
     */
    private static String evaluateFunctions(String text)
    {
        Map<String, String> vars = new HashMap<>();
        String current = text;
        // each pass resolves one call; the bound stops a pathological document from looping
        for (int guard = 0; guard < 10000; guard++)
        {
            int start = innermostCall(current);
            if (start < 0)
            {
                return current;
            }
            int end = matchingClose(current, start, "{{", "}}");
            if (end < 0)
            {
                return current;
            }
            String call = current.substring(start + 2, end);
            current = current.substring(0, start) + apply(call, vars) + current.substring(end + 2);
        }
        return current;
    }

    // offset of the leftmost "{{#" whose body contains no further "{{#"
    private static int innermostCall(String text)
    {
        int candidate = -1;
        for (int i = text.indexOf("{{#"); i >= 0; i = text.indexOf("{{#", i + 2))
        {
            int end = matchingClose(text, i, "{{", "}}");
            if (end < 0)
            {
                continue;
            }
            candidate = i;
            int nested = text.indexOf("{{#", i + 2);
            if (nested < 0 || nested > end)
            {
                return i; // nothing nested inside: this is the innermost of the leftmost chain
            }
        }
        return candidate;
    }

    private static String apply(String call, Map<String, String> vars)
    {
        int colon = call.indexOf(':');
        String name = (colon < 0 ? call : call.substring(0, colon)).trim().toLowerCase();
        String rest = colon < 0 ? "" : call.substring(colon + 1);
        java.util.List<String> parts = splitTopLevel(rest);
        switch (name)
        {
            case "#expr":
                return format(evaluate(rest));
            case "#vardefine":
                vars.put(parts.isEmpty() ? "" : parts.get(0).trim(),
                    parts.size() > 1 ? parts.get(1).trim() : "");
                return "";
            case "#var":
                String stored = vars.get(parts.isEmpty() ? "" : parts.get(0).trim());
                return stored != null ? stored : (parts.size() > 1 ? parts.get(1) : "");
            case "#if":
                boolean truthy = !parts.isEmpty() && !parts.get(0).trim().isEmpty();
                if (truthy)
                {
                    return parts.size() > 1 ? parts.get(1) : "";
                }
                return parts.size() > 2 ? parts.get(2) : "";
            default:
                // an unsupported parser function is dropped: leaving the markup would only feed
                // braces to the drop parser
                return "";
        }
    }

    /** @return the value, or null when the expression is malformed or divides by zero */
    static Double evaluate(String expression)
    {
        try
        {
            Parser p = new Parser(expression);
            double value = p.parseExpression();
            p.skipSpace();
            if (!p.done() || Double.isNaN(value) || Double.isInfinite(value))
            {
                return null;
            }
            return value;
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    private static String format(Double value)
    {
        if (value == null)
        {
            return "";
        }
        BigDecimal d = BigDecimal.valueOf(value).setScale(10, RoundingMode.HALF_UP).stripTrailingZeros();
        return d.scale() <= 0 ? d.toBigInteger().toString() : d.toPlainString();
    }

    /** Splits on top-level "|", ignoring pipes nested inside braces or links. */
    private static java.util.List<String> splitTopLevel(String text)
    {
        java.util.List<String> out = new java.util.ArrayList<>();
        int depth = 0;
        int last = 0;
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c == '{' || c == '[')
            {
                depth++;
            }
            else if (c == '}' || c == ']')
            {
                depth--;
            }
            else if (c == '|' && depth <= 0)
            {
                out.add(text.substring(last, i));
                last = i + 1;
            }
        }
        out.add(text.substring(last));
        return out;
    }

    private static int indexOfTopLevel(String text, char target)
    {
        int depth = 0;
        for (int i = 0; i < text.length(); i++)
        {
            char c = text.charAt(i);
            if (c == '{' || c == '[')
            {
                depth++;
            }
            else if (c == '}' || c == ']')
            {
                depth--;
            }
            else if (c == target && depth <= 0)
            {
                return i;
            }
        }
        return -1;
    }

    // index of the closer matching the opener at `start`, or -1
    private static int matchingClose(String text, int start, String open, String close)
    {
        int depth = 0;
        int i = start;
        while (i < text.length())
        {
            if (text.startsWith(open, i))
            {
                depth++;
                i += open.length();
            }
            else if (text.startsWith(close, i))
            {
                depth--;
                if (depth == 0)
                {
                    return i;
                }
                i += close.length();
            }
            else
            {
                i++;
            }
        }
        return -1;
    }

    /** Recursive-descent parser for the MediaWiki {@code #expr} grammar. */
    private static final class Parser
    {
        private final String text;
        private int pos;

        Parser(String text)
        {
            this.text = text == null ? "" : text;
        }

        boolean done()
        {
            return pos >= text.length();
        }

        void skipSpace()
        {
            while (pos < text.length() && Character.isWhitespace(text.charAt(pos)))
            {
                pos++;
            }
        }

        // "round" binds loosest, matching MediaWiki: "1/3 round 2" rounds the quotient
        double parseExpression()
        {
            double left = parseSum();
            while (word("round"))
            {
                double places = parseSum();
                BigDecimal d = BigDecimal.valueOf(left)
                    .setScale((int) places, RoundingMode.HALF_UP);
                left = d.doubleValue();
            }
            return left;
        }

        private double parseSum()
        {
            double left = parseProduct();
            while (true)
            {
                skipSpace();
                if (symbol('+'))
                {
                    left += parseProduct();
                }
                else if (symbol('-'))
                {
                    left -= parseProduct();
                }
                else
                {
                    return left;
                }
            }
        }

        private double parseProduct()
        {
            double left = parsePower();
            while (true)
            {
                skipSpace();
                if (symbol('*'))
                {
                    left *= parsePower();
                }
                else if (symbol('/'))
                {
                    double divisor = parsePower();
                    if (divisor == 0)
                    {
                        throw new ArithmeticException("division by zero");
                    }
                    left /= divisor;
                }
                else if (word("mod"))
                {
                    left %= parsePower();
                }
                else
                {
                    return left;
                }
            }
        }

        private double parsePower()
        {
            double base = parseUnary();
            skipSpace();
            if (symbol('^'))
            {
                return Math.pow(base, parsePower());
            }
            return base;
        }

        private double parseUnary()
        {
            skipSpace();
            if (symbol('-'))
            {
                return -parseUnary();
            }
            if (symbol('+'))
            {
                return parseUnary();
            }
            // floor/ceil/sqrt/abs/trunc are prefix operators; "floor(x)" is the operator
            // applied to a parenthesised group, so no special casing of brackets is needed
            for (String fn : new String[]{"floor", "ceil", "sqrt", "abs", "trunc", "not"})
            {
                if (word(fn))
                {
                    double v = parseUnary();
                    switch (fn)
                    {
                        case "floor":
                            return Math.floor(v);
                        case "ceil":
                            return Math.ceil(v);
                        case "sqrt":
                            return Math.sqrt(v);
                        case "abs":
                            return Math.abs(v);
                        case "trunc":
                            return (long) v;
                        default:
                            return v == 0 ? 1 : 0;
                    }
                }
            }
            return parsePrimary();
        }

        private double parsePrimary()
        {
            skipSpace();
            if (symbol('('))
            {
                double v = parseExpression();
                skipSpace();
                if (!symbol(')'))
                {
                    throw new IllegalStateException("unclosed bracket");
                }
                return v;
            }
            int start = pos;
            while (pos < text.length()
                && (Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '.'))
            {
                pos++;
            }
            if (start == pos)
            {
                throw new IllegalStateException("expected a number at " + pos);
            }
            return Double.parseDouble(text.substring(start, pos));
        }

        private boolean symbol(char c)
        {
            skipSpace();
            if (pos < text.length() && text.charAt(pos) == c)
            {
                pos++;
                return true;
            }
            return false;
        }

        private boolean word(String w)
        {
            skipSpace();
            if (!text.regionMatches(true, pos, w, 0, w.length()))
            {
                return false;
            }
            int after = pos + w.length();
            if (after < text.length() && Character.isLetterOrDigit(text.charAt(after)))
            {
                return false;
            }
            pos = after;
            return true;
        }
    }
}
