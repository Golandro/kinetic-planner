package net.jsmua.kinetic_planner.projection;

import org.joml.Vector2f;

public final class WorldScreenTransform {
    private static final double WORLD_BOUNDARY = 3.0e7;

    private final CameraParams cam;

    public WorldScreenTransform(CameraParams cam) {
        this.cam = cam;
    }

    public CameraParams cam() {
        return cam;
    }

    public Vector2f worldToScreen(double worldX, double worldZ) {
        float screenX = (float) ((worldX - cam.cameraBlockX()) / cam.blocksPerPixel() + cam.screenCenterX());
        float screenY = (float) ((worldZ - cam.cameraBlockZ()) / cam.blocksPerPixel() + cam.screenCenterY());
        return new Vector2f(screenX, screenY);
    }

    public Vec2d screenToWorld(double screenX, double screenY) {
        double worldX = (screenX - cam.screenCenterX()) * cam.blocksPerPixel() + cam.cameraBlockX();
        double worldZ = (screenY - cam.screenCenterY()) * cam.blocksPerPixel() + cam.cameraBlockZ();
        return new Vec2d(worldX, worldZ);
    }

    public double screenToWorldDistance(double pixelDist) {
        return pixelDist * cam.blocksPerPixel();
    }

    public WorldRect visibleWorldRect() {
        double halfWidthBlocks = (cam.screenWidth() / 2.0) * cam.blocksPerPixel();
        double halfHeightBlocks = (cam.screenHeight() / 2.0) * cam.blocksPerPixel();
        double minX = clamp(cam.cameraBlockX() - halfWidthBlocks);
        double maxX = clamp(cam.cameraBlockX() + halfWidthBlocks);
        double minZ = clamp(cam.cameraBlockZ() - halfHeightBlocks);
        double maxZ = clamp(cam.cameraBlockZ() + halfHeightBlocks);
        return new WorldRect(minX, minZ, maxX, maxZ);
    }

    private static double clamp(double v) {
        return Math.max(-WORLD_BOUNDARY, Math.min(WORLD_BOUNDARY, v));
    }
}
