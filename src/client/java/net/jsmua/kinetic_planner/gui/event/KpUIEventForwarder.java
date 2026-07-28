package net.jsmua.kinetic_planner.gui.event;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.minecraft.client.gui.GuiGraphics;

/**
 * KP UI 事件转发器 - 封装 {@link ModularUI.ModularUIWidget} 的全部事件方法转发。
 *
 * <p>spec §9.2：供 Xaero Mixin 和 JM Event 共用，避免代码重复。
 * 因为 {@code ModularUIWidget} 是 {@code ModularUI} 的非静态内部类，
 * 不继承 MC {@code AbstractWidget}，事件不会自动由 Screen 转发，
 * 必须手动调用每个事件方法。
 *
 * <p>该类在 client sourceSet，引用了 LDLib2 {@link ModularUI} 与 MC {@link GuiGraphics}，
 * 故通过 Mockito mock 依赖测试（{@code KpUIEventForwarderTest}）。
 */
public final class KpUIEventForwarder {

    private final ModularUI ui;
    private boolean sizeInitialized = false;
    private int lastWidth = -1;
    private int lastHeight = -1;

    public KpUIEventForwarder(ModularUI ui) {
        this.ui = ui;
    }

    /**
     * 渲染 UI 树。Mixin 在外部 {@code GuiGraphics} 上调用。
     *
     * @param gg           外部 GuiGraphics（来自地图 Screen）
     * @param mouseX       鼠标 X
     * @param mouseY       鼠标 Y
     * @param partialTick  部分帧时间
     */
    public void render(GuiGraphics gg, int mouseX, int mouseY, float partialTick) {
        ui.getWidget().render(gg, mouseX, mouseY, partialTick);
    }

    /**
     * 鼠标移动转发 - 用于 hover 状态更新。
     */
    public void mouseMoved(double mouseX, double mouseY) {
        ui.getWidget().mouseMoved(mouseX, mouseY);
    }

    /**
     * 鼠标点击转发。
     *
     * @return true 如果 UI 树消费了事件
     */
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return ui.getWidget().mouseClicked(mouseX, mouseY, button);
    }

    /**
     * 鼠标释放转发。
     */
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return ui.getWidget().mouseReleased(mouseX, mouseY, button);
    }

    /**
     * 鼠标拖拽转发。
     */
    public boolean mouseDragged(double mouseX, double mouseY, int button,
                                 double dragX, double dragY) {
        return ui.getWidget().mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    /**
     * 鼠标滚轮转发。
     */
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return ui.getWidget().mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    /**
     * 按键转发。
     */
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return ui.getWidget().keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * 按键释放转发。
     */
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        return ui.getWidget().keyReleased(keyCode, scanCode, modifiers);
    }

    /**
     * 字符输入转发。
     */
    public boolean charTyped(char codePoint, int modifiers) {
        return ui.getWidget().charTyped(codePoint, modifiers);
    }

    /**
     * 屏幕尺寸变化检测 - 若尺寸变化调用 {@link ModularUI#init} 重新初始化布局。
     *
     * <p>适配 GUI Scale 切换与窗口尺寸变化。
     *
     * <p>首次调用只记录当前尺寸，不触发 init - 因为 Mixin 在构造 forwarder
     * 之前已显式调用 {@link ModularUI#init}（spec §8.2），后续只在尺寸真正变化时 re-init。
     *
     * @param width  当前屏幕宽度（gui scaled）
     * @param height 当前屏幕高度（gui scaled）
     */
    public void checkResize(int width, int height) {
        if (!sizeInitialized) {
            // 首次调用：仅记录尺寸，不触发 init（Mixin 已在外部 init 过）
            lastWidth = width;
            lastHeight = height;
            sizeInitialized = true;
            return;
        }
        if (width != lastWidth || height != lastHeight) {
            lastWidth = width;
            lastHeight = height;
            ui.init(width, height);
        }
    }
}
