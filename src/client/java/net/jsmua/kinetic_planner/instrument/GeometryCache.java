package net.jsmua.kinetic_planner.instrument;

import net.jsmua.kinetic_planner.data.EdgeGeometry;
import net.minecraft.world.phys.Vec3;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.*;

public class GeometryCache {
    public record GraphGeometry(List<Vec3> nodes, List<EdgeGeometry> edges) {}

    private final Map<UUID, GraphGeometry> cache = new HashMap<>();
    private int lastVersion = -1;
    private ResourceKey<Level> lastDimension = null;

    public boolean needsRebuild(int version, ResourceKey<Level> dim) {
        return version != lastVersion || !Objects.equals(dim, lastDimension);
    }

    public void update(int version, ResourceKey<Level> dim, Map<UUID, GraphGeometry> newData) {
        cache.clear();
        cache.putAll(newData);
        lastVersion = version;
        lastDimension = dim;
    }

    public Collection<GraphGeometry> geometries() {
        return cache.values();
    }

    public void clear() {
        cache.clear();
        lastVersion = -1;
        lastDimension = null;
    }
}
