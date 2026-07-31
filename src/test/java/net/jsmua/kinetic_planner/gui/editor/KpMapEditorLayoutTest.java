package net.jsmua.kinetic_planner.gui.editor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Reflection-based layout-flattening contract test for {@link KpMapEditor} (Task 3).
 *
 * <p><b>测试环境限制:</b> 直接构造 {@code KpMapEditor} 或 {@code SplittableWindow} 实例会触发
 * {@code UIElement.<clinit>} -> LDLib2 clinit 陷阱 (需要 NeoForge 运行时)。
 * 因此本测试仅通过反射验证类层级、方法签名与字段可达性, 不构造实例 (参见
 * {@code RibbonBarLayoutTest}、{@code KpMapEditorTest} 的同类约定)。
 *
 * <p>验证项 (Task 3 契约):
 * <ol>
 *   <li>{@code KpMapEditor} 存在且继承 LDLib2 {@code Editor}</li>
 *   <li>{@code KpMapEditor} 暴露公共 {@code placeCustomViews} 方法</li>
 *   <li>{@code Editor} 暴露公共 {@code rootWindow}/{@code leftWindow}/{@code centerWindow}/
 *       {@code rightWindow}/{@code bottomWindow} 字段 (扁平化操作的入口)</li>
 *   <li>{@code KpMapEditor} 声明 {@code flattenMainAreaLayout} 方法 (Task 3 新增, TDD 驱动)</li>
 *   <li>{@code SplittableWindow} 暴露扁平化所依赖的公共 API
 *       ({@code removeSplitWindow}/{@code splitStyle}/{@code isSplit}/{@code getFirst}/{@code getSecond})</li>
 * </ol>
 */
class KpMapEditorLayoutTest {

    private static final String KP_MAP_EDITOR_FQN =
        "net.jsmua.kinetic_planner.gui.editor.KpMapEditor";
    private static final String EDITOR_FQN =
        "com.lowdragmc.lowdraglib2.editor.ui.Editor";
    private static final String SPLITTABLE_WINDOW_FQN =
        "com.lowdragmc.lowdraglib2.editor.ui.SplittableWindow";

    /** 加载类但不触发 clinit (避免 LDLib2/NeoForge 运行时依赖)。 */
    private static Class<?> loadWithoutInit(String fqn) throws ClassNotFoundException {
        return Class.forName(fqn, false, Thread.currentThread().getContextClassLoader());
    }

    /** 沿超类链查找声明的方法 (declared or inherited)。 */
    private static Method findDeclaredOrInheritedMethod(Class<?> clazz, String name, Class<?>... params) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    /**
     * 验证 KpMapEditor 类存在且继承 LDLib2 Editor。
     */
    @Test
    void kpMapEditorExtendsEditor() throws ClassNotFoundException {
        Class<?> kpMapEditor = loadWithoutInit(KP_MAP_EDITOR_FQN);
        Class<?> editor = loadWithoutInit(EDITOR_FQN);
        Class<?> superclass = kpMapEditor.getSuperclass();
        assertNotNull(superclass, "KpMapEditor 必须有超类");
        assertEquals(editor, superclass,
            "KpMapEditor 必须直接继承 LDLib2 Editor");
    }

    /**
     * 验证 KpMapEditor 暴露公共 placeCustomViews 方法 (供 KpEditorScreen 构造后调用)。
     */
    @Test
    void placeCustomViewsIsPublic() throws ClassNotFoundException, NoSuchMethodException {
        Class<?> kpMapEditor = loadWithoutInit(KP_MAP_EDITOR_FQN);
        Method placeCustomViews = kpMapEditor.getMethod("placeCustomViews");
        assertEquals(void.class, placeCustomViews.getReturnType(),
            "placeCustomViews 必须返回 void");
        assertTrue(Modifier.isPublic(placeCustomViews.getModifiers()),
            "placeCustomViews 必须为 public");
    }

    /**
     * 验证 Editor 暴露扁平化所依赖的五个公共 window 字段。
     */
    @Test
    void editorExposesPublicWindowFields() throws ClassNotFoundException, NoSuchFieldException {
        Class<?> editor = loadWithoutInit(EDITOR_FQN);
        for (String fieldName : new String[]{"rootWindow", "leftWindow", "centerWindow",
            "rightWindow", "bottomWindow"}) {
            Field f = editor.getDeclaredField(fieldName);
            assertTrue(Modifier.isPublic(f.getModifiers()),
                "Editor." + fieldName + " 必须为 public (扁平化入口)");
        }
    }

    /**
     * 验证 KpMapEditor 声明 flattenMainAreaLayout 方法 (Task 3 新增)。
     *
     * <p>该方法使用公共 API (removeSplitWindow / splitStyle) 将 LDLib2 Editor 默认的四窗格
     * 树扁平为仅 leftWindow + centerWindow, 降低 UIElement DOM 深度。签名: void, 无参。
     */
    @Test
    void kpMapEditorDeclaresFlattenMainAreaLayout() throws ClassNotFoundException {
        Class<?> kpMapEditor = loadWithoutInit(KP_MAP_EDITOR_FQN);
        Method flatten = findDeclaredOrInheritedMethod(kpMapEditor, "flattenMainAreaLayout");
        assertNotNull(flatten,
            "KpMapEditor 必须声明 flattenMainAreaLayout 方法 (Task 3 扁平化入口)");
        assertEquals(void.class, flatten.getReturnType(),
            "flattenMainAreaLayout 必须返回 void");
        assertEquals(0, flatten.getParameterCount(),
            "flattenMainAreaLayout 必须无参数");
    }

    /**
     * 验证 SplittableWindow 暴露扁平化所依赖的公共 API。
     *
     * <p>这些是 Task 3 唯一可用的公共操作 (setImmortal/setAnchorId 为 protected, 不可调用)。
     */
    @Test
    void splittableWindowExposesFlatteningApis() throws ClassNotFoundException, NoSuchMethodException {
        Class<?> splittableWindow = loadWithoutInit(SPLITTABLE_WINDOW_FQN);
        // removeSplitWindow(SplittableWindow) - 移除子窗格
        Method removeSplitWindow = splittableWindow.getMethod(
            "removeSplitWindow", splittableWindow);
        assertTrue(Modifier.isPublic(removeSplitWindow.getModifiers()),
            "removeSplitWindow 必须为 public");
        // splitStyle(Consumer) - 调整分割百分比
        Method splitStyle = splittableWindow.getMethod(
            "splitStyle", java.util.function.Consumer.class);
        assertTrue(Modifier.isPublic(splitStyle.getModifiers()),
            "splitStyle 必须为 public");
        // isSplit() - 判断是否已分割
        Method isSplit = splittableWindow.getMethod("isSplit");
        assertTrue(Modifier.isPublic(isSplit.getModifiers()),
            "isSplit 必须为 public");
        // getFirst() / getSecond() - 访问分割子节点
        assertTrue(Modifier.isPublic(splittableWindow.getMethod("getFirst").getModifiers()),
            "getFirst 必须为 public");
        assertTrue(Modifier.isPublic(splittableWindow.getMethod("getSecond").getModifiers()),
            "getSecond 必须为 public");
    }
}
