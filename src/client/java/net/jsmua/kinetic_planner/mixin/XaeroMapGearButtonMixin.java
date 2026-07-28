package net.jsmua.kinetic_planner.mixin;

import net.jsmua.kinetic_planner.config.KpClientState;
import net.jsmua.kinetic_planner.config.KpConfigUIFactory;
import net.jsmua.kinetic_planner.config.KpGearButton;
import net.jsmua.kinetic_planner.config.KpUIEventForwarder;
import net.jsmua.kinetic_planner.config.OverlayControl;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;

/**
 * Mixin 注入 KP 配置面板到 Xaero 全屏地图。
 *
 * <p>spec §8.2：保持现有 Mixin 挂载点（{@code GuiMap.render} RETURN +
 * {@code GuiMap.mouseClicked} HEAD），但通过 {@link KpUIEventForwarder}
 * 转发事件到 LDLib2 {@link ModularUI}{@code .ModularUIWidget}。
 *
 * <p>{@code remap = false} 因为目标类属于 Xaero mod（非 MC 原生类）。
 * Mixin 配置 {@code defaultRequire: 0}，Xaero 未安装时不崩溃。
 *
 * <p>方法签名（已运行时验证）：
 * <ul>
 *   <li>{@code GuiMap.render(GuiGraphics, int, int, float) : void}</li>
 *   <li>{@code GuiMap.mouseClicked(double, double, int) : boolean}</li>
 * </ul>
 */
@Mixin(value = GuiMap.class, remap = false)
public class XaeroMapGearButtonMixin {

    @Unique
    private static KpUIEventForwarder kp$forwarder;

    @Unique
    private static KpGearButton kp$gearButton;

    /**
     * 初始化 UI 组件（懒加载）。
     *
     * <p>spec §8.2：齿轮按钮始终渲染；配置面板仅在 {@link KpClientState#isConfigPanelVisible()} 时渲染。
     */
    @Unique
    private static void kp$ensureInit() {
        var window = Minecraft.getInstance().getWindow();
        int screenW = window.getGuiScaledWidth();
        int screenH = window.getGuiScaledHeight();

        if (kp$forwarder == null) {
            var modularUI = KpConfigUIFactory.create();
            modularUI.init(screenW, screenH);
            kp$forwarder = new KpUIEventForwarder(modularUI);
        }
        // 屏幕尺寸变化时 re-init
        kp$forwarder.checkResize(screenW, screenH);

        if (kp$gearButton == null) {
            // 齿轮按钮位于右上角 (screenW - 20, 4)，16x16
            kp$gearButton = new KpGearButton(screenW - 20, 4,
                () -> KpClientState.toggleConfigPanel());
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void kp$renderConfigPanel(GuiGraphics gg, int mouseX, int mouseY,
                                       float partialTicks, CallbackInfo ci) {
        if (!OverlayControl.isEnabled()) return;
        // 编辑模式跳过：齿轮按钮由 KpRibbonBar 替代，配置面板由 Ribbon 设置抽屉承载
        if (KpClientState.isEditMode()) return;
        kp$ensureInit();
        kp$gearButton.render(gg, mouseX, mouseY);
        if (KpClientState.isConfigPanelVisible()) {
            kp$forwarder.render(gg, mouseX, mouseY, partialTicks);
        }
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void kp$handleMouseClick(double mouseX, double mouseY, int button,
                                      CallbackInfoReturnable<Boolean> cir) {
        if (!OverlayControl.isEnabled()) return;
        // 编辑模式跳过：事件由 KpEditorScreen 拦截路由
        if (KpClientState.isEditMode()) return;
        kp$ensureInit();
        if (kp$gearButton.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }
        if (KpClientState.isConfigPanelVisible()
            && kp$forwarder.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }
}
