/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.config;

import software.hibiscus.hibiscusfaultline.mode.Mode;
import software.hibiscus.hibiscusfaultline.mode.Modes;

/**
 * A snapshot of the server config, so one vein-mine or preview sees consistent values.
 * {@code enabledModes} has bit {@code i} set when mode {@code i} of {@link Modes#ALL} is enabled.
 */
public record Settings(
        int maxBlocks,
        int maxDistance,
        float hungerPerBlock,
        int cooldownTicks,
        boolean stopBeforeToolBreaks,
        boolean gatherDrops,
        boolean dropsToInventory,
        boolean mergeOreVariants,
        boolean mergeWood,
        boolean shapedModesMatch,
        boolean requireTool,
        boolean toolMustSuitBlock,
        int enabledModes) {

    public boolean isEnabled(Mode mode) {
        int index = Modes.indexOf(mode);
        return index >= 0 && (enabledModes & (1 << index)) != 0;
    }

    public boolean anyModeEnabled() {
        return enabledModes != 0;
    }
}
