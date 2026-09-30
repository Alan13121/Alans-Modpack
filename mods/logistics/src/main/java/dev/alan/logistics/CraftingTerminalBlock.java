package dev.alan.logistics;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;

/** A terminal with a 3x3 crafting grid that draws its ingredients from the warehouse. */
public final class CraftingTerminalBlock extends TerminalBlock {
    public CraftingTerminalBlock(Properties properties) { super(properties); }

    @Override protected SimpleMenuProvider menuProvider(Level level, BlockPos pos) {
        return new SimpleMenuProvider((id, inv, p) -> new CraftingTerminalMenu(id, inv, ContainerLevelAccess.create(level, pos)),
            Component.translatable("block.logistics.crafting_terminal"));
    }
}
