package net.jsmua.kinetic_planner.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.jsmua.kinetic_planner.config.KpClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import com.mojang.blaze3d.vertex.VertexConsumer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import xaero.map.element.HoveredMapElementHolder;
import xaero.map.gui.GuiMap;
import xaero.map.gui.dropdown.rightclick.GuiRightClickMenu;
import xaero.map.gui.message.MessageBox;
import xaero.map.gui.message.render.MessageBoxRenderer;
import xaero.map.radar.tracker.PlayerTrackerMenuRenderer;

/**
 * 编辑模式选择性抑制 Xaero UI 元素（方案 B'，Task 3.1 研究结论）。
 *
 * <p><b>策略变更</b>：Task 3.1 反编译发现 {@code GuiMap.render()} 瓦片渲染完全内联
 * （~5902 行字节码，635 条 INVOKE，无 {@code renderTiles()} 可单独调用）。
 * 原方案 B（HEAD cancel + 手动重调子方法）会丢失全部瓦片底图，不可行。
 * 改用 MixinExtras {@code @WrapOperation} 在 render() 内部选择性跳过 UI 元素
 * 的 INVOKE，保留瓦片底图与玩家箭头自然渲染。
 *
 * <p><b>抑制目标</b>（编辑模式 isEditMode()==true 时跳过）：
 * <ul>
 *   <li>HUD 文本 — {@code MapRenderHelper.drawCenteredStringWithBackground}（2 个重载，共 ~9 次调用）</li>
 *   <li>雷达/玩家菜单 — {@code PlayerTrackerMenuRenderer.renderMenu}</li>
 *   <li>按钮/widgets — {@code ScreenBase.render}（super 调用，连带抑制 renderPreDropdown）</li>
 *   <li>右键菜单 — {@code GuiRightClickMenu.render}</li>
 *   <li>Tooltip — {@code GuiMap.renderTooltips}</li>
 *   <li>消息框 — {@code MessageBoxRenderer.render}</li>
 * </ul>
 *
 * <p><b>保留目标</b>（不在抑制列表，render() 自然执行）：
 * <ul>
 *   <li>地图瓦片（完全内联，无单一 INVOKE 可定位）</li>
 *   <li>玩家/路标箭头（{@code drawArrowOnMap} 等公有方法，在 render() 内被调用）</li>
 *   <li>加载/消息画面（条件分支，地图加载时需要显示）</li>
 *   <li>收尾 {@code MapRenderHelper.restoreDefaultShaderBlendState}（GL 状态恢复，必须执行）</li>
 * </ul>
 *
 * <p><b>观看模式</b>（isEditMode()==false）：所有 @WrapOperation 直接调用 original，行为不变。
 *
 * <p>所有 {@code @WrapOperation} 设置 {@code require = 0}（Xaero 版本未锁，
 * 签名可能变化；未匹配时静默跳过，不崩溃）。
 */
@Mixin(value = GuiMap.class, remap = false)
public class XaeroUiSuppressMixin {

    // === HUD 文本抑制（drawCenteredStringWithBackground，2 个重载）===

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lxaero/map/graphics/MapRenderHelper;drawCenteredStringWithBackground(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIFFFFLcom/mojang/blaze3d/vertex/VertexConsumer;)V"),
        require = 0)
    private void kp$suppressHudString(GuiGraphics gg, Font font, String text,
                                       int x, int y, int color,
                                       float f1, float f2, float f3, float f4,
                                       VertexConsumer vc, Operation<Void> original) {
        if (!KpClientState.isEditMode()) {
            original.call(gg, font, text, x, y, color, f1, f2, f3, f4, vc);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lxaero/map/graphics/MapRenderHelper;drawCenteredStringWithBackground(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIFFFFLcom/mojang/blaze3d/vertex/VertexConsumer;)V"),
        require = 0)
    private void kp$suppressHudComponent(GuiGraphics gg, Font font, Component text,
                                          int x, int y, int color,
                                          float f1, float f2, float f3, float f4,
                                          VertexConsumer vc, Operation<Void> original) {
        if (!KpClientState.isEditMode()) {
            original.call(gg, font, text, x, y, color, f1, f2, f3, f4, vc);
        }
    }

    // === 雷达/玩家菜单抑制 ===

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lxaero/map/radar/tracker/PlayerTrackerMenuRenderer;renderMenu(Lnet/minecraft/client/gui/GuiGraphics;Lxaero/map/gui/GuiMap;DIIIIZZLxaero/map/element/HoveredMapElementHolder;Lnet/minecraft/client/Minecraft;)Lxaero/map/element/HoveredMapElementHolder;"),
        require = 0)
    private HoveredMapElementHolder kp$suppressRadarMenu(
            PlayerTrackerMenuRenderer receiver,
            GuiGraphics gg, GuiMap map, double d,
            int i1, int i2, int i3, int i4,
            boolean b1, boolean b2,
            HoveredMapElementHolder prev, Minecraft mc,
            Operation<HoveredMapElementHolder> original) {
        if (KpClientState.isEditMode()) {
            // 编辑模式跳过雷达菜单渲染，返回传入的 prev 保持状态不变
            return prev;
        }
        return original.call(receiver, gg, map, d, i1, i2, i3, i4, b1, b2, prev, mc);
    }

    // === 按钮/widgets 抑制（super.render，连带抑制 renderPreDropdown）===

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lxaero/lib/client/gui/ScreenBase;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),
        require = 0)
    private void kp$suppressSuperRender(GuiMap self, GuiGraphics gg,
                                         int mouseX, int mouseY, float partialTicks,
                                         Operation<Void> original) {
        if (!KpClientState.isEditMode()) {
            original.call(self, gg, mouseX, mouseY, partialTicks);
        }
    }

    // === 右键菜单抑制 ===

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lxaero/map/gui/dropdown/rightclick/GuiRightClickMenu;render(Lnet/minecraft/client/gui/GuiGraphics;IIF)V"),
        require = 0)
    private void kp$suppressRightClickMenu(GuiRightClickMenu receiver,
                                            GuiGraphics gg, int mouseX, int mouseY,
                                            float partialTicks, Operation<Void> original) {
        if (!KpClientState.isEditMode()) {
            original.call(receiver, gg, mouseX, mouseY, partialTicks);
        }
    }

    // === Tooltip 抑制 ===

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lxaero/map/gui/GuiMap;renderTooltips(Lnet/minecraft/client/gui/GuiGraphics;IIF)Z"),
        require = 0)
    private boolean kp$suppressTooltips(GuiMap self, GuiGraphics gg,
                                         int mouseX, int mouseY, float partialTicks,
                                         Operation<Boolean> original) {
        if (KpClientState.isEditMode()) {
            return false;
        }
        return original.call(self, gg, mouseX, mouseY, partialTicks);
    }

    // === 消息框抑制 ===

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
        target = "Lxaero/map/gui/message/render/MessageBoxRenderer;render(Lnet/minecraft/client/gui/GuiGraphics;Lxaero/map/gui/message/MessageBox;Lnet/minecraft/client/gui/Font;IIZ)V"),
        require = 0)
    private void kp$suppressMessageBox(MessageBoxRenderer receiver,
                                        GuiGraphics gg, MessageBox messageBox, Font font,
                                        int mouseX, int mouseY, boolean flag,
                                        Operation<Void> original) {
        if (!KpClientState.isEditMode()) {
            original.call(receiver, gg, messageBox, font, mouseX, mouseY, flag);
        }
    }
}
