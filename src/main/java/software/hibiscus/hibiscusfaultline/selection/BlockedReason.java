/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.selection;

import java.util.Locale;
import net.minecraft.network.chat.Component;

/** Why Faultline will not run right now. */
public enum BlockedReason {
    EXCLUDED_TOOL,
    TOOL_REQUIRED,
    NO_PERMISSION,
    NO_MODES,
    COOLDOWN;

    public Component message() {
        return Component.translatable("hibiscusfaultline.blocked." + name().toLowerCase(Locale.ROOT));
    }
}
