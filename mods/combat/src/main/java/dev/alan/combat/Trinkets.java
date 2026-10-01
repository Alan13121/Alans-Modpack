package dev.alan.combat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Registry of trinkets plus the rules for how many a player may wear and what they do. */
public final class Trinkets {
    /** Slots every player has before carrying any trinket bag. */
    public static final int BASE_SLOTS = 3;
    private static final int EFFECT_TICKS = 400, REFRESH_BELOW = 300;
    private static final List<Trinket> ALL = new ArrayList<>();
    /** Players we have granted a trinket effect to, so that unequipping can take it away again. */
    private static final Set<UUID> GRANTED = new HashSet<>();

    private Trinkets() {}

    /** Registers the trinket's item and remembers it; only call during mod initialisation. */
    static Item register(String name, Holder<MobEffect> effect, boolean fallImmune) {
        return register(name, effect, fallImmune, false);
    }

    static Item register(String name, Holder<MobEffect> effect, boolean fallImmune, boolean skillFocus) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CombatMod.id(name));
        Item item = Registry.register(BuiltInRegistries.ITEM, key.identifier(),
            new TrinketItem(new Item.Properties().setId(key).stacksTo(1)));
        ALL.add(new Trinket(name, item, effect, fallImmune, skillFocus));
        return item;
    }

    public static List<Trinket> all() { return ALL; }
    public static int total() { return ALL.size(); }

    public static Trinket of(ItemStack stack) {
        for (Trinket trinket : ALL) if (stack.is(trinket.item())) return trinket;
        return null;
    }
    public static Trinket byId(String id) {
        Identifier parsed = id.isEmpty() ? null : Identifier.tryParse(id);
        if (parsed == null) return null;
        for (Trinket trinket : ALL) if (BuiltInRegistries.ITEM.getKey(trinket.item()).equals(parsed)) return trinket;
        return null;
    }
    public static String idOf(ItemStack stack) {
        return of(stack) == null ? "" : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    /** Trinket bags anywhere in the player's inventory (hotbar, main, offhand). */
    public static int bags(Player player) {
        int count = 0;
        var inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.is(CombatMod.TRINKET_BAG)) count += stack.getCount();
        }
        return count;
    }
    /** Pure rule: base slots plus one per bag, never more than there are kinds of trinket. */
    public static int slotCount(int bags, int kinds) {
        return Math.max(0, Math.min(BASE_SLOTS + Math.max(0, bags), kinds));
    }
    public static int slotCount(Player player) { return slotCount(bags(player), total()); }

    /** Is this trinket worn in an active (unlocked) slot? */
    public static boolean worn(Player player, Trinket trinket) {
        TrinketSlots slots = player.getAttachedOrElse(CombatMod.SLOTS, TrinketSlots.EMPTY);
        int active = slotCount(player);
        String id = BuiltInRegistries.ITEM.getKey(trinket.item()).toString();
        for (int i = 0; i < active; i++) if (slots.at(i).equals(id)) return true;
        return false;
    }
    public static boolean wornSkillFocus(Player player) {
        for (Trinket trinket : ALL) if (trinket.skillFocus() && worn(player, trinket)) return true;
        return false;
    }
    public static boolean wornFallImmune(Player player) {
        for (Trinket trinket : ALL) if (trinket.fallImmune() && worn(player, trinket)) return true;
        return false;
    }

    /** Called every server tick for every player; the work is spread over 20 ticks. */
    public static void tick(ServerPlayer player, long tick) {
        if (tick % 20 != player.getId() % 20) return;
        boolean any = false;
        for (Trinket trinket : ALL) {
            if (trinket.effect() == null) continue;
            MobEffectInstance current = player.getEffect(trinket.effect());
            if (worn(player, trinket)) {
                any = true;
                // An infinite effect is a shapeshift form's own; leave it be.
                if (current == null || (!current.isInfiniteDuration() && current.getDuration() < REFRESH_BELOW))
                    player.addEffect(new MobEffectInstance(trinket.effect(), EFFECT_TICKS, 0, true, false, false));
            } else if (current != null && GRANTED.contains(player.getUUID()) && current.isAmbient() && !current.isVisible()
                && !current.isInfiniteDuration() && current.getDuration() <= EFFECT_TICKS) {
                player.removeEffect(trinket.effect());
            }
        }
        if (any) GRANTED.add(player.getUUID()); else GRANTED.remove(player.getUUID());
    }
    public static void forget(ServerPlayer player) { GRANTED.remove(player.getUUID()); }
}
