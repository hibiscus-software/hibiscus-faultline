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
 * The local player's selection and, for Move and Clone, where it's going. Shared by every tool, so switching tools
 * keeps it. Cleared on logout and dimension change.
 */
final class Selection {
    /** The first corner while the second hasn't been picked yet. */
    private static @Nullable BlockPos first;
    private static @Nullable BuildBox box;
    private static BuildTransform transform = BuildTransform.IDENTITY;
    /** Whether the player has started placing a destination (dragged the gizmo, rotated, flipped or used Move To). */
    private static boolean pending;
    /** Stack's copies, kept apart from the Move and Clone destination. */
    private static BuildStack stack = BuildStack.NONE;
    /** Bumped on every change, so caches know when to rebuild. */
    private static int version;

    private Selection() {}

    static @Nullable BlockPos first() {
        return first;
    }

    static @Nullable BuildBox box() {
        return box;
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

    /** After a Move, the selection follows the blocks to where they went. */
    static void replaceBox(BuildBox value) {
        box = value;
        transform = BuildTransform.IDENTITY;
        pending = false;
        changed();
    }

    /** {@return whether a destination is being placed for the current tool} */
    static boolean pending() {
        return pending && box != null && hasDestination(BuildClient.tool());
    }

    static boolean hasDestination(BuildTool tool) {
        return tool == BuildTool.MOVE || tool == BuildTool.CLONE;
    }

    /** {@return the box the selection lands in, or {@code null} without a pending destination} */
    static @Nullable BuildBox destination() {
        return pending() ? transform.destination(box) : null;
    }

    static boolean isEmpty() {
        return first == null && box == null;
    }

    static void setFirst(BlockPos pos) {
        first = pos.immutable();
        box = null;
        resetDestination();
        changed();
    }

    static void complete(BlockPos second) {
        if (first != null) {
            box = BuildBox.of(first, second);
            first = null;
            resetDestination();
            changed();
        }
    }

    static void extendTo(BlockPos pos) {
        if (box != null) {
            box = box.including(pos);
            changed();
        } else if (first != null) {
            complete(pos);
        }
    }

    static void moveFace(Direction face, int amount) {
        if (box != null) {
            BuildBox moved = box.withFaceMoved(face, amount);
            if (!moved.equals(box)) {
                box = moved;
                changed();
            }
        }
    }

    static void setTransform(BuildTransform value) {
        if (box != null && (!pending || !value.equals(transform))) {
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
        if (!isEmpty() || pending) {
            first = null;
            box = null;
            transform = BuildTransform.IDENTITY;
            pending = false;
            stack = BuildStack.NONE;
            changed();
        }
    }

    /** {@return the operation as it stands, for the server to check} {@code null} without a finished selection. */
    static @Nullable BuildRequest request(BuildTool tool, int options) {
        if (box == null) {
            return null;
        }
        return new BuildRequest(tool, options, box, pending() ? transform : BuildTransform.IDENTITY,
                tool == BuildTool.SET ? BuildOperation.setBlock() : Optional.empty(), BuildOperation.mask(tool),
                tool == BuildTool.STACK ? stack : BuildStack.NONE);
    }

    private static void changed() {
        version++;
    }
}
