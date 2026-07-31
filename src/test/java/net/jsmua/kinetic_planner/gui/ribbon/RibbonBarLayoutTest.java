package net.jsmua.kinetic_planner.gui.ribbon;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reflection-based layout contract test for {@link RibbonBar} and
 * {@code KpMapEditor.initMenus()} (Task 2).
 *
 * <p><b>测试环境限制:</b> 直接构造 {@link RibbonBar} 或 {@code KpMapEditor} 实例会触发
 * UIElement.{@code <clinit>} -> LDLib2 clinit 陷阱 (需要 NeoForge 运行时)。
 * 因此本测试仅通过反射验证类层级与方法签名, 不构造实例。
 *
 * <p>验证项 (Task 2 契约):
 * <ol>
 *   <li>{@link RibbonBar} 存在且继承 {@code com.lowdragmc.lowdraglib2.gui.ui.UIElement}</li>
 *   <li>{@code KpMapEditor} 存在且具有 {@code protected initMenus} 方法 (声明或继承)</li>
 *   <li>{@link RibbonBar} 暴露 {@code getTabView} 公共方法 (验证公共 API 面)</li>
 * </ol>
 */
class RibbonBarLayoutTest {

    private static final String RIBBON_BAR_FQN =
        "net.jsmua.kinetic_planner.gui.ribbon.RibbonBar";
    private static final String KP_MAP_EDITOR_FQN =
        "net.jsmua.kinetic_planner.gui.editor.KpMapEditor";
    private static final String UI_ELEMENT_FQN =
        "com.lowdragmc.lowdraglib2.gui.ui.UIElement";

    /** 加载类但不触发 clinit (避免 LDLib2/NeoForge 运行时依赖)。 */
    private static Class<?> loadWithoutInit(String fqn) throws ClassNotFoundException {
        return Class.forName(fqn, false, Thread.currentThread().getContextClassLoader());
    }

    /** 沿超类链查找声明的方法 (declared or inherited)。 */
    private static Method findDeclaredOrInheritedMethod(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 验证 RibbonBar 类存在且继承 UIElement。
     */
    @Test
    void ribbonBarExtendsUIElement() throws ClassNotFoundException {
        Class<?> ribbonBar = loadWithoutInit(RIBBON_BAR_FQN);
        Class<?> uiElement = loadWithoutInit(UI_ELEMENT_FQN);
        Class<?> superclass = ribbonBar.getSuperclass();
        assertNotNull(superclass, "RibbonBar 必须有超类");
        assertEquals(uiElement, superclass,
            "RibbonBar 必须直接继承 UIElement");
    }

    /**
     * 验证 KpMapEditor 类存在且具有 protected initMenus 方法 (声明或继承)。
     */
    @Test
    void kpMapEditorHasProtectedInitMenus() throws ClassNotFoundException {
        Class<?> kpMapEditor = loadWithoutInit(KP_MAP_EDITOR_FQN);
        Method initMenus = findDeclaredOrInheritedMethod(kpMapEditor, "initMenus");
        assertNotNull(initMenus,
            "KpMapEditor (或其超类) 必须声明 initMenus 方法");
        assertTrue(Modifier.isProtected(initMenus.getModifiers()),
            "initMenus 必须是 protected, 实际: "
                + Modifier.toString(initMenus.getModifiers()));
    }

    /**
     * 验证 RibbonBar 暴露公共 getTabView 方法 (公共 API 面)。
     */
    @Test
    void ribbonBarExposesGetTabView() throws ClassNotFoundException, NoSuchMethodException {
        Class<?> ribbonBar = loadWithoutInit(RIBBON_BAR_FQN);
        Method getTabView = ribbonBar.getMethod("getTabView");
        assertTrue(Modifier.isPublic(getTabView.getModifiers()),
            "getTabView 必须是 public");
    }
}
