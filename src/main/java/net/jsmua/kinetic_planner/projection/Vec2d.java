package net.jsmua.kinetic_planner.projection;

public record Vec2d(double x, double y) {
    public Vec2d add(Vec2d other) {
        return new Vec2d(x + other.x, y + other.y);
    }

    public Vec2d subtract(Vec2d other) {
        return new Vec2d(x - other.x, y - other.y);
    }

    public Vec2d scale(double factor) {
        return new Vec2d(x * factor, y * factor);
    }

    public double distanceTo(Vec2d other) {
        double dx = x - other.x;
        double dy = y - other.y;
        return Math.sqrt(dx * dx + dy * dy);
    }
}
