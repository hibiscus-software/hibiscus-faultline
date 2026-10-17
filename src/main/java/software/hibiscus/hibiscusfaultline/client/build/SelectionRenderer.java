/*
 * Copyright (C), 2026 Hibiscus Software. Some rights reserved. This work is
 * licensed under the terms of the MIT license which can be found in the
 * root directory of this project.
 */

package software.hibiscus.hibiscusfaultline.client.build;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import software.hibiscus.hibiscusfaultline.build.BuildBox;
import software.hibiscus.hibiscusfaultline.build.BuildTool;
import org.jspecify.annotations.Nullable;

/** Draws the selection, the destination, the gizmo and the ghost preview in the world. */
final class SelectionRenderer {
    private static final int FILL_ALPHA = 40;
    private static final int FACE_ALPHA = 90;
    private static final int LINE_ALPHA = 230;
    private static final int GHOST_ALPHA = 150;
    private static final int SKIPPED = 0xFFFF4040;
    private static final int SPHERE_SEGMENTS = 10;
    /** Pushes fills slightly outwards so they don't flicker against block faces. */
    private static final float INFLATE = 0.004F;

    /** Model parts per state for the current ghost list, so models aren't looked up every frame. */
    private static Map<BlockState, List<BlockStateModelPart>> partsCache = new IdentityHashMap<>();
    private static List<PreviewSync.Ghost> partsCacheFor = List.of();

    private SelectionRenderer() {}

    static void submit(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        SelectionInput.update(minecraft.getDeltaTracker().getGameTimeDeltaPartialTick(false));
        if (!BuildClient.isActive()) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        Draw draw = new Draw(event.getSubmitNodeCollector(), event.getPoseStack(), camera,
                minecraft.gameRenderer.useImprovedTransparency() ? RenderTypes.linesTranslucentNoDepthWrite() : RenderTypes.linesTranslucent(),
                minecraft.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth);
        BuildTool tool = BuildClient.tool();
        int colour = FaultlineTheme.tool(tool);
        SelectionInput.Hover hover = SelectionInput.hover();
        BuildBox box = Selection.box();

        if (box == null) {
            BlockPos first = Selection.first();
            if (first != null) {
                BuildBox growing = hover.kind() == SelectionInput.Kind.BLOCK ? BuildBox.of(first, hover.block()) : BuildBox.of(first, first);
                draw.box(growing, ARGB.color(FILL_ALPHA, colour), ARGB.color(LINE_ALPHA, colour), null, 0);
            } else if (hover.kind() == SelectionInput.Kind.BLOCK) {
                draw.faceOutline(hover.block(), hover.face(), ARGB.color(LINE_ALPHA, 0xFFFFFF));
            }
            return;
        }

        BuildBox destination = Selection.destination();
        if (tool == BuildTool.STACK && Selection.stack().count() > 0) {
            destination = Selection.stack().span(box);
        }
        Direction face = hover.kind() == SelectionInput.Kind.FACE ? hover.face() : null;
        if (destination != null) {
            // The source fades back while its copy is being placed.
            draw.box(box, ARGB.color(FILL_ALPHA / 3, colour), ARGB.color(LINE_ALPHA / 2, 0xFFFFFF), null, 0);
            drawGhosts(draw, minecraft);
            draw.box(destination, ARGB.color(FILL_ALPHA, colour), ARGB.color(LINE_ALPHA, colour), face, ARGB.color(FACE_ALPHA, colour));
        } else {
            draw.box(box, ARGB.color(FILL_ALPHA, colour), ARGB.color(LINE_ALPHA, 0xFFFFFF), face, ARGB.color(FACE_ALPHA, colour));
        }
        drawSkipped(draw);

        Vec3 gizmo = SelectionInput.gizmoCentre();
        if (gizmo != null) {
            boolean lit = hover.kind() == SelectionInput.Kind.GIZMO || SelectionInput.dragging();
            int handle = lit ? ARGB.color(255, ARGB.scaleRGB(colour, 1.3F)) : ARGB.color(200, colour);
            draw.line(gizmo.subtract(0, SelectionInput.GIZMO_STALK, 0), gizmo.subtract(0, SelectionInput.GIZMO_RADIUS, 0), ARGB.color(LINE_ALPHA, colour));
            draw.sphere(gizmo, SelectionInput.GIZMO_RADIUS, handle);
        }
    }

    private static void drawSkipped(Draw draw) {
        LongSet skipped = PreviewSync.skipped();
        if (skipped.isEmpty()) {
            return;
        }
        int fill = ARGB.color(FACE_ALPHA, SKIPPED);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        draw.quads((pose, buffer, origin) -> {
            for (long packed : skipped) {
                pos.set(packed);
                float x = (float) (pos.getX() - origin.x);
                float y = (float) (pos.getY() - origin.y);
                float z = (float) (pos.getZ() - origin.z);
                Draw.cube(pose, buffer, x, y, z, x + 1, y + 1, z + 1, fill);
            }
        });
    }

    private static void drawGhosts(Draw draw, Minecraft minecraft) {
        List<PreviewSync.Ghost> ghosts = PreviewSync.ghosts();
        if (ghosts.isEmpty()) {
            return;
        }
        if (ghosts != partsCacheFor) {
            partsCacheFor = ghosts;
            partsCache = new IdentityHashMap<>();
        }
        LongSet skipped = PreviewSync.skipped();
        RandomSource random = RandomSource.create();
        QuadInstance plain = new QuadInstance();
        plain.setColor(ARGB.color(GHOST_ALPHA, 0xFFFFFF));
        QuadInstance red = new QuadInstance();
        red.setColor(ARGB.color(GHOST_ALPHA, SKIPPED));
        Vec3 camera = draw.camera;
        draw.collector.submitCustomGeometry(draw.poseStack, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS), (pose, buffer) -> {
            PoseStack work = new PoseStack();
            work.last().set(pose);
            for (PreviewSync.Ghost ghost : ghosts) {
                List<BlockStateModelPart> parts = partsCache.computeIfAbsent(ghost.state(), state -> {
                    List<BlockStateModelPart> collected = new ArrayList<>();
                    random.setSeed(42L);
                    minecraft.getModelManager().getBlockStateModelSet().get(state).collectParts(random, collected);
                    return collected;
                });
                QuadInstance instance = skipped.contains(ghost.pos().asLong()) ? red : plain;
                work.pushPose();
                work.translate(ghost.pos().getX() - camera.x, ghost.pos().getY() - camera.y, ghost.pos().getZ() - camera.z);
                for (BlockStateModelPart part : parts) {
                    putQuads(buffer, work.last(), part.getQuads(null), instance);
                    for (Direction side : Direction.values()) {
                        putQuads(buffer, work.last(), part.getQuads(side), instance);
                    }
                }
                work.popPose();
            }
        });
    }

    private static void putQuads(VertexConsumer buffer, PoseStack.Pose pose, List<BakedQuad> quads, QuadInstance instance) {
        for (BakedQuad quad : quads) {
            buffer.putBakedQuad(pose, quad, instance);
        }
    }

    /** Small helpers for drawing translucent boxes and lines relative to the camera. */
    private static final class Draw {
        final SubmitNodeCollector collector;
        final PoseStack poseStack;
        final Vec3 camera;
        final RenderType lineType;
        final float lineWidth;

        Draw(SubmitNodeCollector collector, PoseStack poseStack, Vec3 camera, RenderType lineType, float lineWidth) {
            this.collector = collector;
            this.poseStack = poseStack;
            this.camera = camera;
            this.lineType = lineType;
            this.lineWidth = lineWidth;
        }

        interface QuadWriter {
            void write(PoseStack.Pose pose, VertexConsumer buffer, Vec3 origin);
        }

        void quads(QuadWriter writer) {
            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> writer.write(pose, buffer, camera));
        }

        /** A translucent box with bright edges, and optionally one face drawn stronger. */
        void box(BuildBox box, int fill, int line, @Nullable Direction face, int faceFill) {
            float x0 = (float) (box.min().getX() - camera.x);
            float y0 = (float) (box.min().getY() - camera.y);
            float z0 = (float) (box.min().getZ() - camera.z);
            float x1 = x0 + box.sizeX();
            float y1 = y0 + box.sizeY();
            float z1 = z0 + box.sizeZ();
            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
                cube(pose, buffer, x0, y0, z0, x1, y1, z1, fill);
                if (face != null) {
                    face(pose, buffer, x0 - INFLATE, y0 - INFLATE, z0 - INFLATE, x1 + INFLATE, y1 + INFLATE, z1 + INFLATE, face, faceFill);
                }
            });
            collector.submitCustomGeometry(poseStack, lineType, (pose, buffer) -> {
                float[][] corners = {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}, {x0, y1, z0}, {x1, y1, z0}, {x1, y1, z1}, {x0, y1, z1}};
                int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
                for (int[] edge : edges) {
                    float[] a = corners[edge[0]];
                    float[] b = corners[edge[1]];
                    lineVertices(pose, buffer, a[0], a[1], a[2], b[0], b[1], b[2], line);
                }
            });
        }

        /** The outline of one face of one block, for the cursor before the first corner is picked. */
        void faceOutline(BlockPos pos, Direction face, int line) {
            float x0 = (float) (pos.getX() - camera.x);
            float y0 = (float) (pos.getY() - camera.y);
            float z0 = (float) (pos.getZ() - camera.z);
            float[][] corners = faceCorners(x0 - INFLATE, y0 - INFLATE, z0 - INFLATE, x0 + 1 + INFLATE, y0 + 1 + INFLATE, z0 + 1 + INFLATE, face);
            collector.submitCustomGeometry(poseStack, lineType, (pose, buffer) -> {
                for (int i = 0; i < 4; i++) {
                    float[] a = corners[i];
                    float[] b = corners[(i + 1) % 4];
                    lineVertices(pose, buffer, a[0], a[1], a[2], b[0], b[1], b[2], line);
                }
            });
        }

        void line(Vec3 from, Vec3 to, int colour) {
            collector.submitCustomGeometry(poseStack, lineType, (pose, buffer) -> lineVertices(pose, buffer,
                    (float) (from.x - camera.x), (float) (from.y - camera.y), (float) (from.z - camera.z),
                    (float) (to.x - camera.x), (float) (to.y - camera.y), (float) (to.z - camera.z), colour));
        }

        void sphere(Vec3 centre, double radius, int colour) {
            float cx = (float) (centre.x - camera.x);
            float cy = (float) (centre.y - camera.y);
            float cz = (float) (centre.z - camera.z);
            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> {
                for (int lat = 0; lat < SPHERE_SEGMENTS; lat++) {
                    double a0 = Math.PI * lat / SPHERE_SEGMENTS - Math.PI / 2;
                    double a1 = Math.PI * (lat + 1) / SPHERE_SEGMENTS - Math.PI / 2;
                    for (int lon = 0; lon < SPHERE_SEGMENTS * 2; lon++) {
                        double b0 = Math.PI * lon / SPHERE_SEGMENTS;
                        double b1 = Math.PI * (lon + 1) / SPHERE_SEGMENTS;
                        spherePoint(pose, buffer, cx, cy, cz, radius, a0, b0, colour);
                        spherePoint(pose, buffer, cx, cy, cz, radius, a1, b0, colour);
                        spherePoint(pose, buffer, cx, cy, cz, radius, a1, b1, colour);
                        spherePoint(pose, buffer, cx, cy, cz, radius, a0, b1, colour);
                    }
                }
            });
        }

        private static void spherePoint(PoseStack.Pose pose, VertexConsumer buffer, float cx, float cy, float cz, double radius,
                                        double latitude, double longitude, int colour) {
            float x = (float) (cx + radius * Math.cos(latitude) * Math.cos(longitude));
            float y = (float) (cy + radius * Math.sin(latitude));
            float z = (float) (cz + radius * Math.cos(latitude) * Math.sin(longitude));
            buffer.addVertex(pose, x, y, z).setColor(colour);
        }

        private void lineVertices(PoseStack.Pose pose, VertexConsumer buffer, float ax, float ay, float az, float bx, float by, float bz, int colour) {
            float nx = bx - ax;
            float ny = by - ay;
            float nz = bz - az;
            buffer.addVertex(pose, ax, ay, az).setColor(colour).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
            buffer.addVertex(pose, bx, by, bz).setColor(colour).setNormal(pose, nx, ny, nz).setLineWidth(lineWidth);
        }

        static void cube(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1, int colour) {
            for (Direction side : Direction.values()) {
                face(pose, buffer, x0 - INFLATE, y0 - INFLATE, z0 - INFLATE, x1 + INFLATE, y1 + INFLATE, z1 + INFLATE, side, colour);
            }
        }

        static void face(PoseStack.Pose pose, VertexConsumer buffer, float x0, float y0, float z0, float x1, float y1, float z1,
                         Direction side, int colour) {
            for (float[] corner : faceCorners(x0, y0, z0, x1, y1, z1, side)) {
                buffer.addVertex(pose, corner[0], corner[1], corner[2]).setColor(colour);
            }
        }

        static float[][] faceCorners(float x0, float y0, float z0, float x1, float y1, float z1, Direction side) {
            return switch (side) {
                case DOWN -> new float[][] {{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
                case UP -> new float[][] {{x0, y1, z0}, {x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}};
                case NORTH -> new float[][] {{x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}, {x1, y0, z0}};
                case SOUTH -> new float[][] {{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
                case WEST -> new float[][] {{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
                case EAST -> new float[][] {{x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}, {x1, y0, z1}};
            };
        }
    }
}
