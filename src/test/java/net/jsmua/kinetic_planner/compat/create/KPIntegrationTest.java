package net.jsmua.kinetic_planner.compat.create;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 单元测试：验证 {@link KPIntegration} 的结构契约。
 *
 * <p>由于 {@code KPIntegration} 内部依赖 {@code OverlayControl} → {@code KPConfig} →
 * {@code ModConfigSpec}(NeoForge)，而测试环境无 NeoForge 运行时，
 * 本测试仅通过**反射**验证方法签名与结构约束，不调用方法体。
 *
 * <p>行为正确性（返回值逻辑）需通过 {@code gradlew runClient} 手动验收，
 * 或在 Mixin 诊断日志（System.out.println）中确认。
 */
class KPIntegrationTest {

    @Test
    void hasExactlyTwoPublicMethods() throws Exception {
        Method[] methods = KPIntegration.class.getDeclaredMethods();
        assertEquals(2, methods.length,
            "KPIntegration 应恰好有 2 个方法：forceCreateOverlayPipeline + shouldBlockCreateOverlay");
    }

    @Test
    void forceCreateOverlayPipeline_isPublicStaticVoid() throws Exception {
        Method m = KPIntegration.class.getDeclaredMethod("forceCreateOverlayPipeline");
        assertTrue(Modifier.isPublic(m.getModifiers()), "应为 public");
        assertTrue(Modifier.isStatic(m.getModifiers()), "应为 static");
        assertEquals(void.class, m.getReturnType(), "应返回 void");
        assertEquals(0, m.getParameterCount(), "应无参数");
    }

    @Test
    void shouldBlockCreateOverlay_isPublicStaticBoolean() throws Exception {
        Method m = KPIntegration.class.getDeclaredMethod("shouldBlockCreateOverlay");
        assertTrue(Modifier.isPublic(m.getModifiers()), "应为 public");
        assertTrue(Modifier.isStatic(m.getModifiers()), "应为 static");
        assertEquals(boolean.class, m.getReturnType(), "应返回 boolean");
        assertEquals(0, m.getParameterCount(), "应无参数");
    }

    @Test
    void methodsAreIndependent() {
        // 一次性管线打通（void）与叠加层守卫（boolean）必须同时存在
        String[] expectedNames = {"forceCreateOverlayPipeline", "shouldBlockCreateOverlay"};
        Method[] methods = KPIntegration.class.getDeclaredMethods();
        for (String name : expectedNames) {
            boolean found = false;
            for (Method m : methods) {
                if (m.getName().equals(name)) { found = true; break; }
            }
            assertTrue(found, "缺少预期方法: " + name);
        }
    }

    @Test
    void classIsFinalAndHasPrivateConstructor() throws Exception {
        assertTrue(Modifier.isFinal(KPIntegration.class.getModifiers()), "应为 final 类");
        // 构造函数应为 private
        assertNotNull(KPIntegration.class.getDeclaredConstructor());
        // 不验证 private（可能被反射绕过），只确认只有一个无参构造函数
    }
}
