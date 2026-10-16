/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.mode;

import java.util.List;
import software.hibiscus.hibiscusfaultline.config.Settings;

/** All modes, in scroll order. The index of a mode in {@link #ALL} is what goes over the network. */
public final class Modes {
    public static final Mode SHAPELESS = new ShapelessMode();

    public static final List<Mode> ALL = List.of(
            SHAPELESS,
            new SmallTunnelMode(),
            new MiningTunnelMode(),
            new LargeTunnelMode(),
            new EscapeTunnelMode(),
            new SmallSquareMode());

    private Modes() {}

    public static int indexOf(Mode mode) {
        return ALL.indexOf(mode);
    }

    public static Mode byIndex(int index) {
        return index >= 0 && index < ALL.size() ? ALL.get(index) : SHAPELESS;
    }

    public static Mode byId(String id) {
        for (Mode mode : ALL) {
            if (mode.id().equals(id)) {
                return mode;
            }
        }
        return SHAPELESS;
    }

    /**
     * Steps from {@code current} through the enabled modes, wrapping around. Returns {@code current}
     * unchanged if no other mode is enabled.
     */
    public static Mode cycle(Mode current, int direction, Settings settings) {
        int size = ALL.size();
        int index = indexOf(current);
        for (int i = 1; i <= size; i++) {
            Mode candidate = ALL.get(Math.floorMod(index + direction * i, size));
            if (settings.isEnabled(candidate)) {
                return candidate;
            }
        }
        return current;
    }

    /** {@code mode} if it is enabled, otherwise the first enabled mode, or {@code null} if none are. */
    public static Mode usable(Mode mode, Settings settings) {
        if (settings.isEnabled(mode)) {
            return mode;
        }
        for (Mode candidate : ALL) {
            if (settings.isEnabled(candidate)) {
                return candidate;
            }
        }
        return null;
    }
}
