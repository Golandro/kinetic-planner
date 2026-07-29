# Registry Framework & Provider Decoupling Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate hardcoded switch statements in KPConfig and static initialization in MapOverlayDispatcher by introducing a generic registry framework and provider config binding strategy.

**Architecture:** A generic `KPRegistry<T>` type-safe registry (main sourceSet, freezeable) replaces scattered static Maps and switch statements. `ProviderConfigBinding` (main interface, client implementations) encapsulates per-provider TOML read/write logic, eliminating the 4 switch branches in KPConfig. `MapProviderFactory` (client interface) replaces the static `new XaeroMapOverlayProvider()` block in MapOverlayDispatcher with a registry-driven approach.

**Tech Stack:** Java 21, NeoForge 1.21.1, ModDev, JUnit 5, Gradle

---

## File Structure

### New Files (main sourceSet)

| File | Responsibility |
|------|---------------|
| `src/main/java/net/jsmua/kinetic_planner/registry/KPId.java` | Mod-internal identifier record (namespace:path) |
| `src/main/java/net/jsmua/kinetic_planner/registry/KPRegistry.java` | Generic freezeable registry |
| `src/main/java/net/jsmua/kinetic_planner/data/ProviderConfigBinding.java` | Interface for per-provider config read/write |

### New Files (client sourceSet)

| File | Responsibility |
|------|---------------|
| `src/client/java/net/jsmua/kinetic_planner/mapadapter/MapProviderFactory.java` | Factory interface for creating MapOverlayProvider instances |
| `src/client/java/net/jsmua/kinetic_planner/mapadapter/XaeroMapProviderFactory.java` | Xaero factory implementation |
| `src/client/java/net/jsmua/kinetic_planner/mapadapter/JourneyMapMapProviderFactory.java` | JourneyMap factory implementation |
| `src/client/java/net/jsmua/kinetic_planner/config/XaeroProviderConfigBinding.java` | Xaero config binding (reads/writes TOML values) |
| `src/client/java/net/jsmua/kinetic_planner/config/JourneyMapProviderConfigBinding.java` | JourneyMap config binding |
| `src/client/java/net/jsmua/kinetic_planner/mapadapter/MapProviderRegistry.java` | Client-side registry for MapProviderFactory instances |

### New Files (test sourceSet)

| File | Responsibility |
|------|---------------|
| `src/test/java/net/jsmua/kinetic_planner/registry/KPIdTest.java` | KPId unit tests |
| `src/test/java/net/jsmua/kinetic_planner/registry/KPRegistryTest.java` | KPRegistry unit tests |
| `src/test/java/net/jsmua/kinetic_planner/data/ProviderConfigBindingTest.java` | ProviderConfigBinding interface contract tests |

### Modified Files

| File | Changes |
|------|---------|
| `src/client/java/net/jsmua/kinetic_planner/config/KPConfig.java` | Replace 3 switch statements with binding registry lookups; register bindings in static block |
| `src/client/java/net/jsmua/kinetic_planner/mapadapter/MapOverlayDispatcher.java` | Replace static PROVIDERS block with factory registry; lazy-init providers; add getRegisteredModIds() |
| `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java` | Register factories and bindings during onClientSetup |
| `src/client/java/net/jsmua/kinetic_planner/config/ProviderConfigControl.java` | Update listProviders() and isKnownModId() to use getRegisteredModIds() |
| `src/client/java/net/jsmua/kinetic_planner/gui/config/KpConfigUIFactory.java` | Update provider tab loop to use getRegisteredModIds() |

---

### Task 1: KPId — Mod-Internal Identifier

**Files:**
- Create: `src/main/java/net/jsmua/kinetic_planner/registry/KPId.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/registry/KPIdTest.java`

- [ ] **Step 1: Write the failing test**

```java
package net.jsmua.kinetic_planner.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class KPIdTest {

    @Test
    void kpFactoryCreatesKpNamespaceId() {
        KPId id = KPId.kp("xaeroworldmap");
        assertEquals("kp", id.namespace());
        assertEquals("xaeroworldmap", id.path());
    }

    @Test
    void ofCreatesCustomNamespaceId() {
        KPId id = KPId.of("ftbchunks", "default");
        assertEquals("ftbchunks", id.namespace());
        assertEquals("default", id.path());
    }

    @Test
    void toStringProducesNamespaceColonPath() {
        KPId id = KPId.kp("journeymap");
        assertEquals("kp:journeymap", id.toString());
    }

    @Test
    void compareToOrdersByNamespaceThenPath() {
        KPId a = KPId.of("aaa", "zzz");
        KPId b = KPId.of("bbb", "aaa");
        assertTrue(a.compareTo(b) < 0);
        assertTrue(b.compareTo(a) > 0);
    }

    @Test
    void compareToEqualWhenSameNamespaceAndPath() {
        KPId a = KPId.kp("select");
        KPId b = KPId.kp("select");
        assertEquals(0, a.compareTo(b));
    }

    @Test
    void equalsAndHashCodeWork() {
        KPId a = KPId.kp("overlay");
        KPId b = KPId.kp("overlay");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, KPId.kp("theme"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.jsmua.kinetic_planner.registry.KPIdTest" 2>&1 | tail -5`
Expected: FAIL with compilation error (KPId does not exist)

- [ ] **Step 3: Write minimal implementation**

```java
package net.jsmua.kinetic_planner.registry;

import java.util.Objects;

/**
 * Mod-internal identifier, format {@code namespace:path}.
 *
 * <p>KP built-in content uses {@code "kp"} namespace.
 * Third-party mods use their own modId as namespace.
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.jsmua.kinetic_planner.registry.KPIdTest" 2>&1 | tail -5`
Expected: PASS — 6 tests passed

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/jsmua/kinetic_planner/registry/KPId.java src/test/java/net/jsmua/kinetic_planner/registry/KPIdTest.java
git commit -m "feat: add KPId mod-internal identifier record"
```

---

### Task 2: KPRegistry — Generic Freezeable Registry

**Files:**
- Create: `src/main/java/net/jsmua/kinetic_planner/registry/KPRegistry.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/registry/KPRegistryTest.java`

- [ ] **Step 1: Write the failing test**

```java
package net.jsmua.kinetic_planner.registry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class KPRegistryTest {

    @Test
    void registerAddsEntry() {
        KPRegistry<String> reg = new KPRegistry<>();
        reg.register(KPId.kp("alpha"), "value-a");
        assertEquals("value-a", reg.get(KPId.kp("alpha")).orElseThrow());
    }

    @Test
    void getReturnsEmptyForMissingId() {
        KPRegistry<String> reg = new KPRegistry<>();
        assertTrue(reg.get(KPId.kp("missing")).isEmpty());
    }

    @Test
    void registerDuplicateThrows() {
        KPRegistry<String> reg = new KPRegistry<>();
        reg.register(KPId.kp("dup"), "first");
        assertThrows(IllegalStateException.class,
            () -> reg.register(KPId.kp("dup"), "second"));
    }

    @Test
    void registerAfterFreezeThrows() {
        KPRegistry<String> reg = new KPRegistry<>();
        reg.register(KPId.kp("a"), "v");
        reg.freeze();
        assertThrows(IllegalStateException.class,
            () -> reg.register(KPId.kp("b"), "v"));
    }

    @Test
    void isFrozenFalseUntilFreeze() {
        KPRegistry<String> reg = new KPRegistry<>();
        assertFalse(reg.isFrozen());
        reg.freeze();
        assertTrue(reg.isFrozen());
    }

    @Test
    void allReturnsUnmodifiableCollection() {
        KPRegistry<String> reg = new KPRegistry<>();
        reg.register(KPId.kp("x"), "1");
        reg.register(KPId.kp("y"), "2");
        var all = reg.all();
        assertEquals(2, all.size());
        assertThrows(UnsupportedOperationException.class, () -> all.add("3"));
    }

    @Test
    void inNamespaceFiltersByNamespace() {
        KPRegistry<String> reg = new KPRegistry<>();
        reg.register(KPId.kp("a"), "kp-a");
        reg.register(KPId.kp("b"), "kp-b");
        reg.register(KPId.of("other", "c"), "other-c");
        List<String> kpEntries = reg.inNamespace("kp");
        assertEquals(2, kpEntries.size());
        assertTrue(kpEntries.contains("kp-a"));
        assertTrue(kpEntries.contains("kp-b"));
    }

    @Test
    void containsReturnsTrueForRegistered() {
        KPRegistry<String> reg = new KPRegistry<>();
        reg.register(KPId.kp("exists"), "v");
        assertTrue(reg.contains(KPId.kp("exists")));
        assertFalse(reg.contains(KPId.kp("nope")));
    }

    @Test
    void idsReturnsUnmodifiableKeySet() {
        KPRegistry<String> reg = new KPRegistry<>();
        reg.register(KPId.kp("one"), "v1");
        var ids = reg.ids();
        assertEquals(1, ids.size());
        assertThrows(UnsupportedOperationException.class,
            () -> ids.add(KPId.kp("two")));
    }

    @Test
    void registerOrderPreservedByLinkedHashMap() {
        KPRegistry<String> reg = new KPRegistry<>();
        reg.register(KPId.kp("first"), "1");
        reg.register(KPId.kp("second"), "2");
        reg.register(KPId.kp("third"), "3");
        var iter = reg.all().iterator();
        assertEquals("1", iter.next());
        assertEquals("2", iter.next());
        assertEquals("3", iter.next());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.jsmua.kinetic_planner.registry.KPRegistryTest" 2>&1 | tail -5`
Expected: FAIL with compilation error (KPRegistry does not exist)

- [ ] **Step 3: Write minimal implementation**

```java
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
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.jsmua.kinetic_planner.registry.KPRegistryTest" 2>&1 | tail -5`
Expected: PASS — 10 tests passed

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/jsmua/kinetic_planner/registry/KPRegistry.java src/test/java/net/jsmua/kinetic_planner/registry/KPRegistryTest.java
git commit -m "feat: add KPRegistry generic freezeable registry"
```

---

### Task 3: ProviderConfigBinding — Per-Provider Config Interface

**Files:**
- Create: `src/main/java/net/jsmua/kinetic_planner/data/ProviderConfigBinding.java`
- Test: `src/test/java/net/jsmua/kinetic_planner/data/ProviderConfigBindingTest.java`

- [ ] **Step 1: Write the failing test**

The test uses an anonymous implementation to verify the interface contract and the `isStrictBool` helper.

```java
package net.jsmua.kinetic_planner.data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ProviderConfigBindingTest {

    @Test
    void isStrictBoolAcceptsTrueAndFalse() {
        assertTrue(ProviderConfigBinding.isStrictBool("true"));
        assertTrue(ProviderConfigBinding.isStrictBool("false"));
        assertTrue(ProviderConfigBinding.isStrictBool("TRUE"));
        assertTrue(ProviderConfigBinding.isStrictBool("False"));
    }

    @Test
    void isStrictBoolRejectsNonBoolean() {
        assertFalse(ProviderConfigBinding.isStrictBool("1"));
        assertFalse(ProviderConfigBinding.isStrictBool("yes"));
        assertFalse(ProviderConfigBinding.isStrictBool(""));
        assertFalse(ProviderConfigBinding.isStrictBool("tru"));
    }

    @Test
    void anonymousImplementationCanReadConfig() {
        ProviderConfig defaultCfg = ProviderConfig.defaultValue("testmod", "Test Mod");
        ProviderConfigBinding binding = new ProviderConfigBinding() {
            @Override public String modId() { return "testmod"; }
            @Override public ProviderConfig read() { return defaultCfg; }
            @Override public boolean writeEnabled(boolean enabled) { return true; }
            @Override public boolean writeParam(String param, String value) { return true; }
        };
        assertEquals("testmod", binding.modId());
        assertEquals("Test Mod", binding.read().displayName());
        assertTrue(binding.writeEnabled(false));
        assertTrue(binding.writeParam("priority", "5"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew test --tests "net.jsmua.kinetic_planner.data.ProviderConfigBindingTest" 2>&1 | tail -5`
Expected: FAIL with compilation error (ProviderConfigBinding does not exist)

- [ ] **Step 3: Write minimal implementation**

```java
package net.jsmua.kinetic_planner.data;

/**
 * Per-provider config read/write strategy.
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

    /** The provider mod ID this binding serves (e.g. "xaeroworldmap"). */
    String modId();

    /**
     * Read current config values into a {@link ProviderConfig}.
     *
     * @return current provider config (never null)
     */
    ProviderConfig read();

    /**
     * Write the enabled flag to the config.
     *
     * @param enabled new enabled state
     * @return true if write succeeded
     */
    boolean writeEnabled(boolean enabled);

    /**
     * Write a named parameter to the config.
     *
     * @param param parameter name ("priority", "lineWidthScale", "alphaScale", "dashed")
     * @param value string form of the new value
     * @return true if parameter name is valid and value parsed successfully
     */
    boolean writeParam(String param, String value);

    /**
     * Strict boolean parser — accepts only "true" or "false" (case-insensitive).
     *
     * <p>Shared by all binding implementations to validate boolean parameters.
     */
    static boolean isStrictBool(String value) {
        return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew test --tests "net.jsmua.kinetic_planner.data.ProviderConfigBindingTest" 2>&1 | tail -5`
Expected: PASS — 3 tests passed

- [ ] **Step 5: Commit**

```bash
git add src/main/java/net/jsmua/kinetic_planner/data/ProviderConfigBinding.java src/test/java/net/jsmua/kinetic_planner/data/ProviderConfigBindingTest.java
git commit -m "feat: add ProviderConfigBinding interface for per-provider config strategy"
```

---

### Task 4: XaeroProviderConfigBinding — Xaero Config Read/Write

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/config/XaeroProviderConfigBinding.java`

This is a client sourceSet class — it captures `ModConfigSpec` value holders from `KPConfig`. No separate test needed because the logic is trivial delegation to ModConfigSpec (which requires NeoForge runtime to test); the binding's contract is verified in Task 3's `ProviderConfigBindingTest`.

- [ ] **Step 1: Write the implementation**

```java
package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.jsmua.kinetic_planner.data.ProviderConfigBinding;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@link ProviderConfigBinding} for Xaero's World Map.
 *
 * <p>Captures references to the {@code ModConfigSpec} value holders created in
 * {@link KPConfig}'s static block. Reading and writing delegate directly to
 * those holders, replacing the former switch-case logic in
 * {@code KPConfig.getProviderConfig} / {@code setProviderEnabled} / {@code setProviderParam}.
 */
public final class XaeroProviderConfigBinding implements ProviderConfigBinding {

    private final ModConfigSpec.BooleanValue enabled;
    private final ModConfigSpec.IntValue priority;
    private final ModConfigSpec.DoubleValue lineWidthScale;
    private final ModConfigSpec.DoubleValue alphaScale;
    private final ModConfigSpec.BooleanValue dashed;

    public XaeroProviderConfigBinding(
            ModConfigSpec.BooleanValue enabled,
            ModConfigSpec.IntValue priority,
            ModConfigSpec.DoubleValue lineWidthScale,
            ModConfigSpec.DoubleValue alphaScale,
            ModConfigSpec.BooleanValue dashed) {
        this.enabled = enabled;
        this.priority = priority;
        this.lineWidthScale = lineWidthScale;
        this.alphaScale = alphaScale;
        this.dashed = dashed;
    }

    @Override
    public String modId() {
        return "xaeroworldmap";
    }

    @Override
    public ProviderConfig read() {
        return new ProviderConfig(
            "xaeroworldmap",
            "Xaero's World Map",
            enabled.get(),
            priority.get(),
            lineWidthScale.get().floatValue(),
            alphaScale.get().floatValue(),
            dashed.get());
    }

    @Override
    public boolean writeEnabled(boolean value) {
        enabled.set(value);
        return true;
    }

    @Override
    public boolean writeParam(String param, String value) {
        try {
            return switch (param) {
                case "lineWidthScale" -> { lineWidthScale.set(Double.parseDouble(value)); yield true; }
                case "alphaScale" -> { alphaScale.set(Double.parseDouble(value)); yield true; }
                case "dashed" -> {
                    if (!isStrictBool(value)) yield false;
                    dashed.set(Boolean.parseBoolean(value));
                    yield true;
                }
                case "priority" -> { priority.set(Integer.parseInt(value)); yield true; }
                default -> false;
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileClientJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/config/XaeroProviderConfigBinding.java
git commit -m "feat: add XaeroProviderConfigBinding"
```

---

### Task 5: JourneyMapProviderConfigBinding — JourneyMap Config Read/Write

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/config/JourneyMapProviderConfigBinding.java`

- [ ] **Step 1: Write the implementation**

```java
package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.jsmua.kinetic_planner.data.ProviderConfigBinding;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@link ProviderConfigBinding} for JourneyMap.
 *
 * <p>Same pattern as {@link XaeroProviderConfigBinding} — captures
 * {@code ModConfigSpec} value holders and delegates read/write.
 */
public final class JourneyMapProviderConfigBinding implements ProviderConfigBinding {

    private final ModConfigSpec.BooleanValue enabled;
    private final ModConfigSpec.IntValue priority;
    private final ModConfigSpec.DoubleValue lineWidthScale;
    private final ModConfigSpec.DoubleValue alphaScale;
    private final ModConfigSpec.BooleanValue dashed;

    public JourneyMapProviderConfigBinding(
            ModConfigSpec.BooleanValue enabled,
            ModConfigSpec.IntValue priority,
            ModConfigSpec.DoubleValue lineWidthScale,
            ModConfigSpec.DoubleValue alphaScale,
            ModConfigSpec.BooleanValue dashed) {
        this.enabled = enabled;
        this.priority = priority;
        this.lineWidthScale = lineWidthScale;
        this.alphaScale = alphaScale;
        this.dashed = dashed;
    }

    @Override
    public String modId() {
        return "journeymap";
    }

    @Override
    public ProviderConfig read() {
        return new ProviderConfig(
            "journeymap",
            "JourneyMap",
            enabled.get(),
            priority.get(),
            lineWidthScale.get().floatValue(),
            alphaScale.get().floatValue(),
            dashed.get());
    }

    @Override
    public boolean writeEnabled(boolean value) {
        enabled.set(value);
        return true;
    }

    @Override
    public boolean writeParam(String param, String value) {
        try {
            return switch (param) {
                case "lineWidthScale" -> { lineWidthScale.set(Double.parseDouble(value)); yield true; }
                case "alphaScale" -> { alphaScale.set(Double.parseDouble(value)); yield true; }
                case "dashed" -> {
                    if (!isStrictBool(value)) yield false;
                    dashed.set(Boolean.parseBoolean(value));
                    yield true;
                }
                case "priority" -> { priority.set(Integer.parseInt(value)); yield true; }
                default -> false;
            };
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileClientJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/config/JourneyMapProviderConfigBinding.java
git commit -m "feat: add JourneyMapProviderConfigBinding"
```

---

### Task 6: Refactor KPConfig — Replace Switch Statements with Binding Registry

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/config/KPConfig.java`

This task replaces the three switch statements (`getProviderConfig`, `setProviderEnabled`, `setProviderParam`) and the two private helpers (`setXaeroParam`, `setJmParam`) with a `Map<String, ProviderConfigBinding>` lookup. The TOML spec entries remain in the static block; bindings are registered immediately after the entries are created.

- [ ] **Step 1: Add binding registry field and registration**

Add the following field declaration after line 18 (`private static final KPConfig INSTANCE = new KPConfig();`):

```java
    /** Per-provider config bindings, keyed by modId. Replaces switch statements. */
    private static final java.util.Map<String, ProviderConfigBinding> BINDINGS = new java.util.LinkedHashMap<>();
```

Add the import for `ProviderConfigBinding` at the top of the file (after the existing `ProviderConfigRegistry` import):

```java
import net.jsmua.kinetic_planner.data.ProviderConfigBinding;
```

- [ ] **Step 2: Register bindings in the static block**

At the end of the static block, right before `SPEC = builder.build();` (line 123), add binding registration:

```java
        // Register config bindings for built-in providers
        BINDINGS.put("xaeroworldmap", new XaeroProviderConfigBinding(
            PROVIDER_XAERO_ENABLED,
            PROVIDER_XAERO_PRIORITY,
            PROVIDER_XAERO_LINE_WIDTH_SCALE,
            PROVIDER_XAERO_ALPHA_SCALE,
            PROVIDER_XAERO_DASHED));
        BINDINGS.put("journeymap", new JourneyMapProviderConfigBinding(
            PROVIDER_JM_ENABLED,
            PROVIDER_JM_PRIORITY,
            PROVIDER_JM_LINE_WIDTH_SCALE,
            PROVIDER_JM_ALPHA_SCALE,
            PROVIDER_JM_DASHED));
```

- [ ] **Step 3: Replace getProviderConfig method**

Replace the entire `getProviderConfig` method (lines 237-258) with:

```java
    @Override
    public ProviderConfig getProviderConfig(String modId) {
        ProviderConfigBinding binding = BINDINGS.get(modId);
        if (binding != null) {
            return binding.read();
        }
        // Fallback to registry default for providers without TOML entries
        return ProviderConfigRegistry.getDefault(modId);
    }
```

- [ ] **Step 4: Replace setProviderEnabled method**

Replace the entire `setProviderEnabled` method (lines 261-267) with:

```java
    @Override
    public boolean setProviderEnabled(String modId, boolean enabled) {
        ProviderConfigBinding binding = BINDINGS.get(modId);
        if (binding != null) {
            return binding.writeEnabled(enabled);
        }
        return false;
    }
```

- [ ] **Step 5: Replace setProviderParam method**

Replace the entire `setProviderParam` method (lines 270-280) with:

```java
    @Override
    public boolean setProviderParam(String modId, String param, String value) {
        ProviderConfigBinding binding = BINDINGS.get(modId);
        if (binding != null) {
            return binding.writeParam(param, value);
        }
        return false;
    }
```

- [ ] **Step 6: Delete private helper methods**

Delete the `setXaeroParam` method (lines 303-314) and the `setJmParam` method (lines 316-327) and the `isStrictBool` method (lines 329-331). These are now handled by the binding classes and `ProviderConfigBinding.isStrictBool`.

- [ ] **Step 7: Verify compilation**

Run: `./gradlew compileJava compileClientJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Run existing tests to verify no regression**

Run: `./gradlew test 2>&1 | tail -10`
Expected: All existing tests pass

- [ ] **Step 9: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/config/KPConfig.java
git commit -m "refactor: replace KPConfig provider switch statements with binding registry"
```

---

### Task 7: MapProviderFactory — Provider Creation Interface

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/mapadapter/MapProviderFactory.java`

- [ ] **Step 1: Write the implementation**

```java
package net.jsmua.kinetic_planner.mapadapter;

import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.minecraft.network.chat.Component;

import javax.annotation.Nullable;

/**
 * Factory for creating {@link MapOverlayProvider} instances.
 *
 * <p>Replaces the static initialization block in {@link MapOverlayDispatcher}.
 * Each map mod provides a factory implementation that:
 * <ol>
 *   <li>Declares its mod ID, display name, and default config</li>
 *   <li>Checks whether the target mod is installed ({@link #isAvailable})</li>
 *   <li>Creates the provider instance on demand ({@link #create})</li>
 * </ol>
 *
 * <p>Factories are registered in {@link MapProviderRegistry} during client setup.
 * Third-party mods can register their own factories to add new map provider support
 * without modifying KP source code.
 */
public interface MapProviderFactory {

    /** The provider mod ID (e.g. "xaeroworldmap", "journeymap"). */
    String modId();

    /** Localized display name for CLI output and UI. */
    Component displayName();

    /** Default config for this provider (registered in ProviderConfigRegistry at startup). */
    ProviderConfig defaultConfig();

    /** Whether the target mod is currently loaded. */
    boolean isAvailable();

    /**
     * Create a new provider instance.
     *
     * @return provider instance, or null if creation failed (e.g. mod not loaded)
     */
    @Nullable
    MapOverlayProvider create();
}
```

- [ ] **Step 2: Verify it compiles**

Run: `./gradlew compileClientJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/mapadapter/MapProviderFactory.java
git commit -m "feat: add MapProviderFactory interface"
```

---

### Task 8: XaeroMapProviderFactory and JourneyMapMapProviderFactory

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/mapadapter/XaeroMapProviderFactory.java`
- Create: `src/client/java/net/jsmua/kinetic_planner/mapadapter/JourneyMapMapProviderFactory.java`

- [ ] **Step 1: Write XaeroMapProviderFactory**

```java
package net.jsmua.kinetic_planner.mapadapter;

import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

/**
 * {@link MapProviderFactory} for Xaero's World Map.
 *
 * <p>Wraps {@link XaeroMapOverlayProvider} creation behind the factory interface.
 * Availability is checked via {@code ModList.get().isLoaded("xaeroworldmap")}.
 */
public final class XaeroMapProviderFactory implements MapProviderFactory {

    private Boolean cachedAvailable;

    @Override
    public String modId() {
        return "xaeroworldmap";
    }

    @Override
    public Component displayName() {
        return Component.literal("Xaero's World Map");
    }

    @Override
    public ProviderConfig defaultConfig() {
        return ProviderConfig.defaultValue("xaeroworldmap", "Xaero's World Map");
    }

    @Override
    public boolean isAvailable() {
        if (cachedAvailable == null) {
            cachedAvailable = ModList.get().isLoaded("xaeroworldmap");
        }
        return cachedAvailable;
    }

    @Override
    @Nullable
    public MapOverlayProvider create() {
        if (!isAvailable()) return null;
        return new XaeroMapOverlayProvider();
    }
}
```

- [ ] **Step 2: Write JourneyMapMapProviderFactory**

```java
package net.jsmua.kinetic_planner.mapadapter;

import net.jsmua.kinetic_planner.data.ProviderConfig;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.ModList;

import javax.annotation.Nullable;

/**
 * {@link MapProviderFactory} for JourneyMap.
 *
 * <p>Wraps {@link JourneyMapOverlayProvider} creation behind the factory interface.
 * Availability is checked via {@code ModList.get().isLoaded("journeymap")}.
 */
public final class JourneyMapMapProviderFactory implements MapProviderFactory {

    private Boolean cachedAvailable;

    @Override
    public String modId() {
        return "journeymap";
    }

    @Override
    public Component displayName() {
        return Component.literal("JourneyMap");
    }

    @Override
    public ProviderConfig defaultConfig() {
        return ProviderConfig.defaultValue("journeymap", "JourneyMap");
    }

    @Override
    public boolean isAvailable() {
        if (cachedAvailable == null) {
            cachedAvailable = ModList.get().isLoaded("journeymap");
        }
        return cachedAvailable;
    }

    @Override
    @Nullable
    public MapOverlayProvider create() {
        if (!isAvailable()) return null;
        return new JourneyMapOverlayProvider();
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileClientJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/mapadapter/XaeroMapProviderFactory.java src/client/java/net/jsmua/kinetic_planner/mapadapter/JourneyMapMapProviderFactory.java
git commit -m "feat: add XaeroMapProviderFactory and JourneyMapMapProviderFactory"
```

---

### Task 9: MapProviderRegistry — Client-Side Factory Registry

**Files:**
- Create: `src/client/java/net/jsmua/kinetic_planner/mapadapter/MapProviderRegistry.java`

- [ ] **Step 1: Write the implementation**

```java
package net.jsmua.kinetic_planner.mapadapter;

import net.jsmua.kinetic_planner.registry.KPId;
import net.jsmua.kinetic_planner.registry.KPRegistry;

import java.util.List;

/**
 * Client-side registry for {@link MapProviderFactory} instances.
 *
 * <p>Built on the generic {@link KPRegistry}. Factories are registered during
 * {@code FMLClientSetupEvent} and frozen before first use.
 *
 * <p>Third-party mods register their factories by calling
 * {@link #register} during client setup (before freeze).
 */
public final class MapProviderRegistry {

    private MapProviderRegistry() {}

    private static final KPRegistry<MapProviderFactory> REGISTRY = new KPRegistry<>();

    /**
     * Register a map provider factory.
     *
     * @param factory the factory to register
     * @throws IllegalStateException if registry is frozen or duplicate modId
     */
    public static void register(MapProviderFactory factory) {
        REGISTRY.register(KPId.kp(factory.modId()), factory);
    }

    /**
     * Return all registered factories (unmodifiable).
     *
     * @return collection of all factories in registration order
     */
    public static List<MapProviderFactory> all() {
        return List.copyOf(REGISTRY.all());
    }

    /**
     * Return only factories whose target mod is loaded.
     *
     * @return list of available factories
     */
    public static List<MapProviderFactory> available() {
        return REGISTRY.all().stream()
            .filter(MapProviderFactory::isAvailable)
            .toList();
    }

    /** Freeze the registry. Called after client setup is complete. */
    public static void freeze() {
        REGISTRY.freeze();
    }

    /** Return true if registry is frozen. */
    public static boolean isFrozen() {
        return REGISTRY.isFrozen();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileClientJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/mapadapter/MapProviderRegistry.java
git commit -m "feat: add MapProviderRegistry client-side factory registry"
```

---

### Task 10: Refactor MapOverlayDispatcher — Replace Static Block with Factory Registry

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/mapadapter/MapOverlayDispatcher.java`

This task removes the `static { PROVIDERS.add(...) }` block and replaces it with lazy initialization from `MapProviderRegistry`. The `PROVIDERS` list becomes a cached list of created provider instances, populated on first access or explicitly initialized via a new `initProviders()` method.

- [ ] **Step 1: Replace the PROVIDERS field and static block**

Remove the static block (lines 51-55):
```java
    static {
        // 按优先级注册：Xaero 优先于 JourneyMap
        PROVIDERS.add(new XaeroMapOverlayProvider());
        PROVIDERS.add(new JourneyMapOverlayProvider());
    }
```

Replace the `PROVIDERS` field declaration (line 37) with:

```java
    /** Cached provider instances, created from registered factories. Null until initialized. */
    private static List<MapOverlayProvider> providers = null;
```

- [ ] **Step 2: Add initProviders method**

Add this method after the `providers` field (before `registeredProviders`):

```java
    /**
     * Initialize provider instances from the factory registry.
     *
     * <p>Called once during {@link net.jsmua.kinetic_planner.KineticPlannerClient#onClientSetup}.
     * After this call, {@link #registeredProviders()} returns the cached list.
     */
    public static void initProviders() {
        if (providers != null) return; // already initialized
        List<MapOverlayProvider> created = new ArrayList<>();
        for (MapProviderFactory factory : MapProviderRegistry.available()) {
            MapOverlayProvider provider = factory.create();
            if (provider != null) {
                created.add(provider);
            }
        }
        providers = List.copyOf(created);
    }
```

- [ ] **Step 3: Update registeredProviders method**

Replace the `registeredProviders` method (lines 62-64) with:

```java
    /**
     * Return all initialized provider instances (immutable snapshot).
     *
     * <p>Only returns providers for mods that are currently installed
     * (filtered by {@link MapProviderFactory#isAvailable}).
     * For all registered factory modIds (including uninstalled mods),
     * use {@link #getRegisteredModIds()}.
     *
     * @return provider list; empty list if not yet initialized
     */
    public static List<MapOverlayProvider> registeredProviders() {
        return providers != null ? providers : List.of();
    }

    /**
     * Return all registered factory modIds, including mods not currently installed.
     *
     * <p>Use this for config UI and CLI listing where uninstalled mods
     * should still be configurable. For rendering/tick, use
     * {@link #registeredProviders()} which only returns available providers.
     *
     * @return unmodifiable set of all registered modIds
     */
    public static java.util.Set<String> getRegisteredModIds() {
        return MapProviderRegistry.all().stream()
            .map(MapProviderFactory::modId)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
```

- [ ] **Step 4: Update tick method to handle null providers**

In the `tick()` method, replace the line that creates the sorted list (lines 85-90):

```java
        List<MapOverlayProvider> sorted = PROVIDERS.stream()
            .sorted(Comparator.comparingInt(p -> {
                var config = KPConfig.getInstance().getProviderConfig(p.modId());
                return config != null ? config.priority() : Integer.MAX_VALUE;
            }))
            .toList();
```

with:

```java
        if (providers == null || providers.isEmpty()) {
            currentContext = null;
            activeProviderModId = null;
            return;
        }
        List<MapOverlayProvider> sorted = providers.stream()
            .sorted(Comparator.comparingInt(p -> {
                var config = KPConfig.getInstance().getProviderConfig(p.modId());
                return config != null ? config.priority() : Integer.MAX_VALUE;
            }))
            .toList();
```

- [ ] **Step 5: Verify compilation**

Run: `./gradlew compileClientJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Run existing tests**

Run: `./gradlew test 2>&1 | tail -10`
Expected: All existing tests pass

- [ ] **Step 7: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/mapadapter/MapOverlayDispatcher.java
git commit -m "refactor: replace MapOverlayDispatcher static block with factory registry"
```

---

### Task 11: Update KineticPlannerClient — Register Factories and Bindings

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java`

This task updates the client setup to register factories in `MapProviderRegistry`, then initialize providers, and register default configs — replacing the old flow that iterated `MapOverlayDispatcher.registeredProviders()` (which was populated by the now-deleted static block).

- [ ] **Step 1: Add imports**

Add these imports after the existing mapadapter imports:

```java
import net.jsmua.kinetic_planner.mapadapter.MapProviderRegistry;
import net.jsmua.kinetic_planner.mapadapter.XaeroMapProviderFactory;
import net.jsmua.kinetic_planner.mapadapter.JourneyMapMapProviderFactory;
```

- [ ] **Step 2: Replace onClientSetup method**

Replace the entire `onClientSetup` method (lines 46-60) with:

```java
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        KineticPlannerMod.LOGGER.info("Kinetic Planner client setup");

        // Register built-in map provider factories
        MapProviderRegistry.register(new XaeroMapProviderFactory());
        MapProviderRegistry.register(new JourneyMapMapProviderFactory());

        // Freeze factory registry — no more registrations allowed
        MapProviderRegistry.freeze();

        // Initialize provider instances from registered factories (only for installed mods)
        MapOverlayDispatcher.initProviders();

        // Register default config for ALL factories (including uninstalled mods)
        // so that ProviderConfigControl can list/configure them
        for (var factory : MapProviderRegistry.all()) {
            ProviderConfigRegistry.register(factory.modId(), factory.defaultConfig());
        }

        // One-time Create train map pipeline activation
        event.enqueueWork(() -> {
            if (ModList.get().isLoaded("create")) {
                KPIntegration.forceCreateOverlayPipeline();
            }
        });
    }
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew compileClientJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Run all tests**

Run: `./gradlew test 2>&1 | tail -10`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/KineticPlannerClient.java
git commit -m "refactor: register factories and initialize providers during client setup"
```

---

### Task 11.5: Update ProviderConfigControl and KpConfigUIFactory — Use getRegisteredModIds

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/config/ProviderConfigControl.java`
- Modify: `src/client/java/net/jsmua/kinetic_planner/gui/config/KpConfigUIFactory.java`

With the factory-based initialization, `MapOverlayDispatcher.registeredProviders()` now only returns providers for installed mods. However, `ProviderConfigControl` and `KpConfigUIFactory` need to list and configure ALL registered providers (including uninstalled mods, so users can pre-configure them). This task updates them to use `MapOverlayDispatcher.getRegisteredModIds()` for enumeration.

- [ ] **Step 1: Update ProviderConfigControl.listProviders()**

In `ProviderConfigControl.java`, replace the `listProviders()` method. Find the method that starts with `public static String listProviders()` and contains `for (MapOverlayProvider p : MapOverlayDispatcher.registeredProviders())`. Replace the for loop:

Replace:
```java
        for (MapOverlayProvider p : MapOverlayDispatcher.registeredProviders()) {
            ProviderConfig pc = config.getProviderConfig(p.modId());
            String status = pc != null && pc.enabled() ? "ON" : "OFF";
            String activeMark = active.map(a -> a.equals(p.modId()) ? " *" : "  ").orElse("  ");
            String fusedMark = MapOverlayDispatcher.isCircuitBroken(p.modId()) ? " [FUSED]" : "";
            lines.add(String.format("%s %-15s [%s] pri=%d lineWidth=%.2f alpha=%.2f dashed=%s%s",
                activeMark, p.modId(), status,
                pc != null ? pc.priority() : 0,
                pc != null ? pc.lineWidthScale() : 1.0f,
                pc != null ? pc.alphaScale() : 1.0f,
                pc != null && pc.dashed() ? "true" : "false",
                fusedMark));
        }
```

with:
```java
        for (String modId : MapOverlayDispatcher.getRegisteredModIds()) {
            ProviderConfig pc = config.getProviderConfig(modId);
            String status = pc != null && pc.enabled() ? "ON" : "OFF";
            String activeMark = active.map(a -> a.equals(modId) ? " *" : "  ").orElse("  ");
            String fusedMark = MapOverlayDispatcher.isCircuitBroken(modId) ? " [FUSED]" : "";
            lines.add(String.format("%s %-15s [%s] pri=%d lineWidth=%.2f alpha=%.2f dashed=%s%s",
                activeMark, modId, status,
                pc != null ? pc.priority() : 0,
                pc != null ? pc.lineWidthScale() : 1.0f,
                pc != null ? pc.alphaScale() : 1.0f,
                pc != null && pc.dashed() ? "true" : "false",
                fusedMark));
        }
```

- [ ] **Step 2: Update ProviderConfigControl.isKnownModId()**

In the same file, replace the `isKnownModId` method:

Replace:
```java
    private static boolean isKnownModId(String modId) {
        return MapOverlayDispatcher.registeredProviders().stream()
            .anyMatch(p -> p.modId().equals(modId));
    }
```

with:
```java
    private static boolean isKnownModId(String modId) {
        return MapOverlayDispatcher.getRegisteredModIds().contains(modId);
    }
```

- [ ] **Step 3: Update KpConfigUIFactory provider tab loop**

In `KpConfigUIFactory.java`, find the line `List<MapOverlayProvider> providers = MapOverlayDispatcher.registeredProviders();` (around line 125). Replace the loop that iterates providers:

Replace:
```java
        List<MapOverlayProvider> providers = MapOverlayDispatcher.registeredProviders();
        for (MapOverlayProvider provider : providers) {
            var tab = new Tab();
            tab.setText(provider.modId());
            tab.addClass("kp-tab");
            if (MapOverlayDispatcher.isCircuitBroken(provider.modId())) {
                tab.addClass("kp-tab-fused");
            }
            tab.layout(layout -> {
                layout.height(18);
                layout.paddingHorizontal(6);
                layout.paddingVertical(2);
            });

            UIElement content = buildProviderConfigRows(provider.modId());
            tabView.addTab(tab, content);
        }
```

with:
```java
        for (String modId : MapOverlayDispatcher.getRegisteredModIds()) {
            var tab = new Tab();
            tab.setText(modId);
            tab.addClass("kp-tab");
            if (MapOverlayDispatcher.isCircuitBroken(modId)) {
                tab.addClass("kp-tab-fused");
            }
            tab.layout(layout -> {
                layout.height(18);
                layout.paddingHorizontal(6);
                layout.paddingVertical(2);
            });

            UIElement content = buildProviderConfigRows(modId);
            tabView.addTab(tab, content);
        }
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew compileClientJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Run all tests**

Run: `./gradlew test 2>&1 | tail -10`
Expected: All tests pass

- [ ] **Step 6: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/config/ProviderConfigControl.java src/client/java/net/jsmua/kinetic_planner/gui/config/KpConfigUIFactory.java
git commit -m "refactor: update ProviderConfigControl and KpConfigUIFactory to use getRegisteredModIds"
```

---

### Task 12: Update MapOverlayProvider Javadoc — Remove Static Block Reference

**Files:**
- Modify: `src/client/java/net/jsmua/kinetic_planner/mapadapter/MapOverlayProvider.java`

- [ ] **Step 1: Update the extension guide in the Javadoc**

Replace lines 16-22 (the `<h2>扩展指南</h2>` section, ending at `</ol>` but NOT including the ` */` closer):

```java
 * <h2>扩展指南</h2>
 * <p>新增地图模组支持时：
 * <ol>
 *   <li>实现本接口</li>
 *   <li>在 {@link MapOverlayDispatcher} 的 static 块中注册</li>
 *   <li>如有 Mixin 需求，在 {@code kinetic_planner.mixins.json} 的 {@code client} 数组中添加</li>
 * </ol>
```

with:

```java
 * <h2>扩展指南</h2>
 * <p>新增地图模组支持时：
 * <ol>
 *   <li>实现本接口</li>
 *   <li>实现 {@link MapProviderFactory} 接口，在工厂中创建 provider 实例</li>
 *   <li>在 client setup 阶段调用 {@link MapProviderRegistry#register} 注册工厂</li>
 *   <li>如有 Mixin 需求，在 {@code kinetic_planner.mixins.json} 的 {@code client} 数组中添加</li>
 * </ol>
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew compileClientJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add src/client/java/net/jsmua/kinetic_planner/mapadapter/MapOverlayProvider.java
git commit -m "docs: update MapOverlayProvider extension guide for factory registry"
```

---

### Task 13: Full Build Verification

- [ ] **Step 1: Compile all sourceSets**

Run: `./gradlew compileJava compileClientJava compileServerJava 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Run all tests**

Run: `./gradlew test 2>&1 | tail -15`
Expected: All tests pass (existing + 19 new tests from Tasks 1-3: 6 in KPIdTest + 10 in KPRegistryTest + 3 in ProviderConfigBindingTest)

- [ ] **Step 3: Full build**

Run: `./gradlew build 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit (if any formatting/adjustment needed)**

```bash
git add -A
git commit -m "chore: full build verification for registry framework refactor"
```

If no changes needed, skip this step.
