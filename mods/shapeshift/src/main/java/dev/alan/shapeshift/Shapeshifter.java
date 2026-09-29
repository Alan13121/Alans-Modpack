package dev.alan.shapeshift;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import org.jspecify.annotations.Nullable;

/** Server-authoritative form switching. Everything that changes a player's form goes through here. */
public final class Shapeshifter {
    private static final Map<UUID, Long> lastSwitch = new HashMap<>();
    /** Extra lockouts, e.g. after exploding as a creeper. */
    private static final Map<UUID, Long> blockedUntil = new HashMap<>();
    private Shapeshifter() {}

    /** Handles a player's own request: checks unlock, blocklist, cooldown and space. */
    public static void request(ServerPlayer player, @Nullable EntityType<?> target) {
        if (target != null) {
            String id = Forms.id(target);
            if (!unlocks(player).contains(id) || !Forms.isLivingForm(target) || ShapeshiftConfig.isBlocked(id)) {
                player.sendOverlayMessage(Component.translatable("shapeshift.locked"));
                return;
            }
        }
        long now = player.level().getGameTime();
        long remaining = Math.max(
            FormStats.cooldownRemaining(now, lastSwitch.getOrDefault(player.getUUID(), -1L), ShapeshiftConfig.cooldownTicks()),
            blockedUntil.getOrDefault(player.getUUID(), 0L) - now);
        if (remaining > 0) {
            player.sendOverlayMessage(Component.translatable("shapeshift.cooldown", (remaining + 19) / 20));
            return;
        }
        if (apply(player, target, true)) lastSwitch.put(player.getUUID(), now);
    }

    /**
     * Switches form without unlock or cooldown checks (commands, death, invalid saved data).
     * @return whether the form changed
     */
    public static boolean apply(ServerPlayer player, @Nullable EntityType<?> target, boolean checkSpace) {
        String current = player.getAttached(ShapeshiftMod.FORM);
        String next = target == null ? null : Forms.id(target);
        if (java.util.Objects.equals(current, next)) return false;
        if (checkSpace) {
            var dims = target == null ? EntityTypes.PLAYER.getDimensions() : Forms.dimensions(target);
            var box = dims.makeBoundingBox(player.position()).deflate(1.0E-7);
            if (!player.level().noCollision(player, box)) {
                player.sendOverlayMessage(Component.translatable("shapeshift.no_space"));
                return false;
            }
        }
        player.stopRiding();
        player.removeAttached(ShapeshiftMod.FUSE);
        Actives.forget(player);   // ability cooldowns belong to the form, not the player
        float oldMax = player.getMaxHealth();
        Passives.clear(player);
        if (target == null) {
            player.removeAttached(ShapeshiftMod.FORM);
        } else {
            player.setAttached(ShapeshiftMod.FORM, next);
            Passives.apply(player, FormDefinitions.resolve(target, false));
        }
        player.setHealth(FormStats.scaledHealth(player.getHealth(), oldMax, player.getMaxHealth()));
        player.refreshDimensions();
        Reactions.onFormChanged(player);
        effects(player);
        player.sendOverlayMessage(target == null
            ? Component.translatable("shapeshift.reverted")
            : Component.translatable("shapeshift.transformed", target.getDescription()));
        return true;
    }

    /** Re-applies the current form's passives, e.g. after /reload changed its definition. Keeps the health percentage. */
    public static void refresh(ServerPlayer player) {
        Forms.current(player).ifPresent(type -> {
            float oldMax = player.getMaxHealth();
            Passives.clear(player);
            Passives.apply(player, FormDefinitions.resolve(type, false));
            player.setHealth(FormStats.scaledHealth(player.getHealth(), oldMax, player.getMaxHealth()));
        });
    }

    /** Called every tick for every player. */
    public static void tick(ServerPlayer player, int serverTick) {
        var type = Forms.current(player).orElse(null);
        if (type == null) return;
        var form = FormDefinitions.resolve(type, false);
        if (serverTick % 20 == 0) Passives.ensure(player, form);
        Weaknesses.tick(player, form);
        Actives.tick(player, form);
        FormSounds.tickAmbient(player);
    }

    /** Blocks transforming for {@code ticks}; 0 does nothing. */
    public static void startCooldown(ServerPlayer player, int ticks) {
        if (ticks > 0) blockedUntil.put(player.getUUID(), player.level().getGameTime() + ticks);
    }

    /** Drops form stats that did not belong to the saved form, e.g. after death or when a form becomes invalid. */
    public static void clearStats(ServerPlayer player) {
        Passives.clear(player);
        player.removeAttached(ShapeshiftMod.FORM);
        player.removeAttached(ShapeshiftMod.FUSE);
        Reactions.onFormChanged(player);
        player.setHealth(Math.min(player.getHealth(), player.getMaxHealth()));
        player.refreshDimensions();
    }

    public static Unlocks unlocks(ServerPlayer player) {
        return player.getAttachedOrElse(ShapeshiftMod.UNLOCKS, Unlocks.EMPTY);
    }

    /** @return whether the form was newly unlocked */
    public static boolean unlock(ServerPlayer player, EntityType<?> type) {
        String id = Forms.id(type);
        if (!Forms.isLivingForm(type) || ShapeshiftConfig.isBlocked(id)) return false;
        Unlocks before = unlocks(player);
        Unlocks after = before.with(id);
        if (after == before) return false;
        player.setAttached(ShapeshiftMod.UNLOCKS, after);
        return true;
    }

    public static void forget(ServerPlayer player) {
        lastSwitch.remove(player.getUUID());
        blockedUntil.remove(player.getUUID());
        Actives.forget(player);
    }

    /**
     * Smoke for everyone else; the transforming player only hears it, because smoke spawned inside
     * their own body would fill the first-person view.
     */
    private static void effects(ServerPlayer player) {
        var level = player.level();
        double w = player.getBbWidth() / 2, h = player.getBbHeight() / 2;
        for (ServerPlayer viewer : level.players())
            if (viewer != player)
                level.sendParticles(viewer, ParticleTypes.POOF, false, false, player.getX(), player.getY() + h, player.getZ(), 20, w, h, w, 0.02);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
            SoundEvents.ILLUSIONER_MIRROR_MOVE, SoundSource.PLAYERS, 0.8f, 1.2f);
    }
}
