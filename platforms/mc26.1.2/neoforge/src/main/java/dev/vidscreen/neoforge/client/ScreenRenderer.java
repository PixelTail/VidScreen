package dev.vidscreen.neoforge.client;

import dev.vidscreen.client.ClientScreenStore;
import dev.vidscreen.client.ScreenMesh;
import dev.vidscreen.client.SelectionOverlay;

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
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ExtractLevelRenderStateEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

import dev.vidscreen.client.VisibleScreenSelector;
import dev.vidscreen.domain.PlaybackStatus;
import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.domain.ViewingArea;
import dev.vidscreen.neoforge.VidScreenNeoForge;

@EventBusSubscriber(modid = VidScreenNeoForge.MOD_ID, value = net.neoforged.api.distmarker.Dist.CLIENT)
final class ScreenRenderer {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private static final ContextKey<FrameState> DATA_KEY = new ContextKey<>(
            Identifier.fromNamespaceAndPath(VidScreenNeoForge.MOD_ID, "screen_render_states"));

    private static ClientScreenStore store;
    private static ScreenTextureManager textures;

    private ScreenRenderer() {
    }

    static void initialize(ClientScreenStore screenStore, ScreenTextureManager textureManager) {
        store = screenStore;
        textures = textureManager;
    }

    @SubscribeEvent
    static void extract(ExtractLevelRenderStateEvent event) {
        if (store == null || Minecraft.getInstance().level == null || Minecraft.getInstance().player == null) {
            return;
        }
        String dimension = Minecraft.getInstance().level.dimension().identifier().toString();
        double viewerX = Minecraft.getInstance().player.getX();
        double viewerY = Minecraft.getInstance().player.getY();
        double viewerZ = Minecraft.getInstance().player.getZ();
        Collection<ScreenState> screens = store.renderSnapshot();
        List<RenderState> states = new ArrayList<>(screens.size());
        for (ScreenState screen : screens) {
            if (!screen.definition().dimension().value().equals(dimension)
                    || !VisibleScreenSelector.isWithinViewDistance(screen, viewerX, viewerY, viewerZ)
                    || !isAnchorChunkLoaded(screen)) {
                continue;
            }
            float[] color = color(screen.playback().status());
            UUID screenId = screen.definition().id();
            Identifier texture = textures.texture(screenId);
            List<ScreenMesh.Quad> placeholder = ScreenMesh.surface(
                    screen.definition().geometry(), screen.definition().style());
            List<ScreenMesh.Quad> content;
            List<ScreenMesh.Quad> background;
            if (texture == null) {
                content = placeholder;
                background = List.of();
            } else {
                ScreenMesh.Layout layout = ScreenMesh.video(
                        screen.definition().geometry(), screen.definition().fit(), screen.definition().style());
                content = layout.contentQuads();
                background = layout.backgroundQuads();
            }
            states.add(new RenderState(
                    screenId,
                    placeholder,
                    content,
                    background,
                    texture,
                    color[0], color[1], color[2], color[3]));
        }
        List<SelectionOverlay.Line> overlayLines = extractOverlays(dimension);
        if (!states.isEmpty() || !overlayLines.isEmpty()) {
            event.getRenderState().setRenderData(
                    DATA_KEY, new FrameState(List.copyOf(states), overlayLines));
        }
    }

    private static List<SelectionOverlay.Line> extractOverlays(String dimension) {
        List<SelectionOverlay.Line> lines = new ArrayList<>();
        ViewingArea area = store.areaPreview();
        if (area != null && area.dimension().value().equals(dimension)) {
            lines.addAll(SelectionOverlay.viewingArea(area));
        }
        if (dimension.equals(store.previewDimension())) {
            lines.addAll(SelectionOverlay.selectionPoints(store.selectionPoints()));
        }
        return List.copyOf(lines);
    }

    @SubscribeEvent
    static void submit(SubmitCustomGeometryEvent event) {
        FrameState frame = event.getLevelRenderState().getRenderData(DATA_KEY);
        if (frame == null) {
            return;
        }
        PoseStack poses = event.getPoseStack();
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        for (RenderState state : frame.screens()) {
            if (state.texture() == null || !textures.isLive(state.screenId(), state.texture())) {
                event.getSubmitNodeCollector().submitCustomGeometry(
                        poses,
                        RenderTypes.debugQuads(),
                        (pose, vertices) -> emitColored(
                                pose.pose(), vertices, state.placeholder(),
                                state.red(), state.green(), state.blue(), state.alpha()));
            } else {
                if (!state.background().isEmpty()) {
                    event.getSubmitNodeCollector().submitCustomGeometry(
                            poses,
                            RenderTypes.debugQuads(),
                            (pose, vertices) -> emitColored(
                                    pose.pose(), vertices, state.background(), 0, 0, 0, 1));
                }
                event.getSubmitNodeCollector().submitCustomGeometry(
                        poses,
                        RenderTypes.entityTranslucentEmissive(state.texture()),
                        (pose, vertices) -> emitTextured(pose.pose(), vertices, state.content()));
            }
        }
        if (!frame.overlayLines().isEmpty()) {
            event.getSubmitNodeCollector().submitCustomGeometry(
                    poses,
                    RenderTypes.linesTranslucent(),
                    (pose, vertices) -> emitLines(pose.pose(), vertices, frame.overlayLines()));
        }
        poses.popPose();
    }

    private static boolean isAnchorChunkLoaded(ScreenState screen) {
        return Minecraft.getInstance().level != null
                && Minecraft.getInstance().level.getChunkSource().hasChunk(
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

    private static void emitColored(
            Matrix4fc matrix,
            VertexConsumer vertices,
            List<ScreenMesh.Quad> quads,
            float red,
            float green,
            float blue,
            float alpha) {
        for (ScreenMesh.Quad quad : quads) {
            quad.emit((x, y, z, u, v, nx, ny, nz) -> vertices
                    .addVertex(matrix, x, y, z)
                    .setColor(red, green, blue, alpha));
        }
    }

    private static void emitTextured(
            Matrix4fc matrix,
            VertexConsumer vertices,
            List<ScreenMesh.Quad> quads) {
        for (ScreenMesh.Quad quad : quads) {
            quad.emit((x, y, z, u, v, nx, ny, nz) -> vertices
                    .addVertex(matrix, x, y, z)
                    .setColor(255, 255, 255, 255)
                    .setUv(u, v)
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(FULL_BRIGHT)
                    .setNormal(nx, ny, nz));
        }
    }

    private static void emitLines(
            Matrix4fc matrix,
            VertexConsumer vertices,
            List<SelectionOverlay.Line> lines) {
        for (SelectionOverlay.Line line : lines) {
            float dx = line.end().x() - line.start().x();
            float dy = line.end().y() - line.start().y();
            float dz = line.end().z() - line.start().z();
            float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float nx = dx / length;
            float ny = dy / length;
            float nz = dz / length;
            vertices.addVertex(matrix, line.start().x(), line.start().y(), line.start().z())
                    .setColor(line.red(), line.green(), line.blue(), line.alpha())
                    .setNormal(nx, ny, nz)
                    .setLineWidth(line.width());
            vertices.addVertex(matrix, line.end().x(), line.end().y(), line.end().z())
                    .setColor(line.red(), line.green(), line.blue(), line.alpha())
                    .setNormal(nx, ny, nz)
                    .setLineWidth(line.width());
        }
    }

    private record FrameState(
            List<RenderState> screens,
            List<SelectionOverlay.Line> overlayLines) {
    }

    private record RenderState(
            UUID screenId,
            List<ScreenMesh.Quad> placeholder,
            List<ScreenMesh.Quad> content,
            List<ScreenMesh.Quad> background,
            Identifier texture,
            float red,
            float green,
            float blue,
            float alpha) {
    }
}
