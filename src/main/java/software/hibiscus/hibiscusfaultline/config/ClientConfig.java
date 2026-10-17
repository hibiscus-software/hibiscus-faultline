/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.config;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.TranslatableEnum;
import software.hibiscus.hibiscusfaultline.build.BuildOptions;

/** Per-install preferences. Only registered on the physical client. */
public final class ClientConfig {
    public enum Corner implements TranslatableEnum {
        TOP_LEFT,
        TOP_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_RIGHT;

        @Override
        public Component getTranslatedName() {
            return Component.translatable(key("hudCorner." + name().toLowerCase(Locale.ROOT)));
        }
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue HUD_ENABLED = BUILDER
            .comment("Show the Faultline panel while the key is held.")
            .translation(key("hudEnabled"))
            .define("hudEnabled", true);

    public static final ModConfigSpec.EnumValue<Corner> HUD_CORNER = BUILDER
            .comment("Which corner of the screen the panel sits in.")
            .translation(key("hudCorner"))
            .defineEnum("hudCorner", Corner.TOP_LEFT);

    public static final ModConfigSpec.IntValue HUD_OFFSET_X = BUILDER
            .comment("Pixels between the panel and the left or right edge of the screen (whichever side its corner is on).")
            .translation(key("hudOffsetX"))
            .defineInRange("hudOffsetX", 4, 0, 4096);

    public static final ModConfigSpec.IntValue HUD_OFFSET_Y = BUILDER
            .comment("Pixels between the panel and the top or bottom edge of the screen. The default leaves room for a one-line FPS counter.")
            .translation(key("hudOffsetY"))
            .defineInRange("hudOffsetY", 16, 0, 4096);

    public static final ModConfigSpec.ConfigValue<String> OUTLINE_COLOR = BUILDER
            .comment("Colour of the selection outline, as RRGGBB hex.")
            .translation(key("outlineColor"))
            .define("outlineColor", "FFFFFF", ClientConfig::isHexColor);

    public static final ModConfigSpec.BooleanValue REMEMBER_LAST_MODE = BUILDER
            .comment("Start in the mode you used last instead of Shapeless.")
            .translation(key("rememberLastMode"))
            .define("rememberLastMode", true);

    public static final ModConfigSpec.BooleanValue INVERT_SCROLL = BUILDER
            .comment("Reverse which way the mouse wheel cycles through the modes.")
            .translation(key("invertScroll"))
            .define("invertScroll", false);

    public static final ModConfigSpec.ConfigValue<String> LAST_MODE = BUILDER
            .comment("The mode used last. Written by the game; used when rememberLastMode is on.")
            .translation(key("lastMode"))
            .define("lastMode", "shapeless");

    public static final ModConfigSpec.BooleanValue BUILD_HINTS;
    public static final ModConfigSpec.BooleanValue BUILD_GHOSTS;
    public static final ModConfigSpec.ConfigValue<String> BUILD_LAST_TOOL;
    public static final ModConfigSpec.ConfigValue<String> BUILD_SET_BLOCK;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BUILD_OPTIONS;

    static {
        BUILDER.comment("The build tool slot.").translation(key("buildTools")).push("buildTools");
        BUILD_HINTS = BUILDER
                .comment("Show the mouse button hints next to the build tool's name.")
                .translation(key("buildTools.hints"))
                .define("hints", true);
        BUILD_GHOSTS = BUILDER
                .comment("Show where Move and Clone would put blocks as translucent ghost blocks.")
                .translation(key("buildTools.ghosts"))
                .define("ghosts", true);
        BUILD_LAST_TOOL = BUILDER
                .comment("The build tool used last. Written by the game.")
                .translation(key("buildTools.lastTool"))
                .define("lastTool", "move");
        BUILD_SET_BLOCK = BUILDER
                .comment("The block Set fills with, picked in the game. Empty uses the block in your offhand.")
                .translation(key("buildTools.setBlock"))
                .define("setBlock", "");
        BUILD_OPTIONS = BUILDER
                .comment("Each build tool's options, as tool.option=true or false. Written by the game.")
                .translation(key("buildTools.options"))
                .defineListAllowEmpty("options", new BuildOptions().write(), () -> "", value -> value instanceof String);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private ClientConfig() {}

    /** {@return the outline colour as opaque ARGB} */
    public static int outlineColor() {
        String value = SPEC.isLoaded() ? OUTLINE_COLOR.get() : OUTLINE_COLOR.getDefault();
        return 0xFF000000 | (isHexColor(value) ? Integer.parseInt(value.strip(), 16) : 0xFFFFFF);
    }

    private static boolean isHexColor(Object value) {
        return value instanceof String text
                && text.strip().toLowerCase(Locale.ROOT).matches("[0-9a-f]{6}");
    }

    private static String key(String name) {
        return "hibiscusfaultline.configuration." + name;
    }
}
