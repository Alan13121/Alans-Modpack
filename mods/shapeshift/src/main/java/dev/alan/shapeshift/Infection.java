package dev.alan.shapeshift;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * A player in a zombie form (a form file with {@code infects_villagers}) that kills a villager does not leave a corpse: the villager
 * rises as a zombie villager that keeps its profession, trades and gossip, and can be cured as usual. The vanilla zombie only does
 * this on normal and hard difficulty and half the time on normal; here it always happens, except in peaceful, where a zombie villager
 * would vanish at once and the villager would simply be lost.
 */
final class Infection {
    private Infection() {}

    static void register() {
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
            !(entity instanceof Villager villager && source.getEntity() instanceof ServerPlayer player && infect(player, villager)));
    }

    /** True when the villager was turned into a zombie villager, which cancels its death. */
    private static boolean infect(ServerPlayer player, Villager villager) {
        if (!(villager.level() instanceof ServerLevel level) || level.getDifficulty() == Difficulty.PEACEFUL) return false;
        var form = Forms.current(player).orElse(null);
        if (form == null || !FormDefinitions.resolve(form, false).infectsVillagers()) return false;
        // The vanilla conversion lives on the zombie that does the killing; a zombie that never enters the world will do.
        var carrier = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.CONVERSION);
        if (carrier == null) return false;
        carrier.snapTo(player.getX(), player.getY(), player.getZ());
        return carrier.convertVillagerToZombieVillager(level, villager);
    }
}
