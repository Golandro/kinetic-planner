package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.gui.ribbon.api.ViewContextProvider;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * KP ViewContextProvider 实现 (spec §10.5)。
 *
 * <p><b>无静态状态:</b> 完全通过构造注入的 {@link Supplier} 报告当前 active 上下文集合。
 * 调用方 ({@link net.jsmua.kinetic_planner.gui.editor.KpMapEditor}) 决定上下文来源
 * (例如: 编辑模式切换、视图状态、demo 切换标志等)。
 *
 * <p>监听器机制: {@link #notifyContextChanged()} 触发已注册监听器,
 * RibbonBar 收到通知后调用 {@link #getActiveContexts()} 取最新集合并调整 contextual tab 可见性。
 */
public final class KpViewContextProvider implements ViewContextProvider {

    private final Supplier<Set<ResourceLocation>> contextSupplier;
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

    public KpViewContextProvider(Supplier<Set<ResourceLocation>> contextSupplier) {
        this.contextSupplier = contextSupplier;
    }

    @Override
    public Set<ResourceLocation> getActiveContexts() {
        return contextSupplier.get();
    }

    @Override
    public void addContextChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    @Override
    public void removeContextChangeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /** 通知监听器上下文已变化 (RibbonBar 调整 contextual tab 可见性)。 */
    public void notifyContextChanged() {
        listeners.forEach(Runnable::run);
    }
}
