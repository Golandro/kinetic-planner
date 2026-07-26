package net.jsmua.kinetic_planner.mixin;

import net.jsmua.kinetic_planner.config.MapGearButtonWidget;
import net.jsmua.kinetic_planner.config.OverlayControl;
import net.jsmua.kinetic_planner.config.ProviderConfigScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.map.gui.GuiMap;

/**
 * Mixin 注入齿轮按钮和配置面板到 Xaero 全屏地图。
 *
 * <p>在 {@code GuiMap.render} RETURN 注入齿轮按钮渲染 + 配置面板渲染。
 * 在 {@code GuiMap.mouseClicked} HEAD 注入鼠标事件路由（cancellable）。
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
    private static MapGearButtonWidget kp$gearButton;

    @Unique
    private static ProviderConfigScreen kp$configScreen;

    /**
     * 初始化 UI 组件（懒加载）。
     */
    @Unique
    private static void kp$ensureInit() {
        // 贴左边缘、全屏地图设置下方的社区惯例位置（与 Create 列车地图按钮同位），
        // 取代 Create 在 (3,30) 的开关，由 KP 按钮接管。
        if (kp$gearButton == null) {
            kp$gearButton = new MapGearButtonWidget(3, 30, () -> {
                kp$ensureConfigScreen();
                kp$configScreen.toggle();
            });
        }
    }

    @Unique
    private static void kp$ensureConfigScreen() {
        if (kp$configScreen == null && kp$gearButton != null) {
            kp$configScreen = new ProviderConfigScreen(kp$gearButton);
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void kp$renderGearButton(GuiGraphics gg, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (!OverlayControl.isEnabled()) return;
        kp$ensureInit();
        kp$gearButton.render(gg, mouseX, mouseY);
        kp$ensureConfigScreen();
        kp$configScreen.renderPanel(gg, mouseX, mouseY, partialTicks);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void kp$handleMouseClick(double mouseX, double mouseY, int button, CallbackInfoReturnable<Boolean> cir) {
        if (!OverlayControl.isEnabled()) return;
        kp$ensureInit();
        // 先检查配置面板
        kp$ensureConfigScreen();
        if (kp$configScreen.handleMouseClick(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
            return;
        }
        // 再检查齿轮按钮
        if (kp$gearButton.mouseClicked(mouseX, mouseY, button)) {
            cir.setReturnValue(true);
        }
    }
}
