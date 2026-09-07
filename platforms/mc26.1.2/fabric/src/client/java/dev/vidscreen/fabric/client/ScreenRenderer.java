package dev.vidscreen.fabric.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4fc;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import dev.vidscreen.client.VisibleScreenSelector;
import dev.vidscreen.domain.BlockPoint;
import dev.vidscreen.domain.PlaybackStatus;
import dev.vidscreen.domain.ScreenGeometry;
import dev.vidscreen.domain.ScreenState;

final class ScreenRenderer {
    private static final int FULL_BRIGHT = 0x00F000F0;

    private static ClientScreenStore store;
    private static ScreenTextureManager textures;
    private static List<RenderState> renderStates = List.of();
    private static boolean closed;

    private ScreenRenderer() {
    }

    static void initialize(ClientScreenStore screenStore, ScreenTextureManager textureManager) {
        store = screenStore;
        textures = textureManager;
        LevelRenderEvents.END_EXTRACTION.register(ScreenRenderer::extract);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(ScreenRenderer::submitScreens);
    }

    private static void extract(LevelExtractionContext context) {
        Minecraft client = Minecraft.getInstance();
        if (closed || client.level == null || client.player == null) {
            renderStates = List.of();
            return;
        }
        String dimension = client.level.dimension().identifier().toString();
        Collection<ScreenState> screens = store.snapshot();
        List<RenderState> extracted = new ArrayList<>(screens.size());
        for (ScreenState screen : screens) {
            if (!screen.definition().dimension().value().equals(dimension)
                    || !VisibleScreenSelector.isWithinViewDistance(
                            screen, client.player.getX(), client.player.getY(), client.player.getZ())
                    || !isAnchorChunkLoaded(client, screen)) {
                continue;
            }
            float[] color = color(screen.playback().status());
            UUID screenId = screen.definition().id();
            extracted.add(new RenderState(
                    screen.definition().geometry(),
                    textures.texture(screenId),
                    color[0], color[1], color[2], color[3]));
        }
        renderStates = List.copyOf(extracted);
    }

    private static boolean isAnchorChunkLoaded(Minecraft client, ScreenState screen) {
        return client.level != null
                && client.level.hasChunk(
                        VisibleScreenSelector.anchorChunkX(screen),
                        VisibleScreenSelector.anchorChunkZ(screen));
    }

    private static float[] color(PlaybackStatus status) {
        return switch (status) {
            case PLAYING -> new float[] {0.05f, 0.85f, 0.95f, 1};
            case PAUSED -> new float[] {0.95f, 0.75f, 0.10f, 1};
            case FAILED -> new float[] {0.95f, 0.10f, 0.15f, 1};
            case BUFFERING -> new float[] {0.65f, 0.25f, 0.95f, 1};
            case STOPPED -> new float[] {0.20f, 0.25f, 0.30f, 1};
        };
    }

    private static void submitScreens(LevelRenderContext context) {
        if (closed || renderStates.isEmpty()) {
            return;
        }
        PoseStack poses = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        for (RenderState state : renderStates) {
            if (state.texture() == null) {
                context.submitNodeCollector().submitCustomGeometry(
                        poses,
                        RenderTypes.debugQuads(),
                        (pose, vertices) -> emitColored(pose.pose(), vertices, state));
            } else {
                context.submitNodeCollector().submitCustomGeometry(
                        poses,
                        RenderTypes.entityTranslucentEmissive(state.texture()),
                        (pose, vertices) -> emitTextured(pose.pose(), vertices, state.geometry()));
            }
        }
        poses.popPose();
    }

    private static void emitColored(Matrix4fc matrix, VertexConsumer vertices, RenderState state) {
        emitGeometry(state.geometry(), (x, y, z, u, v, nx, ny, nz) -> vertices
                .addVertex(matrix, x, y, z)
                .setColor(state.red(), state.green(), state.blue(), state.alpha()));
    }

    private static void emitTextured(Matrix4fc matrix, VertexConsumer vertices, ScreenGeometry geometry) {
        emitGeometry(geometry, (x, y, z, u, v, nx, ny, nz) -> vertices
                .addVertex(matrix, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(FULL_BRIGHT)
                .setNormal(nx, ny, nz));
    }

    private static void emitGeometry(ScreenGeometry geometry, QuadVertex vertex) {
        BlockPoint min = geometry.min();
        BlockPoint max = geometry.max();
        float x0 = min.x();
        float y0 = min.y();
        float z0 = min.z();
        float x1 = max.x() + 1;
        float y1 = max.y() + 1;
        float z1 = max.z() + 1;
        float offset = 0.002f;

        switch (geometry.facing()) {
            case NORTH -> emitQuad(vertex,
                    x1, y0, z0 - offset, x0, y0, z0 - offset,
                    x0, y1, z0 - offset, x1, y1, z0 - offset, 0, 0, -1);
            case SOUTH -> emitQuad(vertex,
                    x0, y0, z1 + offset, x1, y0, z1 + offset,
                    x1, y1, z1 + offset, x0, y1, z1 + offset, 0, 0, 1);
            case WEST -> emitQuad(vertex,
                    x0 - offset, y0, z0, x0 - offset, y0, z1,
                    x0 - offset, y1, z1, x0 - offset, y1, z0, -1, 0, 0);
            case EAST -> emitQuad(vertex,
                    x1 + offset, y0, z1, x1 + offset, y0, z0,
                    x1 + offset, y1, z0, x1 + offset, y1, z1, 1, 0, 0);
            case DOWN -> emitQuad(vertex,
                    x0, y0 - offset, z1, x1, y0 - offset, z1,
                    x1, y0 - offset, z0, x0, y0 - offset, z0, 0, -1, 0);
            case UP -> emitQuad(vertex,
                    x0, y1 + offset, z0, x1, y1 + offset, z0,
                    x1, y1 + offset, z1, x0, y1 + offset, z1, 0, 1, 0);
        }
    }

    private static void emitQuad(
            QuadVertex vertex,
            float ax, float ay, float az,
            float bx, float by, float bz,
            float cx, float cy, float cz,
            float dx, float dy, float dz,
            float nx, float ny, float nz) {
        vertex.add(ax, ay, az, 0, 1, nx, ny, nz);
        vertex.add(bx, by, bz, 1, 1, nx, ny, nz);
        vertex.add(cx, cy, cz, 1, 0, nx, ny, nz);
        vertex.add(dx, dy, dz, 0, 0, nx, ny, nz);
    }

    static void close() {
        closed = true;
        renderStates = List.of();
    }

    @FunctionalInterface
    private interface QuadVertex {
        void add(float x, float y, float z, float u, float v, float nx, float ny, float nz);
    }

    private record RenderState(
            ScreenGeometry geometry,
            Identifier texture,
            float red,
            float green,
            float blue,
            float alpha) {
    }
}
