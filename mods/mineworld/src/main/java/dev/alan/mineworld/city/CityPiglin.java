package dev.alan.mineworld.city;

import java.util.Locale;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal;
import net.minecraft.world.entity.monster.piglin.AbstractPiglin;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * Piglin townsfolk: civilians with a profession, melee and ranged guards, and the guard captain. The vanilla piglin
 * brain (bartering, gold greed, hostility, zombification) never runs; everything is goal based.
 */
public class CityPiglin extends Piglin implements TradingResident {
    public enum Role { CIVILIAN, MELEE, RANGED, CAPTAIN }

    public enum Profession {
        MINER, FARMER, SMITH, BARTENDER, HERDER, DEALER, IDLER;

        /** Professions a civilian marker can roll (the bartender and the black market dealer are placed on purpose). */
        public static final Profession[] RANDOM = {MINER, FARMER, SMITH, HERDER, IDLER};

        public String id() { return this.name().toLowerCase(Locale.ROOT); }

        public ItemStack tool() {
            return switch (this) {
                case MINER -> new ItemStack(Items.IRON_PICKAXE);
                case FARMER -> new ItemStack(Items.IRON_HOE);
                case SMITH -> new ItemStack(Items.ANVIL);
                case BARTENDER -> new ItemStack(Items.POTION);
                case HERDER -> new ItemStack(Items.WHEAT);
                case DEALER -> new ItemStack(Items.NETHERITE_INGOT);
                case IDLER -> ItemStack.EMPTY;
            };
        }
    }

    /** Guards have about 70% of a skeleton guard's health. */
    public static final double GUARD_HEALTH = 14.0;

    private Role role = Role.CIVILIAN;
    private Profession profession = Profession.IDLER;
    private final CityMerchant merchant = new CityMerchant(this, SoundEvents.PIGLIN_CELEBRATE);
    private @Nullable Goal attackGoal;
    private @Nullable Goal targetGoal;

    public CityPiglin(EntityType<? extends AbstractPiglin> type, Level level) {
        super(type, level);
        this.setImmuneToZombification(true);
        this.setCanPickUpLoot(false);
    }

    public Role role() { return this.role; }

    public Profession profession() { return this.profession; }

    /** Sets role and profession, gives the matching item and (for guards) the attack goal. */
    public void setup(Role role, Profession profession) {
        this.role = role;
        this.profession = profession;
        ItemStack hand = switch (role) {
            case CIVILIAN -> profession.tool();
            case MELEE -> new ItemStack(Items.GOLDEN_SWORD);
            case RANGED -> new ItemStack(Items.CROSSBOW);
            case CAPTAIN -> new ItemStack(Items.GOLDEN_AXE);
        };
        this.setItemSlot(EquipmentSlot.MAINHAND, hand);
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        if (role == Role.MELEE || role == Role.RANGED) {
            this.getAttribute(Attributes.MAX_HEALTH).setBaseValue(GUARD_HEALTH);
            this.setHealth((float) GUARD_HEALTH);
        }
        this.applyRoleGoals();
    }

    private void applyRoleGoals() {
        if (this.attackGoal != null) this.goalSelector.removeGoal(this.attackGoal);
        if (this.targetGoal != null) this.targetSelector.removeGoal(this.targetGoal);
        this.attackGoal = switch (this.role) {
            case MELEE, CAPTAIN -> new MeleeAttackGoal(this, 1.0, false);
            case RANGED -> new RangedCrossbowAttackGoal<>(this, 1.0, 12.0F);
            case CIVILIAN -> null;
        };
        this.targetGoal = this.attackGoal == null ? null : CityResident.guardTarget(this);
        if (this.attackGoal != null) this.goalSelector.addGoal(2, this.attackGoal);
        if (this.targetGoal != null) this.targetSelector.addGoal(1, this.targetGoal);
    }

    @Override protected void registerGoals() {
        CityResident.addCivilianGoals(this, this.goalSelector);
    }

    /** AbstractPiglin reads the target from its brain; city piglins use the goal-set target like other mobs. */
    @Override public @Nullable LivingEntity getTarget() {
        return this.asValidTarget(this.getTargetUnchecked());
    }

    /** Skips the piglin brain and the overworld zombification timer; goals run in serverAiStep. */
    @Override protected void customServerAiStep(ServerLevel level) {}

    @Override public boolean wantsToPickUp(ServerLevel level, ItemStack stack) { return false; }

    @Override public CityMerchant merchant() { return this.merchant; }

    /** Civilians sell by profession, the captain pays for bounty proofs; guards do not trade. */
    @Override public CityTrades.@Nullable Seller seller() {
        return switch (this.role) {
            case CIVILIAN -> CityTrades.of(this.profession);
            case CAPTAIN -> CityTrades.Seller.CAPTAIN;
            case MELEE, RANGED -> null;
        };
    }

    @Override public InteractionResult mobInteract(Player player, InteractionHand hand) {
        return this.merchant.interact(player, this.seller(), false);
    }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("CityRole", this.role.name());
        output.putString("Profession", this.profession.name());
    }

    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.role = parse(Role.class, input.getStringOr("CityRole", ""), Role.CIVILIAN);
        this.profession = parse(Profession.class, input.getStringOr("Profession", ""), Profession.IDLER);
        this.applyRoleGoals();
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name);
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
