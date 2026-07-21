package net.jsmua.kinetic_planner.data;

import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.world.phys.Vec3;

/**
 * 轨道边的几何描述符，描述一条轨道边在 3D 空间中的形状。
 *
 * <p>本 record 是纯数据结构，不包含渲染逻辑。由 {@link IRailwayDataAccess#edgeGeometry}
 * 从 Create 的 {@code TrackEdge} 提取，经 {@link net.jsmua.kinetic_planner.instrument.GeometryCache}
 * 缓存后传递给 {@link net.jsmua.kinetic_planner.instrument.NativeLineOverlay} 绘制。
 *
 * <h2>几何类型</h2>
 * <ul>
 *   <li>{@link Type#STRAIGHT} - 直线，Phase 0a 唯一激活的类型</li>
 *   <li>{@link Type#BEZIER} - Create 三次贝塞尔曲线（4 控制点），Phase 0a 简化为端点直线</li>
 *   <li>{@link Type#ARC} - 圆弧，Phase 2 激活</li>
 *   <li>{@link Type#SPLINE} - 样条曲线，Phase 2 激活</li>
 *   <li>{@link Type#EXTENSION} - 第三方模组扩展几何，Phase 4 激活（railx 兼容）</li>
 * </ul>
 *
 * <h2>Create 贝塞尔曲线说明</h2>
 * <p>Create 6.0.10 的 {@code BezierConnection} 是<strong>三次</strong>贝塞尔（非二次），
 * 4 个控制点为 {@code start, control1, control2, end}。
 * 其中 {@code start}/{@code end} 来自 {@code BezierConnection.starts}，
 * {@code control1}/{@code control2} 由 {@code BezierConnection.axes} 加上端点位置计算得出。
 *
 * @param type      几何类型
 * @param p1        边的起点（世界坐标）
 * @param p2        边的终点（世界坐标）
 * @param bezier    贝塞尔曲线参数，仅当 {@code type == BEZIER} 时非 null
 * @param arc       圆弧参数，仅当 {@code type == ARC} 时非 null（Phase 0a 恒 null）
 * @param extension 扩展几何参数，仅当 {@code type == EXTENSION} 时非 null（Phase 0a 恒 null）
 */
public record EdgeGeometry(
    Type type,
    Vec3 p1, Vec3 p2,
    BezierSpec bezier,
    ArcSpec arc,
    ExtensionSpec extension
) {
    /**
     * 轨道边的几何类型枚举。
     */
    public enum Type {
        /** 直线段。 */
        STRAIGHT,
        /** 圆弧段（Phase 2）。 */
        ARC,
        /** Create 三次贝塞尔曲线。 */
        BEZIER,
        /** 第三方模组扩展几何（Phase 4，railx 兼容）。 */
        EXTENSION,
        /** 样条曲线（Phase 2）。 */
        SPLINE
    }

    /**
     * Create 三次贝塞尔曲线参数（4 控制点 + 材质）。
     *
     * @param start    起点（对应 BezierConnection.starts.getFirst()）
     * @param control1 第一控制点（BezierConnection.axes.getFirst() + start）
     * @param control2 第二控制点（BezierConnection.axes.getSecond() + end）
     * @param end      终点（对应 BezierConnection.starts.getSecond()）
     * @param material 轨道材质
     */
    public record BezierSpec(
        Vec3 start, Vec3 control1, Vec3 control2, Vec3 end,
        TrackMaterial material
    ) {}

    /**
     * 圆弧参数（Phase 2 预留，Phase 0a 恒 null）。
     *
     * @param center   圆心坐标
     * @param radius   半径
     * @param startRad 起始角度（弧度）
     * @param endRad   终止角度（弧度）
     * @param material 轨道材质
     */
    public record ArcSpec(
        Vec3 center, double radius, double startRad, double endRad,
        TrackMaterial material
    ) {}

    /**
     * 第三方模组扩展几何参数（Phase 4 预留，Phase 0a 恒 null）。
     *
     * <p>用于支持 railx 等第三方模组定义的自定义轨道几何类型。
     * 扩展提供者通过 {@code sourceModId} 和 {@code geometryTypeId} 标识，
     * 具体几何数据存储在 NBT 中由扩展提供者自行解析。
     *
     * @param sourceModId    扩展来源模组 ID
     * @param geometryTypeId 几何类型标识符（由扩展模组定义）
     * @param data           几何数据（NBT 格式，由扩展模组解析）
     */
    public record ExtensionSpec(
        String sourceModId,
        String geometryTypeId,
        net.minecraft.nbt.CompoundTag data
    ) {}

    /**
     * 创建直线类型的几何描述符。
     *
     * @param p1 起点
     * @param p2 终点
     * @return {@code type=STRAIGHT} 的 EdgeGeometry，bezier/arc/extension 均为 null
     */
    public static EdgeGeometry straight(Vec3 p1, Vec3 p2) {
        return new EdgeGeometry(Type.STRAIGHT, p1, p2, null, null, null);
    }

    /**
     * 创建贝塞尔曲线类型的几何描述符。
     *
     * @param p1     起点（与 {@code bezier.start} 相同）
     * @param p2     终点（与 {@code bezier.end} 相同）
     * @param bezier 贝塞尔曲线参数
     * @return {@code type=BEZIER} 的 EdgeGeometry，arc/extension 为 null
     */
    public static EdgeGeometry bezier(Vec3 p1, Vec3 p2, BezierSpec bezier) {
        return new EdgeGeometry(Type.BEZIER, p1, p2, bezier, null, null);
    }
}
