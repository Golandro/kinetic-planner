package net.jsmua.kinetic_planner.gui.widgets;

import net.jsmua.kinetic_planner.gui.theme.KpTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * KP 自绘图标按钮抽象基类 - 嵌入地图全屏界面。
 *
 * <p>封装统一的 16x16 半透明背景、hover 检测、tooltip 机制和点击路由。
 * 子类只需实现 {@link #drawIcon(GuiGraphics, int, int)} 绘制图标。
 *
 * <p>不是 MC {@code AbstractWidget} 子类（不在 Screen.addWidget 体系中），
 * 由 Mixin/Event 手动调用 {@link #render} 和 {@link #mouseClicked}。
 */
public abstract class KpIconButton {

    protected static final int SIZE = 16;
    protected static final int BG_COLOR = KpTheme.ICON_BUTTON_BG;
    protected static final int BG_HOVER_COLOR = KpTheme.ICON_BUTTON_HOVER;
    protected static final int ICON_COLOR = KpTheme.ICON_COLOR;
    protected static final int ACCENT_COLOR = KpTheme.ACCENT;

    protected final int x;
    protected final int y;
    protected final Runnable onClick;
    protected final String tooltipText;
    protected boolean hovered;

    protected KpIconButton(int x, int y, Runnable onClick, String tooltipText) {
        this.x = x;
        this.y = y;
        this.onClick = onClick;
        this.tooltipText = tooltipText;
    }

    /**
     * 渲染按钮：背景 + 图标 + hover accent + tooltip。
     */
    public void render(GuiGraphics gg, int mouseX, int mouseY) {
        hovered = isInside(mouseX, mouseY);

        int bgColor = hovered ? BG_HOVER_COLOR : BG_COLOR;
        gg.fill(x, y, x + SIZE, y + SIZE, bgColor);

        int cx = x + SIZE / 2;
        int cy = y + SIZE / 2;
        drawIcon(gg, cx, cy);

        if (hovered) {
            gg.fill(x, y + SIZE - 1, x + SIZE, y + SIZE, ACCENT_COLOR);
            gg.renderTooltip(Minecraft.getInstance().font,
                Component.literal(tooltipText), mouseX, mouseY);
        }
    }

    /**
     * 子类实现：在中心点 (cx, cy) 绘制图标。
     */
    protected abstract void drawIcon(GuiGraphics gg, int cx, int cy);

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

    protected boolean isInside(int mouseX, int mouseY) {
        return mouseX >= x && mouseX < x + SIZE && mouseY >= y && mouseY < y + SIZE;
    }
}
