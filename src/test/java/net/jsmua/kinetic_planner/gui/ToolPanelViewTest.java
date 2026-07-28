package net.jsmua.kinetic_planner.gui;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import net.jsmua.kinetic_planner.gui.editor.EditToolState;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ToolPanelView} 构造与工具联动测试。
 *
 * <p>spec §6.5：左侧 View，含工具按钮列表，点击触发
 * {@link EditToolState#setCurrentTool(EditToolState.Tool)}。
 *
 * <p><b>测试环境限制：</b>{@code ToolPanelView extends View extends UIElement}，
 * 构造时 {@code super()} 调用 {@code UIElement.<clinit>}（LDLib2 line 97）
 * → {@code LDLib2Registries.<clinit>} → {@code AutoRegistry.autoRegister}
 * → {@code ModList.get().getAllScanData()}。纯 JVM 测试无 NeoForge 运行时，
 * {@code ModList.get()} 返回 null，导致 {@code ExceptionInInitializerError}。
 * 直接构造 {@link ToolPanelView} 的两个用例 {@code @Disabled}，靠
 * 运行时验收（{@code gradlew runClient}），与项目既有约定一致
 * （参见 {@code MapPlaceholderViewTest}、{@code KpEditorScreenTest}）。
 *
 * <p>类层次结构（extends View extends UIElement）、public 无参构造器、
 * 私有 {@code addToolButton(String, Tool)} 辅助方法的存在性通过反射验证，
 * <b>不</b>触发 {@code <clinit>}（JLS §12.4.1：类字面量与
 * {@link Class#getSuperclass()} / {@link Class#isAssignableFrom(Class)} /
 * {@link Class#getDeclaredConstructor(Class...)} /
 * {@link Class#getDeclaredMethod(String, Class...)} 均不引发初始化）。
 */
class ToolPanelViewTest {

    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时；构造靠 gradlew runClient 验收，结构靠下方反射用例验证")
    void constructCreatesViewWithButtons() {
        ToolPanelView view = new ToolPanelView();
        assertNotNull(view);
        // 运行时验收：构造后应可见 5 个工具按钮（NAV/SELECT/LINE/BEZIER/SNAP），
        // 点击触发 EditToolState.setCurrentTool。
    }

    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时；同上，靠运行时验收")
    void viewIsUIElementSubclass() {
        ToolPanelView view = new ToolPanelView();
        assertInstanceOf(UIElement.class, view);
    }

    /**
     * 验证 {@link ToolPanelView} 直接父类为 {@link View}，
     * 间接父类为 {@link UIElement}。反射访问不触发 {@code <clinit>}。
     */
    @Test
    void classHierarchyExtendsViewAndUIElement() {
        assertEquals(View.class, ToolPanelView.class.getSuperclass(),
                "ToolPanelView 必须直接继承 View");
        assertEquals(UIElement.class, View.class.getSuperclass(),
                "View 必须直接继承 UIElement（LDLib2 契约 spec §13）");
        assertTrue(UIElement.class.isAssignableFrom(ToolPanelView.class),
                "ToolPanelView 必须是 UIElement 子类");
    }

    /**
     * 验证无参构造器存在且为 public。spec §6.5 + brief Step 2：
     * {@code KpMapEditor.placeView(new ToolPanelView(), ...)} 跨包构造，须 public。
     */
    @Test
    void publicNoArgsConstructorExists() throws NoSuchMethodException {
        Constructor<ToolPanelView> defaultCtor =
                ToolPanelView.class.getDeclaredConstructor();
        assertTrue(Modifier.isPublic(defaultCtor.getModifiers()),
                "ToolPanelView() 必须为 public（KpMapEditor 跨包构造）");
    }

    /**
     * 验证私有辅助方法 {@code addToolButton(String, EditToolState.Tool)} 存在。
     *
     * <p>这是 RED→GREEN 区分占位（Task 2.3 空 View）与完整实现（Task 4.2）
     * 的关键 TDD 锚点：占位 ToolPanelView 无此方法，反射查询会抛
     * {@link NoSuchMethodException}；完整实现按 brief Step 2 添加此方法。
     *
     * <p>反射访问方法元数据不触发 {@code <clinit>}。
     */
    @Test
    void addToolButtonPrivateMethodExists() throws NoSuchMethodException {
        Method m = ToolPanelView.class.getDeclaredMethod(
                "addToolButton", String.class, EditToolState.Tool.class);
        assertEquals(void.class, m.getReturnType(),
                "addToolButton 返回类型必须为 void");
        assertTrue(Modifier.isPrivate(m.getModifiers()),
                "addToolButton 必须为 private（内部辅助方法，非 API）");
    }

    /**
     * 验证 {@link ToolPanelView} 不是 abstract（可被 {@code KpMapEditor} 直接实例化）。
     */
    @Test
    void classIsConcrete() {
        assertFalse(Modifier.isAbstract(ToolPanelView.class.getModifiers()),
                "ToolPanelView 必须非 abstract（spec §6.5 可被 KpMapEditor 直接实例化）");
        assertTrue(Modifier.isPublic(ToolPanelView.class.getModifiers()),
                "ToolPanelView 必须为 public（跨包访问：gui → editor）");
    }
}
