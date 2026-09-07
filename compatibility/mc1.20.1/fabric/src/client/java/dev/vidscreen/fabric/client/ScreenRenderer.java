package dev.vidscreen.fabric.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.joml.Matrix4f;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import dev.vidscreen.client.VisibleScreenSelector;
import dev.vidscreen.domain.BlockPoint;
import dev.vidscreen.domain.PlaybackStatus;
import dev.vidscreen.domain.ScreenGeometry;
import dev.vidscreen.domain.ScreenState;

final class ScreenRenderer {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static ClientScreenStore store;
    private static ScreenTextureManager textures;
    private static boolean closed;

    private ScreenRenderer() {
    }

    static void initialize(ClientScreenStore screenStore, ScreenTextureManager textureManager) {
        store = screenStore;
        textures = textureManager;
        WorldRenderEvents.AFTER_ENTITIES.register(context -> render(context.matrixStack(), context.consumers(),
                context.camera().getPos()));
    }

    private static void render(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d camera) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (closed || client.world == null || client.player == null || consumers == null) {
            return;
        }
        String dimension = client.world.getRegistryKey().getValue().toString();
        Collection<ScreenState> candidates = store.snapshot();
        List<RenderState> visible = new ArrayList<RenderState>();
        for (ScreenState screen : candidates) {
            if (!screen.definition().dimension().value().equals(dimension)
                    || !VisibleScreenSelector.isWithinViewDistance(screen,
                            client.player.getX(), client.player.getY(), client.player.getZ())
                    || !client.world.isChunkLoaded(VisibleScreenSelector.anchorChunkX(screen),
                            VisibleScreenSelector.anchorChunkZ(screen))) {
                continue;
            }
            Identifier texture = textures.texture(screen.definition().id());
            if (texture != null) {
                float[] color = color(screen.playback().status());
                visible.add(new RenderState(screen.definition().geometry(), texture,
                        color[0], color[1], color[2], color[3]));
            }
        }
        matrices.push();
        matrices.translate(-camera.x, -camera.y, -camera.z);
        for (RenderState state : visible) {
            VertexConsumer vertices = consumers.getBuffer(RenderLayer.getEntityTranslucent(state.texture));
            emit(matrices.peek().getPositionMatrix(), vertices, state);
        }
        matrices.pop();
    }

    private static float[] color(PlaybackStatus status) {
        switch (status) {
            case PLAYING: return new float[] {0.05f, 0.85f, 0.95f, 1};
            case PAUSED: return new float[] {0.95f, 0.75f, 0.10f, 1};
            case FAILED: return new float[] {0.95f, 0.10f, 0.15f, 1};
            case BUFFERING: return new float[] {0.65f, 0.25f, 0.95f, 1};
            default: return new float[] {0.20f, 0.25f, 0.30f, 1};
        }
    }

    private static void emit(Matrix4f matrix, VertexConsumer vertices, RenderState state) {
        emitGeometry(state.geometry, (x, y, z, u, v, nx, ny, nz) -> vertices
                .vertex(matrix, x, y, z)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(FULL_BRIGHT)
                .normal(nx, ny, nz)
                .next());
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
            case NORTH: emitQuad(vertex, x1, y0, z0 - offset, x0, y0, z0 - offset,
                    x0, y1, z0 - offset, x1, y1, z0 - offset, 0, 0, -1); break;
            case SOUTH: emitQuad(vertex, x0, y0, z1 + offset, x1, y0, z1 + offset,
                    x1, y1, z1 + offset, x0, y1, z1 + offset, 0, 0, 1); break;
            case WEST: emitQuad(vertex, x0 - offset, y0, z0, x0 - offset, y0, z1,
                    x0 - offset, y1, z1, x0 - offset, y1, z0, -1, 0, 0); break;
            case EAST: emitQuad(vertex, x1 + offset, y0, z1, x1 + offset, y0, z0,
                    x1 + offset, y1, z0, x1 + offset, y1, z1, 1, 0, 0); break;
            case DOWN: emitQuad(vertex, x0, y0 - offset, z1, x1, y0 - offset, z1,
                    x1, y0 - offset, z0, x0, y0 - offset, z0, 0, -1, 0); break;
            case UP: emitQuad(vertex, x0, y1 + offset, z0, x1, y1 + offset, z0,
                    x1, y1 + offset, z1, x0, y1 + offset, z1, 0, 1, 0); break;
        }
    }

    private static void emitQuad(QuadVertex vertex,
            float ax, float ay, float az, float bx, float by, float bz,
            float cx, float cy, float cz, float dx, float dy, float dz,
            float nx, float ny, float nz) {
        vertex.add(ax, ay, az, 0, 1, nx, ny, nz);
        vertex.add(bx, by, bz, 1, 1, nx, ny, nz);
        vertex.add(cx, cy, cz, 1, 0, nx, ny, nz);
        vertex.add(dx, dy, dz, 0, 0, nx, ny, nz);
    }

    static void close() {
        closed = true;
    }

    private interface QuadVertex {
        void add(float x, float y, float z, float u, float v, float nx, float ny, float nz);
    }

    private static final class RenderState {
        private final ScreenGeometry geometry;
        private final Identifier texture;
        private final float red;
        private final float green;
        private final float blue;
        private final float alpha;

        private RenderState(ScreenGeometry geometry, Identifier texture,
                float red, float green, float blue, float alpha) {
            this.geometry = geometry;
            this.texture = texture;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
        }
    }

}
