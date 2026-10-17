/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.build;

import java.util.EnumMap;
import java.util.Map;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;

/**
 * Permission nodes for the build tools, under {@code faultline.build}. A permissions mod can override any of them; with
 * none installed NeoForge uses the defaults given here.
 */
public final class BuildPermissions {
    private static final String NAMESPACE = "faultline";

    public static final PermissionNode<Boolean> USE = flag("build.use");
    public static final PermissionNode<Boolean> BYPASS_PROTECTION = new PermissionNode<>(NAMESPACE, "build.bypass_protection",
            PermissionTypes.BOOLEAN,
            (player, uuid, context) -> player != null && player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER));
    /** A per-player volume limit. 0, the default, means "use the config". */
    public static final PermissionNode<Integer> MAX_VOLUME = new PermissionNode<>(NAMESPACE, "build.max_volume",
            PermissionTypes.INTEGER, (player, uuid, context) -> 0);

    private static final Map<BuildTool, PermissionNode<Boolean>> TOOLS = new EnumMap<>(BuildTool.class);

    static {
        for (BuildTool tool : BuildTool.values()) {
            TOOLS.put(tool, flag("build." + tool.id()));
        }
    }

    private BuildPermissions() {}

    public static void register(PermissionGatherEvent.Nodes event) {
        event.addNodes(USE, BYPASS_PROTECTION, MAX_VOLUME);
        event.addNodes(TOOLS.values().toArray(PermissionNode[]::new));
    }

    /**
     * {@return whether the player may use the tool slot at all} Checks the config switch as well as the node, but not
     * the game mode, which can change at any time: operations check for spectators themselves.
     */
    public static boolean canUse(ServerPlayer player) {
        return ServerConfig.buildToolsEnabled() && PermissionAPI.getPermission(player, USE);
    }

    public static boolean canUse(ServerPlayer player, BuildTool tool) {
        return canUse(player) && PermissionAPI.getPermission(player, TOOLS.get(tool));
    }

    /** {@return a bit per {@link BuildTool} ordinal the player may use, or 0 if the tool slot is off for them} */
    public static int toolMask(ServerPlayer player) {
        if (!canUse(player)) {
            return 0;
        }
        int mask = 0;
        for (BuildTool tool : BuildTool.values()) {
            if (PermissionAPI.getPermission(player, TOOLS.get(tool))) {
                mask |= 1 << tool.ordinal();
            }
        }
        return mask;
    }

    private static PermissionNode<Boolean> flag(String name) {
        return new PermissionNode<>(NAMESPACE, name, PermissionTypes.BOOLEAN, (player, uuid, context) -> true);
    }
}
