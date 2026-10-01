package dev.alan.combat.mixin;

import dev.alan.combat.ArmorUpgrades;
import dev.alan.combat.BowUpgrades;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ItemCombinerMenuSlotDefinition;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Armor (or a bow) in the left slot plus an ore (or bow material) in the right slot becomes an upgrade. It costs only the ore, no experience,
 * so the vanilla cost bookkeeping (which also blocks taking a free result) is bypassed while an upgrade is showing.
 */
@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin extends ItemCombinerMenu {
    @Shadow @Final private DataSlot cost;
    /** Ores the current result will use up; 0 while the result (if any) is a normal anvil result. */
    @Unique private int combat$oreCost;

    private AnvilMenuMixin(MenuType<?> type, int id, Inventory inventory, ContainerLevelAccess access, ItemCombinerMenuSlotDefinition definition) {
        super(type, id, inventory, access, definition);
    }

    @Inject(method = "createResult", at = @At("HEAD"), cancellable = true)
    private void combat$createResult(CallbackInfo ci) {
        var upgrade = ArmorUpgrades.plan(inputSlots.getItem(0), inputSlots.getItem(1));
        if (upgrade == null) upgrade = BowUpgrades.plan(inputSlots.getItem(0), inputSlots.getItem(1));
        if (upgrade == null) {
            combat$oreCost = 0;
            return;
        }
        combat$oreCost = upgrade.oreUsed();
        resultSlots.setItem(0, upgrade.stack());
        cost.set(0);
        broadcastChanges();
        ci.cancel();
    }

    @Inject(method = "mayPickup", at = @At("HEAD"), cancellable = true)
    private void combat$mayPickup(Player player, boolean hasResult, CallbackInfoReturnable<Boolean> cir) {
        if (combat$oreCost > 0) cir.setReturnValue(hasResult);
    }

    @Inject(method = "onTake", at = @At("HEAD"), cancellable = true)
    private void combat$onTake(Player player, ItemStack taken, CallbackInfo ci) {
        // Emptying an input slot recomputes the result and resets the field, so read it first.
        int oreCost = combat$oreCost;
        if (oreCost <= 0) return;
        if (player instanceof net.minecraft.server.level.ServerPlayer owner) {
            if (taken.has(dev.alan.combat.CombatMod.UPGRADES)) dev.alan.combat.QuestBook.grant(owner, "ch6/armor_upgrade");
            if (taken.has(dev.alan.combat.CombatMod.BOW_UPGRADES)) dev.alan.combat.QuestBook.grant(owner, "ch6/bow_upgrade");
        }
        inputSlots.setItem(0, ItemStack.EMPTY);
        ItemStack ore = inputSlots.getItem(1);
        if (ore.getCount() > oreCost) {
            ore.shrink(oreCost);
            inputSlots.setItem(1, ore);
        } else {
            inputSlots.setItem(1, ItemStack.EMPTY);
        }
        access.execute((level, pos) -> level.levelEvent(1030, pos, 0));
        ci.cancel();
    }
}
