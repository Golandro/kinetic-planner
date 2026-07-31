package net.jsmua.kinetic_planner.gui.editor.ribbon;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RunnableCommandTest {

    @Test
    void executeDelegatesToRunnable() {
        var count = new AtomicInteger(0);
        var cmd = new RunnableCommand(count::incrementAndGet);

        cmd.execute();
        assertEquals(1, count.get());

        cmd.execute();
        assertEquals(2, count.get());
    }

    @Test
    void isEnabledTrueByDefault() {
        var cmd = new RunnableCommand(() -> {});
        assertTrue(cmd.isEnabled());
    }

    @Test
    void isEnabledReflectsProvidedFlag() {
        var cmd = new RunnableCommand(() -> {}, false);
        assertFalse(cmd.isEnabled());
    }
}
