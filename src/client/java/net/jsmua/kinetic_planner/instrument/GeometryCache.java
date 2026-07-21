package net.jsmua.kinetic_planner.instrument;

import net.jsmua.kinetic_planner.data.EdgeGeometry;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * 轨道几何缓存，基于 (version, dimension) 二元 key 的脏检测机制。
 *
 * <p>避免每帧从 Create {@code TrackGraph} 重新提取几何数据。仅当 Create 的
 * {@code RAILWAYS.version} 变化（轨道被编辑）或玩家切换维度时才触发重建。
 *
 * <h2>缓存生命周期</h2>
 * <ol>
 *   <li>{@link NativeLineOverlay#onClientTick} 每 tick 调用 {@link #needsRebuild}</li>
 *   <li>若需要重建，从 {@link net.jsmua.kinetic_planner.data.IRailwayDataAccess} 提取全部节点和边</li>
 *   <li>调用 {@link #update} 写入新数据并更新 version/dimension</li>
 *   <li>{@link #geometries} 供渲染循环遍历</li>
 *   <li>地图关闭时调用 {@link #clear} 释放内存</li>
 * </ol>
 *
 * <h2>线程安全</h2>
 * <p>仅在客户端 tick 线程和渲染线程中使用，{@code onClientTick} 在 tick 后写入，
 * {@code onMapRender} 在渲染时读取，无并发问题（MC tick 和 render 不重叠）。
 */
public class GeometryCache {

    /**
     * 单个轨道图的几何数据快照。
     *
     * @param nodes 节点世界坐标列表
     * @param edges 边几何描述符列表
     */
    public record GraphGeometry(List<Vec3> nodes, List<EdgeGeometry> edges) {}

    /** 按 TrackGraph UUID 索引的几何缓存。 */
    private final Map<UUID, GraphGeometry> cache = new HashMap<>();

    /** 上次重建时的 RAILWAYS.version，-1 表示从未构建。 */
    private int lastVersion = -1;

    /** 上次重建时的维度，null 表示从未构建。 */
    private ResourceKey<Level> lastDimension = null;

    /**
     * 判断缓存是否需要重建。
     *
     * @param version 当前 RAILWAYS.version
     * @param dim     当前维度
     * @return {@code true} 如果 version 或 dimension 与缓存时不一致
     */
    public boolean needsRebuild(int version, ResourceKey<Level> dim) {
        return version != lastVersion || !Objects.equals(dim, lastDimension);
    }

    /**
     * 用新数据更新缓存。
     *
     * @param version 当前 RAILWAYS.version
     * @param dim     当前维度
     * @param newData 按图 UUID 索引的几何数据
     */
    public void update(int version, ResourceKey<Level> dim, Map<UUID, GraphGeometry> newData) {
        cache.clear();
        cache.putAll(newData);
        lastVersion = version;
        lastDimension = dim;
    }

    /**
     * 返回所有缓存的几何数据，供渲染循环遍历。
     *
     * @return 几何数据集合
     */
    public Collection<GraphGeometry> geometries() {
        return cache.values();
    }

    /**
     * 清空缓存并重置版本/维度，在地图关闭时调用以释放内存。
     */
    public void clear() {
        cache.clear();
        lastVersion = -1;
        lastDimension = null;
    }
}
