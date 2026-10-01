package dev.alan.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CombatTest {
    @Test void modIdIsValid() {
        assertTrue(CombatMod.MOD_ID.matches("[a-z][a-z0-9_]*"));
    }

    @Test void slotCountStartsAtBaseAndGrowsWithBags() {
        assertEquals(3, Trinkets.slotCount(0, 12));
        assertEquals(5, Trinkets.slotCount(2, 12));
        assertEquals(12, Trinkets.slotCount(9, 12));
    }

    @Test void slotCountNeverExceedsKindsOfTrinket() {
        assertEquals(12, Trinkets.slotCount(50, 12));
        assertEquals(3, Trinkets.slotCount(5, 3));
        assertEquals(2, Trinkets.slotCount(0, 2));
        assertEquals(3, Trinkets.slotCount(-4, 12));
    }

    @Test void slotsReadAndWriteById() {
        TrinketSlots slots = TrinketSlots.EMPTY;
        assertEquals("", slots.at(2));
        slots = slots.with(2, "combat:feather_charm");
        assertEquals("combat:feather_charm", slots.at(2));
        assertEquals("", slots.at(0));
        assertEquals(3, slots.ids().size());
        assertEquals("", slots.with(2, "").at(2));
    }
}
