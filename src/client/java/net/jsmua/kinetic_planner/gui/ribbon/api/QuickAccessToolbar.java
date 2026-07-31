package net.jsmua.kinetic_planner.gui.ribbon.api;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * 快速访问工具栏接口 (spec §7.1)。
 *
 * <p>QAT 是独立一等公民, 不是 RibbonHeaderComponent 的特化。
 * UI 元素放置在 RibbonHeader 的 LEADING 侧, 始终排在所有 LEADING 侧组件最左侧。
 */
public interface QuickAccessToolbar {
    /** QAT 中的工具 ID 列表 (持久化到 PreferenceStore)。 */
    List<ResourceLocation> getToolIds();

    /** 添加工具到 QAT (只接受 Ribbon tool ID)。 */
    boolean addTool(ResourceLocation toolId);

    /** 从 QAT 移除。 */
    boolean removeTool(ResourceLocation toolId);

    /** 是否包含指定工具。 */
    boolean containsTool(ResourceLocation toolId);

    /** 构建 QAT 的 UIElement (图标按钮列表)。 */
    UIElement createElement();

    /** 从 PreferenceStore 加载偏好。 */
    void loadPreferences(RibbonPreferenceStore store);

    /** 保存偏好到 PreferenceStore。 */
    void savePreferences(RibbonPreferenceStore store);
}
