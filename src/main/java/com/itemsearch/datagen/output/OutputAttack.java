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
package com.itemsearch.datagen.output;

public class OutputAttack
{
    public String style;
    // what the attack counts as defensively when that differs from style: "magic ranged"
    // rolls magic accuracy (style) but is prayed against with ranged prayers (prayStyle).
    // null = same as style.
    public String prayStyle;
    // maxHit/speed are the low end of a range; maxHitMax/speedMax the high end. For a single-valued
    // attack the high equals the low (multi-version monsters collapse to a real min..max range).
    public int maxHit;
    public int maxHitMax;
    public int speed;
    public int speedMax;
    // conditional max hits (gear/prayer/consumable mitigations); null/empty = unconditional
    public java.util.List<OutputVariant> variants;

    public OutputAttack(String style, int maxHit, int speed)
    {
        this(style, maxHit, maxHit, speed, speed);
    }

    public OutputAttack(String style, int maxHit, int maxHitMax, int speed, int speedMax)
    {
        this.style = style;
        this.maxHit = maxHit;
        this.maxHitMax = maxHitMax;
        this.speed = speed;
        this.speedMax = speedMax;
    }
}
