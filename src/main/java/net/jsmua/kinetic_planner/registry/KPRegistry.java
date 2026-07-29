package net.jsmua.kinetic_planner.registry;

import java.util.*;

/**
 * 带冻结生命周期的类型安全注册表。
 *
 * <p>生命周期：可变注册阶段 -> {@link #freeze()} -> 只读查询阶段。
 * 冻结后再调用 {@link #register} 会抛出 {@link IllegalStateException}。
 *
 * <p>注册顺序通过 {@link LinkedHashMap} 保留。
 *
 * <p>Type-safe registry with a freeze lifecycle.
 *
 * <p>Lifecycle: mutable registration phase -> {@link #freeze()} -> read-only query phase.
 * Attempts to register after freezing throw {@link IllegalStateException}.
 *
 * <p>Registration order is preserved (backed by {@link LinkedHashMap}).
 *
 * @param <T> 条目类型 / entry type
 */
public final class KPRegistry<T> {

    private final LinkedHashMap<KPId, T> entries = new LinkedHashMap<>();
    private boolean frozen = false;

    /**
     * 在给定 id 下注册一个条目。
     *
     * @param id    唯一标识符 / unique identifier
     * @param entry 条目值（不能为 null） / entry value (must not be null)
     * @throws IllegalStateException 如果注册表已冻结或 id 已注册 / if registry is frozen or id already registered
     */
    public void register(KPId id, T entry) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(entry, "entry");
        if (frozen)
            throw new IllegalStateException("Registry already frozen: " + id);
        if (entries.containsKey(id))
            throw new IllegalStateException("Duplicate registration: " + id);
        entries.put(id, entry);
    }

    /** 按 id 查询条目。 / Query entry by id. */
    public Optional<T> get(KPId id) {
        return Optional.ofNullable(entries.get(id));
    }

    /** 如果 id 已注册则返回 true。 / Return true if id is registered. */
    public boolean contains(KPId id) {
        return entries.containsKey(id);
    }

    /** 按注册顺序返回所有已注册值（不可修改）。 / Return all registered values in registration order (unmodifiable). */
    public Collection<T> all() {
        return Collections.unmodifiableCollection(entries.values());
    }

    /** 返回所有已注册 id（不可修改）。 / Return all registered ids (unmodifiable). */
    public Set<KPId> ids() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    /** 按注册顺序返回给定 namespace 下的所有值。 / Return all values in the given namespace, in registration order. */
    public List<T> inNamespace(String namespace) {
        return entries.entrySet().stream()
            .filter(e -> e.getKey().namespace().equals(namespace))
            .map(Map.Entry::getValue)
            .toList();
    }

    /** 如果已调用过 {@link #freeze} 则返回 true。 / Return true if {@link #freeze} has been called. */
    public boolean isFrozen() {
        return frozen;
    }

    /** 冻结注册表。后续 {@link #register} 调用会抛出异常。 / Freeze the registry. Subsequent {@link #register} calls throw. */
    public void freeze() {
        frozen = true;
    }
}
