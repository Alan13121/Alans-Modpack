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

    @Test void upgradePricesRiseAfterSixLevels() {
        assertEquals(1, UpgradeRules.oreCost(0));
        assertEquals(1, UpgradeRules.oreCost(5));
        assertEquals(2, UpgradeRules.oreCost(6));
        assertEquals(2, UpgradeRules.oreCost(9));
    }

    @Test void upgradePlanStopsAtOreCap() {
        assertEquals(new UpgradeRules.Plan(4, 4), UpgradeRules.plan(0, 0, 64));
        assertEquals(new UpgradeRules.Plan(1, 1), UpgradeRules.plan(3, 3, 64));
        assertEquals(new UpgradeRules.Plan(0, 0), UpgradeRules.plan(4, 4, 64));
    }

    @Test void upgradePlanStopsAtPieceCap() {
        // 8 levels already: two more levels fit under the cap of 10 and cost 2 ores each.
        assertEquals(new UpgradeRules.Plan(2, 4), UpgradeRules.plan(0, 8, 64));
        assertEquals(new UpgradeRules.Plan(0, 0), UpgradeRules.plan(0, 10, 64));
    }

    @Test void upgradePlanLimitedByOresInHand() {
        assertEquals(new UpgradeRules.Plan(2, 2), UpgradeRules.plan(0, 0, 2));
        // 5 levels owned; level 6 costs 1, level 7 costs 2, only 2 ores in hand.
        assertEquals(new UpgradeRules.Plan(1, 1), UpgradeRules.plan(0, 5, 2));
        assertEquals(new UpgradeRules.Plan(0, 0), UpgradeRules.plan(0, 6, 1));
    }

    @Test void upgradesTrackLevelsPerOre() {
        Upgrades upgrades = Upgrades.EMPTY.with("iron", 2).with("gold", 3);
        assertEquals(2, upgrades.level("iron"));
        assertEquals(0, upgrades.level("coal"));
        assertEquals(5, upgrades.total());
        assertEquals(4, upgrades.with("iron", 1).total());
    }

    @Test void flatPlanRespectsBothCapsAndStock() {
        assertEquals(new UpgradeRules.Plan(3, 3), UpgradeRules.flatPlan(0, 0, 3, 8, 64));
        assertEquals(new UpgradeRules.Plan(1, 1), UpgradeRules.flatPlan(2, 2, 3, 8, 64));
        assertEquals(new UpgradeRules.Plan(0, 0), UpgradeRules.flatPlan(3, 3, 3, 8, 64));
        assertEquals(new UpgradeRules.Plan(2, 2), UpgradeRules.flatPlan(0, 6, 3, 8, 64));
        assertEquals(new UpgradeRules.Plan(2, 2), UpgradeRules.flatPlan(0, 0, 3, 8, 2));
    }

    @Test void skillPowerGrowsWithEmeraldLevels() {
        assertEquals(1f, SkillMath.power(0), 1e-6);
        assertEquals(1.4f, SkillMath.power(8), 1e-6);
        assertEquals(1f, SkillMath.power(-3), 1e-6);
    }

    @Test void focusShortensCooldown() {
        assertEquals(1f, SkillMath.cooldown(false), 1e-6);
        assertEquals(0.7f, SkillMath.cooldown(true), 1e-6);
    }

    @Test void masterCharmRemovesCooldownAndAddsPower() {
        assertEquals(0f, SkillMath.cooldown(true, true), 1e-6);
        assertEquals(0f, SkillMath.cooldown(false, true), 1e-6);
        assertEquals(1.5f, SkillMath.power(0, true), 1e-6);
        assertEquals(1.4f * 1.5f, SkillMath.power(8, true), 1e-6);
    }

    @Test void markCyclesThroughLockedForms() {
        var locked = java.util.List.of("a", "b", "c");
        assertEquals("a", MarkPick.pick(locked, null, false));
        assertEquals("a", MarkPick.pick(locked, null, true));
        assertEquals("b", MarkPick.pick(locked, "a", true));
        assertEquals("a", MarkPick.pick(locked, "c", true));
        assertEquals("b", MarkPick.pick(locked, "b", false));
        assertEquals("a", MarkPick.pick(locked, "gone", false));
    }
}
