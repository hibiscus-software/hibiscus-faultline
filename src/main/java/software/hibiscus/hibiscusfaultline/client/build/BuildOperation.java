/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client.build;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import software.hibiscus.hibiscusfaultline.build.BuildOption;
import software.hibiscus.hibiscusfaultline.build.BuildRequest;
import software.hibiscus.hibiscusfaultline.build.BuildTool;
import software.hibiscus.hibiscusfaultline.config.ClientConfig;
import software.hibiscus.hibiscusfaultline.network.BuildConfirmPayload;
import software.hibiscus.hibiscusfaultline.network.BuildControlPayload;
import software.hibiscus.hibiscusfaultline.network.BuildLinkPayload;
import software.hibiscus.hibiscusfaultline.network.BuildProgressPayload;
import org.jspecify.annotations.Nullable;

/**
 * The client's side of running operations: confirming, pausing and stopping, the progress the server reports, Set's
 * block, the masks, and the linked containers.
 */
final class BuildOperation {
    private static @Nullable BuildProgressPayload progress;
    /** Set when an operation is confirmed; the selection clears once it finishes. */
    private static boolean confirmed;
    private static List<BlockPos> linked = List.of();
    private static final Map<BuildTool, Holder<Block>> MASKS = new EnumMap<>(BuildTool.class);

    private BuildOperation() {}

    static @Nullable BuildProgressPayload progress() {
        return progress;
    }

    /** {@return whether an operation is under way, running or paused} */
    static boolean running() {
        return progress != null && progress.state() != BuildProgressPayload.FINISHED;
    }

    static boolean paused() {
        return progress != null && progress.state() == BuildProgressPayload.PAUSED;
    }

    static void receive(BuildProgressPayload payload) {
        progress = payload.state() == BuildProgressPayload.FINISHED ? null : payload;
        if (progress == null && confirmed) {
            confirmed = false;
            Selection.clear();
        }
    }

    static List<BlockPos> linked() {
        return linked;
    }

    static void receiveLinked(List<BlockPos> containers) {
        linked = List.copyOf(containers);
    }

    static void unlink(BlockPos pos) {
        ClientPacketDistributor.sendToServer(new BuildLinkPayload(pos, true));
    }

    /** Left-click during an operation pauses it; a second one stops it. */
    static void leftClick() {
        send(paused() ? BuildControlPayload.Action.STOP : BuildControlPayload.Action.PAUSE);
    }

    /**
     * Right-click: resumes a paused operation; with Shift, links or unlinks the looked-at container; picks the second
     * corner; otherwise confirms the operation as it stands.
     */
    static void rightClick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (running()) {
            if (paused()) {
                send(BuildControlPayload.Action.RESUME);
            }
            return;
        }
        if (minecraft.hasShiftDown()) {
            if (minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                ClientPacketDistributor.sendToServer(new BuildLinkPayload(hit.getBlockPos(), false));
            }
            return;
        }
        if (SelectionInput.rightClick() || !SelectionInput.canConfirm()) {
            return;
        }
        BuildTool tool = BuildClient.tool();
        BuildRequest request = Selection.request(tool, BuildClient.optionMask(tool));
        if (request != null) {
            confirmed = true;
            ClientPacketDistributor.sendToServer(new BuildConfirmPayload(request));
        }
    }

    static void undo() {
        send(BuildControlPayload.Action.UNDO);
    }

    static void redo() {
        send(BuildControlPayload.Action.REDO);
    }

    /** Escape during an operation stops it. {@return whether there was one} */
    static boolean stop() {
        if (running()) {
            send(BuildControlPayload.Action.STOP);
            return true;
        }
        return false;
    }

    /** Leaving the tool slot pauses whatever is running. */
    static void onLeaveSlot() {
        if (running() && !paused()) {
            send(BuildControlPayload.Action.PAUSE);
        }
    }

    /** {@return the block Set fills with: the one picked in the block picker, or else the block in the offhand} */
    static Optional<Holder<Block>> setBlock() {
        String picked = ClientConfig.BUILD_SET_BLOCK.get();
        if (!picked.isEmpty()) {
            Identifier id = Identifier.tryParse(picked);
            if (id != null) {
                Optional<Holder.Reference<Block>> block = BuiltInRegistries.BLOCK.get(id);
                if (block.isPresent()) {
                    return Optional.of(block.get());
                }
            }
        }
        return offhandBlock();
    }

    static void pickSetBlock(@Nullable Block block) {
        ClientConfig.BUILD_SET_BLOCK.set(block == null ? "" : BuiltInRegistries.BLOCK.getKey(block).toString());
        ClientConfig.BUILD_SET_BLOCK.save();
    }

    static Optional<Holder<Block>> mask(BuildTool tool) {
        return Optional.ofNullable(MASKS.get(tool));
    }

    /**
     * Captures the mask for Erase's Mask or Set's Replace as it's turned on: the block in the offhand, or else the
     * looked-at block.
     */
    static void captureMask(BuildTool tool, BuildOption option) {
        if (option != BuildOption.MASK && option != BuildOption.REPLACE) {
            return;
        }
        Optional<Holder<Block>> block = offhandBlock();
        Minecraft minecraft = Minecraft.getInstance();
        if (block.isEmpty() && minecraft.level != null && minecraft.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK) {
            block = Optional.of(minecraft.level.getBlockState(hit.getBlockPos()).getBlock().builtInRegistryHolder());
        }
        block.ifPresentOrElse(holder -> MASKS.put(tool, holder), () -> MASKS.remove(tool));
    }

    static void reset() {
        progress = null;
        confirmed = false;
        linked = List.of();
        MASKS.clear();
    }

    private static Optional<Holder<Block>> offhandBlock() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return Optional.empty();
        }
        ItemStack offhand = player.getOffhandItem();
        return offhand.getItem() instanceof BlockItem item ? Optional.of(item.getBlock().builtInRegistryHolder()) : Optional.empty();
    }

    private static void send(BuildControlPayload.Action action) {
        ClientPacketDistributor.sendToServer(new BuildControlPayload(action));
    }
}
