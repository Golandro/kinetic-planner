package net.jsmua.kinetic_planner.projection;

public record WorldRect(double minX, double minZ, double maxX, double maxZ) {
    public boolean contains(double x, double z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    public boolean containsWithMargin(double x, double z, double margin) {
        return x >= minX - margin && x <= maxX + margin && z >= minZ - margin && z <= maxZ + margin;
    }
}
