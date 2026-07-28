package net.jsmua.kinetic_planner.gui;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link MapPlaceholderView} 构造契约测试。
 *
 * <p>View 默认无背景纹理（spec §6.6 + LDLib2 验证）。
 * View 继承 UIElement，构造后应可被添加到容器。
 *
 * <p><b>测试环境限制：</b>{@code UIElement.<clinit>}（LDLib2 line 97）触发
 * {@code LDLib2Registries.<clinit>} → {@code AutoRegistry.autoRegister}
 * → {@code ModList.get().getAllScanData()}。纯 JVM 测试无 NeoForge 运行时，
 * {@code ModList.get()} 返回 null，导致 {@code ExceptionInInitializerError}。
 * 直接构造 {@code MapPlaceholderView} 的两个用例 {@code @Disabled}，靠
 * 运行时验收（{@code gradlew runClient}），与项目既有约定一致
 * （参见 {@code RailwayDataAccessTest}）。
 *
 * <p>类层次结构（extends View extends UIElement）与 public 构造器签名
 * 通过反射验证，<b>不</b>触发 {@code <clinit>}（JLS §12.4.1：类字面量与
 * {@link Class#getSuperclass()} / {@link Class#isAssignableFrom(Class)} /
 * {@link Class#getDeclaredConstructor(Class...)} 均不引发初始化）。
 */
class MapPlaceholderViewTest {

    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时；构造靠 gradlew runClient 验收，类层次靠下方反射用例验证")
    void constructCreatesViewWithNoBackground() {
        MapPlaceholderView view = new MapPlaceholderView();
        assertNotNull(view);
        // View 默认宽高 100%（继承自 View 构造函数）
        // 不抛异常即可
    }

    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时；同上，靠运行时验收")
    void viewIsUIElementSubclass() {
        MapPlaceholderView view = new MapPlaceholderView();
        assertInstanceOf(UIElement.class, view);
    }

    /**
     * 验证 {@link MapPlaceholderView} 直接父类为 {@link View}，
     * 间接父类为 {@link UIElement}。反射访问不触发 {@code <clinit>}。
     */
    @Test
    void classHierarchyExtendsViewAndUIElement() {
        assertEquals(View.class, MapPlaceholderView.class.getSuperclass(),
                "MapPlaceholderView 必须直接继承 View");
        assertEquals(UIElement.class, View.class.getSuperclass(),
                "View 必须直接继承 UIElement（LDLib2 契约 spec §13）");
        assertTrue(UIElement.class.isAssignableFrom(MapPlaceholderView.class),
                "MapPlaceholderView 必须是 UIElement 子类");
    }

    /**
     * 验证无参构造器与 {@code (String name)} 构造器均存在且为 public。
     * 与 brief Step 3 中两个构造器签名一一对应。
     */
    @Test
    void publicConstructorsExist() throws NoSuchMethodException {
        Constructor<MapPlaceholderView> defaultCtor =
                MapPlaceholderView.class.getDeclaredConstructor();
        assertTrue(Modifier.isPublic(defaultCtor.getModifiers()),
                "MapPlaceholderView() 必须为 public");

        Constructor<MapPlaceholderView> nameCtor =
                MapPlaceholderView.class.getDeclaredConstructor(String.class);
        assertTrue(Modifier.isPublic(nameCtor.getModifiers()),
                "MapPlaceholderView(String) 必须为 public");
    }
}
