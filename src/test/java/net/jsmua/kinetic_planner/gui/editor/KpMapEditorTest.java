package net.jsmua.kinetic_planner.gui.editor;

import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.jsmua.kinetic_planner.gui.editor.KpMapEditor;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link KpMapEditor} 构造与方法契约测试。
 *
 * <p>spec §6.3：initMenus 只清除 menuContainer（保留 buttonContainer + closeButton）；
 * onPrepareResourceView/onPrepareHistoryView 为空；placeCustomViews 放置 ToolPanelView（左）
 * + MapPlaceholderView（中）。InspectorView 由 onPrepareInspectorView 默认放右。
 *
 * <p><b>测试环境限制：</b>{@code UIElement.<clinit>}（LDLib2 line 97）触发
 * {@code LDLib2Registries.<clinit>} → {@code AutoRegistry.autoRegister}
 * → {@code ModList.get().getAllScanData()}。纯 JVM 测试无 NeoForge 运行时，
 * {@code ModList.get()} 返回 null，导致 {@code ExceptionInInitializerError}。
 * 由于 {@code Editor extends UIElement}，任何对 {@code KpMapEditor} 的实例化或
 * 类字面量访问都会触发该链路。直接构造的三个用例 {@code @Disabled}，靠运行时验收
 * （{@code gradlew runClient}），与项目既有约定一致（参见 {@code RailwayDataAccessTest}、
 * {@code MapPlaceholderViewTest}）。
 *
 * <p>类层次结构（extends Editor extends UIElement）、{@code createNewEditorInstance} 重写、
 * 必需方法声明、public 字段可达性通过反射验证，<b>不</b>触发 {@code <clinit>}
 * （JLS §12.4.1：{@link Class#getSuperclass()} / {@link Class#getDeclaredMethod} /
 * {@link Class#getDeclaredField} 等结构查询均不引发初始化）。
 */
class KpMapEditorTest {

    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时；构造靠 gradlew runClient 验收，类层次靠下方反射用例验证")
    void constructWithoutCrash() {
        KpMapEditor editor = new KpMapEditor();
        assertNotNull(editor);
    }

    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时；同上，靠运行时验收")
    void createNewEditorInstanceReturnsKpMapEditor() {
        KpMapEditor editor = new KpMapEditor();
        var created = editor.createNewEditorInstance();
        assertNotNull(created);
        assertInstanceOf(KpMapEditor.class, created);
    }

    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时；同上，靠运行时验收")
    void publicFieldsAccessible() {
        KpMapEditor editor = new KpMapEditor();
        // 验证 Editor public 字段可访问（spec §13 LDLib2 验证）
        assertNotNull(editor.top);
        assertNotNull(editor.mainView);
        assertNotNull(editor.menuContainer);
        assertNotNull(editor.buttonContainer);
        assertNotNull(editor.closeButton);
        assertNotNull(editor.rootWindow);
        assertNotNull(editor.centerWindow);
        assertNotNull(editor.leftWindow);
        assertNotNull(editor.rightWindow);
        assertNotNull(editor.inspectorView);
        assertNotNull(editor.historyView);
    }

    /**
     * 验证 {@link KpMapEditor} 直接父类为 LDLib2 {@link Editor}，
     * 间接父类为 {@link UIElement}。反射访问不触发 {@code <clinit>}。
     */
    @Test
    void classHierarchyExtendsEditorAndUIElement() {
        assertEquals(Editor.class, KpMapEditor.class.getSuperclass(),
                "KpMapEditor 必须直接继承 LDLib2 Editor");
        assertEquals(UIElement.class, Editor.class.getSuperclass(),
                "Editor 必须直接继承 UIElement（LDLib2 契约 spec §13）");
        assertTrue(UIElement.class.isAssignableFrom(KpMapEditor.class),
                "KpMapEditor 必须是 UIElement 子类");
    }

    /**
     * 验证 {@code createNewEditorInstance()} 被 KpMapEditor 重写且返回类型为 {@link Editor}
     * （brief Step 3：返回 {@code new KpMapEditor()}）。返回 KpMapEditor 由运行时验收。
     */
    @Test
    void createNewEditorInstanceIsOverridden() throws NoSuchMethodException {
        Method m = KpMapEditor.class.getDeclaredMethod("createNewEditorInstance");
        assertEquals(Editor.class, m.getReturnType(),
                "createNewEditorInstance 必须返回 Editor（spec §13 唯一 abstract 方法）");
        int mods = m.getModifiers();
        assertTrue(Modifier.isProtected(mods),
                "createNewEditorInstance 必须保持 protected 可见性");
        assertFalse(Modifier.isAbstract(mods),
                "KpMapEditor 必须实现 createNewEditorInstance（非 abstract）");
    }

    /**
     * 验证 initMenus / onPrepareResourceView / onPrepareHistoryView 在 KpMapEditor 中声明，
     * 签名为 protected void ()，匹配 brief Step 3。
     * placeCustomViews 签名为 public void ()。
     */
    @Test
    void requiredMethodsDeclared() throws NoSuchMethodException {
        Method initMenus = KpMapEditor.class.getDeclaredMethod("initMenus");
        assertEquals(void.class, initMenus.getReturnType());
        assertTrue(Modifier.isProtected(initMenus.getModifiers()),
                "initMenus 必须为 protected");

        Method onPrepareResourceView = KpMapEditor.class.getDeclaredMethod("onPrepareResourceView");
        assertEquals(void.class, onPrepareResourceView.getReturnType());
        assertTrue(Modifier.isProtected(onPrepareResourceView.getModifiers()),
                "onPrepareResourceView 必须为 protected");

        Method onPrepareHistoryView = KpMapEditor.class.getDeclaredMethod("onPrepareHistoryView");
        assertEquals(void.class, onPrepareHistoryView.getReturnType());
        assertTrue(Modifier.isProtected(onPrepareHistoryView.getModifiers()),
                "onPrepareHistoryView 必须为 protected");

        Method placeCustomViews = KpMapEditor.class.getDeclaredMethod("placeCustomViews");
        assertEquals(void.class, placeCustomViews.getReturnType());
        assertTrue(Modifier.isPublic(placeCustomViews.getModifiers()),
                "placeCustomViews 必须为 public（供 KpEditorScreen 构造后调用）");
    }

    /**
     * 验证无参构造器存在且为 public。匹配 brief Step 3 中 {@code public KpMapEditor()} 签名。
     */
    @Test
    void publicNoArgConstructorExists() throws NoSuchMethodException {
        Constructor<KpMapEditor> ctor = KpMapEditor.class.getDeclaredConstructor();
        assertTrue(Modifier.isPublic(ctor.getModifiers()),
                "KpMapEditor() 必须为 public");
    }

    /**
     * 验证 Editor 的 public final 字段（top/mainView/menuContainer/buttonContainer/closeButton/
     * inspectorView/historyView/icon/topPlaceholder/rootWindow）与 public 非 final 字段
     * （leftWindow/rightWindow/centerWindow/bottomWindow）均存在，匹配 spec §13 验证矩阵。
     *
     * <p>反射访问父类字段不触发 {@code <clinit>}，可在纯 JVM 下验证。
     */
    @Test
    void editorPublicFieldsExistOnSuperclass() throws NoSuchFieldException {
        // Editor public final 字段（spec §13）
        assertPublicField(Editor.class, "top");
        assertPublicField(Editor.class, "mainView");
        assertPublicField(Editor.class, "menuContainer");
        assertPublicField(Editor.class, "buttonContainer");
        assertPublicField(Editor.class, "closeButton");
        assertPublicField(Editor.class, "inspectorView");
        assertPublicField(Editor.class, "historyView");
        assertPublicField(Editor.class, "icon");
        assertPublicField(Editor.class, "topPlaceholder");
        assertPublicField(Editor.class, "rootWindow");
        // Editor public 非 final 字段
        assertPublicField(Editor.class, "leftWindow");
        assertPublicField(Editor.class, "rightWindow");
        assertPublicField(Editor.class, "centerWindow");
        assertPublicField(Editor.class, "bottomWindow");
    }

    private static void assertPublicField(Class<?> cls, String name) throws NoSuchFieldException {
        Field f = cls.getDeclaredField(name);
        assertTrue(Modifier.isPublic(f.getModifiers()),
                "Editor." + name + " 必须为 public（spec §13）");
    }
}
