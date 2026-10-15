package dev.alan.mineworld.city;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

/** A city resident that trades through the vanilla merchant screen; everything is delegated to its {@link CityMerchant}. */
public interface TradingResident extends Merchant, CityResident {
    CityMerchant merchant();

    /** What this resident sells right now, or null when it does not trade. */
    CityTrades.@Nullable Seller seller();

    /** Whether offers can be locked (the enderman's enchanted items). */
    default boolean lockable() { return false; }

    @Override default void setTradingPlayer(@Nullable Player player) { this.merchant().setTrader(player); }

    @Override default @Nullable Player getTradingPlayer() { return this.merchant().trader(); }

    @Override default MerchantOffers getOffers() { return this.merchant().offers(); }

    @Override default void overrideOffers(MerchantOffers offers) { this.merchant().overrideOffers(offers); }

    @Override default void notifyTrade(MerchantOffer offer) { this.merchant().notifyTrade(offer); }

    @Override default void notifyTradeUpdated(ItemStack stack) {}

    @Override default int getVillagerXp() { return this.merchant().xp(this.lockable()); }

    @Override default void overrideXp(int xp) {}

    @Override default boolean showProgressBar() { return false; }

    @Override default SoundEvent getNotifyTradeSound() { return this.merchant().sound(); }

    @Override default boolean isClientSide() { return ((Mob) this).level().isClientSide(); }

    @Override default boolean stillValid(Player player) { return this.merchant().stillValid(player); }
}
