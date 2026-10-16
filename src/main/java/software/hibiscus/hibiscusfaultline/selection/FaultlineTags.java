/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.selection;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import software.hibiscus.hibiscusfaultline.HibiscusFaultline;

public final class FaultlineTags {
    /** Blocks that are never selected. */
    public static final TagKey<Block> EXCLUDED = TagKey.create(Registries.BLOCK, HibiscusFaultline.id("excluded"));
    /** When this tag has any entries, only these blocks can be selected. */
    public static final TagKey<Block> INCLUDED_ONLY = TagKey.create(Registries.BLOCK, HibiscusFaultline.id("included_only"));
    /** Holding one of these turns Faultline off. */
    public static final TagKey<Item> EXCLUDED_TOOLS = TagKey.create(Registries.ITEM, HibiscusFaultline.id("excluded_tools"));

    private FaultlineTags() {}
}
