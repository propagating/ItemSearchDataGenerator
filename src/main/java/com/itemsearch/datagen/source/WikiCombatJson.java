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
package com.itemsearch.datagen.source;

import java.util.List;

public class WikiCombatJson
{
    // raw {{Infobox Monster}} field values as text; numbered variants collected in order
    public String maxHit;
    public List<String> maxHits;         // max hit1, max hit2, ... (one per version)
    public String attackStyle;
    public List<String> attackStyles;   // attack style1, attack style2, ...
    public String attackSpeed;
    public List<String> attackSpeeds;
    // free-text "poisonous" value: "No", "Yes (6)", "Yes ([[venom]])"
    public String poisonous;    // attack speed1, attack speed2, ...

    // Defence bonuses per attack style, as the wiki states them: the LOWER the number the worse
    // this monster defends, so the lowest is its weakness. Numbered variants for versioned pages.
    public String defStab;
    public List<String> defStabs;
    public String defSlash;
    public List<String> defSlashes;
    public String defCrush;
    public List<String> defCrushes;
    public String defMagic;
    public List<String> defMagics;
    public String defRanged;         // "dstandard": standard ranged, the usual case
    public List<String> defRangeds;
    // stated outright rather than derived: "+50% damage from Earth spells"
    public String elementalWeaknessType;
    public String elementalWeaknessPercent;

    // What the monster shrugs off. Resistances are percentages ("0", "100"); the immune fields
    // are yes/no words, burn a phrase ("Immune to weak burns"). Numbered variants per version.
    public String poisonResistance;
    public List<String> poisonResistances;
    public String venomResistance;
    public List<String> venomResistances;
    public String freezeResistance;
    public List<String> freezeResistances;
    public String immuneCannon;
    public List<String> immuneCannons;
    public String immuneThrall;
    public List<String> immuneThralls;
    public String immuneBurn;
    public List<String> immuneBurns;
}
