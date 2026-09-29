package dev.alchemy;

import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

public final class BackpackItem extends Item {
    public BackpackItem(Properties properties) { super(properties); }
    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!level.isClientSide()) {
            int selected = player.getInventory().getSelectedSlot();
            player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new BagMenu(id, inventory, selected),
                Component.translatable("item.alchemy_backpack.backpack")));
        }
        return InteractionResult.SUCCESS;
    }
}
