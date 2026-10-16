package dev.alan.mineworld.mob;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Silverfish;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Crawls out of an ore block a player mined in a mine world and carries that ore: on death it drops two of it.
 * Unlike a silverfish it never wakes friends or hides in stone.
 */
public class OreWorm extends Silverfish {
    /** How many of the carried ore a worm drops. */
    public static final int DROP_COUNT = 2;

    private record Ore(TagKey<Block> blocks, Item drop) {}
    private static final List<Ore> ORES = List.of(
        new Ore(BlockItemTags.COAL_ORES.block(), Items.COAL), new Ore(BlockItemTags.COPPER_ORES.block(), Items.RAW_COPPER),
        new Ore(BlockItemTags.IRON_ORES.block(), Items.RAW_IRON), new Ore(BlockItemTags.GOLD_ORES.block(), Items.RAW_GOLD),
        new Ore(BlockItemTags.REDSTONE_ORES.block(), Items.REDSTONE), new Ore(BlockItemTags.LAPIS_ORES.block(), Items.LAPIS_LAZULI),
        new Ore(BlockItemTags.DIAMOND_ORES.block(), Items.DIAMOND), new Ore(BlockItemTags.EMERALD_ORES.block(), Items.EMERALD));

    /** The item a worm from this block carries, or empty when the block is not one of the eight ores. */
    public static Optional<Item> oreOf(BlockState state) {
        return ORES.stream().filter(ore -> state.is(ore.blocks())).map(Ore::drop).findFirst();
    }

    /** The ore every worm carries unless it came out of a block (a spawn egg, a command). */
    private Item ore = Items.RAW_IRON;

    public OreWorm(EntityType<? extends Silverfish> type, Level level) {
        super(type, level);
    }

    public Item ore() { return this.ore; }
    public void setOre(Item ore) { this.ore = ore; }

    @Override protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(4, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean killedByPlayer) {
        super.dropCustomDeathLoot(level, source, killedByPlayer);
        this.spawnAtLocation(level, new ItemStack(this.ore, DROP_COUNT));
    }

    @Override protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Ore", BuiltInRegistries.ITEM.getKey(this.ore).toString());
    }

    @Override protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        input.getString("Ore").map(Identifier::tryParse).flatMap(BuiltInRegistries.ITEM::getOptional).ifPresent(item -> this.ore = item);
    }
}
