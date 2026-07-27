package net.jsmua.kinetic_planner.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * KP 自绘齿轮按钮 - 嵌入地图全屏界面右上角。
 *
 * <p>spec §9.4：保留自绘方案（不使用 LDLib2 {@code Button}），因为齿轮按钮在
 * LDLib2 UI 树外部，不需要布局引擎。点击切换 {@link KpClientState} 面板可见性。
 *
 * <p>外观：16x16 半透明背景 + 白色齿轮轮廓 + hover tooltip "Kinetic Planner"。
 *
 * <p>不是 MC {@code AbstractWidget} 子类（不在 Screen.addWidget 体系中），
 * 由 Mixin/Event 手动调用 {@link #render} 和 {@link #mouseClicked}。
 */
public class KpGearButton {

    private static final int SIZE = 16;
    private static final int BG_COLOR = 0x80000000;
    private static final int BG_HOVER_COLOR = 0xB0404040;
    private static final int GEAR_COLOR = 0xFFFFFFFF;

    private final int x;
    private final int y;
    private final Runnable onClick;
    private boolean hovered;

    public KpGearButton(int x, int y, Runnable onClick) {
        this.x = x;
        this.y = y;
        this.onClick = onClick;
    }

    /**
     * 渲染齿轮按钮。
     */
    public void render(GuiGraphics gg, int mouseX, int mouseY) {
        hovered = isInside(mouseX, mouseY);

        // 半透明背景
        int bgColor = hovered ? BG_HOVER_COLOR : BG_COLOR;
        gg.fill(x, y, x + SIZE, y + SIZE, bgColor);

        // 齿轮图标：8 条线段从中心向外辐射 + 中心圆
        int cx = x + SIZE / 2;
        int cy = y + SIZE / 2;
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
            gg.fill(minX, minY, maxX, maxY, GEAR_COLOR);
        }

        // 中心圆（2x2 填充）
        gg.fill(cx - 1, cy - 1, cx + 1, cy + 1, GEAR_COLOR);

        // 悬停 tooltip
        if (hovered) {
            gg.renderTooltip(Minecraft.getInstance().font,
                Component.literal("Kinetic Planner"), mouseX, mouseY);
        }
    }

    /**
     * 处理鼠标点击。
     *
     * @return true 如果点击在按钮内（消费事件）
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isInside((int) mouseX, (int) mouseY)) {
            onClick.run();
            return true;
        }
        return false;
    }

    public boolean isHovered() {
        return hovered;
    }

    private boolean isInside(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
    }
}
