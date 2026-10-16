/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;
import software.hibiscus.hibiscusfaultline.config.Settings;
import software.hibiscus.hibiscusfaultline.mode.Mode;
import software.hibiscus.hibiscusfaultline.mode.Modes;
import software.hibiscus.hibiscusfaultline.network.CooldownPayload;
import software.hibiscus.hibiscusfaultline.selection.Selector;
import org.jspecify.annotations.Nullable;

/**
 * Breaks the rest of the vein after the player breaks the first block.
 *
 * <p>When the player breaks a block with the key held, the selection is worked out straight away
 * (while the origin block still exists to match against) and queued. At the end of the server tick
 * the queue is run: if the origin really was broken, every other block is broken through the
 * player's own game mode, so break events, protection, tool damage and enchanted drops all behave
 * as if the player had broken each block by hand.
 */
public final class VeinMineHandler {
    private static final Map<UUID, PlayerState> STATES = new HashMap<>();
    private static final Map<UUID, Job> PENDING = new HashMap<>();
    /** The job currently breaking blocks, so our own break events are ignored and drops can be moved. */
    private static @Nullable Job active;

    private VeinMineHandler() {}

    public static void register(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, VeinMineHandler::onBreak);
        bus.addListener(VeinMineHandler::onDrops);
        bus.addListener(VeinMineHandler::onServerTick);
        bus.addListener(VeinMineHandler::onLoggedOut);
        bus.addListener(VeinMineHandler::onChangedDimension);
        bus.addListener(VeinMineHandler::onDeath);
        bus.addListener(VeinMineHandler::onServerStopped);
    }

    public static void setKeyHeld(Player player, boolean held) {
        state(player).held = held;
    }

    public static void setMode(Player player, int index) {
        if (index >= 0 && index < Modes.ALL.size()) {
            state(player).mode = Modes.byIndex(index);
        }
    }

    private static PlayerState state(Player player) {
        return STATES.computeIfAbsent(player.getUUID(), id -> new PlayerState());
    }

    private static void onBreak(BreakBlockEvent event) {
        if (active != null || !(event.getPlayer() instanceof ServerPlayer player) || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PlayerState state = STATES.get(player.getUUID());
        if (state == null || !state.held || PENDING.containsKey(player.getUUID()) || player.level() != level) {
            return;
        }
        Settings settings = ServerConfig.settings();
        if (Selector.blockedReason(player, settings) != null) {
            return;
        }
        int now = level.getServer().getTickCount();
        if (now < state.cooldownUntil) {
            PacketDistributor.sendToPlayer(player, new CooldownPayload(state.cooldownUntil - now));
            return;
        }
        Mode mode = Modes.usable(state.mode, settings);
        if (mode == null) {
            return;
        }

        BlockPos origin = event.getPos().immutable();
        Direction face = Selector.lookedAtFace(player, origin);
        List<BlockPos> selection = Selector.select(level, player, origin, face, mode, settings);
        if (selection.size() <= 1) {
            return;
        }
        List<BlockPos> rest = selection.subList(1, selection.size());
        List<BlockState> expected = new ArrayList<>(rest.size());
        for (BlockPos pos : rest) {
            expected.add(level.getBlockState(pos));
        }
        PENDING.put(player.getUUID(), new Job(player, level, origin, event.getState(), player.getMainHandItem(), List.copyOf(rest), expected, settings));
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        if (PENDING.isEmpty()) {
            return;
        }
        List<Job> jobs = new ArrayList<>(PENDING.values());
        PENDING.clear();
        for (Job job : jobs) {
            run(job);
        }
    }

    private static void run(Job job) {
        ServerPlayer player = job.player;
        ServerLevel level = job.level;
        if (player.isRemoved() || !player.isAlive() || player.level() != level) {
            return;
        }
        // Another listener may have cancelled the origin break after us, or the break may have failed.
        if (level.getBlockState(job.origin) == job.originState) {
            return;
        }

        Settings settings = job.settings;
        active = job;
        try {
            for (int i = 0; i < job.targets.size(); i++) {
                ItemStack held = player.getMainHandItem();
                if (!job.tool.isEmpty() && (held != job.tool || held.isEmpty())) {
                    break; // the tool broke (possibly on the origin block) or is no longer in hand
                }
                if (settings.stopBeforeToolBreaks() && held.isDamageableItem() && held.getMaxDamage() - held.getDamageValue() <= 1) {
                    break;
                }
                BlockPos pos = job.targets.get(i);
                if (level.getBlockState(pos) != job.expected.get(i)) {
                    continue; // changed since it was selected
                }
                if (player.gameMode.destroyBlock(pos) && !player.isCreative() && settings.hungerPerBlock() > 0) {
                    player.causeFoodExhaustion(settings.hungerPerBlock());
                }
            }
        } finally {
            active = null;
        }

        if (job.experience > 0) {
            ExperienceOrb.award(level, Vec3.atCenterOf(job.origin), job.experience);
        }
        if (settings.cooldownTicks() > 0) {
            state(player).cooldownUntil = level.getServer().getTickCount() + settings.cooldownTicks();
            PacketDistributor.sendToPlayer(player, new CooldownPayload(settings.cooldownTicks()));
        }
    }

    /** Moves drops from the vein (and the origin itself) to the origin, or into the player's inventory. */
    private static void onDrops(BlockDropsEvent event) {
        Entity breaker = event.getBreaker();
        if (breaker == null) {
            return;
        }
        Job job = active;
        boolean extra = job != null && job.player == breaker;
        if (!extra) {
            job = PENDING.get(breaker.getUUID());
            if (job == null || !job.origin.equals(event.getPos())) {
                return;
            }
        }
        if (!job.settings.gatherDrops()) {
            return;
        }

        Vec3 at = Vec3.atCenterOf(job.origin);
        Iterator<ItemEntity> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemEntity drop = drops.next();
            if (job.settings.dropsToInventory()) {
                ItemStack stack = drop.getItem();
                job.player.getInventory().add(stack);
                if (stack.isEmpty()) {
                    drops.remove();
                    continue;
                }
            }
            drop.setPos(at.x, at.y, at.z);
        }
        if (extra) {
            job.experience += event.getDroppedExperience();
            event.setDroppedExperience(0);
        }
    }

    private static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
        PENDING.remove(event.getEntity().getUUID());
    }

    private static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        release(event.getEntity());
    }

    private static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            release(player);
        }
    }

    private static void release(Player player) {
        PlayerState state = STATES.get(player.getUUID());
        if (state != null) {
            state.held = false;
        }
        PENDING.remove(player.getUUID());
    }

    private static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
        PENDING.clear();
        active = null;
    }

    private static final class PlayerState {
        boolean held;
        Mode mode = Modes.SHAPELESS;
        int cooldownUntil;
    }

    private static final class Job {
        final ServerPlayer player;
        final ServerLevel level;
        final BlockPos origin;
        final BlockState originState;
        /** The stack in the main hand when the origin was broken; mining stops if it breaks or leaves the hand. */
        final ItemStack tool;
        final List<BlockPos> targets;
        final List<BlockState> expected;
        final Settings settings;
        int experience;

        Job(ServerPlayer player, ServerLevel level, BlockPos origin, BlockState originState, ItemStack tool, List<BlockPos> targets, List<BlockState> expected, Settings settings) {
            this.player = player;
            this.level = level;
            this.origin = origin;
            this.originState = originState;
            this.tool = tool;
            this.targets = targets;
            this.expected = expected;
            this.settings = settings;
        }
    }
}
