package net.jsmua.kinetic_planner.cadengine;

import com.google.gson.Gson;

/**
 * Theme 的 JSON 序列化/反序列化工具。
 *
 * <p>使用 Gson 序列化 {@link Theme} record 到 JSON 字符串，
 * 并从 JSON 反序列化回 Theme 实例。
 */
public final class ThemeSerializer {

    private static final Gson GSON = new Gson();

    private ThemeSerializer() {}

    /**
     * 将 Theme 序列化为 JSON 字符串。
     */
    public static String serialize(Theme theme) {
        return GSON.toJson(theme);
    }

    /**
     * 从 JSON 字符串反序列化为 Theme。
     */
    public static Theme deserialize(String json) {
        return GSON.fromJson(json, Theme.class);
    }
}
