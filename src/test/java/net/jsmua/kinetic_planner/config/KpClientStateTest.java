package net.jsmua.kinetic_planner.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KpClientState} 单元测试 - 配置面板可见性状态管理。
 *
 * <p>spec §10.1 + §10.2 定义。{@code KpClientState} 在 client sourceSet
 * 但无 MC 依赖，可在纯 JVM 测试。
 */
class KpClientStateTest {

    @BeforeEach
    void resetState() {
        // 静态状态需在每条用例前重置，避免用例间污染
        KpClientState.setConfigPanelVisible(false);
    }

    @Test
    void defaultInvisible() {
        KpClientState.setConfigPanelVisible(false);
        assertFalse(KpClientState.isConfigPanelVisible());
    }

    @Test
    void toggleChangesState() {
        KpClientState.setConfigPanelVisible(false);
        KpClientState.toggleConfigPanel();
        assertTrue(KpClientState.isConfigPanelVisible());
        KpClientState.toggleConfigPanel();
        assertFalse(KpClientState.isConfigPanelVisible());
    }

    @Test
    void setVisibleUpdatesState() {
        KpClientState.setConfigPanelVisible(true);
        assertTrue(KpClientState.isConfigPanelVisible());
        KpClientState.setConfigPanelVisible(false);
        assertFalse(KpClientState.isConfigPanelVisible());
    }
}
