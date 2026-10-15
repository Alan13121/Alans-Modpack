package dev.alan.mineworld.city;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** The general store keeper: sells exploration supplies for ores. Never burns, never turns into a drowned, never attacks. */
public class CityZombie extends Zombie implements TradingResident {
    private final CityMerchant merchant = new CityMerchant(this, SoundEvents.ZOMBIE_AMBIENT);

    public CityZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override protected void registerGoals() {
        CityResident.addCivilianGoals(this, this.goalSelector);
    }

    @Override protected boolean convertsInWater() { return false; }

    @Override protected boolean isSunSensitive() { return false; }

    @Override public CityMerchant merchant() { return this.merchant; }

    @Override public CityTrades.@Nullable Seller seller() { return CityTrades.Seller.ZOMBIE; }

    @Override protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        return this.merchant.interact(player, this.seller(), this.lockable());
    }
}
