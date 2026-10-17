/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client.build;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import software.hibiscus.hibiscusfaultline.build.BuildBox;
import software.hibiscus.hibiscusfaultline.build.BuildRequest;
import software.hibiscus.hibiscusfaultline.build.BuildStack;
import software.hibiscus.hibiscusfaultline.build.BuildTool;
import software.hibiscus.hibiscusfaultline.build.BuildTransform;
import org.jspecify.annotations.Nullable;

/**
 * The local player's selection: two corners, and for Move and Clone where it's going, for Stack its copies. Either
 * corner can be set again at any time. Cleared when switching tools, after an operation, on logout and on dimension
 * change.
 */
final class Selection {
    private static @Nullable BlockPos corner1;
    private static @Nullable BlockPos corner2;
    private static BuildTransform transform = BuildTransform.IDENTITY;
    /** Whether a destination is being placed: the selection was scrolled, dragged, rotated, flipped or moved to. */
    private static boolean pending;
    private static BuildStack stack = BuildStack.NONE;
    /** Bumped on every change, so caches know when to rebuild. */
    private static int version;

    private Selection() {}

    /** {@return the one corner picked so far, while the other is still missing} */
    static @Nullable BlockPos first() {
        if (corner1 != null && corner2 == null) {
            return corner1;
        }
        return corner1 == null ? corner2 : null;
    }

    /** {@return the selected box, once both corners are picked} */
    static @Nullable BuildBox box() {
        return corner1 != null && corner2 != null ? BuildBox.of(corner1, corner2) : null;
    }

    static BuildTransform transform() {
        return transform;
    }

    static int version() {
        return version;
    }

    static BuildStack stack() {
        return stack;
    }

    static void setStack(BuildStack value) {
        if (!value.equals(stack)) {
            stack = value;
            changed();
        }
    }

    /** {@return whether a destination is being placed for the current tool} */
    static boolean pending() {
        return pending && box() != null && hasDestination(BuildClient.tool());
    }

    static boolean hasDestination(BuildTool tool) {
        return tool == BuildTool.MOVE || tool == BuildTool.CLONE;
    }

    /** {@return the box the selection lands in, or {@code null} without a pending destination} */
    static @Nullable BuildBox destination() {
        return pending() ? transform.destination(box()) : null;
    }

    static boolean isEmpty() {
        return corner1 == null && corner2 == null;
    }

    static void setCorner1(BlockPos pos) {
        corner1 = pos.immutable();
        reshaped();
    }

    static void setCorner2(BlockPos pos) {
        corner2 = pos.immutable();
        reshaped();
    }

    /** Pushes one face of the box out, or pulls it in when {@code amount} is negative. */
    static void moveFace(Direction face, int amount) {
        BuildBox box = box();
        if (box == null) {
            return;
        }
        BuildBox moved = box.withFaceMoved(face, amount);
        if (!moved.equals(box)) {
            corner1 = moved.min();
            corner2 = moved.max();
            reshaped();
        }
    }

    static void setTransform(BuildTransform value) {
        if (box() != null && (!pending || !value.equals(transform))) {
            transform = value;
            pending = true;
            changed();
        }
    }

    static void resetDestination() {
        if (pending || !transform.isIdentity()) {
            transform = BuildTransform.IDENTITY;
            pending = false;
            changed();
        }
    }

    static void clear() {
        if (!isEmpty() || pending || !stack.isEmpty()) {
            corner1 = null;
            corner2 = null;
            transform = BuildTransform.IDENTITY;
            pending = false;
            stack = BuildStack.NONE;
            changed();
        }
    }

    /** {@return the operation as it stands, for the server to check} {@code null} without a finished selection. */
    static @Nullable BuildRequest request(BuildTool tool, int options) {
        BuildBox box = box();
        if (box == null) {
            return null;
        }
        return new BuildRequest(tool, options, box, pending() ? transform : BuildTransform.IDENTITY,
                tool == BuildTool.SET ? BuildOperation.setBlock() : Optional.empty(), BuildOperation.mask(tool),
                tool == BuildTool.STACK ? stack : BuildStack.NONE);
    }

    /** A new shape starts afresh: the destination and the copies belonged to the old one. */
    private static void reshaped() {
        transform = BuildTransform.IDENTITY;
        pending = false;
        stack = BuildStack.NONE;
        changed();
    }

    private static void changed() {
        version++;
    }
}
