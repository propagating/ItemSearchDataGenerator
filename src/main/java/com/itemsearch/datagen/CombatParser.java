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
import java.util.Set;
import java.util.HashSet;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.itemsearch.datagen.output.OutputAttack;
import com.itemsearch.datagen.output.OutputVariant;

/**
 * Pure parsers for OSRS Wiki {{Infobox Monster}} combat fields. Turns the wiki's free-text
 * "max hit", "attack style", and "attack speed" values into per-style {@link OutputAttack}s.
 */
public final class CombatParser
{
    // a run of digits, optionally followed by a "(annotation)"; "99+" means at least 99, and the
    // plus must not come between the number and its note
    private static final Pattern MAX_HIT =
        Pattern.compile("(\\d+)(?:\\s*[–-]\\s*(\\d+))?\\+?\\s*(?:\\(([^)]*)\\))?");
    // a max-hit note that describes the player rather than the monster: what they are wearing,
    // or what they are praying. Such a figure is a variant of the attack, not a further attack.
    private static final Pattern GEAR_NOTE = Pattern.compile("^(without|with|no|not wearing|wearing)\\b");
    private static final Pattern PRAYER_NOTE = Pattern.compile("protect from|\\bpray");
    // the two ways a gear note states which side of the protection its figure is on
    private static final Pattern WITHOUT_NOTE = Pattern.compile("^(?:without|no|not wearing)\\s+(.+)$");
    private static final Pattern WITH_NOTE = Pattern.compile("^(?:with|wearing)\\s+(.+)$");
    private static final Pattern INT = Pattern.compile("\\d+");
    // a "(...)" beside a style: a note about it, never part of its name
    private static final Pattern STYLE_NOTE = Pattern.compile("\\s*\\(([^)]*)\\)");
    // max-hit annotations that mean "the ordinary attack", so they attribute nothing
    private static final Set<String> BASE_NOTES =
        new HashSet<>(Arrays.asList("normal", "standard", "default", "base", "regular"));
    // the three styles a hybrid can be built from; anything else is not a hybrid pair
    private static final Set<String> COMBAT_CLASSES =
        new HashSet<>(Arrays.asList("melee", "ranged", "magic"));

    private CombatParser()
    {
    }

    public static final class MaxHit
    {
        public final int value;
        public final int valueMax;      // the top of a "1–12"; equal to value for a plain number
        public final String annotation; // null when the number had no "(...)" note

        public MaxHit(int value, String annotation)
        {
            this(value, value, annotation);
        }

        public MaxHit(int value, int valueMax, String annotation)
        {
            this.value = value;
            this.valueMax = valueMax;
            this.annotation = annotation;
        }
    }

    public static final class ParsedCombat
    {
        public final List<OutputAttack> attacks;
        public final boolean gap;

        public ParsedCombat(List<OutputAttack> attacks, boolean gap)
        {
            this.attacks = attacks;
            this.gap = gap;
        }
    }

    /** Normalized style tokens across all given fields, in order. */
    public static List<String> parseStyles(String... fields)
    {
        List<String> out = new ArrayList<>();
        for (String field : fields)
        {
            if (field == null)
            {
                continue;
            }
            for (String raw : field.split(","))
            {
                String s = normaliseStyle(STYLE_NOTE.matcher(raw).replaceAll(""));
                if (!s.isEmpty())
                {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /**
     * What the page says in brackets beside each style, lined up with {@link #parseStyles}: null
     * where a style has none. "Magical ranged (100% prayer penetration)" is a hybrid style with a
     * note, not a five-word style, and the note is worth showing.
     */
    public static List<String> parseStyleNotes(String... fields)
    {
        List<String> out = new ArrayList<>();
        for (String field : fields)
        {
            if (field == null)
            {
                continue;
            }
            for (String raw : field.split(","))
            {
                if (normaliseStyle(STYLE_NOTE.matcher(raw).replaceAll("")).isEmpty())
                {
                    continue; // no style, so no slot to line up with
                }
                Matcher note = STYLE_NOTE.matcher(raw);
                String text = note.find() ? stripMarkup(note.group(1)) : "";
                out.add(text.isEmpty() ? null : text);
            }
        }
        return out;
    }

    private static String normaliseStyle(String raw)
    {
        String s = stripMarkup(raw);
        if (s.isEmpty())
        {
            return "";
        }
        if (s.contains("typeless"))
        {
            return "typeless";
        }
        return s;
    }

    // strip [[Link|Text]] / [[Link]] markup and anchors, lowercase, trim
    private static String stripMarkup(String raw)
    {
        return raw.replaceAll("\\[\\[([^\\]|]*\\|)?", "").replace("]]", "")
            .toLowerCase(Locale.ROOT).trim();
    }

    /** (value, annotation) per numeric token in the max-hit free text. */
    public static List<MaxHit> parseMaxHits(String field)
    {
        List<MaxHit> out = new ArrayList<>();
        if (field == null)
        {
            return out;
        }
        Matcher m = MAX_HIT.matcher(field);
        while (m.find())
        {
            int value;
            try
            {
                value = Integer.parseInt(m.group(1));
            }
            catch (NumberFormatException e)
            {
                // digit-run too large for an int (e.g. malformed wiki data); skip it
                continue;
            }
            int valueMax = value;
            if (m.group(2) != null)
            {
                try
                {
                    valueMax = Math.max(value, Integer.parseInt(m.group(2)));
                }
                catch (NumberFormatException e)
                {
                    // the top of the range is unreadable; the bottom still is
                }
            }
            String note = m.group(3) == null ? null : normaliseStyle(m.group(3));
            boolean unannotated = note == null || note.isEmpty() || BASE_NOTES.contains(note);
            out.add(new MaxHit(value, valueMax, unannotated ? null : note));
        }
        return out;
    }

    /** First integer found in each non-blank field, in order. */
    public static List<Integer> parseSpeeds(String... fields)
    {
        List<Integer> out = new ArrayList<>();
        for (String field : fields)
        {
            if (field == null || field.trim().isEmpty())
            {
                continue;
            }
            Matcher m = INT.matcher(field);
            if (m.find())
            {
                try
                {
                    out.add(Integer.parseInt(m.group()));
                }
                catch (NumberFormatException e)
                {
                    // digit-run too large for an int (e.g. malformed wiki data); skip it
                }
            }
        }
        return out;
    }

    /**
     * Splits a hybrid style token into {accuracy roll, what it is prayed against}, or null when
     * the token names one plain style.
     *
     * <p>The wiki writes these as two words ("Magical melee", "Ranged magic"): the first is the
     * style the accuracy roll uses, the second is the style that defends against it — Magical
     * melee rolls magic accuracy but is blocked by Protect from Melee. Without this the token
     * reaches the client as an unknown style and the combat card claims nothing protects.
     */
    public static String[] hybridOf(String token)
    {
        String[] words = token.split("\\s+");
        if (words.length != 2)
        {
            return null;
        }
        String roll = styleClass(words[0]);
        String pray = styleClass(words[1]);
        boolean bothKnown = COMBAT_CLASSES.contains(roll) && COMBAT_CLASSES.contains(pray);
        return bothKnown && !roll.equals(pray) ? new String[]{roll, pray} : null;
    }

    // melee sub-styles collapse to "melee"; ranged/projectile to "ranged"; magic to "magic".
    private static String styleClass(String token)
    {
        switch (token)
        {
            case "stab":
            case "slash":
            case "crush":
            case "melee":
                return "melee";
            case "ranged":
            case "range":
            case "projectile":
            case "projectiles":
                return "ranged";
            case "magic":
            case "mage":
            case "magical":
                return "magic";
            default:
                return token;
        }
    }

    /**
     * One {@link OutputAttack} per style. Each style takes the max hit whose annotation is in the
     * same class (melee/ranged/magic); failing that, the sole unannotated value. Speeds match
     * positionally when counts line up, else a single speed applies to all. {@code gap} is true when
     * there is more than one style and the max hits could not be distinctly attributed.
     */
    public static ParsedCombat assemble(List<String> styles, List<MaxHit> maxHits, List<Integer> speeds)
    {
        return assemble(styles, null, maxHits, speeds);
    }

    /** As above, with {@link #parseStyleNotes} carried onto each attack as its note. */
    public static ParsedCombat assemble(List<String> styles, List<String> notes, List<MaxHit> maxHits,
        List<Integer> speeds)
    {
        List<OutputAttack> attacks = new ArrayList<>();
        boolean[] used = new boolean[maxHits.size()];
        int unresolved = 0;

        MaxHit soleUnannotated = null;
        int unannotatedCount = 0;
        for (MaxHit h : maxHits)
        {
            if (h.annotation == null)
            {
                unannotatedCount++;
                soleUnannotated = h;
            }
        }
        // what a note can name: the three combat classes, plus whatever styles this page has
        // ("dragonfire", "typeless") — a note is a style reference, not a special, when it says one
        Set<String> known = new HashSet<>(COMBAT_CLASSES);
        for (String style : styles)
        {
            known.add(styleClass(style));
        }

        for (int i = 0; i < styles.size(); i++)
        {
            String style = styles.get(i);
            String cls = styleClass(style);
            int maxHit = 0;

            // 1) prefer a not-yet-used max hit whose annotation names this style's class. A note
            //    naming several ("Magic/Ranged") is one value for all of them, so it stays usable.
            for (int j = 0; j < maxHits.size(); j++)
            {
                MaxHit h = maxHits.get(j);
                Set<String> classes = h.annotation == null ? null : classesOf(h.annotation, known);
                if (!used[j] && classes != null && classes.contains(cls))
                {
                    maxHit = h.value;
                    used[j] = classes.size() == 1;
                    break;
                }
            }
            // 2) else the single plain value is the page saying every style hits that hard:
            //    "Magic, Ranged" over a bare 41 is both at 41, since the wiki annotates when
            //    they differ. Several plain values, or none, leave this style unresolved.
            if (maxHit == 0)
            {
                if (unannotatedCount == 1)
                {
                    maxHit = soleUnannotated.value;
                }
                else
                {
                    unresolved++;
                }
            }

            int speed = speedFor(speeds, i);
            String[] hybrid = hybridOf(style);
            OutputAttack attack = new OutputAttack(
                hybrid == null ? style : hybrid[0], maxHit, speed);
            if (hybrid != null)
            {
                attack.prayStyle = hybrid[1];
            }
            if (notes != null && i < notes.size() && notes.get(i) != null)
            {
                attack.notes = notes.get(i);
            }
            attacks.add(attack);
        }

        // a gap is a style the page left without a figure it could be given: with one style
        // even a missing max hit is just the page's silence, with several it is an ambiguity
        boolean gap = styles.size() > 1 && unresolved > 0;

        // a max hit whose note names no style at all is one of two things. A condition on the
        // player ("without nose peg", "Protect from Melee") is the same attack hitting differently:
        // a variant of it, filed as such when there is one attack for it to belong to. Anything else
        // ("Charge attack", "Stomp", "at 1 hitpoints") is a further attack the infobox knows about:
        // pre-filled under the note's name so the curator confirms rather than retypes, and asked
        // about, since the client does not know that style. A condition on a monster with several
        // attacks is asked about too, since the page does not say which one it modifies.
        for (int j = 0; j < maxHits.size(); j++)
        {
            MaxHit h = maxHits.get(j);
            if (used[j] || h.annotation == null || !classesOf(h.annotation, known).isEmpty())
            {
                continue;
            }
            String kind = variantKind(h.annotation);
            if (kind != null && attacks.size() == 1)
            {
                OutputAttack only = attacks.get(0);
                if (only.variants == null)
                {
                    only.variants = new ArrayList<>();
                }
                // the client shows a variant as a protection: "8 with Nose peg", "14 unprotected".
                // The wiki states a "without" figure the other way round, the bare number being
                // the protected one, so the two swap and the note loses its "without"
                Matcher without = WITHOUT_NOTE.matcher(h.annotation);
                if (without.find())
                {
                    only.variants.add(new OutputVariant(capitalise(without.group(1).trim()), kind,
                        only.maxHit, only.maxHitMax));
                    only.maxHit = h.value;
                    only.maxHitMax = h.valueMax;
                    continue;
                }
                Matcher with = WITH_NOTE.matcher(h.annotation);
                String label = with.find() ? with.group(1).trim() : h.annotation;
                only.variants.add(new OutputVariant(capitalise(label), kind, h.value, h.valueMax));
                continue;
            }
            attacks.add(new OutputAttack(h.annotation, h.value, h.valueMax,
                speedFor(speeds, styles.size()), speedFor(speeds, styles.size())));
            gap = true;
        }
        return new ParsedCombat(attacks, gap);
    }

    // GEAR for what the player wears, PRAYER for what they pray, null for a note about the monster
    private static String variantKind(String annotation)
    {
        if (PRAYER_NOTE.matcher(annotation).find())
        {
            return "PRAYER";
        }
        if (GEAR_NOTE.matcher(annotation).find())
        {
            return "GEAR";
        }
        return null;
    }

    private static String capitalise(String s)
    {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // the styles a max-hit note names, out of the ones it could: "magic/ranged" both, "melee;
    // without insulated boots" melee, "projectile attack" ranged, "charge attack" none
    private static Set<String> classesOf(String annotation, Set<String> known)
    {
        Set<String> out = new HashSet<>();
        for (String part : annotation.split("[/;]"))
        {
            String whole = part.trim();
            if (whole.isEmpty())
            {
                continue;
            }
            // the whole phrase first, so "icy breath" is the style and not a special called "icy"
            String cls = styleClass(whole);
            if (!known.contains(cls))
            {
                cls = styleClass(whole.split("\\s+")[0]);
            }
            if (known.contains(cls))
            {
                out.add(cls);
            }
        }
        return out;
    }

    private static int speedFor(List<Integer> speeds, int index)
    {
        if (speeds.isEmpty())
        {
            return 0;
        }
        if (speeds.size() == 1)
        {
            return speeds.get(0);
        }
        return index < speeds.size() ? speeds.get(index) : speeds.get(speeds.size() - 1);
    }

    /**
     * Several versions of one monster read as one attack table.
     *
     * <p>Numbered infobox fields (attack style1/2/…, max hit1/2/…) each describe a version of the
     * monster, not another attack of it, and versions that share a combat level are one monster
     * to the player: Killerwatt stabs while idle and crushes once it turns to fight, and both are
     * met with Protect from Melee. So attacks join on what protects against them, and the joined
     * attack spans every version's numbers. A style every version agrees on keeps its name; ones
     * that differ within a class take the class's. A number no version gave (0) is left out of
     * the range rather than pulling it down to nothing. The result is a gap if any version was.
     */
    public static ParsedCombat fold(List<ParsedCombat> versions)
    {
        Map<String, OutputAttack> joined = new LinkedHashMap<>();
        boolean gap = false;
        for (ParsedCombat version : versions)
        {
            gap |= version.gap;
            for (OutputAttack a : version.attacks)
            {
                String key = styleClass(a.style) + "|" + (a.prayStyle == null ? "" : a.prayStyle);
                OutputAttack into = joined.get(key);
                if (into == null)
                {
                    into = new OutputAttack(a.style, a.maxHit, a.maxHitMax, a.speed, a.speedMax);
                    into.prayStyle = a.prayStyle;
                    into.notes = a.notes;
                    into.variants = a.variants;
                    into.effects = a.effects;
                    joined.put(key, into);
                    continue;
                }
                if (!into.style.equals(a.style))
                {
                    into.style = styleClass(a.style);
                }
                into.maxHit = lowOf(into.maxHit, a.maxHit);
                into.maxHitMax = Math.max(into.maxHitMax, a.maxHitMax);
                into.speed = lowOf(into.speed, a.speed);
                into.speedMax = Math.max(into.speedMax, a.speedMax);
                if (into.notes == null)
                {
                    into.notes = a.notes;
                }
                if (into.variants == null || into.variants.isEmpty())
                {
                    into.variants = a.variants;
                }
                if (into.effects == null || into.effects.isEmpty())
                {
                    into.effects = a.effects;
                }
            }
        }
        return new ParsedCombat(new ArrayList<>(joined.values()), gap);
    }

    // the lower of two range bottoms, where 0 means the version did not say
    private static int lowOf(int a, int b)
    {
        if (a <= 0)
        {
            return b;
        }
        return b <= 0 ? a : Math.min(a, b);
    }
}
