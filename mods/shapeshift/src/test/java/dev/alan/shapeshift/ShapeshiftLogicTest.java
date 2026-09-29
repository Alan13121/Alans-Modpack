package dev.alan.shapeshift;

import static org.junit.jupiter.api.Assertions.*;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ShapeshiftLogicTest {
    @Test void unlockingKeepsOrderAndIgnoresDuplicates() {
        var u = Unlocks.EMPTY.with("minecraft:bat").with("minecraft:creeper").with("minecraft:bat");
        assertEquals(List.of("minecraft:bat", "minecraft:creeper"), u.ids());
        assertTrue(u.contains("minecraft:creeper"));
        assertFalse(u.contains("minecraft:horse"));
        assertSame(u, u.with("minecraft:bat"));
    }
    @Test void unlocksAreImmutableAndRejectBlankIds() {
        var source = new ArrayList<>(List.of("minecraft:bat"));
        var u = new Unlocks(source);
        source.clear();
        assertEquals(1, u.ids().size());
        assertThrows(UnsupportedOperationException.class, () -> u.ids().clear());
        assertThrows(IllegalArgumentException.class, () -> u.with(" "));
    }
    @Test void unlocksRoundTripThroughCodec() {
        var u = Unlocks.EMPTY.withAll(List.of("minecraft:bat", "minecraft:iron_golem"));
        var json = Unlocks.CODEC.encodeStart(JsonOps.INSTANCE, u).getOrThrow();
        assertEquals(u, Unlocks.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
    }
    @Test void healthKeepsPercentage() {
        assertEquals(6f, FormStats.scaledHealth(20, 20, 6), 1e-4);     // full human -> full bat
        assertEquals(50f, FormStats.scaledHealth(10, 20, 100), 1e-4);  // half human -> half golem
        assertEquals(10f, FormStats.scaledHealth(3, 6, 20), 1e-4);     // half bat -> half human
    }
    @Test void healthNeverKillsOrOverflows() {
        assertEquals(1f, FormStats.scaledHealth(0.5f, 100, 4), 1e-4);  // tiny ratio still leaves 1 HP
        assertEquals(4f, FormStats.scaledHealth(30, 20, 4), 1e-4);     // absorption-like overflow is clamped
        assertEquals(0.5f, FormStats.scaledHealth(1, 1, 0.5f), 1e-4);  // max below 1 is respected
        assertThrows(IllegalArgumentException.class, () -> FormStats.scaledHealth(1, 0, 20));
    }
    @Test void cooldown() {
        assertEquals(0, FormStats.cooldownRemaining(100, 90, 0));       // default: no cooldown
        assertEquals(0, FormStats.cooldownRemaining(100, -1, 60));      // never switched
        assertEquals(50, FormStats.cooldownRemaining(100, 90, 60));
        assertEquals(0, FormStats.cooldownRemaining(200, 90, 60));
    }
}
