/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client.build;

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
 * <p>Left-click picks the first and second corner, then extends the box to a clicked block outside it, or clears it
 * when clicking at nothing. Scrolling over a face pushes or pulls that face. For Move and Clone, dragging the gizmo,
 * Move To, rotating or flipping starts placing a destination; from then on scrolling moves the destination along the
 * looked-at axis and left-click cancels it.
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

    static void leftClick() {
        BuildBox box = Selection.box();
        if (hover.kind() == Kind.GIZMO) {
            startDrag();
        } else if (Selection.pending()) {
            Selection.resetDestination();
        } else if (box != null) {
            if (hover.kind() == Kind.BLOCK) {
                Selection.extendTo(hover.block());
            } else if (hover.kind() == Kind.NONE) {
                Selection.clear();
            }
        } else if (Selection.first() != null) {
            if (hover.kind() == Kind.BLOCK) {
                Selection.complete(hover.block());
            } else if (hover.kind() == Kind.NONE) {
                Selection.clear();
            }
        } else if (hover.kind() == Kind.BLOCK) {
            Selection.setFirst(hover.block());
        }
    }

    /** Extends the selection to the looked-at block, or for Move and Clone (unless set to extend) does Move To. */
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
        boolean moveTo = Selection.box() != null && Selection.hasDestination(BuildClient.tool())
                && (Selection.pending() || !BuildClient.option(BuildOption.MIDDLE_EXTEND));
        if (moveTo) {
            moveTo(block.getBlockPos().relative(block.getDirection()));
        } else {
            Selection.extendTo(block.getBlockPos());
        }
    }

    /** {@code up} is true for the wheel turning away from the player, which pushes things away. */
    static void scroll(boolean up) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || Selection.box() == null) {
            return;
        }
        int amount = up ? 1 : -1;
        if (BuildClient.tool() == BuildTool.STACK) {
            scrollStack(player, amount);
            return;
        }
        if (Selection.pending()) {
            Vec3 look = player.getViewVector(1.0F);
            Direction direction;
            if (hover.kind() == Kind.FACE && hover.face() != null) {
                Direction.Axis axis = hover.face().getAxis();
                direction = Direction.fromAxisAndDirection(axis,
                        look.get(axis) >= 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
            } else {
                direction = Direction.getApproximateNearest(look);
            }
            Selection.setTransform(Selection.transform().moved(direction, amount));
        } else if (hover.kind() == Kind.FACE && hover.face() != null) {
            Selection.moveFace(hover.face(), amount);
        }
    }

    /**
     * Stack: the wheel sets how many copies, Shift + wheel the gap between them. The direction is the way the player
     * looks when the first copy is added, and is free again once the count is back to zero.
     */
    private static void scrollStack(LocalPlayer player, int amount) {
        BuildStack stack = Selection.stack();
        if (Minecraft.getInstance().hasShiftDown()) {
            if (stack.count() > 0) {
                Selection.setStack(new BuildStack(stack.direction(), stack.count(), stack.gap() + amount));
            }
            return;
        }
        Direction direction = stack.count() == 0 ? Direction.getApproximateNearest(player.getViewVector(1.0F)) : stack.direction();
        int count = Math.clamp(stack.count() + amount, 0, ServerConfig.buildMaxStack());
        Selection.setStack(count == 0 ? BuildStack.NONE : new BuildStack(direction, count, stack.gap()));
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
        if (BuildClient.tool() == BuildTool.STACK && Selection.stack().count() > 0) {
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
