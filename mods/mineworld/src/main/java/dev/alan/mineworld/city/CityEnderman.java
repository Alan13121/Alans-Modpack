package dev.alan.mineworld.city;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enderman;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** The enchantment merchant: enchanted books and gear, re-rolled each visit, lockable per player. Ignores stares, moves no blocks. */
public class CityEnderman extends Enderman implements TradingResident {
    private final CityMerchant merchant = new CityMerchant(this, SoundEvents.ENDERMAN_AMBIENT);

    public CityEnderman(EntityType<? extends Enderman> type, Level level) {
        super(type, level);
    }

    @Override protected void registerGoals() {
        CityResident.addCivilianGoals(this, this.goalSelector);
    }

    @Override public CityMerchant merchant() { return this.merchant; }

    @Override public CityTrades.@Nullable Seller seller() { return CityTrades.Seller.ENDERMAN; }

    @Override public boolean lockable() { return true; }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return this.merchant.interact(player, this.seller(), this.lockable());
    }
}
