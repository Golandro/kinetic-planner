package net.jsmua.kinetic_planner.projection;

public record CameraParams(
    double cameraBlockX,
    double cameraBlockZ,
    double blocksPerPixel,
    int screenWidth,
    int screenHeight
) {
    public int screenCenterX() { return screenWidth / 2; }
    public int screenCenterY() { return screenHeight / 2; }
}
