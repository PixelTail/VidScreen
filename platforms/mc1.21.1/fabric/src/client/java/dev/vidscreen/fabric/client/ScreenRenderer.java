package dev.vidscreen.fabric.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

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
        closed = false;
        WorldRenderEvents.AFTER_TRANSLUCENT.register(ScreenRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (closed || client.world == null || client.player == null || context.matrixStack() == null
                || context.consumers() == null) {
            return;
        }
        String dimension = client.world.getRegistryKey().getValue().toString();
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
        if (renderStates.isEmpty()) {
            return;
        }

        MatrixStack poses = context.matrixStack();
        Camera camera = context.camera();
        Vec3d cameraPosition = camera.getPos();
        poses.push();
        poses.translate(-cameraPosition.x, -cameraPosition.y, -cameraPosition.z);
        MatrixStack.Entry entry = poses.peek();
        for (RenderState state : renderStates) {
            RenderLayer layer = state.texture() == null
                    ? RenderLayer.getDebugQuads()
                    : RenderLayer.getEntityTranslucent(state.texture());
            VertexConsumer vertices = context.consumers().getBuffer(layer);
            emitGeometry(entry, vertices, state);
        }
        poses.pop();
    }

    private static boolean isAnchorChunkLoaded(MinecraftClient client, ScreenState screen) {
        return client.world != null
                && client.world.isChunkLoaded(
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

    private static void emitGeometry(MatrixStack.Entry entry, VertexConsumer vertices, RenderState state) {
        Matrix4f position = entry.getPositionMatrix();
        emitGeometry(state.geometry(), (x, y, z, u, v, nx, ny, nz) -> vertices
                .vertex(position, x, y, z)
                .color(state.texture() == null ? state.red() : 1f,
                        state.texture() == null ? state.green() : 1f,
                        state.texture() == null ? state.blue() : 1f,
                        state.alpha())
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(FULL_BRIGHT)
                .normal(entry, nx, ny, nz));
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
