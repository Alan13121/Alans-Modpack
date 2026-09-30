package dev.alan.mineworld;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** Throwing one of each overworld ore into a water cauldron turns it into a world cauldron. */
public final class CauldronRecipe {
    /** One bit per ore kind; raw ore and refined form both count. */
    private static final Item[][] KINDS = {
        {Items.COAL},
        {Items.RAW_COPPER, Items.COPPER_INGOT},
        {Items.RAW_IRON, Items.IRON_INGOT},
        {Items.RAW_GOLD, Items.GOLD_INGOT},
        {Items.REDSTONE},
        {Items.LAPIS_LAZULI},
        {Items.DIAMOND},
        {Items.EMERALD},
    };
    public static final int FULL = (1 << KINDS.length) - 1;
    private static final Map<Item, Integer> BITS = new LinkedHashMap<>();

    static {
        for (int i = 0; i < KINDS.length; i++) for (Item item : KINDS[i]) BITS.put(item, 1 << i);
    }

    private CauldronRecipe() {}

    public static int bitOf(ItemStack stack) { return BITS.getOrDefault(stack.getItem(), 0); }

    public static void tryAccept(ServerLevel level, ItemEntity entity) {
        ItemStack stack = entity.getItem();
        int bit = bitOf(stack);
        if (bit == 0) return;
        BlockPos pos = entity.blockPosition();
        if (!level.getBlockState(pos).is(Blocks.WATER_CAULDRON)) return;

        MineWorldData data = MineWorldData.get(level.getServer());
        GlobalPos key = GlobalPos.of(level.dimension(), pos);
        int mask = data.progress(key);
        if (mask == FULL || (mask & bit) != 0) return;

        stack.shrink(1);
        if (stack.isEmpty()) entity.discard(); else entity.setItem(stack);
        mask |= bit;
        double x = pos.getX() + 0.5, y = pos.getY() + 0.8, z = pos.getZ() + 0.5;
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 0.8F + Integer.bitCount(mask) * 0.1F);
        level.sendParticles(ParticleTypes.END_ROD, x, y, z, 8, 0.2, 0.1, 0.2, 0.02);

        if (mask == FULL) {
            data.setProgress(key, 0);
            level.setBlockAndUpdate(pos, MineWorldMod.WORLD_CAULDRON.defaultBlockState());
            if (level.getBlockEntity(pos) instanceof WorldCauldronBlockEntity be) be.assignWorld();
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.2F);
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 60, 0.3, 0.3, 0.3, 0.12);
            if (entity.getOwner() instanceof ServerPlayer p) p.sendSystemMessage(Component.translatable("mineworld.created"), true);
        } else {
            data.setProgress(key, mask);
            if (entity.getOwner() instanceof ServerPlayer p) {
                p.sendSystemMessage(Component.translatable("mineworld.progress", Integer.bitCount(mask), KINDS.length, missing(mask)), true);
            }
        }
    }

    private static Component missing(int mask) {
        var list = Component.empty();
        boolean first = true;
        for (int i = 0; i < KINDS.length; i++) {
            if ((mask & (1 << i)) != 0) continue;
            if (!first) list.append(Component.literal(" "));
            list.append(KINDS[i][0].getDefaultInstance().getHoverName());
            first = false;
        }
        return list;
    }
}
