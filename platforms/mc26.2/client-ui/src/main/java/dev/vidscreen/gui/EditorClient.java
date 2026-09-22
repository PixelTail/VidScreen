package dev.vidscreen.gui;

import java.util.List;
import java.util.ArrayList;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.InteractionHand;
import dev.vidscreen.client.ClientConnection;
import dev.vidscreen.client.ClientScreenStore;
import dev.vidscreen.client.ScreenSelection;
import dev.vidscreen.domain.*;
import dev.vidscreen.protocol.WireMessage;
import dev.vidscreen.protocol.message.EditorRequest;
import dev.vidscreen.protocol.message.OperationResult;

public final class EditorClient {
    private final ClientScreenStore store;
    private final ClientConnection connection;
    private final Consumer<WireMessage> transport;
    private final Supplier<String> availability;
    private final ScreenSelection selection = new ScreenSelection();
    private UUID selected;
    private UUID pending;
    private long pendingAt;
    private Runnable afterSuccess;
    private boolean selecting;
    private String status = "";
    private String draftName = "screen";
    private String source = "";
    private final List<BlockPoint> areaCorners = new ArrayList<>();
    private DimensionKey areaDimension;
    private boolean areaDraft;
    private UUID selectedArea;
    private UUID deleteAreaCandidate;
    private ScreenDefinition editDraft;
    private UUID editingArea;
    private boolean suppressUse;

    public EditorClient(ClientScreenStore store, ClientConnection connection,
            Consumer<WireMessage> transport, Supplier<String> availability) {
        this.store = store;
        this.connection = connection;
        this.transport = transport;
        this.availability = availability;
    }

    public void open() {
        Minecraft client = Minecraft.getInstance();
        if (selected == null && client.level != null && client.player != null) {
            List<ScreenState> candidates = new ArrayList<>(store.playbackSnapshot(client.level.dimension().identifier().toString(),
                    client.player.getX(), client.player.getY(), client.player.getZ()));
            candidates.sort(java.util.Comparator.comparingInt(s -> s.definition().viewingAreaId() == null ? 1 : 0));
            for (ScreenState screen : candidates) {
                if (screen.definition().dimension().value().equals(client.level.dimension().identifier().toString())
                        && dev.vidscreen.client.VisibleScreenSelector.isWithinViewDistance(screen,
                        client.player.getX(), client.player.getY(), client.player.getZ())) {
                    selected = screen.definition().id(); break;
                }
            }
        }
        if (client.player != null) { show(new VidScreenMenu(this, selecting ? VidScreenMenu.Page.CREATE : VidScreenMenu.Page.PLAYER)); }
    }

    public void move() {
        if (current() == null) { status("先在观影菜单中选择一块屏幕。"); open(); return; }
        show(new VidScreenMenu(this, VidScreenMenu.Page.EDIT));
    }

    public void reset() {
        selection.clear(); selecting = false; pending = null; afterSuccess = null; selected = null;
        source = ""; status = "";
        areaCorners.clear(); areaDimension = null; areaDraft = false; selectedArea = null;
        editDraft = null; deleteAreaCandidate = null;
        editingArea = null;
        suppressUse = false;
    }

    public void tick() {
        Minecraft client = Minecraft.getInstance();
        if (!client.options.keyUse.isDown()) { suppressUse = false; }
        ScreenState activeScreen = current();
        if (activeScreen != null && client.level != null
                && !activeScreen.definition().dimension().value().equals(client.level.dimension().identifier().toString())) {
            selected = null; editDraft = null; store.setPreview(null);
        }
        if (selectedArea != null && client.level != null
                && areas().stream().noneMatch(a -> a.id().equals(selectedArea)
                && a.dimension().value().equals(client.level.dimension().identifier().toString()))) {
            selectedArea = null;
        }
        DimensionKey draftWorld = areaDraft ? areaDimension : selection.dimension();
        if (draftWorld != null && (client.level == null
                || !draftWorld.value().equals(client.level.dimension().identifier().toString()))) {
            cancelSelection(); status("维度已改变，已取消未确认的选区。");
        }
        if (pending != null && System.currentTimeMillis() - pendingAt > 10_000) {
            pending = null; afterSuccess = null;
            status("服务器尚未确认操作。请刷新屏幕列表后再试。");
        }
        if (selecting && client.player != null && screen() == null) {
            dev.vidscreen.gui.EditorClient.message(Component.literal(areaDraft
                    ? "观影区 " + areaCorners.size() + "/2：左键选空间对角，右键撤销，N 返回确认。"
                    : "屏幕 " + selection.size() + "/4：左键顺时针选四角，右键撤销，N 返回确认。"), true);
        }
    }

    public boolean selecting() { return selecting; }
    public boolean consumeUse(InteractionHand hand) {
        if (!selecting && !suppressUse) { return false; }
        if (hand == InteractionHand.MAIN_HAND && !suppressUse) {
            suppressUse = true; undo();
        }
        return true;
    }

    public void selectTarget() {
        Minecraft client = Minecraft.getInstance();
        if (!selecting || client.player == null || client.level == null) { return; }
        HitResult result = client.player.pick(128, 1, false);
        if (!(result instanceof BlockHitResult hit) || result.getType() != HitResult.Type.BLOCK) {
            status("请指向 128 格内的方块表面。"); return;
        }
        BlockPos point = hit.getBlockPos();
        try {
            if (areaDraft) {
                if (areaCorners.size() == 2) { status("区域两点已选好。按 N 命名并确认。"); return; }
                areaDimension = new DimensionKey(client.level.dimension().identifier().toString());
                areaCorners.add(new BlockPoint(point.getX(), point.getY(), point.getZ()));
                try {
                    if (areaCorners.size() == 2) { areaGeometry("preview"); }
                } catch (RuntimeException error) { areaCorners.remove(areaCorners.size() - 1); throw error; }
                updatePreview(); return;
            }
            selection.select(new DimensionKey(client.level.dimension().identifier().toString()),
                    new BlockPoint(point.getX(), point.getY(), point.getZ()), Facing.valueOf(hit.getDirection().name()));
            status(selection.size() == 4 ? "四角已选好。按 N 命名并确认创建。" : "已选 " + selection.size() + "/4 个角点。");
            updatePreview();
        } catch (RuntimeException error) { status(error.getMessage()); }
    }

    public void undo() {
        if (selectedPoints() == 0) { cancelSelection(); return; }
        if (areaDraft && !areaCorners.isEmpty()) { areaCorners.remove(areaCorners.size() - 1); }
        else { selection.undo(); }
        updatePreview(); status("已撤销上一个角点。");
    }
    public ScreenSelection selection() { return selection; }
    public void beginSelection() { cancelSelection(); selecting = true; show(null); }
    public void beginAreaSelection() { cancelSelection(); selecting = true; areaDraft = true; draftName = "cinema"; show(null); }
    public void editArea(ViewingArea area) {
        beginAreaSelection(); editingArea = area.id(); draftName = area.name();
    }
    public void cancelSelection() {
        selection.clear(); areaCorners.clear(); areaDimension = null; areaDraft = false; selecting = false;
        editingArea = null;
        clearPreview(); status("已取消创建。");
    }
    public void clearPreview() {
        store.setPreview(null); store.setAreaPreview(null); store.setSelectionPoints(List.of()); store.setPreviewDimension(null);
    }

    private void updatePreview() {
        DimensionKey world = areaDraft ? areaDimension : selection.dimension();
        store.setPreviewDimension(world == null ? null : world.value());
        store.setSelectionPoints(areaDraft ? areaCorners : selection.corners());
        if (areaDraft) {
            store.setAreaPreview(areaCorners.size() == 2 ? areaGeometry("preview") : null);
            return;
        }
        if (selection.size() == 4) {
            ScreenDefinition definition = new ScreenDefinition(new UUID(0, 0), "preview", selection.dimension(),
                    selection.geometry(), ScreenFit.CONTAIN, 256);
            store.setPreview(new ScreenState(0, definition, null,
                    new PlaybackState(0, PlaybackStatus.BUFFERING, 0, 0, 1, false)));
        } else { store.setPreview(null); }
    }

    public void create(String name) {
        try {
            if (areaDraft) {
                ViewingArea area = areaGeometry(name);
                request(EditorRequest.area(editingArea == null ? EditorRequest.Action.AREA_CREATE : EditorRequest.Action.AREA_UPDATE, area), () -> {
                    selectedArea = area.id(); cancelSelection(); status = "观影区已创建。下一步选择屏幕四角。";
                    show(new VidScreenMenu(this, VidScreenMenu.Page.CREATE));
                });
                return;
            }
            ScreenDefinition definition = new ScreenDefinition(UUID.randomUUID(), name, selection.dimension(),
                    selection.geometry(), ScreenFit.CONTAIN, 96, ScreenStyle.FLAT, selectedArea);
            request(EditorRequest.definition(EditorRequest.Action.CREATE, definition), () -> {
                selected = definition.id(); cancelSelection(); status = "屏幕已创建。粘贴视频链接开始播放。";
                show(new VidScreenMenu(this, VidScreenMenu.Page.PLAYER));
            });
        } catch (RuntimeException error) { status(error.getMessage()); }
    }

    public void request(EditorRequest request, Runnable success) {
        if (!connection.editorAvailable()) { status("服务器未启用可视化编辑。请安装匹配的新版本服务端。"); return; }
        if (pending != null) { status("正在等待服务器保存，请稍候。"); return; }
        pending = request.operationId(); pendingAt = System.currentTimeMillis(); afterSuccess = success;
        try { transport.accept(request); status = "正在等待服务器确认…"; }
        catch (RuntimeException error) { pending = null; afterSuccess = null; status("操作发送失败，请检查连接。"); }
    }

    public void accept(WireMessage message) {
        if (!(message instanceof OperationResult result) || !result.operationId().equals(pending)) { return; }
        Runnable action = afterSuccess;
        pending = null; afterSuccess = null;
        status = result.success() ? "操作已保存。" : result.message();
        if (result.success() && action != null) { action.run(); }
        if (screen() instanceof VidScreenMenu menu) { menu.refresh(); }
    }

    public ScreenState current() {
        for (ScreenState state : store.snapshot()) { if (state.definition().id().equals(selected)) { return state; } }
        return null;
    }
    public List<ScreenState> screens() { return List.copyOf(store.snapshot()); }
    public List<ViewingArea> areas() { return List.copyOf(store.areas()); }
    public UUID selectedArea() { return selectedArea; }
    public UUID deleteAreaCandidate() { return deleteAreaCandidate; }
    public void deleteAreaCandidate(UUID id) { deleteAreaCandidate = id; }
    public ScreenDefinition editDraft() { return editDraft; }
    public void editDraft(ScreenDefinition value) { editDraft = value; }
    public long serverTime() { return connection.estimatedServerTimeMillis(System.currentTimeMillis()); }
    public void showArea(ViewingArea area) { store.setAreaPreview(area); store.setPreviewDimension(area.dimension().value()); }
    public void selectedArea(UUID id) { selectedArea = id; }
    public boolean areaDraft() { return areaDraft; }
    public int selectedPoints() { return areaDraft ? areaCorners.size() : selection.size(); }
    public void preview(ScreenDefinition definition) {
        ScreenState current = current();
        store.setPreview(new ScreenState(0, definition, current == null ? null : current.media(),
                new PlaybackState(0, PlaybackStatus.BUFFERING, 0, 0, 1, false)));
    }
    private ViewingArea areaGeometry(String name) {
        if (areaCorners.size() != 2) { throw new IllegalStateException("请先选择观影区两个空间对角。"); }
        return new ViewingArea(editingArea == null ? UUID.randomUUID() : editingArea,
                name, areaDimension, areaCorners.get(0), areaCorners.get(1));
    }
    public void selected(UUID id) { selected = id; editDraft = null; store.setPreview(null); }
    public String status() { return status; }
    public String availability() { return availability.get(); }
    public boolean ready() { return connection.editorAvailable(); }
    public boolean busy() { return pending != null; }
    public String draftName() { return draftName; }
    public void draftName(String name) { draftName = name; }
    public String source() { return source; }
    public void source(String text) { source = text; }
    public void status(String text) {
        status = text == null ? "操作未完成。" : text;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && screen() == null) { dev.vidscreen.gui.EditorClient.message(Component.literal(status), false); }
    }
    public static void message(Component text, boolean overlay) {
        Minecraft client = Minecraft.getInstance();
        if (overlay) { client.gui.hud.setOverlayMessage(text, false); }
        else { client.gui.hud.getChat().addClientSystemMessage(text); }
    }
    public static Screen screen() { return Minecraft.getInstance().gui.screen(); }
    public static void show(Screen screen) { Minecraft.getInstance().gui.setScreen(screen); }
}
