package net.jsmua.kinetic_planner.data;

/**
 * Provider 配置的读写策略接口。
 *
 * <p>每个地图 provider 注册一个 binding，封装如何从 TOML 配置读取
 * {@link ProviderConfig} 以及如何将修改写回。此接口替代
 * {@code KPConfig} 中硬编码的 switch 语句。
 *
 * <p>接口位于 main sourceSet（无客户端依赖）。实现位于 client sourceSet，
 * 在那里持有 {@code ModConfigSpec} 的 value holder 引用。
 *
 * <p>Per-provider config read/write strategy.
 *
 * <p>Each map provider registers a binding that encapsulates how to read
 * its TOML config values into a {@link ProviderConfig} and how to write
 * changes back. This replaces hardcoded switch statements in KPConfig.
 *
 * <p>The interface is in main sourceSet (no client deps). Implementations
 * live in client sourceSet where they capture references to
 * {@code ModConfigSpec} value holders.
 */
public interface ProviderConfigBinding {

    /**
     * 该 binding 服务的 provider mod ID（如 "xaeroworldmap"）。
     *
     * @return provider mod ID
     */
    String modId();

    /**
     * 将当前配置值读取为 {@link ProviderConfig}。
     *
     * @return 当前 provider 配置（永不为 null） / current provider config (never null)
     */
    ProviderConfig read();

    /**
     * 将 enabled 标志写入配置。
     *
     * @param enabled 新的启用状态 / new enabled state
     * @return true 如果写入成功 / true if write succeeded
     */
    boolean writeEnabled(boolean enabled);

    /**
     * 将命名参数写入配置。
     *
     * @param param 参数名（"priority"、"lineWidthScale"、"alphaScale"、"dashed"）
     *             / parameter name ("priority", "lineWidthScale", "alphaScale", "dashed")
     * @param value 新值的字符串形式 / string form of the new value
     * @return true 如果参数名合法且值解析成功
     *         / true if parameter name is valid and value parsed successfully
     */
    boolean writeParam(String param, String value);

    /**
     * 严格布尔解析器——只接受 "true" 或 "false"（大小写不敏感）。
     *
     * <p>所有 binding 实现共享此方法验证布尔参数。
     *
     * <p>Strict boolean parser — accepts only "true" or "false" (case-insensitive).
     *
     * <p>Shared by all binding implementations to validate boolean parameters.
     *
     * @param value 待校验的字符串 / string to validate
     * @return true 如果是严格布尔值 / true if strictly boolean
     */
    static boolean isStrictBool(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }
}
