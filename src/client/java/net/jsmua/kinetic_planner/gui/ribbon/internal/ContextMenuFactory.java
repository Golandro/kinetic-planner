package net.jsmua.kinetic_planner.gui.ribbon.internal;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Menu;
import com.lowdragmc.lowdraglib2.gui.util.ITreeNode;
import net.jsmua.kinetic_planner.gui.ribbon.api.QuickAccessToolbar;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabDefinition;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinition;

import java.util.Collections;

/** 右键菜单构建 (spec §8)。 */
public final class ContextMenuFactory {

    private ContextMenuFactory() {}

    /** Tool 右键菜单 (spec §8.2)。 */
    public static Menu<String, Void> forTool(RibbonToolDefinition tool, QuickAccessToolbar qat) {
        // 简化: 返回空 Menu, 完整实现见 spec §8.2
        return new Menu<>(emptyRoot(), key -> new UIElement());
    }

    /** Tab 右键菜单 (spec §8.3)。 */
    public static Menu<String, Void> forTab(RibbonTabDefinition tab) {
        // CONTEXTUAL tab 不显示固定/浮动/隐藏选项
        // 简化: 返回空 Menu, 完整实现见 spec §8.3
        return new Menu<>(emptyRoot(), key -> new UIElement());
    }

    /** 空树根节点 (无子节点)。 */
    private static ITreeNode<String, Void> emptyRoot() {
        return new ITreeNode<>() {
            @Override
            public int getDimension() { return 0; }

            @Override
            public String getKey() { return "root"; }

            @Override
            public Void getContent() { return null; }

            @Override
            public ITreeNode<String, Void> getParent() { return null; }

            @Override
            public java.util.List<? extends ITreeNode<String, Void>> getChildren() {
                return Collections.emptyList();
            }
        };
    }
}
