package net.jsmua.kinetic_planner.gui.theme;

import com.lowdragmc.lowdraglib2.gui.ui.style.Stylesheet;
import com.lowdragmc.lowdraglib2.gui.ui.style.StylesheetManager;
import net.minecraft.resources.ResourceLocation;

/**
 * 程序化生成需要 token 插值的 LSS 样式表，并注册到 StylesheetManager。
 *
 * <p>分工：
 * <ul>
 *   <li>本类：所有需要 {@code #%08X} 色值插值的规则</li>
 *   <li>资源包 {@code assets/kinetic_planner/lss/*.lss}：无需插值的纯结构/状态规则</li>
 * </ul>
 *
 * <p>调用 {@link #register()} 在客户端 setup 阶段注册为 builtin stylesheet。
 */
public final class KpThemeStylesheet {

    private static final ResourceLocation THEME_RL =
        ResourceLocation.fromNamespaceAndPath("kinetic_planner", "theme_colors");

    private KpThemeStylesheet() {}

    /**
     * 生成含 token 插值的主题 LSS 样式表。
     *
     * <p>颜色值通过 {@code String.formatted("#%08X", KpTheme.XXX)} 插值。
     * Task 2 将填充完整的颜色规则。
     */
    public static Stylesheet create() {
        String lss = """
            // KpThemeStylesheet - 颜色规则将在 Task 2 填充
            """.stripIndent();
        return Stylesheet.parse(lss);
    }

    /**
     * 注册为 builtin stylesheet。
     *
     * <p>在客户端 setup 阶段调用一次。StylesheetManager 在资源包 reload 时
     * 会保留此注册，并通知 StyleEngine 重新匹配。
     */
    public static void register() {
        StylesheetManager.INSTANCE.registerBuiltinStylesheet(THEME_RL, create());
    }
}
