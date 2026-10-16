/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import software.hibiscus.hibiscusfaultline.config.ClientConfig;

/** Draws the merged outline and a faint fill around every block the current selection would mine. */
final class OutlineRenderer {
    private static final int LINE_ALPHA = 230;
    private static final int FILL_ALPHA = 28;

    private OutlineRenderer() {}

    static void submit(SubmitCustomGeometryEvent event) {
        OutlineMesh mesh = Preview.mesh();
        if (!ClientState.held || mesh.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        int colour = ClientConfig.outlineColor();
        int lineColour = ARGB.color(LINE_ALPHA, colour);
        int fillColour = ARGB.color(FILL_ALPHA, colour);
        float lineWidth = minecraft.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth;

        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        BlockPos anchor = mesh.anchor();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(anchor.getX() - camera.x, anchor.getY() - camera.y, anchor.getZ() - camera.z);

        SubmitNodeCollector collector = event.getSubmitNodeCollector();
        float[] quads = mesh.quads();
        collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
            for (int i = 0; i < quads.length; i += 3) {
                buffer.addVertex(pose, quads[i], quads[i + 1], quads[i + 2]).setColor(fillColour);
            }
        });

        float[] lines = mesh.lines();
        collector.submitCustomGeometry(poseStack,
                minecraft.gameRenderer.useImprovedTransparency() ? RenderTypes.linesTranslucentNoDepthWrite() : RenderTypes.linesTranslucent(),
                (pose, buffer) -> {
                    for (int i = 0; i < lines.length; i += 6) {
                        float nx = lines[i + 3] - lines[i];
                        float ny = lines[i + 4] - lines[i + 1];
                        float nz = lines[i + 5] - lines[i + 2];
                        buffer.addVertex(pose, lines[i], lines[i + 1], lines[i + 2])
                                .setColor(lineColour).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
                        buffer.addVertex(pose, lines[i + 3], lines[i + 4], lines[i + 5])
                                .setColor(lineColour).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
                    }
                });
        poseStack.popPose();
    }
}
