package net.jsmua.kinetic_planner.gui.editor;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.lwjgl.glfw.GLFW;

/**
 * 编辑会话状态（spec §4.3 + §6.8）。
 *
 * <p>管理当前工具、选择集、绘制状态。仅编辑模式活跃，与全局 {@link net.jsmua.kinetic_planner.config.KpClientState}
 * 生命周期不同（后者是 Mixin 守卫读取的全局模式标志）。
 *
 * <p>单例模式：编辑模式开始时通过 {@link #reset()} 清空状态。
 */
public final class EditToolState {

    /**
     * 编辑工具枚举（spec §4.3 表格）。
     *
     * <p>每个工具携带 displayName/keyBinding/keyLabel 元数据，消除散弹式修改（审计 R2）：
     * 添加新工具只需在此枚举追加一行，KpEditorScreen/KpRibbonBar/ToolPanelView 自动遍历。
     */
    public enum Tool {
        /** 地图导航（平移/缩放），事件转发给 guiMap */
        NAVIGATION("Pan", GLFW.GLFW_KEY_P, "P"),
        /** 选择节点/边，CADRenderEngine 命中检测 */
        SELECT("Select", GLFW.GLFW_KEY_V, "V"),
        /** 绘制直线 */
        DRAW_LINE("Line", GLFW.GLFW_KEY_L, "L"),
        /** 绘制三次贝塞尔 */
        DRAW_BEZIER("Bezier", GLFW.GLFW_KEY_B, "B"),
        /** 捕捉模式，鼠标移动高亮可捕捉点 */
        SNAP("Snap", GLFW.GLFW_KEY_S, "S");

        private final String displayName;
        private final int keyBinding;
        private final String keyLabel;

        Tool(String displayName, int keyBinding, String keyLabel) {
            this.displayName = displayName;
            this.keyBinding = keyBinding;
            this.keyLabel = keyLabel;
        }

        public String getDisplayName() { return displayName; }
        public int getKeyBinding() { return keyBinding; }
        public String getKeyLabel() { return keyLabel; }

        /**
         * 按 GLFW 键码查找工具。
         *
         * @param keyCode GLFW 键码
         * @return 匹配的工具；无匹配返回 null
         */
        public static Tool fromKeyCode(int keyCode) {
            for (Tool tool : values()) {
                if (tool.keyBinding == keyCode) return tool;
            }
            return null;
        }
    }

    private static final EditToolState INSTANCE = new EditToolState();

    private Tool currentTool = Tool.NAVIGATION;
    private final Set<UUID> selectedNodes = new LinkedHashSet<>();

    private EditToolState() {}

    /**
     * 包级构造器，仅供测试创建独立实例（不污染单例）。
     *
     * <p>审计 R5 修复：测试可通过此构造器创建隔离的 EditToolState 实例，
     * 验证工具切换/选择集行为而不影响全局单例状态。
     */
    EditToolState(boolean forTesting) {
        // forTesting 参数仅用于区分签名，无实际逻辑
    }

    public static EditToolState getInstance() {
        return INSTANCE;
    }

    public Tool getCurrentTool() {
        return currentTool;
    }

    public void setCurrentTool(Tool tool) {
        this.currentTool = tool;
    }

    public Set<UUID> getSelectedNodes() {
        return selectedNodes;
    }

    public void addSelectedNode(UUID nodeId) {
        selectedNodes.add(nodeId);
    }

    public void clearSelection() {
        selectedNodes.clear();
    }

    /**
     * 重置全部状态（编辑模式开始时调用）。
     */
    public void reset() {
        currentTool = Tool.NAVIGATION;
        selectedNodes.clear();
    }
}
