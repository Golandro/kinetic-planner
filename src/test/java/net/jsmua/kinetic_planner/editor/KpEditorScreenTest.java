package net.jsmua.kinetic_planner.editor;

import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import net.jsmua.kinetic_planner.config.KpClientState;
import net.jsmua.kinetic_planner.gui.event.KpUIEventForwarder;
import net.jsmua.kinetic_planner.gui.editor.KpEditorScreen;
import net.jsmua.kinetic_planner.gui.editor.KpMapEditor;
import net.jsmua.kinetic_planner.mapadapter.MapOverlayContextProvider;
import net.minecraft.client.gui.screens.Screen;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link KpEditorScreen} 类层次与方法契约测试。
 *
 * <p>spec §6.2：编辑模式 Screen 壳，{@code extends Screen implements MapOverlayContextProvider}，
 * 持有 {@link KpMapEditor} 与 {@link ModularUI}，三层渲染（地图层 → CAD 编辑层 → Editor UI 层）。
 *
 * <p><b>测试环境限制：</b>{@code KpEditorScreen extends Screen}（MC 类），构造时调用
 * {@code super(Component.literal(...))} 会触发 MC {@code Screen.<clinit>} 链路；
 * 同时构造函数内调用 {@code new KpMapEditor()}（间接 {@code UIElement.<clinit>}，
 * 同 Task 2.2/2.3 的 {@code ModList.get()} 返回 null 问题）。因此直接实例化的用例
 * {@code @Disabled}，靠运行时验收（{@code gradlew runClient}），与项目既有约定一致
 * （参见 {@code RailwayDataAccessTest}、{@code MapPlaceholderViewTest}、{@code KpMapEditorTest}）。
 *
 * <p>类层次结构（extends Screen implements MapOverlayContextProvider）、必需方法声明、
 * 字段类型、构造器可见性通过反射验证，<b>不</b>触发 {@code <clinit>}
 * （JLS §12.4.1：{@link Class#getSuperclass()} / {@link Class#getDeclaredMethod} /
 * {@link Class#getDeclaredField} / {@link Class#getDeclaredConstructor} 等结构查询
 * 均不引发初始化）。
 */
class KpEditorScreenTest {

    @AfterEach
    void resetEditMode() {
        // 防御性重置：staticCreateSetsEditMode 的 @Disabled 用例若未来在 mock 环境下启用，
        // 不应污染后续测试。当前 @Disabled 不会触发 setEditMode(true)，但保留以保稳健。
        KpClientState.setEditMode(false);
    }

    @Test
    @Disabled("Screen.<clinit> + UIElement.<clinit> 需要 NeoForge 运行时；构造靠 gradlew runClient 验收")
    void constructWithoutCrash() {
        // 直接 new GuiMap(...) 在纯 JVM 不可行（Xaero 类亦依赖 MC 运行时），
        // 此处传 null 仅验证构造路径不抛出（实际验收在 runClient）。
        KpEditorScreen screen = KpEditorScreen.create(null);
        assertNotNull(screen);
    }

    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时；同上，靠运行时验收")
    void getGuiMapReturnsConstructorArg() {
        // 运行时验收：传入 mock GuiMap，断言 getGuiMap 返回同一引用
    }

    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时；同上，靠运行时验收")
    void getEditorReturnsNonNullAfterConstruction() {
        // 运行时验收：构造后 getEditor() 返回非 null KpMapEditor
    }

    @Test
    @Disabled("UIElement.<clinit> 需要 NeoForge 运行时；同上，靠运行时验收")
    void isPauseScreenReturnsFalse() {
        // 运行时验收：isPauseScreen() 返回 false，不暂停单机游戏
    }

    /**
     * 验证 {@link KpEditorScreen} 直接父类为 MC {@link Screen}，
     * 且实现 {@link MapOverlayContextProvider} 接口。反射访问不触发 {@code <clinit>}。
     */
    @Test
    void classHierarchyExtendsScreenImplementsProvider() {
        assertEquals(Screen.class, KpEditorScreen.class.getSuperclass(),
                "KpEditorScreen 必须直接继承 MC Screen");
        assertTrue(MapOverlayContextProvider.class.isAssignableFrom(KpEditorScreen.class),
                "KpEditorScreen 必须实现 MapOverlayContextProvider（spec §6.2 / §6.10）");
    }

    /**
     * 验证静态工厂 {@code create(GuiMap)} 存在且为 public static，
     * 返回类型为 {@link KpEditorScreen}（spec §7.1）。
     */
    @Test
    void createStaticFactorySignature() throws NoSuchMethodException {
        Method m = KpEditorScreen.class.getDeclaredMethod("create", xaero.map.gui.GuiMap.class);
        assertEquals(KpEditorScreen.class, m.getReturnType(),
                "create 必须返回 KpEditorScreen");
        int mods = m.getModifiers();
        assertTrue(Modifier.isPublic(mods), "create 必须为 public");
        assertTrue(Modifier.isStatic(mods), "create 必须为 static");
    }

    /**
     * 验证私有构造器 {@code KpEditorScreen(GuiMap)} 存在且为 private，
     * 强制通过静态工厂 {@code create} 进入（spec §7.1）。
     */
    @Test
    void privateConstructorExists() throws NoSuchMethodException {
        Constructor<KpEditorScreen> ctor =
                KpEditorScreen.class.getDeclaredConstructor(xaero.map.gui.GuiMap.class);
        assertTrue(Modifier.isPrivate(ctor.getModifiers()),
                "KpEditorScreen(GuiMap) 必须为 private（强制走 create 工厂）");
    }

    /**
     * 验证必需方法声明与签名（spec §6.2 + brief Step 1）。
     *
     * <ul>
     *   <li>{@code getGuiMap()} → public GuiMap（实现 MapOverlayContextProvider）</li>
     *   <li>{@code getEditor()} → public KpMapEditor（暴露给 Phase 4 Ribbon / Phase 6 EditLayerRenderer）</li>
     *   <li>{@code init()} → protected void（重写 Screen.init，调用 modularUI.init）</li>
     *   <li>{@code render(GuiGraphics, int, int, float)} → public void（三层渲染入口）</li>
     *   <li>{@code onClose()} → public void（清理 editMode + 切回 GuiMap）</li>
     *   <li>{@code isPauseScreen()} → public boolean（返回 false）</li>
     * </ul>
     */
    @Test
    void requiredMethodsDeclared() throws NoSuchMethodException {
        Method getGuiMap = KpEditorScreen.class.getDeclaredMethod("getGuiMap");
        assertEquals(xaero.map.gui.GuiMap.class, getGuiMap.getReturnType());
        assertTrue(Modifier.isPublic(getGuiMap.getModifiers()));

        Method getEditor = KpEditorScreen.class.getDeclaredMethod("getEditor");
        assertEquals(KpMapEditor.class, getEditor.getReturnType());
        assertTrue(Modifier.isPublic(getEditor.getModifiers()));

        Method init = KpEditorScreen.class.getDeclaredMethod("init");
        assertEquals(void.class, init.getReturnType());
        assertTrue(Modifier.isProtected(init.getModifiers()),
                "init 必须为 protected（Screen.init 同可见性）");

        Method render = KpEditorScreen.class.getDeclaredMethod(
                "render",
                net.minecraft.client.gui.GuiGraphics.class, int.class, int.class, float.class);
        assertEquals(void.class, render.getReturnType());
        assertTrue(Modifier.isPublic(render.getModifiers()));

        Method onClose = KpEditorScreen.class.getDeclaredMethod("onClose");
        assertEquals(void.class, onClose.getReturnType());
        assertTrue(Modifier.isPublic(onClose.getModifiers()));

        Method isPauseScreen = KpEditorScreen.class.getDeclaredMethod("isPauseScreen");
        assertEquals(boolean.class, isPauseScreen.getReturnType());
        assertTrue(Modifier.isPublic(isPauseScreen.getModifiers()));
    }

    /**
     * 验证 brief Step 1 中声明的四个 private final 字段存在且类型正确。
     * 反射访问字段元数据不触发 {@code <clinit>}。
     */
    @Test
    void requiredFieldsDeclared() throws NoSuchFieldException {
        Field guiMap = KpEditorScreen.class.getDeclaredField("guiMap");
        assertEquals(xaero.map.gui.GuiMap.class, guiMap.getType());
        assertTrue(Modifier.isPrivate(guiMap.getModifiers()));
        assertTrue(Modifier.isFinal(guiMap.getModifiers()));

        Field editor = KpEditorScreen.class.getDeclaredField("editor");
        assertEquals(KpMapEditor.class, editor.getType());
        assertTrue(Modifier.isPrivate(editor.getModifiers()));
        assertTrue(Modifier.isFinal(editor.getModifiers()));

        Field modularUI = KpEditorScreen.class.getDeclaredField("modularUI");
        assertEquals(ModularUI.class, modularUI.getType());
        assertTrue(Modifier.isPrivate(modularUI.getModifiers()));
        assertTrue(Modifier.isFinal(modularUI.getModifiers()));

        Field eventForwarder = KpEditorScreen.class.getDeclaredField("eventForwarder");
        assertEquals(KpUIEventForwarder.class, eventForwarder.getType());
        assertTrue(Modifier.isPrivate(eventForwarder.getModifiers()));
        assertTrue(Modifier.isFinal(eventForwarder.getModifiers()));
    }

    /**
     * 验证 {@link KpEditorScreen} 不是 abstract（可被静态工厂直接实例化）。
     */
    @Test
    void classIsConcrete() {
        assertFalse(Modifier.isAbstract(KpEditorScreen.class.getModifiers()),
                "KpEditorScreen 必须非 abstract（spec §6.2 可被 create 工厂实例化）");
        assertTrue(Modifier.isPublic(KpEditorScreen.class.getModifiers()),
                "KpEditorScreen 必须为 public（跨包访问：Mixin/命令触发 create）");
    }
}
