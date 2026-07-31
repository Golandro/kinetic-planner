package net.jsmua.kinetic_planner.gui.ribbon;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 框架解耦校验 (spec §1.1, §11.4)。
 *
 * <p>验证 gui/ribbon/ 目录下所有 .java 文件不 import net.jsmua.kinetic_planner.* (KP 包),
 * 确保框架与 KP 解耦。
 *
 * <p>允许的 import:
 * <ul>
 *   <li>com.lowdragmc.lowdraglib2.* (LDLib2)</li>
 *   <li>net.minecraft.* (MC)</li>
 *   <li>dev.vfyjxf.taffy.* (Taffy 布局)</li>
 *   <li>java.*, javax.* (JDK)</li>
 *   <li>org.jetbrains.annotations.* (注解)</li>
 * </ul>
 *
 * <p>例外: gui/ribbon/ 内部包互相 import (net.jsmua.kinetic_planner.gui.ribbon.*) 允许。
 */
class RibbonFrameworkDecouplingTest {

    private static final Path RIBBON_SOURCE_ROOT = Paths.get(
        "src", "client", "java", "net", "jsmua", "kinetic_planner", "gui", "ribbon");

    @Test
    void noRibbonFileImportsKpPackage() throws IOException {
        var violations = new ArrayList<String>();

        if (!Files.exists(RIBBON_SOURCE_ROOT)) {
            fail("Ribbon 源码目录不存在: " + RIBBON_SOURCE_ROOT);
        }

        Files.walkFileTree(RIBBON_SOURCE_ROOT, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.toString().endsWith(".java")) {
                    try {
                        var content = Files.readString(file);
                        for (var line : content.split("\n")) {
                            var trimmed = line.trim();
                            if (trimmed.startsWith("import ") && trimmed.contains("net.jsmua.kinetic_planner.")) {
                                // 允许: gui/ribbon/ 内部互相 import (net.jsmua.kinetic_planner.gui.ribbon.*)
                                if (trimmed.contains("net.jsmua.kinetic_planner.gui.ribbon.")) {
                                    continue;  // 框架内部包, 允许
                                }
                                violations.add(file + ": " + trimmed);
                            }
                        }
                    } catch (IOException e) {
                        violations.add("读取失败 " + file + ": " + e.getMessage());
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });

        assertTrue(violations.isEmpty(),
            "gui/ribbon/ 禁止 import KP 包 (net.jsmua.kinetic_planner.*)\n违规:\n" + String.join("\n", violations));
    }

    @Test
    void ribbonSourceDirectoryExists() {
        assertTrue(Files.exists(RIBBON_SOURCE_ROOT),
            "gui/ribbon/ 源码目录必须存在");
    }
}
