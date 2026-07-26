package net.jsmua.kinetic_planner.cadengine;

import net.jsmua.kinetic_planner.KineticPlannerMod;
import net.jsmua.kinetic_planner.projection.WorldScreenTransform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Blaze3D 矢量渲染封装（Phase 0b 核心）。
 *
 * <p>用三角形带展开粗线，无抗锯齿（AA 推迟）。
 * 所有几何在屏幕空间提交，世界坐标变换通过 {@link #applyWorldTransform} 设置。
 */
public final class CADRenderEngine {

    private GLStateGuard stateGuard;
    private boolean inFrame = false;
    private Matrix4f worldTransform = new Matrix4f();
    private boolean hasWorldTransform = false;

    private final List<float[]> triangleVertices = new ArrayList<>();

    /**
     * 开始一帧渲染。捕获 GL 状态。
     */
    public void beginFrame(int screenWidth, int screenHeight, float dpr) {
        stateGuard = GLStateGuard.capture(true);
        inFrame = true;
        triangleVertices.clear();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
    }

    /**
     * 设置世界坐标变换。此后所有 drawXxx 的坐标按世界坐标解释。
     */
    public void applyWorldTransform(WorldScreenTransform transform) {
        float scale = (float) (1.0 / transform.cam().blocksPerPixel());
        float cx = transform.cam().screenCenterX();
        float cy = transform.cam().screenCenterY();
        float camX = (float) transform.cam().cameraBlockX();
        float camZ = (float) transform.cam().cameraBlockZ();

        worldTransform = new Matrix4f()
            .translate(cx, cy, 0)
            .scale(scale, scale, 1)
            .translate(-camX, -camZ, 0);
        hasWorldTransform = true;
    }

    /**
     * 恢复到屏幕坐标空间。
     */
    public void restoreWorldTransform() {
        worldTransform = new Matrix4f();
        hasWorldTransform = false;
    }

    private float toScreenX(float worldX) {
        return hasWorldTransform ? worldTransform.m00() * worldX + worldTransform.m30() : worldX;
    }

    private float toScreenY(float worldY) {
        return hasWorldTransform ? worldTransform.m11() * worldY + worldTransform.m31() : worldY;
    }

    /**
     * 绘制粗线段（三角形展开）。
     */
    public void drawLine(float x1, float y1, float x2, float y2, float widthPx, int color) {
        if (!inFrame) return;
        float sx1 = toScreenX(x1), sy1 = toScreenY(y1);
        float sx2 = toScreenX(x2), sy2 = toScreenY(y2);
        float[] quad = LineGeometry.expandLineToTriangleStrip(sx1, sy1, sx2, sy2, widthPx);
        float[] rgba = unpackColor(color);
        // 两个三角形: v1-v2-v3, v2-v4-v3
        addTriangle(quad[0], quad[1], quad[2], quad[3], quad[4], quad[5], rgba);
        addTriangle(quad[2], quad[3], quad[6], quad[7], quad[4], quad[5], rgba);
    }

    /**
     * 绘制三次贝塞尔曲线（tessellate 后逐段 drawLine）。
     */
    public void drawBezier(
            float p0x, float p0y, float p1x, float p1y,
            float p2x, float p2y, float p3x, float p3y,
            float widthPx, int color, int segments) {
        drawBezier(p0x, p0y, p1x, p1y, p2x, p2y, p3x, p3y, widthPx, color, segments, false);
    }

    /**
     * 绘制三次贝塞尔曲线（可选虚线）。虚线模式下沿 tessellate 后的每段折线分别进行 dash/gap 分段，
     * 再逐段调用实线 {@link #drawLine(float, float, float, float, float, int)}。
     *
     * <p>注意：dashed 模式下分段阈值基于屏幕像素坐标，tessellate 段数过少时虚线视觉效果会变粗糙。
     * 调用方应保证 segments 足够大（通常 32）以获得平滑虚线。
     *
     * @param segments tessellate 段数
     * @param dashed   是否虚线
     */
    public void drawBezier(
            float p0x, float p0y, float p1x, float p1y,
            float p2x, float p2y, float p3x, float p3y,
            float widthPx, int color, int segments, boolean dashed) {
        if (!inFrame) return;
        float[] pts = BezierTessellator.tessellate(p0x, p0y, p1x, p1y, p2x, p2y, p3x, p3y, segments);
        if (dashed) {
            for (int i = 0; i < segments; i++) {
                float x1 = pts[i * 2], y1 = pts[i * 2 + 1];
                float x2 = pts[(i + 1) * 2], y2 = pts[(i + 1) * 2 + 1];
                float[][] segs = LineGeometry.buildDashedSegments(x1, y1, x2, y2, 4f, 2f);
                for (float[] s : segs) {
                    drawLine(s[0], s[1], s[2], s[3], widthPx, color);
                }
            }
        } else {
            for (int i = 0; i < segments; i++) {
                drawLine(pts[i * 2], pts[i * 2 + 1], pts[(i + 1) * 2], pts[(i + 1) * 2 + 1], widthPx, color);
            }
        }
    }

    /**
     * 绘制虚线段（三角形展开 + dash/gap 分段，默认 dash=4, gap=2）。
     *
     * @param dashed 是否虚线；false 时退化为普通 {@link #drawLine}
     */
    public void drawLine(float x1, float y1, float x2, float y2,
                         float widthPx, int color, boolean dashed) {
        drawLine(x1, y1, x2, y2, widthPx, color, dashed, 4f, 2f);
    }

    /**
     * 绘制虚线段（三角形展开 + dash/gap 分段）。
     *
     * @param dashed   是否虚线；false 时退化为普通 {@link #drawLine}
     * @param dashLen  实线段长度（像素）
     * @param gapLen   间隔长度（像素）
     */
    public void drawLine(float x1, float y1, float x2, float y2,
                         float widthPx, int color, boolean dashed,
                         float dashLen, float gapLen) {
        if (!inFrame) return;
        if (!dashed) {
            drawLine(x1, y1, x2, y2, widthPx, color);
            return;
        }
        // 注意：dashed 分段在屏幕空间进行，需要先做世界->屏幕变换
        float sx1 = toScreenX(x1), sy1 = toScreenY(y1);
        float sx2 = toScreenX(x2), sy2 = toScreenY(y2);
        float[][] segments = LineGeometry.buildDashedSegments(sx1, sy1, sx2, sy2, dashLen, gapLen);
        float[] rgba = unpackColor(color);
        for (float[] seg : segments) {
            float[] quad = LineGeometry.expandLineToTriangleStrip(seg[0], seg[1], seg[2], seg[3], widthPx);
            // 屏幕空间坐标，直接 addTriangle 不再走 toScreenX/Y
            addTriangle(quad[0], quad[1], quad[2], quad[3], quad[4], quad[5], rgba);
            addTriangle(quad[2], quad[3], quad[6], quad[7], quad[4], quad[5], rgba);
        }
    }

    /**
     * 绘制填充圆（三角形扇）。
     */
    public void drawFilledCircle(float cx, float cy, float radiusPx, int color) {
        if (!inFrame) return;
        float sx = toScreenX(cx), sy = toScreenY(cy);
        float[] rgba = unpackColor(color);
        int slices = 16;
        for (int i = 0; i < slices; i++) {
            float a1 = (float) (2 * Math.PI * i / slices);
            float a2 = (float) (2 * Math.PI * (i + 1) / slices);
            addTriangle(
                sx, sy,
                sx + (float) Math.cos(a1) * radiusPx, sy + (float) Math.sin(a1) * radiusPx,
                sx + (float) Math.cos(a2) * radiusPx, sy + (float) Math.sin(a2) * radiusPx,
                rgba);
        }
    }

    /**
     * 绘制填充矩形。
     */
    public void drawFilledRect(float x, float y, float w, float h, int color) {
        if (!inFrame) return;
        float sx = toScreenX(x), sy = toScreenY(y);
        float sw = hasWorldTransform ? worldTransform.m00() * w : w;
        float sh = hasWorldTransform ? worldTransform.m11() * h : h;
        float[] rgba = unpackColor(color);
        addTriangle(sx, sy, sx + sw, sy, sx, sy + sh, rgba);
        addTriangle(sx + sw, sy, sx + sw, sy + sh, sx, sy + sh, rgba);
    }

    /**
     * 结束一帧渲染。提交所有顶点，恢复 GL 状态。
     */
    public void endFrame() {
        if (!inFrame) return;
        try {
            if (!triangleVertices.isEmpty()) {
                submitVertices();
            }
        } catch (Throwable t) {
            KineticPlannerMod.LOGGER.error("CADRenderEngine render failed", t);
        } finally {
            triangleVertices.clear();
            inFrame = false;
            if (stateGuard != null) {
                stateGuard.restore();
                stateGuard = null;
            }
        }
    }

    private void addTriangle(float x1, float y1, float x2, float y2, float x3, float y3, float[] rgba) {
        triangleVertices.add(new float[]{x1, y1, rgba[0], rgba[1], rgba[2], rgba[3]});
        triangleVertices.add(new float[]{x2, y2, rgba[0], rgba[1], rgba[2], rgba[3]});
        triangleVertices.add(new float[]{x3, y3, rgba[0], rgba[1], rgba[2], rgba[3]});
    }

    private void submitVertices() {
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION_COLOR);
        for (float[] v : triangleVertices) {
            builder.addVertex(v[0], v[1], 0f).setColor(v[2], v[3], v[4], v[5]);
        }
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    private static float[] unpackColor(int color) {
        float r = ((color >> 16) & 0xFF) / 255.0f;
        float g = ((color >> 8) & 0xFF) / 255.0f;
        float b = (color & 0xFF) / 255.0f;
        float a = ((color >> 24) & 0xFF) / 255.0f;
        return new float[]{r, g, b, a};
    }
}
