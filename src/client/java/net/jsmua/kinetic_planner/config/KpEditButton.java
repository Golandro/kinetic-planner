package net.jsmua.kinetic_planner.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * KP 自绘编辑按钮 - 嵌入地图全屏界面右上角，齿轮按钮左侧。
 *
 * <p>与 {@link KpGearButton} 同模式：自绘 POJO，由 Mixin 手动调用 render/mouseClicked。
 * 点击后进入 {@code KpEditorScreen} 编辑模式（等价于 {@code /kp edit} 命令）。
 *
 * <p>外观：16x16 半透明背景 + 白色铅笔图标 + hover tooltip "Edit Mode"。
 */
public class KpEditButton {

    private static final int SIZE = 16;
    private static final int BG_COLOR = 0x80000000;
    private static final int BG_HOVER_COLOR = 0xB0404040;
    private static final int ICON_COLOR = 0xFFFFFFFF;
    private static final int ACCENT_COLOR = 0xFF7C57D4;

    private final int x;
    private final int y;
    private final Runnable onClick;
    private boolean hovered;

    public KpEditButton(int x, int y, Runnable onClick) {
        this.x = x;
        this.y = y;
        this.onClick = onClick;
    }

    public void render(GuiGraphics gg, int mouseX, int mouseY) {
        hovered = isInside(mouseX, mouseY);

        int bgColor = hovered ? BG_HOVER_COLOR : BG_COLOR;
        gg.fill(x, y, x + SIZE, y + SIZE, bgColor);

        // 铅笔图标：从左上到右下的斜线 + 笔尖三角
        int cx = x + SIZE / 2;
        int cy = y + SIZE / 2;

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

        // hover 时底部 accent 条
        if (hovered) {
            gg.fill(x, y + SIZE - 1, x + SIZE, y + SIZE, ACCENT_COLOR);
        }

        if (hovered) {
            gg.renderTooltip(Minecraft.getInstance().font,
                Component.literal("Edit Mode"), mouseX, mouseY);
        }
    }

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
