package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.cadengine.Theme;
import net.jsmua.kinetic_planner.cadengine.ThemeSerializer;
import net.jsmua.kinetic_planner.instrument.WorldTreeReadOverlay;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 主题管理器：扫描、加载、切换、重载和重置主题。
 *
 * <p>此管理器将主题文件 I/O 逻辑从命令层分离，使命令处理器（{@link KPCommands}）
 * 仅负责命令分发，不处理文件扫描或 JSON 反序列化。
 *
 * <h2>设计分层</h2>
 * <ul>
 *   <li><b>纯方法</b>（{@link #listThemeNames} / {@link #loadThemeFile}）：
 *       接受 {@link Path} 参数，不依赖运行时状态，可在 JUnit 5 中用 {@code @TempDir} 单测。</li>
 *   <li><b>副作用方法</b>（{@link #setTheme} / {@link #reload} / {@link #reset}）：
 *       操作 {@link KPConfig#THEME_ACTIVE} 和 {@link WorldTreeReadOverlay#setTheme}，
 *       需运行时上下文，靠手动验收。</li>
 * </ul>
 *
 * <h2>主题文件布局</h2>
 * <pre>
 * config/kineticplanner/themes/
 *   ├── default.json    （可选 -- 不存在时使用 {@link Theme#defaultValue()}）
 *   ├── dark.json
 *   └── light.json
 * </pre>
 * <p>"default" 主题始终可用，不依赖文件存在。
 */
public final class ThemeManager {

    private static final IKPConfig config = KPConfig.getInstance();

    private ThemeManager() {}

    /**
     * 返回主题文件目录的运行时路径。
     *
     * <p>路径为 {@code <game-dir>/config/kineticplanner/themes/}。
     * 目录可能不存在，调用方应处理此情况。
     *
     * @return 主题目录的绝对路径
     */
    public static Path getThemesDir() {
        return FMLPaths.CONFIGDIR.get().resolve("kineticplanner/themes");
    }

    /**
     * 扫描目录中的 {@code .json} 主题文件，返回主题名列表。
     *
     * <p><b>纯方法</b>：不依赖运行时状态，可单测。
     *
     * <p>主题名 = 文件名去掉 {@code .json} 后缀。列表始终包含 "default"
     * （即使对应的 JSON 文件不存在，因为默认主题由 {@link Theme#defaultValue()} 提供）。
     * 结果去重并按字母序排列。
     *
     * @param themesDir 主题目录路径（可以不存在，此时仅返回 ["default"]）
     * @return 排序后的主题名列表，始终包含 "default"
     */
    public static List<String> listThemeNames(Path themesDir) {
        List<String> names = new ArrayList<>();
        names.add("default");
        if (Files.isDirectory(themesDir)) {
            try (Stream<Path> files = Files.list(themesDir)) {
                files
                    .filter(f -> f.toString().endsWith(".json"))
                    .forEach(f -> {
                        String fileName = f.getFileName().toString();
                        // 去掉 .json 后缀得到主题名
                        names.add(fileName.substring(0, fileName.length() - 5));
                    });
            } catch (IOException ignored) {
                // 目录读取失败，仅返回 default
            }
        }
        return names.stream().distinct().sorted().toList();
    }

    /**
     * 从目录加载指定名称的主题 JSON 文件。
     *
     * <p><b>纯方法</b>：不依赖运行时状态，可单测。
     *
     * <p>文件路径为 {@code themesDir/<name>.json}。使用 {@link ThemeSerializer#deserialize}
     * 反序列化为 {@link Theme} record。
     *
     * @param themesDir 主题目录路径
     * @param name      主题名（不含 {@code .json} 后缀）
     * @return 反序列化的 {@link Theme}，或 {@code null} 如果文件不存在或解析失败
     */
    public static Theme loadThemeFile(Path themesDir, String name) {
        Path file = themesDir.resolve(name + ".json");
        if (!Files.exists(file)) return null;
        try {
            String json = Files.readString(file);
            return ThemeSerializer.deserialize(json);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * 设置当前活动主题。
     *
     * <p>对于 "default"，直接使用 {@link Theme#defaultValue()}。
     * 对于其他名称，从 {@link #getThemesDir()} 加载 JSON 文件。
     * 成功时更新 {@link KPConfig#THEME_ACTIVE} 并通过
     * {@link WorldTreeReadOverlay#setTheme} 应用到渲染层。
     *
     * @param name 主题名
     * @return {@code true} 如果主题加载成功；{@code false} 如果文件未找到
     */
    public static boolean setTheme(String name) {
        if ("default".equals(name)) {
            config.setActiveThemeName("default");
            WorldTreeReadOverlay.setTheme(Theme.defaultValue());
            return true;
        }
        Theme theme = loadThemeFile(getThemesDir(), name);
        if (theme != null) {
            config.setActiveThemeName(name);
            WorldTreeReadOverlay.setTheme(theme);
            return true;
        }
        return false;
    }

    /**
     * 从磁盘重载当前活动主题。
     *
     * <p>如果当前主题是 "default"，重置为 {@link Theme#defaultValue()}。
     * 否则从 JSON 文件重新加载。如果文件已被删除，回退到默认主题
     * 并更新 {@link KPConfig#THEME_ACTIVE}。
     */
    public static void reload() {
        String currentName = config.getActiveThemeName();
        if ("default".equals(currentName)) {
            WorldTreeReadOverlay.setTheme(Theme.defaultValue());
            return;
        }
        Theme theme = loadThemeFile(getThemesDir(), currentName);
        if (theme != null) {
            WorldTreeReadOverlay.setTheme(theme);
        } else {
            // 主题文件已删除，回退到默认
            config.setActiveThemeName("default");
            WorldTreeReadOverlay.setTheme(Theme.defaultValue());
        }
    }

    /**
     * 重置为默认主题。
     *
     * <p>将 {@link KPConfig#THEME_ACTIVE} 设为 "default"，
     * 并将 {@link Theme#defaultValue()} 应用到渲染层。
     */
    public static void reset() {
        config.setActiveThemeName("default");
        WorldTreeReadOverlay.setTheme(Theme.defaultValue());
    }
}
