package net.jsmua.kinetic_planner.cadengine;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.ShaderInstance;

/**
 * RenderSystem 状态管理。
 *
 * <p>在 {@link CADRenderEngine#beginFrame} 前设置叠加层所需的 GL 状态，
 * 在 {@link CADRenderEngine#endFrame} 后恢复到 MC 默认状态。
 */
public final class GLStateGuard {

    private final ShaderInstance savedShader;

    private GLStateGuard(ShaderInstance savedShader) {
        this.savedShader = savedShader;
    }

    /**
     * 保存当前 shader 并设置叠加层 GL 状态。
     */
    public static GLStateGuard capture(boolean disableDepthTest) {
        ShaderInstance shader = RenderSystem.getShader();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (disableDepthTest) {
            RenderSystem.disableDepthTest();
        }
        return new GLStateGuard(shader);
    }

    /**
     * 恢复 MC 默认 GL 状态。
     */
    public void restore() {
        RenderSystem.enableDepthTest();
        if (savedShader != null) {
            RenderSystem.setShader(() -> savedShader);
        }
    }
}
