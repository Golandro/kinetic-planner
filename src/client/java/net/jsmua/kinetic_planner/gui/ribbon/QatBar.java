package net.jsmua.kinetic_planner.gui.ribbon;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
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
 * <p>图标按钮列表, 始终 SMALL 尺寸。空状态显示淡色星标图标。
 * 状态逻辑由 DefaultQuickAccessToolbar 持有, 本类仅负责 UI 渲染。
 */
public final class QatBar extends UIElement {

    public QatBar() {
        super();
        addClass("kp-ribbon-qat");
        layout(layout -> {
            layout.flexDirection(FlexDirection.ROW);
        });
    }

    /** 用指定工具 ID 列表 + 查找回调重建按钮。 */
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
            btn.setText(def.getDisplayName());
            btn.setOnClick(event -> btnAction.command().execute());
            addChild(btn);
        }
    }
}
