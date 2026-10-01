package dev.alan.combat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Registry of trinkets plus the rules for how many a player may wear and what they do. */
public final class Trinkets {
    /** Slots every player has before carrying any trinket bag. */
    public static final int BASE_SLOTS = 3;
    private static final int EFFECT_TICKS = 400, REFRESH_BELOW = 300;
    /** The regeneration charm heals this much on every third pass of the 20 tick refresh: 1 health per 3 seconds. */
    private static final float REGEN_AMOUNT = 1.0f;
    private static final int REGEN_EVERY = 3;
    /** Share of the damage a thorns ring gives back to the attacker (at least 1). */
    public static final float THORNS_FRACTION = 0.3f;
    /** Blast ward: explosion damage is multiplied by this. */
    public static final float BLAST_WARD_FACTOR = 0.5f;
    /** Hunter charm: chance of one more roll of a victim's loot table. */
    public static final float HUNTER_CHANCE = 0.5f;
    public static final double MAGNET_RANGE = 5.0, MAGNET_SPEED = 0.35;
    private static final List<Trinket> ALL = new ArrayList<>();
    /** Players we have granted a trinket effect to, so that unequipping can take it away again. */
    private static final Set<UUID> GRANTED = new HashSet<>();
    /** Players currently wearing the magnet, pulled every tick. */
    private static final Set<UUID> MAGNETIZED = new HashSet<>();

    private Trinkets() {}

    /** Registers the trinket's item and remembers it; only call during mod initialisation. */
    static Item register(String name, Trinket.Spec spec) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, CombatMod.id(name));
        Item item = Registry.register(BuiltInRegistries.ITEM, key.identifier(),
            new TrinketItem(new Item.Properties().setId(key).stacksTo(1)));
        ALL.add(new Trinket(name, item, spec.effect, spec.boost, Set.copyOf(spec.perks)));
        return item;
    }

    static Trinket.Spec spec() { return new Trinket.Spec(); }

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

    /** Does the player wear any trinket with this perk? */
    public static boolean has(Player player, Trinket.Perk perk) {
        for (Trinket trinket : ALL) if (trinket.perks().contains(perk) && worn(player, trinket)) return true;
        return false;
    }

    /** Called every server tick for every player; the work is spread over 20 ticks. */
    public static void tick(ServerPlayer player, long tick) {
        if (MAGNETIZED.contains(player.getUUID())) pullItems(player);
        if (tick % 20 != player.getId() % 20) return;
        refresh(player);
        if ((tick / 20) % REGEN_EVERY == 0 && player.isAlive() && player.getHealth() < player.getMaxHealth() && has(player, Trinket.Perk.REGEN))
            player.heal(REGEN_AMOUNT);
    }

    /** Brings potion effects and attribute boosts in line with what the player wears right now. */
    public static void refresh(ServerPlayer player) {
        boolean anyEffect = false, magnet = false;
        for (Trinket trinket : ALL) {
            boolean on = worn(player, trinket);
            if (trinket.effect() != null) anyEffect |= refreshEffect(player, trinket, on);
            if (trinket.boost() != null) refreshBoost(player, trinket, on);
            if (on && trinket.perks().contains(Trinket.Perk.MAGNET)) magnet = true;
        }
        if (anyEffect) GRANTED.add(player.getUUID()); else GRANTED.remove(player.getUUID());
        if (magnet) MAGNETIZED.add(player.getUUID()); else MAGNETIZED.remove(player.getUUID());
    }

    private static boolean refreshEffect(ServerPlayer player, Trinket trinket, boolean on) {
        MobEffectInstance current = player.getEffect(trinket.effect());
        if (on) {
            // An infinite effect is a shapeshift form's own; leave it be.
            if (current == null || (!current.isInfiniteDuration() && current.getDuration() < REFRESH_BELOW))
                player.addEffect(new MobEffectInstance(trinket.effect(), EFFECT_TICKS, 0, true, false, false));
            return true;
        }
        if (current != null && GRANTED.contains(player.getUUID()) && current.isAmbient() && !current.isVisible()
            && !current.isInfiniteDuration() && current.getDuration() <= EFFECT_TICKS) {
            player.removeEffect(trinket.effect());
        }
        return false;
    }

    private static void refreshBoost(ServerPlayer player, Trinket trinket, boolean on) {
        var instance = player.getAttribute(trinket.boost().attribute());
        if (instance == null) return;
        Identifier id = CombatMod.id("trinket/" + trinket.name());
        if (!on) {
            instance.removeModifier(id);
        } else if (!instance.hasModifier(id)) {
            instance.addTransientModifier(new AttributeModifier(id, trinket.boost().amount(), trinket.boost().operation()));
        }
    }

    /** Magnet: every loose item nearby that may be picked up drifts toward the player. */
    private static void pullItems(ServerPlayer player) {
        Vec3 center = player.position().add(0, player.getBbHeight() / 2, 0);
        for (ItemEntity item : player.level().getEntitiesOfClass(ItemEntity.class, player.getBoundingBox().inflate(MAGNET_RANGE))) {
            if (!item.isAlive() || item.hasPickUpDelay()) continue;
            Vec3 toward = center.subtract(item.position());
            double distance = toward.length();
            if (distance < 0.5) continue;
            item.setDeltaMovement(toward.scale(MAGNET_SPEED / distance));
        }
    }

    /** Thorns ring: a melee attacker takes part of the damage back. Reflected damage itself is never reflected. */
    public static void reflect(ServerPlayer player, net.minecraft.world.damagesource.DamageSource source, float damageTaken) {
        if (damageTaken <= 0 || source.is(net.minecraft.world.damagesource.DamageTypes.THORNS)) return;
        if (!(source.getDirectEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) || attacker != source.getEntity()) return;
        if (!has(player, Trinket.Perk.THORNS)) return;
        attacker.hurtServer(player.level(), player.level().damageSources().thorns(player), Math.max(1f, damageTaken * THORNS_FRACTION));
    }

    public static void forget(ServerPlayer player) {
        GRANTED.remove(player.getUUID());
        MAGNETIZED.remove(player.getUUID());
    }
}
