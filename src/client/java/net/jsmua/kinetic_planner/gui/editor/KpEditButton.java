package net.jsmua.kinetic_planner.gui.editor;

import net.jsmua.kinetic_planner.gui.widgets.KpIconButton;
import net.minecraft.client.gui.GuiGraphics;

/**
 * KP 自绘编辑按钮 - 嵌入地图全屏界面右上角，齿轮按钮左侧。
 *
 * <p>与 {@link net.jsmua.kinetic_planner.gui.config.KpGearButton} 同模式：
 * 自绘 POJO，由 Mixin 手动调用 render/mouseClicked。
 * 点击后进入 {@code KpEditorScreen} 编辑模式（等价于 {@code /kp edit} 命令）。
 *
 * <p>外观：16x16 半透明背景 + 白色铅笔图标 + hover tooltip "Edit Mode"。
 */
public class KpEditButton extends KpIconButton {

    public KpEditButton(int x, int y, Runnable onClick) {
        super(x, y, onClick, "Edit Mode");
    }

    @Override
    protected void drawIcon(GuiGraphics gg, int cx, int cy) {
        // 铅笔主体（斜线：从 (cx-3, cy+3) 到 (cx+3, cy-3)）
        for (int i = 0; i < 7; i++) {
            int px = cx - 3 + i;
            int py = cy + 3 - i;
            gg.fill(px, py, px + 1, py + 1, ICON_COLOR);
        }

        // 笔尖（右上端，短横线）
        gg.fill(cx + 3, cy - 4, cx + 5, cy - 3, ICON_COLOR);
        // 笔尾（左下端，短竖线）
        gg.fill(cx - 5, cy + 3, cx - 4, cy + 5, ICON_COLOR);
    }
}
