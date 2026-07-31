package net.jsmua.kinetic_planner.gui.ribbon.registry;

import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonHeaderComponent;
import net.jsmua.kinetic_planner.gui.ribbon.api.RibbonTabDefinition;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * 框架独立可冻结静态注册表 (spec §2.4)。
 *
 * <p>不复用 KPRegistry, 避免框架反向依赖 KP。Mod 不能热加载, 静态注册即可。
 *
 * <p>生命周期: 注册阶段 -> freeze() -> 只读查询阶段。
 * 测试用 {@link #resetForTest()} 重置 (package-private, 仅 test sourceSet 调用)。
 */
public final class RibbonRegistry {

    private static final Map<ResourceLocation, RibbonTabDefinition> TABS = new LinkedHashMap<>();
    private static final Map<ResourceLocation, RibbonHeaderComponent> HEADER_COMPONENTS = new LinkedHashMap<>();
    private static boolean frozen = false;

    private RibbonRegistry() {}

    /** 注册选项卡。冻结后或重复 ID 抛 IllegalStateException。 */
    public static void registerTab(ResourceLocation id, RibbonTabDefinition def) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(def, "def");
        ensureNotFrozen();
        if (TABS.containsKey(id)) {
            throw new IllegalStateException("Tab already registered: " + id);
        }
        TABS.put(id, def);
    }

    /** 注册头部组件。冻结后或重复 ID 抛 IllegalStateException。 */
    public static void registerHeaderComponent(ResourceLocation id, RibbonHeaderComponent comp) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(comp, "comp");
        ensureNotFrozen();
        if (HEADER_COMPONENTS.containsKey(id)) {
            throw new IllegalStateException("Header component already registered: " + id);
        }
        HEADER_COMPONENTS.put(id, comp);
    }

    /** ClientSetup 调用, 冻结后不可再注册。 */
    public static void freeze() {
        frozen = true;
    }

    /** 是否已冻结。 */
    public static boolean isFrozen() {
        return frozen;
    }

    /** 返回按 priority 升序的核心 tab 列表。 */
    public static List<RibbonTabDefinition> getTabsSortedByPriority() {
        var all = new ArrayList<>(TABS.values());
        all.sort(Comparator.comparingInt(RibbonTabDefinition::getPriority));
        return Collections.unmodifiableList(all);
    }

    /** 返回指定 Placement 的头部组件, 按 priority 升序。 */
    public static List<RibbonHeaderComponent> getHeaderComponents(RibbonHeaderComponent.Placement placement) {
        Objects.requireNonNull(placement, "placement");
        var filtered = new ArrayList<RibbonHeaderComponent>();
        for (var comp : HEADER_COMPONENTS.values()) {
            if (comp.getPlacement() == placement) filtered.add(comp);
        }
        filtered.sort(Comparator.comparingInt(RibbonHeaderComponent::getPriority));
        return Collections.unmodifiableList(filtered);
    }

    private static void ensureNotFrozen() {
        if (frozen) {
            throw new IllegalStateException("RibbonRegistry is frozen; cannot register");
        }
    }

    /** 测试专用: 重置注册表状态。仅 test sourceSet 调用。 */
    static void resetForTest() {
        TABS.clear();
        HEADER_COMPONENTS.clear();
        frozen = false;
    }
}
