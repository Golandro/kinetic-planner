package net.jsmua.kinetic_planner.editor;

import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具定义元数据一致性测试（审计 R2 Critical 修复）。
 *
 * <p>验证 Tool 枚举的 keyBinding 元数据满足：
 * <ul>
 *   <li>每个工具有唯一的 keyBinding（无冲突）</li>
 *   <li>keyBinding 为有效的 GLFW 键码</li>
 *   <li>displayName 和 keyLabel 非空</li>
 * </ul>
 */
class ToolKeyBindingConsistencyTest {

    @Test
    void eachToolHasUniqueKeyBinding() {
        Set<Integer> seen = new HashSet<>();
        for (var tool : EditToolState.Tool.values()) {
            int key = tool.getKeyBinding();
            assertTrue(key > 0, tool.name() + " 的 keyBinding 必须为正数");
            assertTrue(seen.add(key), tool.name() + " 的 keyBinding " + key + " 与其他工具冲突");
        }
    }

    @Test
    void eachToolHasDisplayNameAndKeyLabel() {
        for (var tool : EditToolState.Tool.values()) {
            assertNotNull(tool.getDisplayName(), tool.name() + " 必须有 displayName");
            assertFalse(tool.getDisplayName().isBlank(), tool.name() + " 的 displayName 不能为空");
            assertNotNull(tool.getKeyLabel(), tool.name() + " 必须有 keyLabel");
            assertFalse(tool.getKeyLabel().isBlank(), tool.name() + " 的 keyLabel 不能为空");
        }
    }

    @Test
    void navigationKeyIsP() {
        // spec §4.4：P=Pan(NAVIGATION)
        assertEquals(GLFW.GLFW_KEY_P, EditToolState.Tool.NAVIGATION.getKeyBinding());
    }

    @Test
    void selectKeyIsV() {
        // spec §4.4：V=Select
        assertEquals(GLFW.GLFW_KEY_V, EditToolState.Tool.SELECT.getKeyBinding());
    }

    @Test
    void drawLineKeyIsL() {
        assertEquals(GLFW.GLFW_KEY_L, EditToolState.Tool.DRAW_LINE.getKeyBinding());
    }

    @Test
    void drawBezierKeyIsB() {
        assertEquals(GLFW.GLFW_KEY_B, EditToolState.Tool.DRAW_BEZIER.getKeyBinding());
    }

    @Test
    void snapKeyIsS() {
        assertEquals(GLFW.GLFW_KEY_S, EditToolState.Tool.SNAP.getKeyBinding());
    }
}
