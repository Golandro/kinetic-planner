package net.jsmua.kinetic_planner.gui;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link KpRibbonBar} 构造测试。
 *
 * <p>spec §6.4：Ribbon 是 UIElement，含 File/Tools/View Tab 组 + Settings 按钮。
 *
 * <p><b>测试环境限制：</b>{@code KpRibbonBar extends UIElement}，构造时
 * {@code super()} 调用 {@code UIElement.<clinit>}（LDLib2 line 97）
 * → {@code LDLib2Registries.<clinit>} → {@code AutoRegistry.autoRegister}
 * → {@code ModList.get().getAllScanData()}。纯 JVM 测试无 NeoForge 运行时，
 * {@code ModList.get()} 返回 null，导致 {@code ExceptionInInitializerError}。
 * 直接构造 {@link KpRibbonBar} 的两个用例 {@code @Disabled}，靠
 * 运行时验收（{@code gradlew runClient}），与项目既有约定一致
 * （参见 {@code MapPlaceholderViewTest}、{@code ToolPanelViewTest}）。
 *
 * <p>类层次结构（extends UIElement）、public 无参构造器、私有辅助方法
 * {@code addGroupLabel(String)} / {@code addButton(String, Runnable)} 的存在性
 * 通过反射验证，<b>不</b>触发 {@code <clinit>}（JLS §12.4.1：类字面量与
 * {@link Class#getSuperclass()} / {@link Class#isAssignableFrom(Class)} /
 * {@link Class#getDeclaredConstructor(Class...)} /
 * {@link Class#getDeclaredMethod(String, Class...)} 均不引发初始化）。
 */
class KpRibbonBarTest {

    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时；构造靠 gradlew runClient 验收，结构靠下方反射用例验证")
    void constructCreatesRibbonWithTabs() {
        KpRibbonBar ribbon = new KpRibbonBar();
        assertNotNull(ribbon);
        // 运行时验收：构造后应可见 File/Tools/View 三个 Tab 组 + Settings 按钮。
    }

    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时；同上，靠运行时验收")
    void ribbonIsUIElementSubclass() {
        KpRibbonBar ribbon = new KpRibbonBar();
        assertInstanceOf(UIElement.class, ribbon);
    }

    /**
     * 验证 {@link KpRibbonBar} 直接父类为 {@link UIElement}。
     * 反射访问不触发 {@code <clinit>}。
     *
     * <p>spec §6.4 + brief Step 2：{@code public class KpRibbonBar extends UIElement}，
     * 与 {@code ToolPanelView extends View extends UIElement} 不同——Ribbon
     * 不是 editor View，而是顶层菜单栏 UIElement。
     */
    @Test
    void classHierarchyExtendsUIElement() {
        assertEquals(UIElement.class, KpRibbonBar.class.getSuperclass(),
                "KpRibbonBar 必须直接继承 UIElement（spec §6.4 Ribbon 是 UIElement，非 View）");
        assertTrue(UIElement.class.isAssignableFrom(KpRibbonBar.class),
                "KpRibbonBar 必须是 UIElement 子类");
    }

    /**
     * 验证无参构造器存在且为 public。spec §6.4 + brief Step 2：
     * {@code KpMapEditor} 跨包构造 {@code new KpRibbonBar()}，须 public。
     */
    @Test
    void publicNoArgsConstructorExists() throws NoSuchMethodException {
        Constructor<KpRibbonBar> defaultCtor =
                KpRibbonBar.class.getDeclaredConstructor();
        assertTrue(Modifier.isPublic(defaultCtor.getModifiers()),
                "KpRibbonBar() 必须为 public（KpMapEditor 跨包构造）");
    }

    /**
     * 验证私有辅助方法 {@code addGroupLabel(String)} 存在。
     *
     * <p>这是 RED→GREEN 区分占位（空 UIElement）与完整实现（Task 4.3）
     * 的关键 TDD 锚点：占位 KpRibbonBar 无此方法，反射查询会抛
     * {@link NoSuchMethodException}；完整实现按 brief Step 2 添加此方法。
     *
     * <p>反射访问方法元数据不触发 {@code <clinit>}。
     */
    @Test
    void addGroupLabelPrivateMethodExists() throws NoSuchMethodException {
        Method m = KpRibbonBar.class.getDeclaredMethod(
                "addGroupLabel", String.class);
        assertEquals(void.class, m.getReturnType(),
                "addGroupLabel 返回类型必须为 void");
        assertTrue(Modifier.isPrivate(m.getModifiers()),
                "addGroupLabel 必须为 private（内部辅助方法，非 API）");
    }

    /**
     * 验证私有辅助方法 {@code addButton(String, Runnable)} 存在。
     *
     * <p>同上，是 TDD 锚点：占位实现无此方法。
     */
    @Test
    void addButtonPrivateMethodExists() throws NoSuchMethodException {
        Method m = KpRibbonBar.class.getDeclaredMethod(
                "addButton", String.class, Runnable.class);
        assertEquals(void.class, m.getReturnType(),
                "addButton 返回类型必须为 void");
        assertTrue(Modifier.isPrivate(m.getModifiers()),
                "addButton 必须为 private（内部辅助方法，非 API）");
    }

    /**
     * 验证 {@link KpRibbonBar} 不是 abstract（可被 {@code KpMapEditor} 直接实例化）。
     */
    @Test
    void classIsConcrete() {
        assertFalse(Modifier.isAbstract(KpRibbonBar.class.getModifiers()),
                "KpRibbonBar 必须非 abstract（spec §6.4 可被 KpMapEditor 直接实例化）");
        assertTrue(Modifier.isPublic(KpRibbonBar.class.getModifiers()),
                "KpRibbonBar 必须为 public（跨包访问：gui → editor）");
    }
}
