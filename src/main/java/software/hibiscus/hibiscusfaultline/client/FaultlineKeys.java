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
import net.neoforged.neoforge.client.settings.KeyModifier;
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

    /** Turns a Move or Clone destination a quarter turn clockwise. Ctrl+R by default. */
    public static final KeyMapping ROTATE = new KeyMapping(
            "key.hibiscusfaultline.build_rotate",
            KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_R,
            CATEGORY);

    /** Flips a Move or Clone destination along the axis the player faces. Ctrl+F by default. */
    public static final KeyMapping FLIP = new KeyMapping(
            "key.hibiscusfaultline.build_flip",
            KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_F,
            CATEGORY);

    /** Undoes the last build operation. Ctrl+Z by default, only in the tool slot. */
    public static final KeyMapping UNDO = new KeyMapping(
            "key.hibiscusfaultline.build_undo",
            KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_Z,
            CATEGORY);

    /** Redoes the last undone build operation. Ctrl+Y by default, only in the tool slot. */
    public static final KeyMapping REDO = new KeyMapping(
            "key.hibiscusfaultline.build_redo",
            KeyConflictContext.IN_GAME,
            KeyModifier.CONTROL,
            InputConstants.Type.KEYBOARD,
            InputConstants.KEY_Y,
            CATEGORY);

    private FaultlineKeys() {}

    static void register(RegisterKeyMappingsEvent event) {
        event.registerCategory(CATEGORY);
        event.register(ACTIVATE);
        event.register(TOGGLE_BUILD_SLOT);
        event.register(ROTATE);
        event.register(FLIP);
        event.register(UNDO);
        event.register(REDO);
    }
}
