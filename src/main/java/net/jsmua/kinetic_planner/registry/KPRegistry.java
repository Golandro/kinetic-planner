package net.jsmua.kinetic_planner.registry;

import java.util.*;

/**
 * Type-safe registry with a freeze lifecycle.
 *
 * <p>Lifecycle: mutable registration phase -> {@link #freeze()} -> read-only query phase.
 * Attempts to register after freezing throw {@link IllegalStateException}.
 *
 * <p>Registration order is preserved (backed by {@link LinkedHashMap}).
 *
 * @param <T> entry type
 */
public final class KPRegistry<T> {

    private final LinkedHashMap<KPId, T> entries = new LinkedHashMap<>();
    private boolean frozen = false;

    /**
     * Register an entry under the given id.
     *
     * @param id    unique identifier
     * @param entry entry value (must not be null)
     * @throws IllegalStateException if registry is frozen or id already registered
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

    /** Query entry by id. */
    public Optional<T> get(KPId id) {
        return Optional.ofNullable(entries.get(id));
    }

    /** Return true if id is registered. */
    public boolean contains(KPId id) {
        return entries.containsKey(id);
    }

    /** Return all registered values in registration order (unmodifiable). */
    public Collection<T> all() {
        return Collections.unmodifiableCollection(entries.values());
    }

    /** Return all registered ids (unmodifiable). */
    public Set<KPId> ids() {
        return Collections.unmodifiableSet(entries.keySet());
    }

    /** Return all values in the given namespace, in registration order. */
    public List<T> inNamespace(String namespace) {
        return entries.entrySet().stream()
            .filter(e -> e.getKey().namespace().equals(namespace))
            .map(Map.Entry::getValue)
            .toList();
    }

    /** Return true if {@link #freeze} has been called. */
    public boolean isFrozen() {
        return frozen;
    }

    /** Freeze the registry. Subsequent {@link #register} calls throw. */
    public void freeze() {
        frozen = true;
    }
}
