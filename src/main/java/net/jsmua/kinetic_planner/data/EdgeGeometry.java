package net.jsmua.kinetic_planner.data;

import com.simibubi.create.content.trains.track.TrackMaterial;
import net.minecraft.world.phys.Vec3;

public record EdgeGeometry(
    Type type,
    Vec3 p1, Vec3 p2,
    BezierSpec bezier,
    ArcSpec arc,
    ExtensionSpec extension
) {
    public enum Type {
        STRAIGHT, ARC, BEZIER, EXTENSION, SPLINE
    }

    public record BezierSpec(
        Vec3 start, Vec3 control1, Vec3 control2, Vec3 end,
        TrackMaterial material
    ) {}

    public record ArcSpec(
        Vec3 center, double radius, double startRad, double endRad,
        TrackMaterial material
    ) {}

    public record ExtensionSpec(
        String sourceModId,
        String geometryTypeId,
        net.minecraft.nbt.CompoundTag data
    ) {}

    public static EdgeGeometry straight(Vec3 p1, Vec3 p2) {
        return new EdgeGeometry(Type.STRAIGHT, p1, p2, null, null, null);
    }

    public static EdgeGeometry bezier(Vec3 p1, Vec3 p2, BezierSpec bezier) {
        return new EdgeGeometry(Type.BEZIER, p1, p2, bezier, null, null);
    }
}
