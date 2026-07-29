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
