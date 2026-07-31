package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * KpViewContextProvider 监听器机制 + supplier 注入测试。
 *
 * <p><b>无静态状态:</b> provider 完全靠构造注入的 supplier 报告 active contexts;
 * 不再有静态 demoContextActive 标志或全局 INSTANCES 列表。
 *
 * <p>测试用 {@code new KpViewContextProvider(supplier)} 构造, 不依赖 KpEditorScreen。
 */
class KpViewContextProviderTest {

    private static final ResourceLocation CTX_ID =
        ResourceLocation.fromNamespaceAndPath("kp", "demo_context_group");

    /** supplier 返回固定集合, 验证 getActiveContexts 直接返回。 */
    @Test
    void getActiveContextsReturnsSupplierResult() {
        var provider = new KpViewContextProvider(() -> Set.of(CTX_ID));
        var ctx = provider.getActiveContexts();
        assertEquals(1, ctx.size());
        assertTrue(ctx.contains(CTX_ID));
    }

    /** supplier 返回空集合, 验证 getActiveContexts 返回空。 */
    @Test
    void emptySupplierReturnsEmptyContexts() {
        var provider = new KpViewContextProvider(Set::of);
        assertTrue(provider.getActiveContexts().isEmpty());
    }

    /** supplier 可变, 调用 notifyContextChanged 后 getActiveContexts 反映新值。 */
    @Test
    void mutableSupplierReflectsChangesAcrossCalls() {
        var holder = new java.util.HashSet<ResourceLocation>();
        var provider = new KpViewContextProvider(() -> java.util.Collections.unmodifiableSet(holder));

        assertTrue(provider.getActiveContexts().isEmpty());
        holder.add(CTX_ID);
        assertTrue(provider.getActiveContexts().contains(CTX_ID));
    }

    /** addListenerReceivesNotify: 注册监听器后调用 notifyContextChanged 必须触发。 */
    @Test
    void addListenerReceivesNotify() {
        var provider = new KpViewContextProvider(Set::of);
        var counter = new AtomicInteger(0);
        var listener = (Runnable) () -> counter.incrementAndGet();

        provider.addContextChangeListener(listener);
        provider.notifyContextChanged();

        assertEquals(1, counter.get(), "notifyContextChanged 必须触发已注册监听器");
    }

    /** removedListenerNotNotified: 移除监听器后调用 notifyContextChanged 不应触发。 */
    @Test
    void removedListenerNotNotified() {
        var provider = new KpViewContextProvider(Set::of);
        var counter = new AtomicInteger(0);
        var listener = (Runnable) () -> counter.incrementAndGet();

        provider.addContextChangeListener(listener);
        provider.removeContextChangeListener(listener);
        provider.notifyContextChanged();

        assertEquals(0, counter.get(), "移除后的监听器不应被通知");
    }

    /** multipleListenersAllNotified: 多个监听器都被通知。 */
    @Test
    void multipleListenersAllNotified() {
        var provider = new KpViewContextProvider(Set::of);
        var c1 = new AtomicInteger(0);
        var c2 = new AtomicInteger(0);

        provider.addContextChangeListener(() -> c1.incrementAndGet());
        provider.addContextChangeListener(() -> c2.incrementAndGet());
        provider.notifyContextChanged();

        assertEquals(1, c1.get());
        assertEquals(1, c2.get());
    }
}
