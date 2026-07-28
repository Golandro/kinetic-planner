package net.jsmua.kinetic_planner.gui.config;

import net.jsmua.kinetic_planner.gui.widgets.KpIconButton;
import net.minecraft.client.gui.GuiGraphics;

/**
 * KP 自绘齿轮按钮 - 嵌入地图全屏界面右上角。
 *
 * <p>spec §9.4：保留自绘方案（不使用 LDLib2 {@code Button}），因为齿轮按钮在
 * LDLib2 UI 树外部，不需要布局引擎。点击切换 {@code KpClientState} 面板可见性。
 *
 * <p>外观：16x16 半透明背景 + 白色齿轮轮廓 + hover tooltip "Kinetic Planner"。
 */
public class KpGearButton extends KpIconButton {

    public KpGearButton(int x, int y, Runnable onClick) {
        super(x, y, onClick, "Kinetic Planner");
    }

    @Override
    protected void drawIcon(GuiGraphics gg, int cx, int cy) {
        // 齿轮图标：8 条线段从中心向外辐射 + 中心圆
        int outerR = 5;
        int innerR = 2;

        for (int i = 0; i < 8; i++) {
            double angle = 2 * Math.PI * i / 8;
            int x1 = (int) (cx + Math.cos(angle) * innerR);
            int y1 = (int) (cy + Math.sin(angle) * innerR);
            int x2 = (int) (cx + Math.cos(angle) * outerR);
            int y2 = (int) (cy + Math.sin(angle) * outerR);
            int minX = Math.min(x1, x2);
            int minY = Math.min(y1, y2);
            int maxX = Math.max(x1, x2) + 1;
            int maxY = Math.max(y1, y2) + 1;
            gg.fill(minX, minY, maxX, maxY, ICON_COLOR);
        }

        // 中心圆（2x2 填充）
        gg.fill(cx - 1, cy - 1, cx + 1, cy + 1, ICON_COLOR);
    }
}
