package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.gui.editor.KpEditorScreen;
import net.jsmua.kinetic_planner.gui.ribbon.api.ViewContextProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * KP ViewContextProvider 实现 (spec §10.5)。
 *
 * <p><b>首版:</b> getActiveContexts() 返回空 Set (KP 当前只有单一 Map Editor 视图, 无多视图场景)。
 * 监听器机制完整实现, 未来多视图时在 getActiveContexts() 中查询 editorScreen 状态。
 *
 * <p>构造注入 KpEditorScreen, 不调 getInstance()。
 */
public final class KpViewContextProvider implements ViewContextProvider {

    private final KpEditorScreen editorScreen;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    public KpViewContextProvider(KpEditorScreen editorScreen) {
        this.editorScreen = editorScreen;
    }

    @Override
    public Set<ResourceLocation> getActiveContexts() {
        // 首版: 无多视图, 返回空 Set
        // 未来: 根据 editorScreen 状态返回对应上下文 ID
        //   if (editorScreen.isMapEditorActive()) ctx.add(RL("kp", "map_editor"));
        //   if (editorScreen.isTrackGraphEditorActive()) ctx.add(RL("kp", "track_graph_editor"));
        return Collections.emptySet();
    }

    @Override
    public void addContextChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    @Override
    public void removeContextChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /** KP 编辑模式切换时调用 (未来多视图扩展点)。 */
    public void notifyContextChanged() {
        listeners.forEach(Runnable::run);
    }
}
