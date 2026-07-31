package net.jsmua.kinetic_planner.gui.editor.ribbon;

import net.jsmua.kinetic_planner.config.IKPConfig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BooleanToggleCommandTest {

    @Test
    void isActiveReflectsGetter() {
        var flag = new AtomicBoolean(true);
        var cmd = new BooleanToggleCommand(flag::get, flag::set);

        assertTrue(cmd.isActive());
        flag.set(false);
        assertFalse(cmd.isActive());
    }

    @Test
    void executeFlipsValue() {
        var flag = new AtomicBoolean(false);
        var cmd = new BooleanToggleCommand(flag::get, flag::set);

        cmd.execute();
        assertTrue(flag.get());

        cmd.execute();
        assertFalse(flag.get());
    }

    @Test
    void setActiveSetsDirectly() {
        var flag = new AtomicBoolean(false);
        var cmd = new BooleanToggleCommand(flag::get, flag::set);

        cmd.setActive(true);
        assertTrue(flag.get());

        cmd.setActive(false);
        assertFalse(flag.get());
    }

    @Test
    void worksWithIkpConfigMethodReferences() {
        var config = mock(IKPConfig.class);
        when(config.isLayerTracksVisible()).thenReturn(false);

        var cmd = new BooleanToggleCommand(
            config::isLayerTracksVisible,
            config::setLayerTracksVisible);

        cmd.execute();

        verify(config).setLayerTracksVisible(true);
    }

    @Test
    void isEnabledAlwaysTrue() {
        var cmd = new BooleanToggleCommand(() -> false, b -> {});
        assertTrue(cmd.isEnabled());
    }
}
