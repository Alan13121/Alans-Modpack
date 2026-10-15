package dev.alan.mineworld.city;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.alan.mineworld.Guide;
import dev.alan.mineworld.MineWorldMod;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import org.jspecify.annotations.Nullable;

/**
 * The trading side of a city resident: builds unlimited offers from {@link CityTrades}, rolls the enderman's enchanted
 * items each time the screen opens, and keeps each player's locked enchanted offers (shared by every city).
 */
public final class CityMerchant {
    /** A locked enchanted offer: which trade, and the exact enchanted result. */
    public record LockedTrade(String trade, ItemStack result) {
        public static final Codec<LockedTrade> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("trade").forGetter(LockedTrade::trade),
            ItemStack.CODEC.fieldOf("result").forGetter(LockedTrade::result)).apply(i, LockedTrade::new));
    }

    /** The player's locked offers; kept on death. */
    public static final AttachmentType<List<LockedTrade>> LOCKS = AttachmentRegistry.create(MineWorldMod.id("locked_trades"),
        b -> b.persistent(LockedTrade.CODEC.listOf()).copyOnDeath().initializer(List::of));

    /** Menu button ids from LOCK_BUTTON up toggle the lock of offer (id - LOCK_BUTTON). */
    public static final int LOCK_BUTTON = 100;
    private static final int TRY_ENCHANT = 8;

    private final Mob owner;
    private final SoundEvent tradeSound;
    private @Nullable Player trader;
    private MerchantOffers offers = new MerchantOffers();
    private final List<LockedTrade> locked = new ArrayList<>();
    private final List<Trade> unlocked = new ArrayList<>();

    /** An unlocked offer and the result rolled for it this time. */
    private record Trade(CityTrades.Trade trade, ItemStack result) {}

    public CityMerchant(Mob owner, SoundEvent tradeSound) {
        this.owner = owner;
        this.tradeSound = tradeSound;
    }

    /** Opens the trading screen when this resident sells something; busy merchants wave other players off. */
    public InteractionResult interact(Player player, CityTrades.@Nullable Seller seller, boolean lockable) {
        if (seller == null || !this.owner.isAlive()) return InteractionResult.PASS;
        if (this.owner.level().isClientSide()) return InteractionResult.SUCCESS;
        if (this.trader != null && this.trader != player && this.trader.isAlive()) return InteractionResult.SUCCESS;
        ServerPlayer serverPlayer = (ServerPlayer) player;
        this.locked.clear();
        this.unlocked.clear();
        if (lockable) {
            for (LockedTrade lock : serverPlayer.getAttachedOrCreate(LOCKS)) {
                if (CityTrades.byId(lock.trade()) != null) this.locked.add(lock);
            }
        }
        for (CityTrades.Trade trade : CityTrades.of(seller)) {
            this.unlocked.add(new Trade(trade, roll(trade, this.owner.getRandom(), serverPlayer)));
        }
        this.rebuild();
        this.trader = player;
        this.owner.getNavigation().stop();
        ((Merchant) this.owner).openTradingScreen(player, this.owner.getDisplayName(), 0);
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Locks the offer at index (or unlocks a locked one) and resends the list; false when nothing changed. */
    public boolean toggleLock(ServerPlayer player, int index) {
        if (player != this.trader || index < 0 || index >= this.offers.size()) return false;
        if (index < this.locked.size()) {
            LockedTrade lock = this.locked.remove(index);
            CityTrades.Trade trade = CityTrades.byId(lock.trade());
            if (trade != null) this.unlocked.addFirst(new Trade(trade, lock.result()));
        } else {
            Trade offer = this.unlocked.get(index - this.locked.size());
            if (!offer.trade().enchanted()) return false;
            this.unlocked.remove(offer);
            this.locked.add(new LockedTrade(offer.trade().id(), offer.result()));
        }
        player.setAttached(LOCKS, List.copyOf(this.locked));
        this.rebuild();
        player.sendMerchantOffers(player.containerMenu.containerId, this.offers, 0, this.xp(true), false, false);
        return true;
    }

    private void rebuild() {
        MerchantOffers out = new MerchantOffers();
        for (LockedTrade lock : this.locked) out.add(offer(CityTrades.byId(lock.trade()), lock.result()));
        for (Trade t : this.unlocked) out.add(offer(t.trade(), t.result()));
        this.offers = out;
    }

    private static MerchantOffer offer(CityTrades.Trade trade, ItemStack result) {
        return new MerchantOffer(cost(trade.costA()), trade.costB().map(CityMerchant::cost), result.copy(), Integer.MAX_VALUE, 0, 0.0F);
    }

    private static ItemCost cost(ItemStack stack) {
        return new ItemCost(stack.getItem(), stack.getCount());
    }

    /** The result for this opening: fixed trades as listed, enchanted ones rolled from the tradeable pool without curses. */
    static ItemStack roll(CityTrades.Trade trade, RandomSource random, ServerPlayer player) {
        if (!trade.enchanted()) return trade.result().copy();
        var enchantments = player.level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        List<Holder<Enchantment>> pool = enchantments.getOrThrow(EnchantmentTags.TRADEABLE).stream()
            .filter(e -> !e.is(EnchantmentTags.CURSE)).toList();
        CityTrades.Enchant e = trade.enchant();
        for (int i = 0; i < TRY_ENCHANT; i++) {
            int cost = e.minCost + random.nextInt(e.maxCost - e.minCost + 1);
            ItemStack out = EnchantmentHelper.enchantItem(random, trade.result().copy(), cost, pool.stream());
            if (EnchantmentHelper.hasAnyEnchantments(out)) return out;
        }
        return trade.result().copy();
    }

    // --- Merchant plumbing, delegated to by the resident entities

    /** Unused by the vanilla screen (no progress bar); carries "1 + locked count" so the client can draw the lock button. */
    public int xp(boolean lockable) {
        return lockable ? 1 + this.locked.size() : 0;
    }

    public @Nullable Player trader() { return this.trader; }

    public void setTrader(@Nullable Player player) { this.trader = player; }

    public MerchantOffers offers() { return this.offers; }

    public void overrideOffers(MerchantOffers offers) { this.offers = offers; }

    public void notifyTrade(MerchantOffer offer) {
        offer.increaseUses();
        this.owner.playSound(this.tradeSound);
        if (this.trader instanceof ServerPlayer player) Guide.grant(player, "ch3/trade");
    }

    public SoundEvent sound() { return this.tradeSound; }

    public boolean stillValid(Player player) {
        return this.trader == player && this.owner.isAlive() && player.distanceToSqr(this.owner) < 64.0;
    }
}
