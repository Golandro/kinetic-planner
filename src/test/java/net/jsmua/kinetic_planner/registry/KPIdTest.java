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
