/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.mode;

import net.minecraft.network.chat.Component;
import software.hibiscus.hibiscusfaultline.selection.SelectionContext;

/** A way of choosing which blocks around the origin get mined. */
public interface Mode {
    /** Stable id, used in the config, lang keys and the saved last mode. */
    String id();

    /** Whether every selected block has to match the origin block regardless of config. */
    default boolean alwaysMatches() {
        return false;
    }

    /**
     * Offers positions to the context, nearest to the origin first, until the context is full or the
     * shape is exhausted. The origin itself has already been added.
     */
    void collect(SelectionContext context);

    default Component displayName() {
        return Component.translatable("hibiscusfaultline.mode." + id());
    }
}
