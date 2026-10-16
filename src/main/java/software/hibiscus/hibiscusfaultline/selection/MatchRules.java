/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.selection;

import java.util.List;
import java.util.function.Predicate;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import software.hibiscus.hibiscusfaultline.config.Settings;

/** Decides whether a block counts as "the same" as the origin block. */
public final class MatchRules {
    private MatchRules() {}

    /**
     * Builds a matcher for one origin. The origin's relevant tags are looked up once here rather
     * than for every candidate.
     */
    public static Predicate<BlockState> sameAs(BlockState origin, Settings settings) {
        Block originBlock = origin.getBlock();
        List<TagKey<Block>> oreTags = settings.mergeOreVariants() ? oreTags(origin) : List.of();
        List<TagKey<Block>> treeTags = settings.mergeWood() && origin.is(BlockTags.LOGS) ? treeTags(origin) : List.of();

        return candidate -> {
            if (candidate.is(originBlock)) {
                return true;
            }
            for (TagKey<Block> tag : oreTags) {
                if (candidate.is(tag)) {
                    return true;
                }
            }
            if (!treeTags.isEmpty() && candidate.is(BlockTags.LOGS)) {
                for (TagKey<Block> tag : treeTags) {
                    if (candidate.is(tag)) {
                        return true;
                    }
                }
            }
            return false;
        };
    }

    /** Leaves are only ever taken when the vein started on a leaf block. */
    public static boolean leavesAllowed(BlockState origin, BlockState candidate) {
        return !candidate.is(BlockTags.LEAVES) || origin.is(BlockTags.LEAVES);
    }

    /** The {@code c:ores/<name>} tags, which group every stone variant of one ore together. */
    private static List<TagKey<Block>> oreTags(BlockState state) {
        return state.typeHolder().tags()
                .filter(tag -> tag.location().getNamespace().equals("c"))
                .filter(tag -> {
                    String path = tag.location().getPath();
                    return path.startsWith("ores/") && path.indexOf('/', 5) < 0;
                })
                .toList();
    }

    /**
     * Per-species tags such as {@code minecraft:oak_logs} or {@code minecraft:crimson_stems}, which
     * cover the log, wood and stripped forms of one tree. Broad groupings that happen to share the
     * suffix (all natural logs, all stripped logs) are skipped so different trees stay separate.
     */
    private static List<TagKey<Block>> treeTags(BlockState state) {
        return state.typeHolder().tags()
                .filter(tag -> {
                    String path = tag.location().getPath();
                    return (path.endsWith("_logs") || path.endsWith("_stems"))
                            && !path.contains("stripped")
                            && !path.contains("natural")
                            && !path.contains("/");
                })
                .toList();
    }
}
