package net.jsmua.kinetic_planner.gui;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 反射签名测试: {@link MapPlaceholderView#prepareTransparentChain} 静态辅助方法 (Task 4)。
 *
 * <p><b>测试环境限制:</b> 直接构造 {@link MapPlaceholderView} 或
 * {@code ViewContainer} 实例会触发 {@code UIElement.<clinit>} -> LDLib2 clinit 陷阱
 * (需要 NeoForge 运行时)。因此本测试仅通过反射验证类层级与方法签名, 不构造实例
 * (与 {@link MapPlaceholderViewTest} / {@code RibbonBarLayoutTest} 同一策略)。
 *
 * <p>验证项 (Task 4 契约):
 * <ol>
 *   <li>{@link MapPlaceholderView} 类存在且继承 {@code com.lowdragmc.lowdraglib2.editor.ui.View}</li>
 *   <li>{@link MapPlaceholderView} 暴露 {@code public static void prepareTransparentChain(ViewContainer)}
 *       方法 (参数类型为 {@code com.lowdragmc.lowdraglib2.editor.ui.ViewContainer}, 返回 {@code void})</li>
 * </ol>
 */
class MapPlaceholderViewTransparencyTest {

    private static final String MAP_PLACEHOLDER_VIEW_FQN =
        "net.jsmua.kinetic_planner.gui.MapPlaceholderView";
    private static final String VIEW_FQN =
        "com.lowdragmc.lowdraglib2.editor.ui.View";
    private static final String VIEW_CONTAINER_FQN =
        "com.lowdragmc.lowdraglib2.editor.ui.ViewContainer";

    /** 加载类但不触发 clinit (避免 LDLib2/NeoForge 运行时依赖)。 */
    private static Class<?> loadWithoutInit(String fqn) throws ClassNotFoundException {
        return Class.forName(fqn, false, Thread.currentThread().getContextClassLoader());
    }

    /**
     * 验证 {@link MapPlaceholderView} 类存在且直接继承 {@code View}。
     */
    @Test
    void mapPlaceholderViewExtendsView() throws ClassNotFoundException {
        Class<?> mapPlaceholderView = loadWithoutInit(MAP_PLACEHOLDER_VIEW_FQN);
        Class<?> view = loadWithoutInit(VIEW_FQN);
        Class<?> superclass = mapPlaceholderView.getSuperclass();
        assertNotNull(superclass, "MapPlaceholderView 必须有超类");
        assertEquals(view, superclass,
            "MapPlaceholderView 必须直接继承 View");
    }

    /**
     * 验证 {@link MapPlaceholderView} 暴露 {@code public static void prepareTransparentChain(ViewContainer)}
     * 方法, 参数类型为 {@code ViewContainer}, 返回 {@code void}。
     *
     * <p>使用 {@link Class#getMethod(String, Class...)} (仅检索 public 方法, 含继承),
     * 配合 {@link Modifier#isStatic(int)} 验证静态修饰符, {@link Method#getReturnType()} 验证返回类型。
     */
    @Test
    void prepareTransparentChainIsPublicStaticWithViewContainerParam() throws Exception {
        Class<?> mapPlaceholderView = loadWithoutInit(MAP_PLACEHOLDER_VIEW_FQN);
        Class<?> viewContainer = loadWithoutInit(VIEW_CONTAINER_FQN);

        Method method = mapPlaceholderView.getMethod("prepareTransparentChain", viewContainer);

        int modifiers = method.getModifiers();
        assertTrue(Modifier.isPublic(modifiers),
            "prepareTransparentChain 必须是 public, 实际: "
                + Modifier.toString(modifiers));
        assertTrue(Modifier.isStatic(modifiers),
            "prepareTransparentChain 必须是 static, 实际: "
                + Modifier.toString(modifiers));

        assertEquals(void.class, method.getReturnType(),
            "prepareTransparentChain 返回类型必须为 void");

        Class<?>[] paramTypes = method.getParameterTypes();
        assertEquals(1, paramTypes.length,
            "prepareTransparentChain 必须有且仅有 1 个参数");
        assertEquals(viewContainer, paramTypes[0],
            "prepareTransparentChain 参数类型必须为 ViewContainer");
    }
}
