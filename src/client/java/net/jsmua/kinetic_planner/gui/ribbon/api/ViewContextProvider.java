package net.jsmua.kinetic_planner.gui.ribbon.api;

import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Set;

/**
 * 视图上下文提供者 (spec §10.3)。
 *
 * <p>报告当前活动的视图上下文 ID 集合。RibbonBar 通过此接口决定哪些 ContextualTabGroup 应显示。
 *
 * <p><b>测试 seam:</b> KP 测试时可传入返回固定 Set 的 stub 实现, 无需启动真实视图系统。
 */
public interface ViewContextProvider {

    /** 当前活动的视图上下文 ID 集合 (可同时多个)。 */
    Set<ResourceLocation> getActiveContexts();

    /** 注册上下文变化监听器 (RibbonBar 在构造时注册)。 */
    void addContextChangeListener(Runnable listener);

    /** 移除监听器 (RibbonBar 销毁时调用, 避免泄漏)。 */
    void removeContextChangeListener(Runnable listener);

    /** 空实现: 无上下文场景的默认 provider, 返回空 Set, 不持有监听器。 */
    static ViewContextProvider empty() {
        return EmptyViewContextProvider.INSTANCE;
    }
}

/** empty() 的单例实现。 */
final class EmptyViewContextProvider implements ViewContextProvider {
    static final EmptyViewContextProvider INSTANCE = new EmptyViewContextProvider();

    @Override
    public Set<ResourceLocation> getActiveContexts() {
        return Collections.emptySet();
    }

    @Override
    public void addContextChangeListener(Runnable listener) {
        // no-op
    }

    @Override
    public void removeContextChangeListener(Runnable listener) {
        // no-op
    }
}
