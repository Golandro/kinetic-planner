package net.jsmua.kinetic_planner.registry;

import java.util.Objects;

/**
 * 模组内部标识符，格式为 {@code namespace:path}。
 *
 * <p>KP 内置内容使用 {@code "kp"} namespace，第三方模组使用自己的 modId 作为 namespace。
 *
 * <p><b>注意：</b>截至本次实现，所有注册都通过 {@link #kp(String)} 使用 {@code "kp"} namespace。
 * namespace 字段保留给未来第三方模组自注册使用，当前不影响行为。
 *
 * <p>Mod-internal identifier, format {@code namespace:path}.
 *
 * <p>KP built-in content uses {@code "kp"} namespace.
 * Third-party mods use their own modId as namespace.
 *
 * <p><b>Note:</b> As of this implementation, all registrations use the {@code "kp"}
 * namespace via {@link #kp(String)}. The namespace field is reserved for future
 * third-party mod self-registration and does not affect current behavior.
 *
 * @param namespace 标识符 namespace（如 "kp"、"ftbchunks”） / identifier namespace (e.g. "kp", "ftbchunks")
 * @param path      标识符 path（如 "xaeroworldmap"、"select”） / identifier path (e.g. "xaeroworldmap", "select")
 */
public record KPId(String namespace, String path) implements Comparable<KPId> {

    /** 创建 KP namespace 的标识符。 / Create a KP-namespaced identifier. */
    public static KPId kp(String path) {
        return new KPId("kp", path);
    }

    /** 创建自定义 namespace 的标识符。 / Create a custom-namespaced identifier. */
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
