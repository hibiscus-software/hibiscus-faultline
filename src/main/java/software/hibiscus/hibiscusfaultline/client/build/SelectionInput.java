/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client.build;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.hibiscus.hibiscusfaultline.build.BuildBox;
import software.hibiscus.hibiscusfaultline.build.BuildOption;
import software.hibiscus.hibiscusfaultline.build.BuildStack;
import software.hibiscus.hibiscusfaultline.build.BuildTool;
import software.hibiscus.hibiscusfaultline.build.BuildTransform;
import software.hibiscus.hibiscusfaultline.config.ClientConfig;
import software.hibiscus.hibiscusfaultline.config.ServerConfig;
import org.jspecify.annotations.Nullable;

/**
 * Turns the mouse into selection edits. What the cursor is over is worked out once per frame; clicks, scrolls and
 * keys then act on that.
 *
 * <p>Following Axiom: left-click sets the first corner and right-click the second, at any time; middle-click moves the
 * second corner to the looked-at block. Move and Clone move the selection with the wheel (up pushes it away from
 * you, down pulls it closer; hold X, Y or Z to keep to one axis), the gizmo, Move To, Ctrl+R and Ctrl+F. Stack grows
 * copies with the wheel along whichever axis you look along, a grid if you use more than one. Erase and Set push or
 * pull the face under the cursor with the wheel. While moving or stacking, left-click cancels and right-click
 * confirms.
 */
final class SelectionInput {
    /** Height of the gizmo's stalk above the top of the box, and the radius of its handle. */
    static final double GIZMO_STALK = 1.25;
    static final double GIZMO_RADIUS = 0.3;

    private static Hover hover = Hover.NONE;
    private static boolean dragging;
    private static double dragPlaneY;
    private static Vec3 dragStart = Vec3.ZERO;
    private static BlockPos dragStartOffset = BlockPos.ZERO;

    private SelectionInput() {}

    enum Kind {
        NONE,
        /** A block outside the selection. */
        BLOCK,
        /** A face of the selection, or of the destination while one is being placed. */
        FACE,
        /** A block inside the selection, seen from inside it. Clicks leave the selection alone. */
        INSIDE,
        GIZMO
    }

    record Hover(Kind kind, @Nullable BlockPos block, @Nullable Direction face) {
        static final Hover NONE = new Hover(Kind.NONE, null, null);
    }

    static Hover hover() {
        return hover;
    }

    static boolean dragging() {
        return dragging;
    }

    /** Recomputes what the cursor is over. Called every frame while the tool slot is active. */
    static void update(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || !BuildClient.isActive()) {
            hover = Hover.NONE;
            dragging = false;
            return;
        }
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 look = player.getViewVector(partialTick);
        Vec3 end = eye.add(look.scale(reach(player)));

        if (dragging) {
            if (minecraft.options.keyAttack.isDown()) {
                drag(eye, look);
            } else {
                dragging = false;
            }
        }

        double best = Double.MAX_VALUE;
        Hover found = Hover.NONE;
        Vec3 gizmo = gizmoCentre();
        if (gizmo != null) {
            double distance = raySphere(eye, look, gizmo, GIZMO_RADIUS);
            if (distance >= 0) {
                best = distance;
                found = new Hover(Kind.GIZMO, null, null);
            }
        }
        BuildBox faces = faceBox();
        if (faces != null) {
            AABB aabb = faces.aabb();
            Vec3 point = aabb.clip(eye, end).orElse(null);
            if (point != null && eye.distanceTo(point) < best) {
                best = eye.distanceTo(point);
                found = new Hover(Kind.FACE, null, faceAt(aabb, point));
            }
        }
        BlockHitResult block = lookedAtBlock(minecraft.level, player, eye, end);
        if (block != null && eye.distanceTo(block.getLocation()) < best) {
            BuildBox box = Selection.box();
            boolean inside = box != null && box.contains(block.getBlockPos());
            found = new Hover(inside ? Kind.INSIDE : Kind.BLOCK, block.getBlockPos(), block.getDirection());
        }
        hover = found;
    }

    /**
     * Left-click: on the gizmo, drag it; while moving or stacking, cancel that; otherwise set the first corner on the
     * looked-at block.
     */
    static void leftClick() {
        if (hover.kind() == Kind.GIZMO) {
            startDrag();
        } else if (Selection.pending()) {
            Selection.resetDestination();
        } else if (BuildClient.tool() == BuildTool.STACK && !Selection.stack().isEmpty()) {
            Selection.setStack(BuildStack.NONE);
        } else {
            BlockPos target = cornerTarget();
            if (target != null) {
                Selection.setCorner1(target);
            }
        }
    }

    /** Right-click when it doesn't confirm: set the second corner on the looked-at block. */
    static void setSecondCorner() {
        BlockPos target = cornerTarget();
        if (target != null) {
            Selection.setCorner2(target);
        }
    }

    /** The block a corner goes on: the one under the cursor, looking through the selection box if it's in the way. */
    private static @Nullable BlockPos cornerTarget() {
        return hover.kind() == Kind.BLOCK || hover.kind() == Kind.INSIDE ? hover.block() : lookedAtBlock();
    }

    /** {@return whether right-click confirms now} Only while a Move or Clone is being placed or Stack has copies. */
    static boolean rightClickConfirms() {
        return Selection.pending() || (BuildClient.tool() == BuildTool.STACK && Selection.box() != null && !Selection.stack().isEmpty());
    }

    /** {@return whether an operation could be confirmed now, by right-click or the confirm key} */
    static boolean canConfirm() {
        if (Selection.box() == null) {
            return false;
        }
        return switch (BuildClient.tool()) {
            case MOVE, CLONE, STACK -> rightClickConfirms();
            case ERASE, SET -> true;
        };
    }

    /** {@return the block under the cursor, ignoring the selection box, for right-click with nothing selected} */
    static @Nullable BlockPos lookedAtBlock() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return null;
        }
        Vec3 eye = player.getEyePosition();
        BlockHitResult hit = lookedAtBlock(minecraft.level, player, eye, eye.add(player.getViewVector(1.0F).scale(reach(player))));
        return hit != null ? hit.getBlockPos() : null;
    }

    /**
     * Middle-click: while moving, Move To the looked-at block; while stacking, stack up to it; for Move and Clone with
     * "Middle Click: Extend Select" off, start moving with Move To; otherwise move the second corner to the looked-at
     * block (the first corner, if none is set yet).
     */
    static void middleClick() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        BlockHitResult block = lookedAtBlock(minecraft.level, player, eye, eye.add(player.getViewVector(1.0F).scale(reach(player))));
        if (block == null) {
            return;
        }
        BlockPos pos = block.getBlockPos();
        boolean moveTo = Selection.box() != null && Selection.hasDestination(BuildClient.tool())
                && (Selection.pending() || !BuildClient.option(BuildOption.MIDDLE_EXTEND));
        if (moveTo) {
            moveTo(pos.relative(block.getDirection()));
        } else if (BuildClient.tool() == BuildTool.STACK && Selection.box() != null && !Selection.stack().isEmpty()) {
            stackTo(player, pos);
        } else if (Selection.isEmpty()) {
            Selection.setCorner1(pos);
        } else {
            Selection.setCorner2(pos);
        }
    }

    /** {@return whether the wheel does something for the tool now} Without a selection it goes back to the hotbar. */
    static boolean wantsScroll() {
        return Selection.box() != null;
    }

    /** {@code up} is true for the wheel turning away from the player, which pushes things away. */
    static void scroll(boolean up) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || Selection.box() == null) {
            return;
        }
        int amount = up ? 1 : -1;
        switch (BuildClient.tool()) {
            case STACK -> scrollStack(player, amount);
            case MOVE, CLONE -> Selection.setTransform(Selection.transform().moved(scrollDirection(player), amount));
            case ERASE, SET -> {
                if (hover.kind() == Kind.FACE && hover.face() != null) {
                    Selection.moveFace(hover.face(), amount);
                }
            }
        }
    }

    /**
     * {@return the way scrolling up moves things} Away from the player along the axis they look along most, or along
     * X, Y or Z while that key is held.
     */
    static Direction scrollDirection(LocalPlayer player) {
        Vec3 look = player.getViewVector(1.0F);
        Direction.Axis axis = lockedAxis();
        if (axis == null) {
            return Direction.getApproximateNearest(look);
        }
        return Direction.fromAxisAndDirection(axis, look.get(axis) >= 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
    }

    private static Direction.@Nullable Axis lockedAxis() {
        if (InputConstants.isKeyDown(InputConstants.KEY_X)) {
            return Direction.Axis.X;
        }
        if (InputConstants.isKeyDown(InputConstants.KEY_Y)) {
            return Direction.Axis.Y;
        }
        if (InputConstants.isKeyDown(InputConstants.KEY_Z)) {
            return Direction.Axis.Z;
        }
        return null;
    }

    /**
     * Stack: each wheel step adds a copy along the axis the player looks along (or the held X, Y or Z), on the far
     * side when scrolling up and the near side when scrolling down; using more than one axis makes a grid. Shift +
     * wheel sets the gap between copies.
     */
    private static void scrollStack(LocalPlayer player, int amount) {
        BuildStack stack = Selection.stack();
        if (Minecraft.getInstance().hasShiftDown()) {
            if (!stack.isEmpty()) {
                Selection.setStack(stack.withGap(stack.gap() + amount));
            }
            return;
        }
        Direction direction = scrollDirection(player);
        int step = direction.getAxisDirection() == Direction.AxisDirection.POSITIVE ? amount : -amount;
        BuildStack next = stack.withCount(direction.getAxis(), stack.count(direction.getAxis()) + step);
        if (next.copies() <= ServerConfig.buildMaxStack()) {
            Selection.setStack(next);
        }
    }

    /** Stack up to a block: enough copies along the looked-at axis for the stack to reach {@code target}. */
    private static void stackTo(LocalPlayer player, BlockPos target) {
        BuildBox box = Selection.box();
        BuildStack stack = Selection.stack();
        Direction.Axis axis = scrollDirection(player).getAxis();
        int step = box.sizeAlong(axis) + stack.gap();
        int at = target.get(axis);
        int count = 0;
        if (at > box.max().get(axis)) {
            count = Math.ceilDiv(at - box.max().get(axis), step);
        } else if (at < box.min().get(axis)) {
            count = -Math.ceilDiv(box.min().get(axis) - at, step);
        }
        BuildStack next = stack.withCount(axis, count);
        if (next.copies() <= ServerConfig.buildMaxStack()) {
            Selection.setStack(next);
        }
    }

    static void rotate() {
        if (Selection.box() != null && Selection.hasDestination(BuildClient.tool())) {
            Selection.setTransform(Selection.transform().rotated());
        }
    }

    static void flip() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && Selection.box() != null && Selection.hasDestination(BuildClient.tool())) {
            Selection.setTransform(Selection.transform().flipped(player.getDirection().getAxis()));
        }
    }

    /** Steps back once: cancels a destination, then clears the selection. {@return whether there was anything to undo} */
    static boolean back() {
        if (BuildClient.tool() == BuildTool.STACK && !Selection.stack().isEmpty()) {
            Selection.setStack(BuildStack.NONE);
            return true;
        }
        if (Selection.pending()) {
            Selection.resetDestination();
            return true;
        }
        if (!Selection.isEmpty()) {
            Selection.clear();
            return true;
        }
        return false;
    }

    /** {@return the centre of the gizmo's handle, or {@code null} when the current tool has none} */
    static @Nullable Vec3 gizmoCentre() {
        BuildBox box = Selection.pending() ? Selection.destination() : Selection.box();
        if (box == null || !Selection.hasDestination(BuildClient.tool())) {
            return null;
        }
        return new Vec3((box.min().getX() + box.max().getX() + 1) / 2.0, box.max().getY() + 1 + GIZMO_STALK,
                (box.min().getZ() + box.max().getZ() + 1) / 2.0);
    }

    /** The box whose faces react to the cursor: the destination while one is being placed, otherwise the selection. */
    static @Nullable BuildBox faceBox() {
        return Selection.pending() ? Selection.destination() : Selection.box();
    }

    /** Moves the destination so the middle of its bottom layer sits at {@code target}. */
    private static void moveTo(BlockPos target) {
        BuildBox box = Selection.box();
        BuildTransform transform = Selection.transform();
        BuildBox destination = transform.withOffset(BlockPos.ZERO).destination(box);
        BlockPos anchor = new BlockPos(destination.sizeX() / 2, 0, destination.sizeZ() / 2);
        Selection.setTransform(transform.withOffset(target.subtract(box.min()).subtract(anchor)));
    }

    private static void startDrag() {
        LocalPlayer player = Minecraft.getInstance().player;
        Vec3 gizmo = gizmoCentre();
        if (player == null || gizmo == null) {
            return;
        }
        Vec3 hit = planeHit(player.getEyePosition(), player.getViewVector(1.0F), gizmo.y);
        if (hit == null) {
            return;
        }
        dragging = true;
        dragPlaneY = gizmo.y;
        dragStart = hit;
        dragStartOffset = Selection.transform().offset();
        Selection.setTransform(Selection.transform());
    }

    /** Slides the destination across the horizontal plane the drag started in. Scrolling moves it up and down. */
    private static void drag(Vec3 eye, Vec3 look) {
        Vec3 hit = planeHit(eye, look, dragPlaneY);
        if (hit == null) {
            return;
        }
        double sensitivity = ClientConfig.BUILD_GIZMO_SENSITIVITY.get();
        int dx = (int) Math.round((hit.x - dragStart.x) * sensitivity);
        int dz = (int) Math.round((hit.z - dragStart.z) * sensitivity);
        Selection.setTransform(Selection.transform().withOffset(dragStartOffset.offset(dx, 0, dz)));
    }

    private static @Nullable Vec3 planeHit(Vec3 eye, Vec3 look, double y) {
        if (Math.abs(look.y) < 1.0E-4) {
            return null;
        }
        double t = (y - eye.y) / look.y;
        if (t < 0 || t > 512) {
            return null;
        }
        return eye.add(look.scale(t));
    }

    private static @Nullable BlockHitResult lookedAtBlock(ClientLevel level, LocalPlayer player, Vec3 eye, Vec3 end) {
        BlockHitResult hit = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    private static double reach(LocalPlayer player) {
        return ServerConfig.buildMaxDistance(player.isCreative());
    }

    /** {@return the face of {@code box} that {@code point}, on its surface, lies on} */
    private static Direction faceAt(AABB box, Vec3 point) {
        Direction best = Direction.UP;
        double bestDistance = Double.MAX_VALUE;
        for (Direction face : Direction.values()) {
            double plane = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? box.max(face.getAxis()) : box.min(face.getAxis());
            double distance = Math.abs(point.get(face.getAxis()) - plane);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = face;
            }
        }
        return best;
    }

    /** {@return how far along the ray it meets the sphere, or -1 if it misses} */
    private static double raySphere(Vec3 origin, Vec3 direction, Vec3 centre, double radius) {
        Vec3 offset = origin.subtract(centre);
        double b = offset.dot(direction);
        double c = offset.dot(offset) - radius * radius;
        double discriminant = b * b - c;
        if (discriminant < 0) {
            return -1;
        }
        double root = Math.sqrt(discriminant);
        double t = -b - root;
        return t >= 0 ? t : -b + root >= 0 ? -b + root : -1;
    }
}
