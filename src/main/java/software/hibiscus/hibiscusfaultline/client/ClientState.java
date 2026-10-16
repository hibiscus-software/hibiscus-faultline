/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client;

import software.hibiscus.hibiscusfaultline.config.ClientConfig;
import software.hibiscus.hibiscusfaultline.mode.Mode;
import software.hibiscus.hibiscusfaultline.mode.Modes;
import software.hibiscus.hibiscusfaultline.selection.BlockedReason;
import org.jspecify.annotations.Nullable;

/** What the local player is doing with Faultline right now. */
final class ClientState {
    static boolean held;
    /** The key state the server was last told about. */
    static boolean sentHeld;
    static Mode mode = Modes.SHAPELESS;
    static int cooldownTicks;
    static @Nullable BlockedReason blocked;

    private ClientState() {}

    static void reset() {
        held = false;
        sentHeld = false;
        cooldownTicks = 0;
        blocked = null;
        mode = ClientConfig.REMEMBER_LAST_MODE.get() ? Modes.byId(ClientConfig.LAST_MODE.get()) : Modes.SHAPELESS;
    }

    /** {@return why Faultline is blocked, counting the cooldown, or {@code null}} */
    static @Nullable BlockedReason effectiveBlock() {
        if (blocked != null) {
            return blocked;
        }
        return cooldownTicks > 0 ? BlockedReason.COOLDOWN : null;
    }
}
