package dev.vidscreen.forge.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Matrix4f;

import dev.vidscreen.client.VisibleScreenSelector;
import dev.vidscreen.domain.BlockPoint;
import dev.vidscreen.domain.PlaybackStatus;
import dev.vidscreen.domain.ScreenGeometry;
import dev.vidscreen.domain.ScreenState;

final class ScreenRenderer {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private final ClientScreenStore store;
    private final ScreenTextureManager textures;
    private boolean closed;

    ScreenRenderer(ClientScreenStore store, ScreenTextureManager textures) {
        this.store = store;
        this.textures = textures;
    }

    @SubscribeEvent
    public void render(RenderLevelStageEvent event) {
        if (closed || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null || client.player == null) {
            return;
        }
        String dimension = level.dimension().location().toString();
        Collection<ScreenState> candidates = store.snapshot();
        List<RenderState> visible = new ArrayList<RenderState>();
        for (ScreenState screen : candidates) {
            if (!screen.definition().dimension().value().equals(dimension)
                    || !VisibleScreenSelector.isWithinViewDistance(screen,
                            client.player.getX(), client.player.getY(), client.player.getZ())
                    || !level.hasChunk(VisibleScreenSelector.anchorChunkX(screen),
                            VisibleScreenSelector.anchorChunkZ(screen))) {
                continue;
            }
            ResourceLocation texture = textures.texture(screen.definition().id());
            if (texture != null) {
                float[] color = color(screen.playback().status());
                visible.add(new RenderState(screen.definition().geometry(), texture,
                        color[0], color[1], color[2], color[3]));
            }
        }
        if (visible.isEmpty()) {
            return;
        }
        PoseStack poses = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        for (RenderState state : visible) {
            VertexConsumer vertices = buffers.getBuffer(RenderType.entityTranslucent(state.texture));
            emit(poses.last().pose(), vertices, state);
        }
        poses.popPose();
        buffers.endBatch();
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
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(FULL_BRIGHT)
                .normal(nx, ny, nz)
                .endVertex());
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

    void close() {
        closed = true;
    }

    private interface QuadVertex {
        void add(float x, float y, float z, float u, float v, float nx, float ny, float nz);
    }

    private static final class RenderState {
        private final ScreenGeometry geometry;
        private final ResourceLocation texture;
        private final float red;
        private final float green;
        private final float blue;
        private final float alpha;

        private RenderState(ScreenGeometry geometry, ResourceLocation texture,
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
