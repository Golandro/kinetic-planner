package net.jsmua.kinetic_planner.mixin;

import net.jsmua.kinetic_planner.config.KpClientState;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.GuiMap;

/**
 * 编辑模式抑制 Xaero UI 渲染（spec §6.7 方案 B）。
 *
 * <p>HEAD cancel {@code GuiMap.render} 完整方法，由 {@link net.jsmua.kinetic_planner.editor.KpEditorScreen}
 * 手动调用可控部分（瓦片+路标）。
 *
 * <p>观看模式不触发（{@code !isEditMode} 时 return）。
 *
 * <p>若 Task 3.1 反编译发现 render() 不可分离，改为在 render 内部多个
 * {@code @At("INVOKE")} 点 cancel 特定子调用（见文末降级模板）。
 */
@Mixin(value = GuiMap.class, remap = false)
public class XaeroUiSuppressMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void kp$suppressRender(GuiGraphics gg, int mouseX, int mouseY,
                                    float partialTicks, CallbackInfo ci) {
        if (KpClientState.isEditMode()) {
            ci.cancel();
        }
    }

    // === 降级情况模板（若 Task 3.1 发现 render 不可分离，替换上面的 HEAD cancel）===
    //
    // @Inject(method = "render", at = @At(value = "INVOKE",
    //     target = "Lxaero/map/...;renderRadar(...)V"), cancellable = true)
    // private void kp$suppressRadar(CallbackInfo ci) {
    //     if (KpClientState.isEditMode()) ci.cancel();
    // }
    //
    // @Inject(method = "render", at = @At(value = "INVOKE",
    //     target = "Lxaero/map/...;renderButtons(...)V"), cancellable = true)
    // private void kp$suppressButtons(CallbackInfo ci) {
    //     if (KpClientState.isEditMode()) ci.cancel();
    // }
    //
    // @Inject(method = "render", at = @At(value = "INVOKE",
    //     target = "Lxaero/map/...;renderHud(...)V"), cancellable = true)
    // private void kp$suppressHud(CallbackInfo ci) {
    //     if (KpClientState.isEditMode()) ci.cancel();
    // }
}
