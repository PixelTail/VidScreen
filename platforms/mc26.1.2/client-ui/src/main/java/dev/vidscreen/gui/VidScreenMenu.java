package dev.vidscreen.gui;

import java.util.List;
import java.util.UUID;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.network.chat.Component;
import dev.vidscreen.domain.*;
import dev.vidscreen.protocol.message.EditorRequest;

public final class VidScreenMenu extends Screen {
    public enum Page { PLAYER, CREATE, AREAS, EDIT }
    private final EditorClient editor;
    private final Page page;
    private int listPage;
    private StringWidget status;
    private EditBox name, url, curve, segments, dx, dy, dz;
    private ScreenDefinition draft;
    private ScreenFit fit;
    private UUID areaId;
    private boolean deleteConfirmed;
    private int left, top, panelWidth;

    public VidScreenMenu(EditorClient editor, Page page) {
        super(Component.literal("VidScreen · 观影菜单"));
        this.editor = editor;
        this.page = page;
        ScreenState selected = editor.current();
        if (selected != null) {
            draft = editor.editDraft() != null && editor.editDraft().id().equals(selected.definition().id())
                    ? editor.editDraft() : selected.definition();
            fit = draft.fit();
            areaId = draft.viewingAreaId();
        }
    }

    @Override
    protected void init() {
        panelWidth = Math.max(280, Math.min(540, width - 24));
        left = (width - panelWidth) / 2;
        top = Math.max(2, (height - 238) / 2);
        label(title.getString(), left, top, panelWidth);
        int navWidth = (panelWidth - 8) / 3;
        button("观影", left, top + 18, navWidth, () -> navigate(Page.PLAYER));
        button("新建", left + navWidth + 4, top + 18, navWidth, () -> navigate(Page.CREATE));
        button("观影区", left + (navWidth + 4) * 2, top + 18, navWidth, () -> navigate(Page.AREAS));
        switch (page) {
            case PLAYER -> player();
            case CREATE -> create();
            case AREAS -> areas();
            case EDIT -> edit();
        }
        status = label(editor.status(), left, top + 204, panelWidth);
        button("返回游戏", left, top + 216, panelWidth, this::onClose);
    }

    private void player() {
        int split = panelWidth / 3;
        List<ScreenState> screens = editor.screens();
        int first = Math.min(listPage * 5, Math.max(0, screens.size() - 1));
        label("屏幕列表", left, top + 44, split - 6);
        for (int i = first; i < Math.min(screens.size(), first + 5); i++) {
            ScreenState screen = screens.get(i);
            button(screen.definition().name(), left, top + 57 + (i - first) * 22, split - 8, () -> {
                editor.selected(screen.definition().id()); deleteConfirmed = false; refresh();
            });
        }
        if (screens.isEmpty()) { label("暂无屏幕，点击“新建”", left, top + 65, split - 6); }
        button("<", left, top + 172, (split - 12) / 2, () -> { listPage = Math.max(0, listPage - 1); refresh(); });
        button(">", left + split / 2, top + 172, (split - 12) / 2, () -> {
            if ((listPage + 1) * 5 < screens.size()) { listPage++; } refresh();
        });
        int x = left + split, w = panelWidth - split;
        ScreenState current = editor.current();
        if (current == null) {
            label("先选择屏幕，或新建观影区和屏幕。", x, top + 50, w);
            label(editor.ready() ? "已连接服务端" : "等待匹配的 VidScreen 服务端", x, top + 76, w);
            label(editor.availability(), x, top + 102, w);
            return;
        }
        label(current.definition().name() + " · " + playbackLabel(current.playback().status()), x, top + 44, w);
        url = field(editor.source(), "粘贴 HTTPS 视频链接", x, top + 58, w - 54, 4096);
        url.setResponder(editor::source);
        button("播放", x + w - 50, top + 58, 50, () -> {
            try {
                MediaDescriptor media = MediaSources.parse(url.getValue().trim());
                UUID id = current.definition().id();
                editor.request(EditorRequest.source(id, media),
                        () -> editor.request(EditorRequest.operation(EditorRequest.Action.PLAY, id, 0), null));
            } catch (RuntimeException error) { editor.status(error.getMessage()); }
        });
        int bw = (w - 8) / 3;
        button("继续", x, top + 84, bw, () -> operate(EditorRequest.Action.PLAY, 0));
        button("暂停", x + bw + 4, top + 84, bw, () -> operate(EditorRequest.Action.PAUSE, 0));
        button("停止", x + (bw + 4) * 2, top + 84, bw, () -> operate(EditorRequest.Action.STOP, 0));
        button("-10 秒", x, top + 110, bw, () -> seek(-10_000));
        button("+10 秒", x + bw + 4, top + 110, bw, () -> seek(10_000));
        button(current.playback().looping() ? "循环：开" : "循环：关", x + (bw + 4) * 2, top + 110, bw,
                () -> operate(EditorRequest.Action.LOOP, current.playback().looping() ? 0 : 1));
        button("屏幕设置 / 移动", x, top + 136, (w - 4) / 2, () -> navigate(Page.EDIT));
        button("重新同步", x + (w + 4) / 2, top + 136, (w - 4) / 2, () -> operate(EditorRequest.Action.SYNC, 0));
        button(deleteConfirmed ? "确认删除" : "删除屏幕…", x, top + 164, (w - 4) / 2, () -> {
            if (!deleteConfirmed) { deleteConfirmed = true; refresh(); }
            else { editor.request(EditorRequest.operation(EditorRequest.Action.DELETE, current.definition().id(), 0),
                    () -> { editor.selected(null); deleteConfirmed = false; refresh(); }); }
        });
        button("倍速：" + current.playback().playbackRate() + "x", x + (w + 4) / 2, top + 164, (w - 4) / 2, () -> {
            double rate = current.playback().playbackRate();
            operate(EditorRequest.Action.RATE, rate >= 2 ? 0.5 : rate + 0.5);
        });
        label("粘贴新视频链接，或继续已保存的视频。", x, top + 191, w);
    }

    private void create() {
        int half = (panelWidth - 6) / 2;
        button("① 选择观影区两点", left, top + 47, half, editor::beginAreaSelection);
        button("② 选择屏幕四角", left + half + 6, top + 47, half, editor::beginSelection);
        label(editor.areaDraft() ? "左键选择空间对角，右键撤销，N 返回。" : "按顺时针选择：左上 → 右上 → 右下 → 左下。", left, top + 75, panelWidth);
        label((editor.areaDraft() ? "观影区 " : "屏幕 ") + editor.selectedPoints()
                + (editor.areaDraft() ? "/2" : "/4") + " 已选；确认前不会保存到服务器。", left, top + 90, panelWidth);
        name = field(editor.draftName(), "名称：英文、数字、下划线或连字符", left, top + 108, panelWidth, 64);
        name.setResponder(editor::draftName);
        button("绑定观影区：" + areaName(editor.selectedArea()), left, top + 136, panelWidth, () -> {
            editor.selectedArea(nextArea(editor.selectedArea())); refresh();
        });
        button(editor.areaDraft() ? "确认创建观影区" : "确认创建屏幕", left, top + 164, half,
                () -> editor.create(name.getValue()));
        button("取消创建", left + half + 6, top + 164, half, () -> { editor.cancelSelection(); refresh(); });
        label("区域限定客户端的播放范围；“无”表示按距离播放。", left, top + 190, panelWidth);
    }

    private void areas() {
        List<ViewingArea> areas = editor.areas();
        int first = Math.min(listPage * 4, Math.max(0, areas.size() - 1));
        int w = panelWidth - 138;
        for (int i = first; i < Math.min(areas.size(), first + 4); i++) {
            ViewingArea area = areas.get(i);
            button(area.name() + " · " + area.dimension().value(), left, top + 48 + (i - first) * 25, w, () -> {
                editor.selectedArea(area.id()); editor.showArea(area); editor.status("已选择观影区 " + area.name());
            });
            button("重选", left + w + 4, top + 48 + (i - first) * 25, 62, () -> editor.editArea(area));
            button("删除…", left + w + 70, top + 48 + (i - first) * 25, 68, () -> {
                if (!area.id().equals(editor.deleteAreaCandidate())) {
                    editor.deleteAreaCandidate(area.id()); editor.status("再次点击该区域的删除按钮确认。已绑定屏幕的区域不能删除。");
                } else {
                    editor.request(EditorRequest.operation(EditorRequest.Action.AREA_DELETE, area.id(), 0),
                            () -> { editor.deleteAreaCandidate(null); editor.selectedArea(null); refresh(); });
                }
            });
        }
        if (areas.isEmpty()) { label("还没有观影区。先选择两个空间对角。", left, top + 60, panelWidth); }
        int third = (panelWidth - 8) / 3;
        button("上一页", left, top + 156, third, () -> { listPage = Math.max(0, listPage - 1); refresh(); });
        button("创建观影区", left + third + 4, top + 156, third, editor::beginAreaSelection);
        button("下一页", left + (third + 4) * 2, top + 156, third, () -> {
            if ((listPage + 1) * 4 < areas.size()) { listPage++; } refresh();
        });
        label("点击区域名称显示边界。删除前请先解除屏幕绑定。", left, top + 187, panelWidth);
    }

    private void edit() {
        if (draft == null) { label("先在观影页选择屏幕。", left, top + 55, panelWidth); return; }
        int half = (panelWidth - 6) / 2;
        name = field(draft.name(), "屏幕名称", left, top + 46, half, 64);
        button("适配：" + switch (fit) { case CONTAIN -> "完整显示"; case COVER -> "裁剪铺满"; case STRETCH -> "拉伸铺满"; },
                left + half + 6, top + 46, half, () -> {
            captureDraft(); fit = ScreenFit.values()[(fit.ordinal() + 1) % ScreenFit.values().length]; refresh();
        });
        label("曲率（度，0 为平面；-170～170） / 细分（1～128）", left, top + 72, panelWidth);
        curve = field(Double.toString(draft.style().curvatureDegrees()), "曲率", left, top + 86, half, 12);
        segments = field(Integer.toString(draft.style().segments()), "细分", left + half + 6, top + 86, half, 4);
        label("相对选点的位移 X / Y / Z（格，可用小数，±256）", left, top + 111, panelWidth);
        int third = (panelWidth - 8) / 3;
        dx = field(Double.toString(draft.style().offsetX()), "X", left, top + 124, third, 12);
        dy = field(Double.toString(draft.style().offsetY()), "Y", left + third + 4, top + 124, third, 12);
        dz = field(Double.toString(draft.style().offsetZ()), "Z", left + (third + 4) * 2, top + 124, third, 12);
        button("区域：" + areaName(areaId), left, top + 149, panelWidth, () -> {
            captureDraft(); areaId = nextArea(areaId); refresh();
        });
        button("预览并回到世界", left, top + 175, third, () -> {
            if (captureDraft()) { editor.editDraft(draft); editor.preview(draft); EditorClient.show(null); }
        });
        button("保存设置", left + third + 4, top + 175, third, () -> {
            if (captureDraft()) {
                editor.request(EditorRequest.definition(EditorRequest.Action.UPDATE, draft), () -> {
                    editor.editDraft(null); editor.clearPreview(); navigate(Page.PLAYER);
                });
            }
        });
        button("取消更改", left + (third + 4) * 2, top + 175, third, () -> {
            editor.editDraft(null); editor.clearPreview(); navigate(Page.PLAYER);
        });
    }

    private boolean captureDraft() {
        if (draft == null || curve == null) { return true; }
        try {
            draft = new ScreenDefinition(draft.id(), name.getValue(), draft.dimension(), draft.geometry(), fit, draft.viewDistance(),
                    new ScreenStyle(Double.parseDouble(curve.getValue()), Integer.parseInt(segments.getValue()),
                            Double.parseDouble(dx.getValue()), Double.parseDouble(dy.getValue()), Double.parseDouble(dz.getValue())), areaId);
            return true;
        } catch (RuntimeException error) { editor.status("请检查名称、曲率、细分和位移数值。"); return false; }
    }

    private void operate(EditorRequest.Action action, double value) {
        ScreenState state = editor.current();
        if (state != null) { editor.request(EditorRequest.operation(action, state.definition().id(), value), null); }
    }
    private void seek(long offset) {
        ScreenState state = editor.current();
        if (state != null) {
            long current = state.playback().targetPositionMillis(editor.serverTime());
            operate(EditorRequest.Action.SEEK, Math.max(0, current + offset));
        }
    }
    private String areaName(UUID id) {
        for (ViewingArea area : editor.areas()) { if (area.id().equals(id)) { return area.name(); } }
        return "无";
    }
    private UUID nextArea(UUID id) {
        List<ViewingArea> areas = editor.areas().stream()
                .filter(a -> minecraft.level != null && a.dimension().value().equals(minecraft.level.dimension().identifier().toString())).toList();
        if (id == null) { return areas.isEmpty() ? null : areas.get(0).id(); }
        for (int i = 0; i < areas.size() - 1; i++) { if (areas.get(i).id().equals(id)) { return areas.get(i + 1).id(); } }
        return null;
    }
    private String playbackLabel(PlaybackStatus status) {
        return switch (status) {
            case PLAYING -> "播放中";
            case PAUSED -> "已暂停";
            case STOPPED -> "未播放";
            case BUFFERING -> "加载中";
            case FAILED -> "客户端无法播放";
        };
    }
    private void navigate(Page target) {
        if (page == Page.EDIT) { editor.editDraft(null); editor.clearPreview(); }
        EditorClient.show(new VidScreenMenu(editor, target));
    }
    public void refresh() { rebuildWidgets(); }
    @Override public void tick() { if (status != null) { status.setMessage(Component.literal(editor.status())); } }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() {
        if (page == Page.EDIT) { editor.editDraft(null); editor.clearPreview(); }
        EditorClient.show(null);
    }
    private StringWidget label(String text, int x, int y, int w) {
        return addRenderableWidget(new StringWidget(x, y, w, 12, Component.literal(text), font));
    }
    private Button button(String text, int x, int y, int w, Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(text), ignored -> action.run()).bounds(x, y, w, 20).build());
    }
    private EditBox field(String value, String hint, int x, int y, int w, int maxLength) {
        EditBox box = new EditBox(font, x, y, w, 20, Component.literal(hint));
        box.setMaxLength(maxLength); box.setValue(value); box.setHint(Component.literal(hint));
        return addRenderableWidget(box);
    }
}
