/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.config;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.neoforged.neoforge.common.ModConfigSpec;
import software.hibiscus.hibiscusfaultline.mode.Mode;
import software.hibiscus.hibiscusfaultline.mode.Modes;

/**
 * Gameplay limits. Registered as a synced config, so connected clients receive the server's values
 * and the preview they draw matches what the server will actually break.
 */
public final class ServerConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.IntValue MAX_BLOCKS = BUILDER
            .comment("Most blocks a single vein-mine can break, including the one you broke yourself.")
            .translation(key("maxBlocks"))
            .defineInRange("maxBlocks", 64, 1, 4096);

    public static final ModConfigSpec.IntValue MAX_DISTANCE = BUILDER
            .comment("How far from the first block, in blocks, the selection may reach.")
            .translation(key("maxDistance"))
            .defineInRange("maxDistance", 32, 1, 128);

    public static final ModConfigSpec.DoubleValue HUNGER_PER_BLOCK = BUILDER
            .comment("Extra exhaustion added for each additional block broken (vanilla adds 0.005 per block on its own).")
            .translation(key("hungerPerBlock"))
            .defineInRange("hungerPerBlock", 0.025, 0.0, 10.0);

    public static final ModConfigSpec.IntValue COOLDOWN_TICKS = BUILDER
            .comment("Ticks a player must wait between vein-mines. 20 ticks is one second.")
            .translation(key("cooldownTicks"))
            .defineInRange("cooldownTicks", 0, 0, 72000);

    public static final ModConfigSpec.BooleanValue STOP_BEFORE_TOOL_BREAKS = BUILDER
            .comment("Stop mining when the held tool has one point of durability left, so it is never destroyed.")
            .translation(key("stopBeforeToolBreaks"))
            .define("stopBeforeToolBreaks", true);

    public static final ModConfigSpec.BooleanValue GATHER_DROPS = BUILDER
            .comment("Drop everything at the block you broke instead of scattering it across the vein.")
            .translation(key("gatherDrops"))
            .define("gatherDrops", true);

    public static final ModConfigSpec.BooleanValue DROPS_TO_INVENTORY = BUILDER
            .comment("With gatherDrops on, put the drops straight into the player's inventory. Anything that does not fit is dropped at the block.")
            .translation(key("dropsToInventory"))
            .define("dropsToInventory", false);

    public static final ModConfigSpec.BooleanValue MERGE_ORE_VARIANTS = BUILDER
            .comment("Stone and deepslate variants of the same ore count as the same block (uses the c:ores/<name> tags).")
            .translation(key("mergeOreVariants"))
            .define("mergeOreVariants", true);

    public static final ModConfigSpec.BooleanValue MERGE_WOOD = BUILDER
            .comment("Logs and wood of the same tree count as the same block.")
            .translation(key("mergeWood"))
            .define("mergeWood", true);

    public static final ModConfigSpec.BooleanValue SHAPED_MODES_MATCH = BUILDER
            .comment("Tunnel and square modes only take blocks that match the first block, like Shapeless does.")
            .translation(key("shapedModesMatch"))
            .define("shapedModesMatch", false);

    public static final ModConfigSpec.BooleanValue REQUIRE_TOOL = BUILDER
            .comment("Only allow vein-mining while holding a tool. When off, any item or a bare hand works.")
            .translation(key("requireTool"))
            .define("requireTool", false);

    public static final ModConfigSpec.BooleanValue TOOL_MUST_SUIT_BLOCK = BUILDER
            .comment("Only take blocks the held tool is meant for, so a shovel skips stone and a pickaxe skips dirt. A bare hand or non-tool item only takes blocks that drop without a tool.")
            .translation(key("toolMustSuitBlock"))
            .define("toolMustSuitBlock", true);

    private static final Map<String, ModConfigSpec.BooleanValue> MODE_ENABLED = new LinkedHashMap<>();

    public static final ModConfigSpec.BooleanValue BUILD_ENABLED;
    public static final ModConfigSpec.IntValue BUILD_MAX_VOLUME;
    public static final ModConfigSpec.IntValue BUILD_MAX_VOLUME_CREATIVE;
    public static final ModConfigSpec.IntValue BUILD_MAX_DISTANCE;
    public static final ModConfigSpec.IntValue BUILD_MAX_DISTANCE_CREATIVE;
    public static final ModConfigSpec.IntValue BUILD_GHOST_PREVIEW_LIMIT;

    static {
        BUILDER.comment("Which modes players can pick.").translation(key("modes")).push("modes");
        for (Mode mode : Modes.ALL) {
            MODE_ENABLED.put(mode.id(), BUILDER
                    .comment("Allow the " + mode.id() + " mode.")
                    .translation(key("modes." + mode.id()))
                    .define(mode.id(), true));
        }
        BUILDER.pop();

        BUILDER.comment("The build tool slot to the right of the hotbar.").translation(key("buildTools")).push("buildTools");
        BUILD_ENABLED = BUILDER
                .comment("Let players use the build tool slot. The faultline.build.use permission node can still turn it off per player.")
                .translation(key("buildTools.enabled"))
                .define("enabled", true);
        BUILD_MAX_VOLUME = BUILDER
                .comment("Most blocks one build operation can cover in survival. The faultline.build.max_volume permission overrides it per player.")
                .translation(key("buildTools.maxVolume"))
                .defineInRange("maxVolume", 4096, 1, 1_000_000);
        BUILD_MAX_VOLUME_CREATIVE = BUILDER
                .comment("Most blocks one build operation can cover in creative. The faultline.build.max_volume permission overrides it per player.")
                .translation(key("buildTools.maxVolumeCreative"))
                .defineInRange("maxVolumeCreative", 65536, 1, 1_000_000);
        BUILD_MAX_DISTANCE = BUILDER
                .comment("How far from the player, in blocks, a selection or destination may be in survival.")
                .translation(key("buildTools.maxDistance"))
                .defineInRange("maxDistance", 64, 1, 512);
        BUILD_MAX_DISTANCE_CREATIVE = BUILDER
                .comment("How far from the player, in blocks, a selection or destination may be in creative.")
                .translation(key("buildTools.maxDistanceCreative"))
                .defineInRange("maxDistanceCreative", 256, 1, 512);
        BUILD_GHOST_PREVIEW_LIMIT = BUILDER
                .comment("Most blocks drawn as ghost blocks in a preview. Bigger operations show only their outline.")
                .translation(key("buildTools.ghostPreviewLimit"))
                .defineInRange("ghostPreviewLimit", 4096, 0, 65536);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ServerConfig() {}

    /**
     * Reads the current values. Before the config has loaded (for example on the title screen) the
     * defaults are used, so callers never have to handle an unloaded config.
     */
    public static Settings settings() {
        int enabled = 0;
        for (int i = 0; i < Modes.ALL.size(); i++) {
            if (read(MODE_ENABLED.get(Modes.ALL.get(i).id()))) {
                enabled |= 1 << i;
            }
        }
        return new Settings(
                read(MAX_BLOCKS),
                read(MAX_DISTANCE),
                read(HUNGER_PER_BLOCK).floatValue(),
                read(COOLDOWN_TICKS),
                read(STOP_BEFORE_TOOL_BREAKS),
                read(GATHER_DROPS),
                read(DROPS_TO_INVENTORY),
                read(MERGE_ORE_VARIANTS),
                read(MERGE_WOOD),
                read(SHAPED_MODES_MATCH),
                read(REQUIRE_TOOL),
                read(TOOL_MUST_SUIT_BLOCK),
                enabled);
    }

    public static boolean buildToolsEnabled() {
        return read(BUILD_ENABLED);
    }

    public static int buildMaxVolume(boolean creative) {
        return read(creative ? BUILD_MAX_VOLUME_CREATIVE : BUILD_MAX_VOLUME);
    }

    public static int buildMaxDistance(boolean creative) {
        return read(creative ? BUILD_MAX_DISTANCE_CREATIVE : BUILD_MAX_DISTANCE);
    }

    public static int buildGhostPreviewLimit() {
        return read(BUILD_GHOST_PREVIEW_LIMIT);
    }

    private static <T> T read(ModConfigSpec.ConfigValue<T> value) {
        Supplier<T> source = SPEC.isLoaded() ? value : value::getDefault;
        return source.get();
    }

    private static String key(String name) {
        return "hibiscusfaultline.configuration." + name;
    }
}
