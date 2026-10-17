/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.build;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * A toggle in a tool's options panel. Values are kept per tool, so Keep Existing can be on for Move and off for
 * Clone; see {@link BuildOptions}.
 */
public enum BuildOption {
    /** Middle-click extends the selection to the looked-at block instead of doing Move To. */
    MIDDLE_EXTEND(true, false),
    KEEP_EXISTING(false, false),
    MOVE_ENTITIES(false, false),
    MOVE_AIR(false, false),
    COPY_AIR(false, false),
    COPY_ENTITIES(false, true),
    /** Place what the materials allow, bottom layer up, instead of refusing when short. */
    PARTIAL(false, false),
    MASK(false, false),
    FLUIDS(true, false),
    AIR_ONLY(false, false),
    REPLACE(false, false),
    HOLLOW(false, false),
    WALLS(false, false);

    /** Options every tool shows, above its own. */
    static final List<BuildOption> SHARED = List.of(MIDDLE_EXTEND);

    private final boolean defaultValue;
    private final boolean creativeOnly;

    BuildOption(boolean defaultValue, boolean creativeOnly) {
        this.defaultValue = defaultValue;
        this.creativeOnly = creativeOnly;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean defaultValue() {
        return defaultValue;
    }

    /** Only usable in creative; shown greyed out in survival. */
    public boolean creativeOnly() {
        return creativeOnly;
    }

    public Component displayName() {
        return Component.translatable("hibiscusfaultline.build.option." + id());
    }
}
