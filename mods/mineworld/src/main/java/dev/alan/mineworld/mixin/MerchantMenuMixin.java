package dev.alan.mineworld.mixin;

import dev.alan.mineworld.city.CityMerchant;
import dev.alan.mineworld.city.TradingResident;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** The lock button of the city enderman's trading screen arrives as a menu button click carrying the offer index. */
@Mixin(MerchantMenu.class)
abstract class MerchantMenuMixin extends AbstractContainerMenu {
    @Shadow @Final private Merchant trader;

    protected MerchantMenuMixin(@Nullable MenuType<?> type, int containerId) {
        super(type, containerId);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId >= CityMerchant.LOCK_BUTTON && this.trader instanceof TradingResident resident && resident.lockable()
            && player instanceof ServerPlayer serverPlayer) {
            return resident.merchant().toggleLock(serverPlayer, buttonId - CityMerchant.LOCK_BUTTON);
        }
        return super.clickMenuButton(player, buttonId);
    }
}
