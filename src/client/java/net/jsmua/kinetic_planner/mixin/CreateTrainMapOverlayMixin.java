package net.jsmua.kinetic_planner.mixin;

import java.util.List;

import com.simibubi.create.compat.trainmap.TrainMapManager;
import com.simibubi.create.compat.trainmap.TrainMapRenderer;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.jsmua.kinetic_planner.compat.create.KPIntegration;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

/**
 * 受 "Show Create Track Map" 开关控制的叠加层渲染（轨道 / 列车 / 车站）。
 *
 * <p>与按钮不同，叠加层需在开关开启时恢复 Create 原始渲染，因此用 {@code @Overwrite} 复制
 * 原始 {@code renderAndPick} 逻辑，并以 {@link KPIntegration#shouldBlockCreateOverlay()}
 * 短路：关闭时返回 {@code null}（KP 完全替代），开启时按原逻辑渲染（KP 与 Create 共存）。
 *
 * <p>原始私有辅助方法通过 {@link #drawTrains} / {@link #drawPoints} / {@link #listTrainDetails}
 * 的 {@code @Shadow} 复用，避免重复实现渲染逻辑。
 *
 * <p>目标类 {@link TrainMapManager} 属 Create（非 Mojang 类），故 {@code remap = false}。
 */
@Mixin(value = TrainMapManager.class, remap = false)
public abstract class CreateTrainMapOverlayMixin {

    @Shadow
    private static Object drawTrains(GuiGraphics graphics, int mouseX, int mouseY,
        Object hoveredElement, Rect2i bounds) {
        return null;
    }

    @Shadow
    private static Object drawPoints(GuiGraphics graphics, int mouseX, int mouseY,
        Object hoveredElement, Rect2i bounds) {
        return null;
    }

    @Shadow
    private static List<FormattedText> listTrainDetails(Train train) {
        return null;
    }

    @Overwrite
    public static List<FormattedText> renderAndPick(GuiGraphics graphics, int mouseX, int mouseY,
            boolean linearFiltering, Rect2i bounds) {
        if (KPIntegration.shouldBlockCreateOverlay()) {
            return null;
        }

        Object hoveredElement = null;

        int offScreenMargin = 32;
        bounds.setX(bounds.getX() - offScreenMargin);
        bounds.setY(bounds.getY() - offScreenMargin);
        bounds.setWidth(bounds.getWidth() + 2 * offScreenMargin);
        bounds.setHeight(bounds.getHeight() + 2 * offScreenMargin);

        TrainMapRenderer.INSTANCE.render(graphics, linearFiltering, bounds);
        hoveredElement = drawTrains(graphics, mouseX, mouseY, hoveredElement, bounds);
        hoveredElement = drawPoints(graphics, mouseX, mouseY, hoveredElement, bounds);

        graphics.bufferSource().endBatch();

        if (hoveredElement instanceof GlobalStation station) {
            return List.of(Component.literal(station.name));
        }

        if (hoveredElement instanceof Train train)
            return listTrainDetails(train);

        return null;
    }
}
