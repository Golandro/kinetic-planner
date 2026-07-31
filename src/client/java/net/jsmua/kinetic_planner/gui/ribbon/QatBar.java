package net.jsmua.kinetic_planner.gui.ribbon;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.jsmua.kinetic_planner.gui.ribbon.api.ButtonAction;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonToolDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Function;

/**
 * QAT UI 实现 (spec §7.4)。
 *
 * <p><b>图标优先渲染:</b> 工具定义有 icon 时, 按钮仅显示 icon (无文字);
 * 无 icon 时, 按钮显示 displayName 的首字母。
 * 状态逻辑由 {@code DefaultQuickAccessToolbar} 持有, 本类仅负责 UI 渲染。
 *
 * <p>空状态显示淡化星标提示 ({@code kp-ribbon-qat-empty} class)。
 */
public final class QatBar extends UIElement {

    public QatBar() {
        super();
        addClass("kp-ribbon-qat");
        layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
        });
    }

    /**
     * 用指定工具 ID 列表 + 查找回调重建按钮。
     *
     * @param toolIds 工具 ID 列表 (顺序即渲染顺序)
     * @param lookup  ID -> RibbonToolDefinition 查找回调; 返回 null 跳过
     */
    public void rebuild(List<ResourceLocation> toolIds,
                        Function<ResourceLocation, RibbonToolDefinition> lookup) {
        clearAllChildren();
        if (toolIds.isEmpty()) {
            // 空状态: 显示淡色星标提示
            var hint = new Button();
            hint.setText(Component.literal("★"));
            hint.addClass("kp-ribbon-qat-empty");
            addChild(hint);
            return;
        }
        for (var id : toolIds) {
            var def = lookup.apply(id);
            if (def == null) continue;
            var action = def.getAction();
            if (!(action instanceof ButtonAction btnAction)) continue;
            var btn = new Button();
            // 图标优先: 有 icon 用 icon, 否则用 displayName 首字母
            var iconOpt = def.getIcon();
            if (iconOpt.isPresent()) {
                btn.noText();
                btn.addPreIcon(iconOpt.get());
            } else {
                btn.setText(firstChar(def.getDisplayName()));
            }
            btn.setOnClick(event -> btnAction.command().execute());
            btn.addClass("kp-ribbon-tool");
            addChild(btn);
        }
    }

    /** 取 Component 文本的第一个字符 (用于无 icon 时的占位显示)。 */
    private static Component firstChar(Component displayName) {
        var s = displayName.getString();
        if (s == null || s.isEmpty()) {
            return Component.literal("?");
        }
        return Component.literal(s.substring(0, 1));
    }
}
