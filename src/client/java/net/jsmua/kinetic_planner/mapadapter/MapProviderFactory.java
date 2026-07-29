package net.jsmua.kinetic_planner.mapadapter;

import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * 创建 {@link MapOverlayProvider} 实例的工厂接口。
 *
 * <p>替代 {@link MapOverlayDispatcher} 中的静态初始化块。
 * 每个地图模组提供一个工厂实现，负责：
 * <ol>
 *   <li>声明其 mod ID、显示名和默认配置</li>
 *   <li>检查目标模组是否已安装（{@link #isAvailable}）</li>
 *   <li>按需创建 provider 实例（{@link #create}）</li>
 * </ol>
 *
 * <p>工厂在 client setup 阶段注册到 {@link MapProviderRegistry}。
 * 第三方模组可以注册自己的工厂来添加新的地图 provider 支持，
 * 无需修改 KP 源码。
 *
 * <p>Factory for creating {@link MapOverlayProvider} instances.
 *
 * <p>Replaces the static initialization block in {@link MapOverlayDispatcher}.
 * Each map mod provides a factory implementation that:
 * <ol>
 *   <li>Declares its mod ID, display name, and default config</li>
 *   <li>Checks whether the target mod is installed ({@link #isAvailable})</li>
 *   <li>Creates the provider instance on demand ({@link #create})</li>
 * </ol>
 *
 * <p>Factories are registered in {@link MapProviderRegistry} during client setup.
 * Third-party mods can register their own factories to add new map provider support
 * without modifying KP source code.
 */
public interface MapProviderFactory {

    /**
     * 该 provider 的 mod ID（如 "xaeroworldmap"、"journeymap"）。
     *
     * @return provider mod ID
     */
    String modId();

    /**
     * 用于 CLI 输出和 UI 的本地化显示名。
     *
     * @return 显示名组件 / localized display name
     */
    Component displayName();

    /**
     * 该 provider 的默认配置（启动时注册到 ProviderConfigRegistry）。
     *
     * @return 默认配置 / default config for this provider (registered in ProviderConfigRegistry at startup)
     */
    ProviderConfig defaultConfig();

    /**
     * 目标模组当前是否已加载。
     *
     * @return true 如果目标模组已加载 / Whether the target mod is currently loaded
     */
    boolean isAvailable();

    /**
     * 创建新的 provider 实例。
     *
     * @return provider 实例，创建失败（如模组未加载）时返回 null
     *         / provider instance, or null if creation failed (e.g. mod not loaded)
     */
    @Nullable
    MapOverlayProvider create();
}
