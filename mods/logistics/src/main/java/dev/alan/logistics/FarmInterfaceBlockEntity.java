package dev.alan.logistics;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealSource;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Farms the 9x9 columns below it, three layers deep: harvests mature crops and replants them with the seeds they drop
 * (or from the warehouse), cuts sugar cane, cactus and bamboo above their base, breaks melons and pumpkins next to a
 * stem, and can spend the warehouse's bone meal on young crops. Everything goes straight into the warehouse. Upgrades
 * set how often it works and how many plants it handles per pass.
 */
public final class FarmInterfaceBlockEntity extends BlockEntity {
    public static final int RADIUS = 4, DEPTH = 3;
    private static final int COLUMNS = (2 * RADIUS + 1) * (2 * RADIUS + 1);

    private final UpgradeSlots upgrades = new UpgradeSlots(this::setChanged);
    private boolean useBoneMeal;
    private int cursor;

    private final ContainerData toggle = new ContainerData() {
        @Override public int get(int index) { return useBoneMeal ? 1 : 0; }
        @Override public void set(int index, int value) {
            useBoneMeal = value != 0;
            FarmInterfaceBlockEntity.this.setChanged();
        }
        @Override public int getCount() { return 1; }
    };

    public FarmInterfaceBlockEntity(BlockPos pos, BlockState state) {
        super(LogisticsMod.FARM_ENTITY, pos, state);
    }

    public UpgradeSlots upgrades() { return upgrades; }
    public ContainerData toggle() { return toggle; }

    public static void serverTick(Level level, BlockPos pos, BlockState state, FarmInterfaceBlockEntity self) {
        if (!(level instanceof ServerLevel server) || (level.getGameTime() + pos.hashCode()) % self.upgrades.interval() != 0) return;
        self.pulse(server, pos);
    }

    /** What one plant needs. */
    private enum Job { NONE, HARVEST_REPLANT, HARVEST_ONLY, BONE_MEAL }

    private Job jobFor(ServerLevel level, BlockPos pos, BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) return crop.isMaxAge(state) ? Job.HARVEST_REPLANT : useBoneMeal ? Job.BONE_MEAL : Job.NONE;
        if (block instanceof NetherWartBlock) return state.getValue(NetherWartBlock.AGE) >= NetherWartBlock.MAX_AGE ? Job.HARVEST_REPLANT : Job.NONE;
        if (block instanceof StemBlock) return useBoneMeal && ((BonemealableBlock) block).isValidBonemealTarget(level, pos, state, BonemealSource.INTERACTION) ? Job.BONE_MEAL : Job.NONE;
        if (state.is(Blocks.MELON) || state.is(Blocks.PUMPKIN)) {
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                Block next = level.getBlockState(pos.relative(dir)).getBlock();
                if (next instanceof StemBlock || next instanceof AttachedStemBlock) return Job.HARVEST_ONLY;
            }
            return Job.NONE;
        }
        // Tall plants are cut above their base so they regrow.
        if ((block instanceof SugarCaneBlock || block instanceof CactusBlock || block instanceof BambooStalkBlock)
            && level.getBlockState(pos.below()).is(block)) return Job.HARVEST_ONLY;
        return Job.NONE;
    }

    private void pulse(ServerLevel level, BlockPos origin) {
        int actions = Math.max(1, upgrades.amount() / 8);
        Network network = null;
        for (int k = 0; k < COLUMNS && actions > 0; k++) {
            int column = (cursor + k) % COLUMNS;
            int dx = column % (2 * RADIUS + 1) - RADIUS, dz = column / (2 * RADIUS + 1) - RADIUS;
            // Top down, so a stack of cane is cut from the upper end.
            for (int dy = -1; dy >= -DEPTH && actions > 0; dy--) {
                BlockPos pos = origin.offset(dx, dy, dz);
                if (!level.hasChunkAt(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) continue;
                Job job = jobFor(level, pos, state);
                if (job == Job.NONE) continue;
                if (network == null) {
                    network = Network.scan(level, origin);
                    if (!network.usable()) return;
                }
                if (work(level, network, pos, state, job)) actions--;
                cursor = (column + 1) % COLUMNS;
            }
        }
    }

    private boolean work(ServerLevel level, Network network, BlockPos pos, BlockState state, Job job) {
        if (job == Job.BONE_MEAL) {
            ItemStack meal = network.extract(new ItemStack(Items.BONE_MEAL), 1);
            if (meal.isEmpty()) return false;
            var block = (BonemealableBlock) state.getBlock();
            if (block.isBonemealSuccess(level, level.getRandom(), pos, state, BonemealSource.INTERACTION))
                block.performBonemeal(level, level.getRandom(), pos, state, BonemealSource.INTERACTION);
            level.levelEvent(1505, pos, 15);
            return true;
        }
        List<ItemStack> drops = new ArrayList<>(Block.getDrops(state, level, pos, null));
        level.destroyBlock(pos, false);
        if (job == Job.HARVEST_REPLANT) replant(level, network, pos, state, drops);
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) continue;
            ItemStack rest = network.insert(drop);
            if (!rest.isEmpty()) Block.popResource(level, pos, rest);
        }
        return true;
    }

    /** Plants the crop again with one of its own seeds, from the drops first and from the warehouse otherwise. */
    private static void replant(ServerLevel level, Network network, BlockPos pos, BlockState old, List<ItemStack> drops) {
        Item seed = Item.byBlock(old.getBlock());
        if (seed == Items.AIR) return;
        BlockState fresh = old.getBlock().defaultBlockState();
        if (!fresh.canSurvive(level, pos)) return;
        boolean found = false;
        for (ItemStack drop : drops) {
            if (drop.is(seed) && !drop.isEmpty()) { drop.shrink(1); found = true; break; }
        }
        if (!found) found = !network.extract(new ItemStack(seed), 1).isEmpty();
        if (found) level.setBlock(pos, fresh, 3);
    }

    @Override public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null) net.minecraft.world.Containers.dropContents(level, pos, upgrades);
    }

    @Override protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        upgrades.save(output);
        output.putBoolean("bone_meal", useBoneMeal);
    }

    @Override protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        upgrades.load(input);
        useBoneMeal = input.getBooleanOr("bone_meal", false);
    }
}
