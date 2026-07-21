package net.jsmua.kinetic_planner.mixin;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.instrument.NativeLineOverlay;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xaero.map.gui.GuiMap;

/**
 * Mixin 注入 Xaero {@code GuiMap.render} 方法，在渲染返回后触发叠加层绘制。
 *
 * <p>注入点为 {@code @At("RETURN")}，即 {@code render} 方法正常返回后执行。
 * 这确保 Xaero 的地图纹理已绘制完毕，叠加层绘制在其上方。
 *
 * <p>注入方法 {@code kp$onMapRender} 将渲染调用委托给
 * {@link NativeLineOverlay#onMapRender}，并用 try-catch 包裹防止异常影响 Xaero。
 *
 * <p>{@code remap = false} 因为 {@code GuiMap.render} 不在 Mojang 的 obfuscation 映射中。
 * {@code defaultRequire = 0} 在 mixin 配置中设置，确保 Xaero 未安装时注入失败不崩溃。
 */
@Mixin(value = GuiMap.class, remap = false)
public class XaeroMapRenderHook {

    /**
     * 在 {@code GuiMap.render} 返回后注入叠加层渲染。
     *
     * @param guiGraphics MC 图形上下文
     * @param mouseX      鼠标 X 位置
     * @param mouseY      鼠标 Y 位置
     * @param partialTicks 渲染插值 partial tick
     * @param ci          回调信息（不取消原方法）
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void kp$onMapRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        try {
            // (GuiMap)(Object)this 是 Mixin 中获取目标实例的标准写法
            NativeLineOverlay.onMapRender((GuiMap) (Object) this, guiGraphics, mouseX, mouseY, partialTicks);
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("NativeLineOverlay render failed", t);
        }
    }
}
