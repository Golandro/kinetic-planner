package net.jsmua.kinetic_planner.mixin;

import com.simibubi.create.compat.trainmap.TrainMapManager;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;

/**
 * 彻底移除 Create 列车地图在地图 UI 上的开关按钮（Xaero / JourneyMap 通用）。
 *
 * <p>两个 Overwrite 各司其职：
 * <ul>
 *   <li>{@link #renderToggleWidget} — 按钮贴图永不渲染；</li>
 *   <li>{@link #isToggleWidgetHovered} — 悬停判定恒为 {@code false}。这是单点封杀：
 *       Xaero/Journey 适配层的 "列车网络叠加层" Toast 以此判定为唯一条件，
 *       且 {@code handleToggleWidgetClick} 内部亦先调用本方法——
 *       Toast 与点击由此一并消灭，无需再覆写点击处理。</li>
 * </ul>
 *
 * <p>使用 {@code @Overwrite} 而非 {@code @Inject cancellable}：后者在 NeoForge 对 static 方法的
 * 处理中存在 {@code ci.cancel()} 静默失效的问题（回调被调用，但原始方法体仍完整执行）。
 *
 * <p>目标类 {@link TrainMapManager} 属 Create（非 Mojang 类），故 {@code remap = false}。
 */
@Mixin(value = TrainMapManager.class, remap = false)
public abstract class CreateTrainMapMixin {

    /** 按钮永不渲染 —— KP 齿轮按钮已取代其位置。 */
    @Overwrite
    public static void renderToggleWidget(GuiGraphics graphics, int x, int y) {
    }

    /**
     * 悬停判定恒 {@code false}：一举消除 "列车网络叠加层" Toast 与按钮点击。
     *
     * @author Kinetic Planner
     * @reason KP 接管列车地图开关后，Create 的悬停区域（与 KP 齿轮按钮同位于 (3,30)）
     *         不应再触发任何 UI 反馈。
     */
    @Overwrite
    public static boolean isToggleWidgetHovered(int mouseX, int mouseY, int x, int y) {
        return false;
    }
}
