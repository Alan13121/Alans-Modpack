package dev.alan.guide;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Right-click opens the handbook screen. The screen lives in the client source set; it registers itself through {@link GuideMod#openHandbook}. */
public final class HandbookItem extends Item {
    public HandbookItem(Properties properties) {
        super(properties);
    }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) GuideMod.openHandbook.run();
        return InteractionResult.SUCCESS;
    }
}
