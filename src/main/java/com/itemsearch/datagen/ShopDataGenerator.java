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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Set;
import com.itemsearch.datagen.output.OutputShop;
import com.itemsearch.datagen.source.ItemInfoJson;
import com.itemsearch.datagen.source.MonsterJson;
import com.itemsearch.datagen.source.ShopPageJson;
import com.itemsearch.datagen.source.ShopTableJson;
import com.itemsearch.datagen.source.ShopLineJson;

public final class ShopDataGenerator
{
    private static final String DEFAULT_MONSTERS = "monsters-complete.json";
    private static final String DEFAULT_SHOPS_SNAPSHOT = "wiki-shops.json";
    private static final String DEFAULT_ITEMS_SNAPSHOT = "wiki-items.json";
    // same sibling-repo layout as DropDataGenerator.DEFAULT_OUTPUT: the ItemSearch
    // plugin checkout lives at ../../JAVA/ItemSearch relative to this repo
    private static final String DEFAULT_OUTPUT =
        "../../JAVA/ItemSearch/src/main/resources/com/itemsearch/shops.json";

    private ShopDataGenerator()
    {
    }

    public static void main(String[] args) throws IOException
    {
        boolean refreshShops = false;
        boolean refreshItems = false;
        List<String> positional = new ArrayList<>();
        for (String a : args)
        {
            if ("--refresh-shops".equals(a))
            {
                refreshShops = true;
            }
            else if ("--refresh-items".equals(a))
            {
                refreshItems = true;
            }
            else
            {
                positional.add(a);
            }
        }
        Path monsters = Paths.get(positional.isEmpty() ? DEFAULT_MONSTERS : positional.get(0));
        Path shopsSnapshot = Paths.get(DEFAULT_SHOPS_SNAPSHOT);
        Path itemsSnapshot = Paths.get(DEFAULT_ITEMS_SNAPSHOT);
        Path output = Paths.get(positional.size() >= 2 ? positional.get(1) : DEFAULT_OUTPUT);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

        if (refreshShops)
        {
            List<String> titles = WikiShopFetch.fetchShopPageTitles(gson);
            System.out.println("Fetching " + titles.size() + " shop pages...");
            Map<String, ShopPageJson> shops = WikiShopFetch.fetchShops(gson, titles);
            Files.write(shopsSnapshot,
                (gson.toJson(new TreeMap<>(shops)) + "\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote " + shops.size() + " shop pages to " + shopsSnapshot);
        }

        Map<String, ShopPageJson> pages = readSnapshot(gson, shopsSnapshot,
            new TypeToken<Map<String, ShopPageJson>>()
            {
            }.getType());

        if (refreshItems)
        {
            Set<String> names = new TreeSet<>();
            for (ShopPageJson page : pages.values())
            {
                for (ShopTableJson table : page.tables)
                {
                    for (ShopLineJson line : table.lines)
                    {
                        names.add(line.name);
                    }
                }
            }
            System.out.println("Fetching item info for " + names.size() + " item names...");
            Map<String, ItemInfoJson> items = WikiItemFetch.fetch(gson, names);
            Files.write(itemsSnapshot,
                (gson.toJson(new TreeMap<>(items)) + "\n").getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote " + items.size() + " item records to " + itemsSnapshot);
        }

        Map<String, ItemInfoJson> itemInfo = readSnapshot(gson, itemsSnapshot,
            new TypeToken<Map<String, ItemInfoJson>>()
            {
            }.getType());

        Map<String, Integer> fallback = Collections.emptyMap();
        if (Files.exists(monsters))
        {
            Type monstersType = new TypeToken<Map<String, MonsterJson>>()
            {
            }.getType();
            try (BufferedReader reader = Files.newBufferedReader(monsters, StandardCharsets.UTF_8))
            {
                fallback = ShopTransform.itemIdsFromMonsters(gson.fromJson(reader, monstersType));
            }
        }

        List<OutputShop> shops = ShopTransform.transform(pages, itemInfo, fallback);
        Files.write(output, (gson.toJson(shops) + "\n").getBytes(StandardCharsets.UTF_8));
        long unresolved = shops.stream().flatMap(s -> s.items.stream())
            .filter(i -> i.itemId < 0).count();
        long priceless = shops.stream().flatMap(s -> s.items.stream())
            .filter(i -> i.sellPrice == null).count();
        System.out.println("Wrote " + shops.size() + " shops to " + output);
        System.out.println(unresolved + " items without ids, " + priceless + " without prices");
    }

    private static <T> T readSnapshot(Gson gson, Path path, Type type) throws IOException
    {
        if (!Files.exists(path))
        {
            throw new IOException("Missing snapshot " + path
                + " — run with --refresh-shops / --refresh-items first");
        }
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8))
        {
            return gson.fromJson(reader, type);
        }
    }
}
