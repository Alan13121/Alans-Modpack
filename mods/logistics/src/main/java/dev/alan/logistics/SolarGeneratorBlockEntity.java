package dev.alan.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Makes 1 EMC a second for its network's channel while the sky above is open and it is day. */
public final class SolarGeneratorBlockEntity extends BlockEntity {
    public SolarGeneratorBlockEntity(BlockPos pos, BlockState state) { super(LogisticsMod.SOLAR_ENTITY, pos, state); }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BlockEntity entity) {
        if ((level.getGameTime() + pos.hashCode()) % 20 != 0) return;
        if (!level.canSeeSky(pos.above()) || !level.isBrightOutside()) return;
        Network network = Network.scan(level, pos);
        if (network.usable() && !network.channels().isEmpty()) Energy.add(network.channels(), 1);
    }
}
