package dev.alan.combat;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/**
 * Unlocks one rare form. Sneak + use cycles which locked rare form it will unlock (stored on the item), plain use
 * consumes the mark and unlocks it. The shapeshift mod is only touched through {@link ShapeshiftLink}.
 */
public final class FormMarkItem extends Item {
    public FormMarkItem(Properties properties) { super(properties); }

    @Override public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        var stack = player.getItemInHand(hand);
        if (!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("shapeshift")) {
            serverPlayer.sendOverlayMessage(Component.translatable("combat.boss.dormant"));
            return InteractionResult.FAIL;
        }
        List<String> locked = ShapeshiftLink.lockedRare(serverPlayer);
        if (locked.isEmpty()) {
            serverPlayer.sendOverlayMessage(Component.translatable("combat.mark.none"));
            return InteractionResult.FAIL;
        }
        String chosen = MarkPick.pick(locked, stack.get(CombatMod.MARK_TARGET), player.isShiftKeyDown());
        if (player.isShiftKeyDown()) {
            stack.set(CombatMod.MARK_TARGET, chosen);
            serverPlayer.sendOverlayMessage(Component.translatable("combat.mark.target", ShapeshiftLink.name(chosen)));
            return InteractionResult.SUCCESS;
        }
        if (ShapeshiftLink.unlock(serverPlayer, chosen)) {
            if (!player.hasInfiniteMaterials()) stack.shrink(1);
            serverPlayer.sendOverlayMessage(Component.translatable("combat.mark.unlocked", ShapeshiftLink.name(chosen)));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.FAIL;
    }
}
