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

import java.util.ArrayList;
import java.util.List;

/**
 * One {{Recipe}} on an item's page: what goes in, what comes out, and what it takes. Cutting a
 * sacred eel into Zulrah's scales and smithing a bronze bar into a dagger are both written this
 * way; which of them count as breaking something down is decided later.
 */
public class RecipeJson
{
    public List<String> materials = new ArrayList<>();
    public String tools;   // "Knife", "Hammer"; null when none is named
    public String skill;   // skill1; null when no skill is involved (dismantling, opening)
    public String level;   // skill1lvl, as written
    public List<Output> outputs = new ArrayList<>();

    public static class Output
    {
        public String name;
        public String quantity; // as written: "7", "20000", "{{#expr:5/6 round 2}}", or null for one
        public String note;     // output quantity note, when the wiki explains a variable yield

        public Output()
        {
        }

        public Output(String name, String quantity, String note)
        {
            this.name = name;
            this.quantity = quantity;
            this.note = note;
        }
    }
}
