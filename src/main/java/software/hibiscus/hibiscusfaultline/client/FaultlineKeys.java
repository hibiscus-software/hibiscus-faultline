/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import software.hibiscus.hibiscusfaultline.HibiscusFaultline;

public final class FaultlineKeys {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(HibiscusFaultline.id("faultline"));

    /** Hold to vein-mine. Defaults to the grave (backtick) key. */
    public static final KeyMapping ACTIVATE = new KeyMapping(
            "key.hibiscusfaultline.activate",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_GRAVE,
            CATEGORY);

    /** Enters and leaves the build tool slot. Unbound by default; Ctrl + scroll past the hotbar does the same. */
    public static final KeyMapping TOGGLE_BUILD_SLOT = new KeyMapping(
            "key.hibiscusfaultline.toggle_build_slot",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYBOARD,
            InputConstants.UNKNOWN.getValue(),
            CATEGORY);

    private FaultlineKeys() {}

    static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(ACTIVATE);
        event.register(TOGGLE_BUILD_SLOT);
    }
}
