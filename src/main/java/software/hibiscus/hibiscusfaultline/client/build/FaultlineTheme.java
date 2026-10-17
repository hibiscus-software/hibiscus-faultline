/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client.build;

import java.util.Locale;
import net.minecraft.resources.Identifier;
import software.hibiscus.hibiscusfaultline.HibiscusFaultline;
import software.hibiscus.hibiscusfaultline.build.BuildTool;
import software.hibiscus.hibiscuslib.client.gui.HibiscusTheme;

/**
 * Every colour and sprite the build tools draw with. Shared colours come from the Hibiscus theme; the tool and axis
 * colours are extra theme keys ({@code faultline_move}, {@code faultline_axis_x} and so on) with built-in fallbacks,
 * so a theme resource pack can restyle them along with the rest of Hibiscus.
 */
public final class FaultlineTheme {
    /** What {@link HibiscusTheme#color} returns for a key the theme doesn't define. */
    private static final int MISSING = 0xFFFF00FF;

    private FaultlineTheme() {}

    public static HibiscusTheme hibiscus() {
        return HibiscusTheme.current();
    }

    /** {@return the tool's colour as opaque ARGB} Move amber, Clone blue, Stack teal, Erase red, Set green. */
    public static int tool(BuildTool tool) {
        int fallback = switch (tool) {
            case MOVE -> 0xFFF2A93B;
            case CLONE -> 0xFF4F8FF2;
            case STACK -> 0xFF2EC4B6;
            case ERASE -> 0xFFE5484D;
            case SET -> 0xFF4FC78A;
        };
        return color("faultline_" + tool.id(), fallback);
    }

    /** The X, Y and Z colours of the Size and Offset readouts: red, green and cyan. */
    public static int axisX() {
        return color("faultline_axis_x", 0xFFFF5555);
    }

    public static int axisY() {
        return color("faultline_axis_y", 0xFF55FF55);
    }

    public static int axisZ() {
        return color("faultline_axis_z", 0xFF55FFFF);
    }

    /** The label of a readout ("Size:", "Offset:"). */
    public static int readoutLabel() {
        return hibiscus().color(HibiscusTheme.WARNING);
    }

    /** The translucent backing behind readouts and in-world hints. */
    public static int backdrop() {
        return color("faultline_backdrop", 0xA0101114);
    }

    public static int text() {
        return hibiscus().text();
    }

    public static int textMuted() {
        return hibiscus().textMuted();
    }

    public static int textDisabled() {
        return hibiscus().color(HibiscusTheme.TEXT_DISABLED);
    }

    public static int accent() {
        return hibiscus().accent();
    }

    public static int negative() {
        return hibiscus().color(HibiscusTheme.NEGATIVE);
    }

    public static Identifier toolIcon(BuildTool tool) {
        return HibiscusFaultline.id("build/" + tool.id());
    }

    /** A small mouse with one button lit, for hints. */
    public static Identifier mouseIcon(MouseHint button) {
        return HibiscusFaultline.id("build/mouse_" + button.name().toLowerCase(Locale.ROOT));
    }

    private static int color(String key, int fallback) {
        int value = hibiscus().color(key);
        return value == MISSING ? fallback : value;
    }

    /** Which mouse button a hint is about. */
    public enum MouseHint {
        LEFT,
        RIGHT,
        MIDDLE,
        SCROLL
    }
}
