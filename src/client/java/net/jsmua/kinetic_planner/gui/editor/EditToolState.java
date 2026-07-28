package net.jsmua.kinetic_planner.gui.editor;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

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
     */
    public enum Tool {
        /** 地图导航（平移/缩放），事件转发给 guiMap */
        NAVIGATION,
        /** 选择节点/边，CADRenderEngine 命中检测 */
        SELECT,
        /** 绘制直线 */
        DRAW_LINE,
        /** 绘制三次贝塞尔 */
        DRAW_BEZIER,
        /** 捕捉模式，鼠标移动高亮可捕捉点 */
        SNAP
    }

    private static final EditToolState INSTANCE = new EditToolState();

    private Tool currentTool = Tool.NAVIGATION;
    private final Set<UUID> selectedNodes = new LinkedHashSet<>();

    private EditToolState() {}

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
