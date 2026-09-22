package dev.vidscreen.smoke;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import dev.vidscreen.domain.*;
import dev.vidscreen.gui.EditorClient;
import dev.vidscreen.protocol.message.EditorRequest;

/** Bounded, loopback-only application integration test; requires a disposable test server. */
public final class RuntimeSmoke implements ClientModInitializer {
    private final long started = System.currentTimeMillis();
    private final AtomicInteger captures = new AtomicInteger();
    private EditorClient editor;
    private int stage;
    private long after;
    private UUID areaId;
    private UUID screenId;
    private ScreenDefinition definition;
    private boolean finished;
    private final java.util.concurrent.ScheduledExecutorService watchdog =
            java.util.concurrent.Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "vidscreen-smoke-watchdog"); thread.setDaemon(true); return thread;
            });

    @Override public void onInitializeClient() {
        System.out.println("VIDSCREEN_SMOKE_START");
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
        watchdog.schedule(() -> Minecraft.getInstance().execute(() -> {
            if (!finished) { System.err.println("VIDSCREEN_SMOKE_FAIL watchdog stage=" + stage); finished = true; Minecraft.getInstance().stop(); }
            watchdog.shutdown();
        }), 150, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void tick(Minecraft client) {
        if (finished) { return; }
        try {
            long now = System.currentTimeMillis();
            if (now - started > 150_000) { throw new IllegalStateException("Runtime smoke timed out at stage " + stage); }
            if (now < after) { return; }
            if (stage == 0 && client.gui.screen() instanceof TitleScreen) {
                ConnectScreen.startConnecting(client.gui.screen(), client, ServerAddress.parseString("127.0.0.1:25565"),
                        new ServerData("VidScreen local test", "127.0.0.1:25565", ServerData.Type.OTHER), false, null);
                stage++; return;
            }
            if (client.level == null || client.player == null) { return; }
            if (stage == 0) { stage = 1; }
            if (editor == null) {
                var field = Class.forName("dev.vidscreen.fabric.client.VidScreenFabricClient").getDeclaredField("editor");
                field.setAccessible(true);
                editor = (EditorClient) field.get(null);
            }
            if (!editor.ready() || editor.busy()) { return; }
            switch (stage) {
                case 1 -> {
                    if (!client.player.getAbilities().mayfly) { throw new IllegalStateException("Smoke test requires the test server's creative mode"); }
                    client.player.getAbilities().flying = true; client.player.onUpdateAbilities();
                    client.player.connection.sendCommand("tp @s ~ 180 ~");
                    stage = 11; after = now + 1500;
                }
                case 11 -> {
                    int x = client.player.getBlockX(), y = client.player.getBlockY(), z = client.player.getBlockZ();
                    areaId = UUID.randomUUID(); screenId = UUID.randomUUID();
                    DimensionKey world = new DimensionKey(client.level.dimension().identifier().toString());
                    ViewingArea area = new ViewingArea(areaId, "smoke_area", world,
                            new BlockPoint(x-24,y-8,z-24), new BlockPoint(x+24,y+24,z+24));
                    definition = new ScreenDefinition(screenId, "smoke_screen", world,
                            ScreenGeometry.between(new BlockPoint(x-8,y+1,z+12), new BlockPoint(x+7,y+9,z+12), Facing.NORTH),
                            ScreenFit.CONTAIN, 96, ScreenStyle.FLAT, areaId);
                    editor.request(EditorRequest.area(EditorRequest.Action.AREA_CREATE, area), null);
                    stage = 2; after = now + 1000;
                }
                case 2 -> {
                    if (editor.areas().stream().noneMatch(a -> a.id().equals(areaId))) { failStatus(); }
                    editor.request(EditorRequest.definition(EditorRequest.Action.CREATE, definition), null);
                    stage++; after = now + 1000;
                }
                case 3 -> {
                    if (editor.screens().stream().noneMatch(s -> s.definition().id().equals(screenId))) { failStatus(); }
                    editor.selected(screenId);
                    editor.request(EditorRequest.source(screenId, MediaSources.parse(
                            "https://archive.blender.org/wiki/2024/w/images/c/c2/Testx2.mp4")), null);
                    stage++; after = now + 1000;
                }
                case 4 -> {
                    editor.request(EditorRequest.operation(EditorRequest.Action.PLAY, screenId, 0), null);
                    client.player.setYRot(0); client.player.setXRot(-14);
                    stage++; after = now + 7000;
                }
                case 5 -> {
                    var playbackField = Class.forName("dev.vidscreen.fabric.client.VidScreenFabricClient").getDeclaredField("playback");
                    playbackField.setAccessible(true);
                    Object playback = playbackField.get(null);
                    var texturesMethod = playback.getClass().getDeclaredMethod("textures"); texturesMethod.setAccessible(true);
                    Object textures = texturesMethod.invoke(playback);
                    var textureMethod = textures.getClass().getDeclaredMethod("texture", UUID.class); textureMethod.setAccessible(true);
                    if (textureMethod.invoke(textures, screenId) == null) { throw new IllegalStateException("No decoded GPU texture"); }
                    capture(client, "01-world-flat.png"); editor.open();
                    stage++; after = now + 1500;
                }
                case 6 -> {
                    capture(client, "02-player-menu.png");
                    definition = new ScreenDefinition(definition.id(), definition.name(), definition.dimension(), definition.geometry(),
                            definition.fit(), definition.viewDistance(), new ScreenStyle(70,32,0,1,0), areaId);
                    editor.request(EditorRequest.definition(EditorRequest.Action.UPDATE, definition), null);
                    EditorClient.show(null); stage++; after = now + 1500;
                }
                case 7 -> {
                    if (editor.current().definition().style().curvatureDegrees() != 70) { failStatus(); }
                    capture(client, "03-world-curved.png");
                    editor.showArea(editor.areas().stream().filter(a -> a.id().equals(areaId)).findFirst().orElseThrow());
                    var storeField = EditorClient.class.getDeclaredField("store"); storeField.setAccessible(true);
                    var store = (dev.vidscreen.client.ClientScreenStore) storeField.get(editor);
                    BlockPoint min = definition.geometry().min(), max = definition.geometry().max();
                    store.setSelectionPoints(java.util.List.of(min,max,
                            new BlockPoint(min.x(),max.y(),min.z()), new BlockPoint(max.x(),min.y(),max.z())));
                    stage = 12; after = now + 1500;
                }
                case 12 -> {
                    capture(client, "05-selection-overlays.png"); editor.clearPreview(); editor.move();
                    stage = 8; after = now + 1500;
                }
                case 8 -> {
                    capture(client, "04-screen-settings.png"); EditorClient.show(null);
                    editor.request(EditorRequest.operation(EditorRequest.Action.DELETE, screenId, 0), null);
                    stage++; after = now + 1000;
                }
                case 9 -> {
                    if (editor.screens().stream().anyMatch(s -> s.definition().id().equals(screenId))) { failStatus(); }
                    editor.request(EditorRequest.operation(EditorRequest.Action.AREA_DELETE, areaId, 0), null);
                    stage++; after = now + 1000;
                }
                case 10 -> {
                    if (editor.areas().stream().anyMatch(a -> a.id().equals(areaId))) { failStatus(); }
                    if (captures.get() < 5) { return; }
                    System.out.println("VIDSCREEN_SMOKE_PASS: handshake, area, create, source, play, curve, menu, delete");
                    finished = true; watchdog.shutdownNow(); client.stop();
                }
                default -> { }
            }
        } catch (Throwable error) {
            System.err.println("VIDSCREEN_SMOKE_FAIL stage=" + stage + " " + error);
            finished = true; watchdog.shutdownNow(); client.stop();
        }
    }
    private void failStatus() { throw new IllegalStateException(editor.status()); }
    private void capture(Minecraft client, String name) {
        Screenshot.grab(client.gameDirectory, name, client.gameRenderer.mainRenderTarget(), 1,
                result -> { captures.incrementAndGet(); System.out.println("VIDSCREEN_SMOKE_CAPTURE " + result.getString()); });
    }
}
