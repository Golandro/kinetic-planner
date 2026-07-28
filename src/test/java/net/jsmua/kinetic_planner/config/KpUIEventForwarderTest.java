package net.jsmua.kinetic_planner.config;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.jsmua.kinetic_planner.gui.event.KpUIEventForwarder;
import net.minecraft.client.gui.GuiGraphics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * {@link KpUIEventForwarder} 单元测试 - 事件转发封装。
 *
 * <p>spec §10.1 + §10.2 定义。Mockito mock {@link ModularUI} 与
 * {@link ModularUI.ModularUIWidget}（非静态内部类，Mockito 5 支持）。
 *
 * <p>{@code ModularUIWidget} 实现了 MC 的 {@code GuiEventListener} 接口，
 * mock 时返回默认值（false/void），故 forwarder 的转发返回值需独立断言。
 */
@ExtendWith(MockitoExtension.class)
class KpUIEventForwarderTest {

    @Mock
    private ModularUI mockUI;

    @Mock
    private ModularUI.ModularUIWidget mockWidget;

    @Mock
    private GuiGraphics mockGg;

    @Test
    void mouseClicked_forwardsToWidget() {
        when(mockUI.getWidget()).thenReturn(mockWidget);
        when(mockWidget.mouseClicked(10.0, 20.0, 0)).thenReturn(true);

        var forwarder = new KpUIEventForwarder(mockUI);
        assertTrue(forwarder.mouseClicked(10.0, 20.0, 0));
        verify(mockWidget).mouseClicked(10.0, 20.0, 0);
    }

    @Test
    void mouseClicked_returnsFalseWhenWidgetReturnsFalse() {
        when(mockUI.getWidget()).thenReturn(mockWidget);
        when(mockWidget.mouseClicked(1.0, 2.0, 1)).thenReturn(false);

        var forwarder = new KpUIEventForwarder(mockUI);
        assertFalse(forwarder.mouseClicked(1.0, 2.0, 1));
        verify(mockWidget).mouseClicked(1.0, 2.0, 1);
    }

    @Test
    void mouseReleased_forwardsToWidget() {
        when(mockUI.getWidget()).thenReturn(mockWidget);
        when(mockWidget.mouseReleased(5.0, 6.0, 0)).thenReturn(true);

        var forwarder = new KpUIEventForwarder(mockUI);
        assertTrue(forwarder.mouseReleased(5.0, 6.0, 0));
        verify(mockWidget).mouseReleased(5.0, 6.0, 0);
    }

    @Test
    void mouseScrolled_forwardsToWidget() {
        when(mockUI.getWidget()).thenReturn(mockWidget);
        when(mockWidget.mouseScrolled(1.0, 2.0, 3.0, 4.0)).thenReturn(true);

        var forwarder = new KpUIEventForwarder(mockUI);
        assertTrue(forwarder.mouseScrolled(1.0, 2.0, 3.0, 4.0));
        verify(mockWidget).mouseScrolled(1.0, 2.0, 3.0, 4.0);
    }

    @Test
    void mouseMoved_forwardsToWidget() {
        when(mockUI.getWidget()).thenReturn(mockWidget);
        doNothing().when(mockWidget).mouseMoved(7.0, 8.0);

        var forwarder = new KpUIEventForwarder(mockUI);
        forwarder.mouseMoved(7.0, 8.0);
        verify(mockWidget).mouseMoved(7.0, 8.0);
    }

    @Test
    void render_forwardsToWidget() {
        when(mockUI.getWidget()).thenReturn(mockWidget);
        doNothing().when(mockWidget).render(mockGg, 10, 20, 0.5f);

        var forwarder = new KpUIEventForwarder(mockUI);
        forwarder.render(mockGg, 10, 20, 0.5f);
        verify(mockWidget).render(mockGg, 10, 20, 0.5f);
    }

    @Test
    void checkResize_reInitOnSizeChange() {
        var forwarder = new KpUIEventForwarder(mockUI);

        forwarder.checkResize(100, 100);  // 首次设置
        forwarder.checkResize(200, 100);  // 宽度变化
        verify(mockUI, times(1)).init(200, 100);
    }

    @Test
    void checkResize_noReInitOnSameSize() {
        var forwarder = new KpUIEventForwarder(mockUI);

        forwarder.checkResize(100, 100);
        forwarder.checkResize(100, 100);
        verify(mockUI, never()).init(anyInt(), anyInt());
    }

    @Test
    void checkResize_reInitOnHeightChange() {
        var forwarder = new KpUIEventForwarder(mockUI);

        forwarder.checkResize(100, 100);
        forwarder.checkResize(100, 200);  // 高度变化
        verify(mockUI, times(1)).init(100, 200);
    }
}
