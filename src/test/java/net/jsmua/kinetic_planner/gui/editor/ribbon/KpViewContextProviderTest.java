package net.jsmua.kinetic_planner.gui.editor.ribbon;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KpViewContextProvider 监听器机制测试。
 *
 * <p><b>测试策略:</b> 首版 provider 的 getActiveContexts() 返回空 Set,
 * 逻辑不依赖 KpEditorScreen。测试用 {@code new KpViewContextProvider(null)} 构造,
 * 避免 mock(KpEditorScreen.class) 触发 Screen 父类 clinit 风险 (spec §13 LDLib2 clinit 陷阱)。
 *
 * <p>覆盖: 空上下文 + 监听器注册/通知/移除 + 多监听器广播。
 */
class KpViewContextProviderTest {

    @Test
    void emptyContextsByDefault() {
        var provider = new KpViewContextProvider(null);

        assertTrue(provider.getActiveContexts().isEmpty(),
            "首版无多视图, getActiveContexts 返回空 Set");
    }

    @Test
    void addListenerReceivesNotify() {
        var provider = new KpViewContextProvider(null);
        var counter = new AtomicInteger(0);
        var listener = (Runnable) () -> counter.incrementAndGet();

        provider.addContextChangeListener(listener);
        provider.notifyContextChanged();

        assertEquals(1, counter.get(), "notifyContextChanged 必须触发已注册监听器");
    }

    @Test
    void removedListenerNotNotified() {
        var provider = new KpViewContextProvider(null);
        var counter = new AtomicInteger(0);
        var listener = (Runnable) () -> counter.incrementAndGet();

        provider.addContextChangeListener(listener);
        provider.removeContextChangeListener(listener);
        provider.notifyContextChanged();

        assertEquals(0, counter.get(), "移除后的监听器不应被通知");
    }

    @Test
    void multipleListenersAllNotified() {
        var provider = new KpViewContextProvider(null);
        var c1 = new AtomicInteger(0);
        var c2 = new AtomicInteger(0);

        provider.addContextChangeListener(() -> c1.incrementAndGet());
        provider.addContextChangeListener(() -> c2.incrementAndGet());
        provider.notifyContextChanged();

        assertEquals(1, c1.get());
        assertEquals(1, c2.get());
    }
}
