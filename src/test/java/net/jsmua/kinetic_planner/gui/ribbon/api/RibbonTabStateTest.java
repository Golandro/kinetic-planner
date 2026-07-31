package net.jsmua.kinetic_planner.gui.ribbon.api;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RibbonTabStateTest {

    private RibbonTabDefinition makeTab(TabDisplayMode defaultMode) {
        return new SimpleRibbonTabDefinition(
            ResourceLocation.fromNamespaceAndPath("kp", "test"),
            Component.literal("Test"),
            Optional.empty(),
            100,
            List.of(),
            defaultMode,
            true,
            Optional.empty()
        );
    }

    @Test
    void pinnedModeHasContentVisible() {
        var state = new RibbonTabState(makeTab(TabDisplayMode.PINNED));
        state.setDisplayMode(TabDisplayMode.PINNED);
        assertTrue(state.getContentVisible(), "PINNED -> contentVisible 必须 = true");
        assertTrue(state.isHeaderVisible(), "PINNED -> header 必须 visible");
    }

    @Test
    void hiddenModeHidesContentAndHeader() {
        var state = new RibbonTabState(makeTab(TabDisplayMode.PINNED));
        state.setDisplayMode(TabDisplayMode.HIDDEN);
        assertFalse(state.getContentVisible(), "HIDDEN -> contentVisible 必须 = false");
        assertFalse(state.isHeaderVisible(), "HIDDEN -> header 必须 hidden");
    }

    @Test
    void floatingModeToggleContentVisible() {
        var state = new RibbonTabState(makeTab(TabDisplayMode.PINNED));
        state.setDisplayMode(TabDisplayMode.FLOATING);
        // FLOATING 初始 contentVisible = false (setDisplayMode 只在 PINNED 时设 true)
        assertFalse(state.getContentVisible());
        state.toggleContentVisible();
        assertTrue(state.getContentVisible(), "toggle 后 contentVisible = true");
        state.toggleContentVisible();
        assertFalse(state.getContentVisible(), "再 toggle 回 false");
    }

    @Test
    void contextualModeRespectsContextActive() {
        var state = new RibbonTabState(makeTab(TabDisplayMode.CONTEXTUAL));
        // 初始未激活
        assertFalse(state.isHeaderVisible(), "CONTEXTUAL 未激活 -> header hidden");

        state.setContextActive(true);
        assertTrue(state.isHeaderVisible(), "CONTEXTUAL 激活 -> header visible");
        // 激活后 contentVisible 应遵循 PINNED 子模式 = true (spec §10.2 getActiveSubMode 默认 PINNED)
        assertTrue(state.getContentVisible(), "CONTEXTUAL 激活后 contentVisible 遵循 PINNED 子模式 = true");

        state.setContextActive(false);
        assertFalse(state.isHeaderVisible(), "CONTEXTUAL 停用 -> header hidden");
        assertFalse(state.getContentVisible(), "CONTEXTUAL 停用 -> contentVisible = false");
    }

    @Test
    void setContextActiveNoOpOnNonContextualMode() {
        var state = new RibbonTabState(makeTab(TabDisplayMode.PINNED));
        state.setContextActive(true);
        // PINNED 模式不受 contextActive 影响
        assertTrue(state.isHeaderVisible(), "PINNED 模式 setContextActive 不影响可见性");
    }

    @Test
    void setDisplayModeOnContextualNoOp() {
        var state = new RibbonTabState(makeTab(TabDisplayMode.CONTEXTUAL));
        // CONTEXTUAL 模式不应响应 setDisplayMode (用户不可手动覆盖)
        state.setDisplayMode(TabDisplayMode.PINNED);
        assertEquals(TabDisplayMode.CONTEXTUAL, state.getDisplayMode(),
            "CONTEXTUAL 模式 setDisplayMode 必须无效果");
    }
}
