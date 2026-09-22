package dev.vidscreen.fabric.client;

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
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelExtractionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import dev.vidscreen.client.VisibleScreenSelector;
import dev.vidscreen.domain.PlaybackStatus;
import dev.vidscreen.domain.ScreenState;
import dev.vidscreen.domain.ViewingArea;

final class ScreenRenderer {
    private static final int FULL_BRIGHT = 0x00F000F0;

    private static ClientScreenStore store;
    private static ScreenTextureManager textures;
    private static List<RenderState> renderStates = List.of();
    private static List<SelectionOverlay.Line> overlayLines = List.of();
    private static boolean closed;

    private ScreenRenderer() {
    }

    static void initialize(ClientScreenStore screenStore, ScreenTextureManager textureManager) {
        store = screenStore;
        textures = textureManager;
        LevelExtractionEvents.END_EXTRACTION.register(ScreenRenderer::extract);
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(ScreenRenderer::submitScreens);
    }

    private static void extract(LevelExtractionContext context) {
        Minecraft client = Minecraft.getInstance();
        if (closed || client.level == null || client.player == null) {
            renderStates = List.of();
            overlayLines = List.of();
            return;
        }
        String dimension = client.level.dimension().identifier().toString();
        Collection<ScreenState> screens = store.renderSnapshot();
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
            extracted.add(new RenderState(
                    screenId,
                    placeholder,
                    content,
                    background,
                    texture,
                    color[0], color[1], color[2], color[3]));
        }
        renderStates = List.copyOf(extracted);
        overlayLines = extractOverlays(dimension);
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

    private static boolean isAnchorChunkLoaded(Minecraft client, ScreenState screen) {
        return client.level != null
                && client.level.getChunkSource().hasChunk(
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
        if (closed || renderStates.isEmpty() && overlayLines.isEmpty()) {
            return;
        }
        PoseStack poses = context.poseStack();
        Vec3 camera = context.levelState().cameraRenderState.pos;
        poses.pushPose();
        poses.translate(-camera.x, -camera.y, -camera.z);
        for (RenderState state : renderStates) {
            if (state.texture() == null || !textures.isLive(state.screenId(), state.texture())) {
                context.submitNodeCollector().submitCustomGeometry(
                        poses,
                        RenderTypes.debugQuads(),
                        (pose, vertices) -> emitColored(
                                pose.pose(), vertices, state.placeholder(),
                                state.red(), state.green(), state.blue(), state.alpha()));
            } else {
                if (!state.background().isEmpty()) {
                    context.submitNodeCollector().submitCustomGeometry(
                            poses,
                            RenderTypes.debugQuads(),
                            (pose, vertices) -> emitColored(
                                    pose.pose(), vertices, state.background(), 0, 0, 0, 1));
                }
                context.submitNodeCollector().submitCustomGeometry(
                        poses,
                        RenderTypes.entityTranslucentEmissive(state.texture()),
                        (pose, vertices) -> emitTextured(pose.pose(), vertices, state.content()));
            }
        }
        if (!overlayLines.isEmpty()) {
            context.submitNodeCollector().submitCustomGeometry(
                    poses,
                    RenderTypes.linesTranslucent(),
                    (pose, vertices) -> emitLines(pose.pose(), vertices, overlayLines));
        }
        poses.popPose();
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

    static void close() {
        closed = true;
        renderStates = List.of();
        overlayLines = List.of();
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
