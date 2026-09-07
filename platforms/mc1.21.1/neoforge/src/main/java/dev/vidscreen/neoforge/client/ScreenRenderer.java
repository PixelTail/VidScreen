package dev.vidscreen.neoforge.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import dev.vidscreen.client.VisibleScreenSelector;
import dev.vidscreen.domain.BlockPoint;
import dev.vidscreen.domain.PlaybackStatus;
import dev.vidscreen.domain.ScreenGeometry;
import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.neoforge.VidScreenNeoForge;

@EventBusSubscriber(modid = VidScreenNeoForge.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
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
        closed = false;
    }

    @SubscribeEvent
    static void render(RenderLevelStageEvent event) {
        if (closed || event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS
                || store == null || textures == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        String dimension = client.level.dimension().location().toString();
        Collection<ScreenState> screens = store.snapshot();
        List<RenderState> states = new ArrayList<>(screens.size());
        for (ScreenState screen : screens) {
            if (!screen.definition().dimension().value().equals(dimension)
                    || !VisibleScreenSelector.isWithinViewDistance(
                            screen, client.player.getX(), client.player.getY(), client.player.getZ())
                    || !isAnchorChunkLoaded(screen)) {
                continue;
            }
            float[] color = color(screen.playback().status());
            UUID screenId = screen.definition().id();
            states.add(new RenderState(
                    screen.definition().geometry(),
                    textures.texture(screenId),
                    color[0], color[1], color[2], color[3]));
        }
        if (states.isEmpty()) {
            return;
        }

        PoseStack poses = event.getPoseStack();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = client.renderBuffers().bufferSource();
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        PoseStack.Pose pose = poses.last();
        for (RenderState state : states) {
            RenderType layer = state.texture() == null
                    ? RenderType.debugQuads()
                    : RenderType.entityTranslucent(state.texture());
            VertexConsumer vertices = buffers.getBuffer(layer);
            emitGeometry(pose.pose(), vertices, state);
        }
        poses.popPose();
    }

    private static boolean isAnchorChunkLoaded(ScreenState screen) {
        return Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.hasChunk(
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

    private static void emitGeometry(Matrix4f matrix, VertexConsumer vertices, RenderState state) {
        emitGeometry(state.geometry(), (x, y, z, u, v, nx, ny, nz) -> vertices
                .addVertex(matrix, x, y, z)
                .setColor(state.texture() == null ? state.red() : 1f,
                        state.texture() == null ? state.green() : 1f,
                        state.texture() == null ? state.blue() : 1f,
                        state.alpha())
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
    }

    @FunctionalInterface
    private interface QuadVertex {
        void add(float x, float y, float z, float u, float v, float nx, float ny, float nz);
    }

    private record RenderState(
            ScreenGeometry geometry,
            ResourceLocation texture,
            float red,
            float green,
            float blue,
            float alpha) {
    }
}
