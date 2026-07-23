package net.jsmua.kinetic_planner.config;

import net.jsmua.kinetic_planner.cadengine.Theme;
import net.jsmua.kinetic_planner.cadengine.ThemeSerializer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link ThemeManager} 纯方法单元测试。
 *
 * <p>仅测试 {@link ThemeManager#listThemeNames} 和 {@link ThemeManager#loadThemeFile}
 * 两个纯方法，使用 JUnit 5 {@link TempDir @TempDir} 提供临时目录。
 * 副作用方法（{@link ThemeManager#setTheme} 等）依赖 NeoForge 运行时，不做单测。
 */
class ThemeManagerTest {

    @Test
    void listThemeNamesReturnsDefaultWhenDirEmpty(@TempDir Path tempDir) {
        List<String> names = ThemeManager.listThemeNames(tempDir);
        assertTrue(names.contains("default"));
        assertEquals(1, names.size());
    }

    @Test
    void listThemeNamesReturnsDefaultWhenDirNotExists(@TempDir Path tempDir) {
        List<String> names = ThemeManager.listThemeNames(tempDir.resolve("nonexistent"));
        assertTrue(names.contains("default"));
        assertEquals(1, names.size());
    }

    @Test
    void listThemeNamesFindsJsonFiles(@TempDir Path tempDir) throws IOException {
        Files.writeString(tempDir.resolve("dark.json"), "{\"name\":\"dark\"}");
        Files.writeString(tempDir.resolve("light.json"), "{\"name\":\"light\"}");
        // 非 .json 文件应被忽略
        Files.writeString(tempDir.resolve("notjson.txt"), "ignore me");
        List<String> names = ThemeManager.listThemeNames(tempDir);
        assertTrue(names.contains("default"));
        assertTrue(names.contains("dark"));
        assertTrue(names.contains("light"));
        assertEquals(3, names.size());
    }

    @Test
    void loadThemeFileReturnsNullWhenNotFound(@TempDir Path tempDir) {
        Theme theme = ThemeManager.loadThemeFile(tempDir, "nonexistent");
        assertNull(theme);
    }

    @Test
    void loadThemeFileReturnsThemeWhenFound(@TempDir Path tempDir) throws IOException {
        Theme original = Theme.defaultValue();
        String json = ThemeSerializer.serialize(original);
        Files.writeString(tempDir.resolve("default.json"), json);
        Theme loaded = ThemeManager.loadThemeFile(tempDir, "default");
        assertNotNull(loaded);
        assertEquals("default", loaded.name());
        assertEquals(original.track().width(), loaded.track().width(), 1e-6f);
    }
}
