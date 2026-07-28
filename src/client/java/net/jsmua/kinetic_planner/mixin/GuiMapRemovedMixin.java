package net.jsmua.kinetic_planner.mixin;

import net.jsmua.kinetic_planner.config.KpClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.GuiMap;

/**
 * 编辑模式抑制 GuiMap.removed() 状态清理（spec §5 GuiMapRemovedMixin 按需）。
 *
 * <p>setScreen(KpEditorScreen) 会触发 guiMap.removed()。
 * 若 removed() 清理状态导致后续 render() 不可用，添加此 Mixin cancel。
 *
 * <p><b>Defensive preemptive creation</b>：本环境无法运行 {@code gradlew runClient}
 * 进行 Step 1 运行时验证，故按 Task 3.4 brief 的防御性策略预先创建此 Mixin。
 * Mixin 逻辑极简（HEAD cancel when isEditMode），若 {@code removed()} 实际未清理
 * 状态则 cancel 等同于无操作，无副作用；若 {@code removed()} 确实清理状态，
 * 此 Mixin 防止瓦片消失/位置重置。运行时验证留待后续 {@code gradlew runClient}。
 */
@Mixin(value = GuiMap.class, remap = false)
public class GuiMapRemovedMixin {

    @Inject(method = "removed", at = @At("HEAD"), cancellable = true)
    private void kp$suppressRemoved(CallbackInfo ci) {
        if (KpClientState.isEditMode()) {
            ci.cancel();
        }
    }
}
