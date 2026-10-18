package dev.alan.mineworld.city;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import org.jspecify.annotations.Nullable;

/**
 * Every city trade. Shared by the server (offers) and the client (Lookup cards), so it lives in code rather than in a
 * data pack. Trades never run out and prices never change.
 */
public final class CityTrades {
    private CityTrades() {}

    public enum Seller { ZOMBIE, ENDERMAN, MINER, FARMER, SMITH, BARTENDER, HERDER, CAPTAIN, DEALER }

    /** How an enderman's result is enchanted each time the trading screen opens (enchanting table cost). */
    public enum Enchant {
        NONE(0, 0), RANDOM(5, 20), HIGH(30, 30);

        public final int minCost, maxCost;

        Enchant(int minCost, int maxCost) { this.minCost = minCost; this.maxCost = maxCost; }
    }

    /** One trade; {@code id} is stable (locks are saved by it). The result of an enchanted trade is the unenchanted base. */
    public record Trade(String id, Seller seller, ItemStack costA, Optional<ItemStack> costB, ItemStack result, Enchant enchant) {
        public boolean enchanted() { return this.enchant != Enchant.NONE; }
    }

    public static final List<Trade> ALL = build();

    public static List<Trade> of(Seller seller) {
        return ALL.stream().filter(t -> t.seller() == seller).toList();
    }

    public static @Nullable Trade byId(String id) {
        for (Trade t : ALL) if (t.id().equals(id)) return t;
        return null;
    }

    /** The seller a piglin profession maps to, or null (idlers and guards do not trade). */
    public static @Nullable Seller of(CityPiglin.Profession profession) {
        return switch (profession) {
            case MINER -> Seller.MINER;
            case FARMER -> Seller.FARMER;
            case SMITH -> Seller.SMITH;
            case BARTENDER -> Seller.BARTENDER;
            case HERDER -> Seller.HERDER;
            case DEALER -> Seller.DEALER;
            case IDLER -> null;
        };
    }

    private static List<Trade> build() {
        List<Trade> out = new ArrayList<>();
        Builder b = new Builder(out);

        b.seller(Seller.ZOMBIE);
        b.add("ender_pearl", Items.IRON_INGOT, 4, Items.ENDER_PEARL, 1);
        b.add("torches", Items.COAL, 4, Items.TORCH, 32);
        b.add("ladders", Items.COPPER_INGOT, 4, Items.LADDER, 16);
        b.add("arrows", Items.COPPER_INGOT, 3, Items.ARROW, 16);
        b.add("water_bucket", Items.IRON_INGOT, 3, Items.WATER_BUCKET, 1);
        b.add("shield", Items.IRON_INGOT, 4, Items.SHIELD, 1);
        b.add("campfire", Items.COAL, 3, Items.CAMPFIRE, 1);
        b.add("redstone_torches", Items.REDSTONE, 4, Items.REDSTONE_TORCH, 8);

        b.seller(Seller.MINER);
        b.gold("iron_pickaxe", 3, Items.IRON_PICKAXE, 1);
        b.gold("iron_shovel", 2, Items.IRON_SHOVEL, 1);
        b.gold("torches", 1, Items.TORCH, 16);
        b.gold("rails", 2, Items.RAIL, 16);

        b.seller(Seller.FARMER);
        b.gold("bread", 1, Items.BREAD, 6);
        b.gold("baked_potato", 1, Items.BAKED_POTATO, 6);
        b.gold("glow_berries", 1, Items.GLOW_BERRIES, 8);
        b.gold("golden_carrot", 2, Items.GOLDEN_CARROT, 3);

        b.seller(Seller.SMITH);
        b.gold("iron_sword", 3, Items.IRON_SWORD, 1);
        b.gold("iron_helmet", 3, Items.IRON_HELMET, 1);
        b.gold("iron_boots", 3, Items.IRON_BOOTS, 1);
        b.gold("iron_leggings", 4, Items.IRON_LEGGINGS, 1);
        b.gold("iron_chestplate", 5, Items.IRON_CHESTPLATE, 1);
        b.gold("diamond_sword", 10, Items.DIAMOND_SWORD, 1);
        b.gold("diamond_pickaxe", 10, Items.DIAMOND_PICKAXE, 1);
        b.gold("diamond_chestplate", 16, Items.DIAMOND_CHESTPLATE, 1);

        b.seller(Seller.BARTENDER);
        b.potion("night_vision", 2, Potions.NIGHT_VISION);
        b.potion("swiftness", 2, Potions.SWIFTNESS);
        b.potion("healing", 3, Potions.HEALING);
        b.potion("fire_resistance", 3, Potions.FIRE_RESISTANCE);
        b.potion("regeneration", 3, Potions.REGENERATION);

        // Only ordinary animals: rare-form animals stay something to find (shapeshift collection, form marks).
        b.seller(Seller.HERDER);
        b.gold("cow", 4, Items.COW_SPAWN_EGG, 1);
        b.gold("pig", 4, Items.PIG_SPAWN_EGG, 1);
        b.gold("sheep", 4, Items.SHEEP_SPAWN_EGG, 1);
        b.gold("chicken", 4, Items.CHICKEN_SPAWN_EGG, 1);
        b.gold("rabbit", 4, Items.RABBIT_SPAWN_EGG, 1);
        b.gold("goat", 5, Items.GOAT_SPAWN_EGG, 1);
        b.gold("horse", 6, Items.HORSE_SPAWN_EGG, 1);
        b.gold("donkey", 6, Items.DONKEY_SPAWN_EGG, 1);
        b.gold("mooshroom", 8, Items.MOOSHROOM_SPAWN_EGG, 1);

        b.seller(Seller.ENDERMAN);
        b.enchanted("book", Items.DIAMOND, 3, null, 0, Items.BOOK, Enchant.RANDOM);
        b.enchanted("high_book", Items.DIAMOND, 4, Items.LAPIS_LAZULI, 2, Items.BOOK, Enchant.HIGH);
        b.enchanted("diamond_sword", Items.DIAMOND, 5, Items.EMERALD, 2, Items.DIAMOND_SWORD, Enchant.HIGH);
        b.enchanted("diamond_pickaxe", Items.DIAMOND, 5, Items.EMERALD, 2, Items.DIAMOND_PICKAXE, Enchant.HIGH);
        b.enchanted("diamond_axe", Items.DIAMOND, 5, Items.EMERALD, 2, Items.DIAMOND_AXE, Enchant.HIGH);
        b.enchanted("diamond_helmet", Items.DIAMOND, 5, Items.EMERALD, 2, Items.DIAMOND_HELMET, Enchant.HIGH);
        b.enchanted("diamond_boots", Items.DIAMOND, 5, Items.EMERALD, 2, Items.DIAMOND_BOOTS, Enchant.HIGH);
        b.enchanted("diamond_leggings", Items.DIAMOND, 6, Items.EMERALD, 2, Items.DIAMOND_LEGGINGS, Enchant.HIGH);
        b.enchanted("diamond_chestplate", Items.DIAMOND, 7, Items.EMERALD, 2, Items.DIAMOND_CHESTPLATE, Enchant.HIGH);
        b.enchanted("bow", Items.DIAMOND, 3, Items.EMERALD, 1, Items.BOW, Enchant.HIGH);

        // The guard captain pays for bounty proofs from wanted mobs.
        b.seller(Seller.CAPTAIN);
        b.add("experience", City.BOUNTY_PROOF, 1, Items.EXPERIENCE_BOTTLE, 8);
        b.add("golden_apple", City.BOUNTY_PROOF, 2, Items.GOLDEN_APPLE, 1);
        b.add("totem", City.BOUNTY_PROOF, 6, Items.TOTEM_OF_UNDYING, 1);
        b.add("enchanted_golden_apple", City.BOUNTY_PROOF, 10, Items.ENCHANTED_GOLDEN_APPLE, 1);

        // The black market: the rarest things in the mine world, for a great deal of diamonds and emeralds. Never the items that gate
        // progress (nether star, dragon breath, eyes of ender, form items).
        b.seller(Seller.DEALER);
        b.pay("netherite_ingot", Items.DIAMOND, 24, Items.EMERALD, 8, Items.NETHERITE_INGOT);
        b.pay("ancient_debris", Items.DIAMOND, 12, null, 0, Items.ANCIENT_DEBRIS);
        b.pay("elytra", Items.DIAMOND, 64, Items.EMERALD, 16, Items.ELYTRA);
        b.pay("totem", Items.DIAMOND, 16, Items.EMERALD, 4, Items.TOTEM_OF_UNDYING);
        b.pay("enchanted_golden_apple", Items.DIAMOND, 16, Items.EMERALD, 4, Items.ENCHANTED_GOLDEN_APPLE);
        b.pay("trident", Items.DIAMOND, 24, null, 0, Items.TRIDENT);
        b.pay("heart_of_the_sea", Items.EMERALD, 32, null, 0, Items.HEART_OF_THE_SEA);
        b.pay("shulker_shell", Items.EMERALD, 8, null, 0, Items.SHULKER_SHELL);
        return List.copyOf(out);
    }

    private static final class Builder {
        private final List<Trade> out;
        private Seller seller = Seller.ZOMBIE;

        Builder(List<Trade> out) { this.out = out; }

        void seller(Seller seller) { this.seller = seller; }

        private String id(String name) { return this.seller.name().toLowerCase(java.util.Locale.ROOT) + "/" + name; }

        void add(String name, Item cost, int costCount, Item result, int count) {
            this.out.add(new Trade(id(name), this.seller, new ItemStack(cost, costCount), Optional.empty(), new ItemStack(result, count), Enchant.NONE));
        }

        void gold(String name, int gold, Item result, int count) { add(name, Items.GOLD_INGOT, gold, result, count); }

        /** One item for up to two kinds of payment ({@code costB} may be null). */
        void pay(String name, Item costA, int countA, @Nullable Item costB, int countB, Item result) {
            this.out.add(new Trade(id(name), this.seller, new ItemStack(costA, countA),
                costB == null ? Optional.empty() : Optional.of(new ItemStack(costB, countB)), new ItemStack(result), Enchant.NONE));
        }

        void potion(String name, int gold, Holder<Potion> potion) {
            this.out.add(new Trade(id(name), this.seller, new ItemStack(Items.GOLD_INGOT, gold), Optional.empty(),
                PotionContents.createItemStack(Items.POTION, potion), Enchant.NONE));
        }

        void enchanted(String name, Item costA, int countA, @Nullable Item costB, int countB, Item base, Enchant enchant) {
            this.out.add(new Trade(id(name), this.seller, new ItemStack(costA, countA),
                costB == null ? Optional.empty() : Optional.of(new ItemStack(costB, countB)), new ItemStack(base), enchant));
        }
    }
}
