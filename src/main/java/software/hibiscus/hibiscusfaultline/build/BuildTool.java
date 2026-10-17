/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.build;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;

/** The build tools in the tool slot. The palette stacks them upwards from the slot in this order, Move nearest. */
public enum BuildTool {
    MOVE(List.of(BuildOption.KEEP_EXISTING, BuildOption.MOVE_ENTITIES, BuildOption.MOVE_AIR)),
    CLONE(List.of(BuildOption.KEEP_EXISTING, BuildOption.COPY_AIR, BuildOption.COPY_ENTITIES, BuildOption.PARTIAL)),
    STACK(List.of(BuildOption.KEEP_EXISTING, BuildOption.COPY_AIR, BuildOption.COPY_ENTITIES, BuildOption.PARTIAL)),
    ERASE(List.of(BuildOption.MASK, BuildOption.FLUIDS)),
    SET(List.of(BuildOption.AIR_ONLY, BuildOption.REPLACE, BuildOption.HOLLOW, BuildOption.WALLS, BuildOption.PARTIAL));

    private static final BuildTool[] VALUES = values();

    private final List<BuildOption> options;

    BuildTool(List<BuildOption> toolOptions) {
        // The selection options apply to every tool, so they head each panel.
        this.options = Stream.concat(BuildOption.SHARED.stream(), toolOptions.stream()).toList();
    }

    /** Stable id, used in lang keys, sprites, the client config and permission nodes. */
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** The options shown in this tool's options panel, top to bottom. */
    public List<BuildOption> options() {
        return options;
    }

    public Component displayName() {
        return Component.translatable("hibiscusfaultline.build.tool." + id());
    }

    public BuildTool cycle(int direction) {
        return VALUES[Math.floorMod(ordinal() + direction, VALUES.length)];
    }

    public static BuildTool byIndex(int index) {
        return index >= 0 && index < VALUES.length ? VALUES[index] : MOVE;
    }

    public static BuildTool byId(String id) {
        for (BuildTool tool : VALUES) {
            if (tool.id().equals(id)) {
                return tool;
            }
        }
        return MOVE;
    }
}
