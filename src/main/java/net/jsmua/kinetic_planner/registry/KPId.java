package net.jsmua.kinetic_planner.registry;

import java.util.Objects;

/**
 * Mod-internal identifier, format {@code namespace:path}.
 *
 * <p>KP built-in content uses {@code "kp"} namespace.
 * Third-party mods use their own modId as namespace.
 *
 * <p><b>Note:</b> As of this implementation, all registrations use the {@code "kp"}
 * namespace via {@link #kp(String)}. The namespace field is reserved for future
 * third-party mod self-registration and does not affect current behavior.
 *
 * @param namespace identifier namespace (e.g. "kp", "ftbchunks")
 * @param path identifier path (e.g. "xaeroworldmap", "select")
 */
public record KPId(String namespace, String path) implements Comparable<KPId> {

    /** Create a KP-namespaced identifier. */
    public static KPId kp(String path) {
        return new KPId("kp", path);
    }

    /** Create a custom-namespaced identifier. */
    public static KPId of(String namespace, String path) {
        return new KPId(namespace, path);
    }

    public KPId {
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(path, "path");
        if (namespace.isEmpty())
            throw new IllegalArgumentException("namespace must not be empty");
        if (path.isEmpty())
            throw new IllegalArgumentException("path must not be empty");
    }

    @Override
    public int compareTo(KPId o) {
        int c = namespace.compareTo(o.namespace);
        return c != 0 ? c : path.compareTo(o.path);
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
