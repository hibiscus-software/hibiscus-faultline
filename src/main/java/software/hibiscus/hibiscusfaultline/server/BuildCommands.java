/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.server;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;

/**
 * Operator commands for the build tools:
 * {@code /faultline build cancel <player>}, {@code /faultline build undo <player>} and
 * {@code /faultline build limits [player]}.
 */
public final class BuildCommands {
    private BuildCommands() {}

    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("faultline")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("build")
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> cancel(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("undo")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> undo(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("limits")
                                .executes(context -> limits(context.getSource(), context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(context -> limits(context.getSource(), EntityArgument.getPlayer(context, "player")))))));
    }

    private static int cancel(CommandSourceStack source, ServerPlayer player) {
        if (BuildJobs.get(player) == null) {
            source.sendFailure(Component.translatable("hibiscusfaultline.command.cancel.none", player.getDisplayName()));
            return 0;
        }
        BuildJobs.stop(player, Component.translatable("hibiscusfaultline.build.stopped.operator"));
        source.sendSuccess(() -> Component.translatable("hibiscusfaultline.command.cancel.done", player.getDisplayName()), true);
        return 1;
    }

    private static int undo(CommandSourceStack source, ServerPlayer player) {
        if (BuildJobs.get(player) != null) {
            source.sendFailure(Component.translatable("hibiscusfaultline.build.problem.busy"));
            return 0;
        }
        if (!BuildHistory.canUndo(player)) {
            source.sendFailure(Component.translatable("hibiscusfaultline.command.undo.none", player.getDisplayName()));
            return 0;
        }
        Component result = BuildHistory.undo(player);
        source.sendSuccess(() -> Component.translatable("hibiscusfaultline.command.undo.done", player.getDisplayName(), result), true);
        return 1;
    }

    /** The limits that apply to the player right now, from the config and their permissions. */
    private static int limits(CommandSourceStack source, ServerPlayer player) throws CommandSyntaxException {
        source.sendSuccess(() -> Component.translatable("hibiscusfaultline.command.limits.header", player.getDisplayName()), false);
        line(source, "volume", BuildValidator.maxVolume(player, false), BuildValidator.maxVolume(player, true));
        line(source, "distance", ServerConfig.buildMaxDistance(false), ServerConfig.buildMaxDistance(true));
        line(source, "undo", ServerConfig.buildUndoDepth(), ServerConfig.buildUndoDepthCreative());
        source.sendSuccess(() -> Component.translatable("hibiscusfaultline.command.limits.other",
                ServerConfig.buildMaxStack(), ServerConfig.buildMaxLinkedContainers(), ServerConfig.buildLinkRange(),
                Component.translatable(BuildProtection.bypasses(player) ? "options.on" : "options.off")), false);
        return 1;
    }

    private static void line(CommandSourceStack source, String key, long survival, long creative) {
        source.sendSuccess(() -> Component.translatable("hibiscusfaultline.command.limits." + key, survival, creative), false);
    }
}
