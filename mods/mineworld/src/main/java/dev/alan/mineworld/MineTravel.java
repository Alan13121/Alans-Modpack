package dev.alan.mineworld;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

/** Sneak on a world cauldron (or the return block) for 4 seconds, like waiting in a nether portal. */
public final class MineTravel {
    public static final int DELAY_TICKS = 80;

    private static final Map<UUID, Integer> COUNTDOWN = new HashMap<>();
    /** Players who released sneak since their last teleport; you must let go before a new trip can start. */
    private static final Set<UUID> ARMED = new HashSet<>();

    private MineTravel() {}

    public static void tick(MinecraftServer server) {
        DynamicLevels.processQueue(server);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) tickPlayer(server, player);
    }

    public static void forget() { COUNTDOWN.clear(); ARMED.clear(); }

    private static void tickPlayer(MinecraftServer server, ServerPlayer player) {
        UUID id = player.getUUID();
        if (!player.isShiftKeyDown()) {
            ARMED.add(id);
            cancel(player);
            return;
        }
        ServerLevel level = player.level();
        boolean onTarget = player.onGround() && isTarget(level, player);
        boolean counting = COUNTDOWN.containsKey(id);
        if (!onTarget || (!counting && !ARMED.contains(id))) {
            cancel(player);
            return;
        }
        int ticks = COUNTDOWN.merge(id, 1, Integer::sum);
        if (ticks == 1) {
            player.addEffect(new MobEffectInstance(MobEffects.NAUSEA, DELAY_TICKS + 20, 0, false, false));
            level.playSound(null, player.blockPosition(), SoundEvents.PORTAL_TRIGGER, SoundSource.PLAYERS, 0.3F, 1.0F);
        }
        if (ticks % 3 == 0) {
            level.sendParticles(ParticleTypes.PORTAL, player.getX(), player.getY() + 1.0, player.getZ(), 6, 0.3, 0.6, 0.3, 0.2);
        }
        if (ticks >= DELAY_TICKS) {
            cancel(player);
            ARMED.remove(id);
            travel(server, player);
        }
    }

    private static void cancel(ServerPlayer player) {
        if (COUNTDOWN.remove(player.getUUID()) != null) player.removeEffect(MobEffects.NAUSEA);
    }

    private static BlockPos standingPos(ServerPlayer player) {
        return BlockPos.containing(player.getX(), player.getY() - 0.01, player.getZ());
    }

    private static boolean isTarget(ServerLevel level, ServerPlayer player) {
        var state = level.getBlockState(standingPos(player));
        return state.is(MineWorldMod.WORLD_CAULDRON) || state.is(MineWorldMod.RETURN_BLOCK);
    }

    private static void travel(MinecraftServer server, ServerPlayer player) {
        ServerLevel level = player.level();
        BlockPos pos = standingPos(player);
        if (level.getBlockState(pos).is(MineWorldMod.WORLD_CAULDRON)) {
            if (level.getBlockEntity(pos) instanceof WorldCauldronBlockEntity be) enter(server, player, level, pos, be);
        } else if (DynamicLevels.worldId(level) != null) {
            leave(server, player, DynamicLevels.worldId(level));
        }
    }

    private static void enter(MinecraftServer server, ServerPlayer player, ServerLevel from, BlockPos pos, WorldCauldronBlockEntity be) {
        String worldId = be.assignWorld();
        ServerLevel target = DynamicLevels.ensure(server, worldId);
        if (target == null) return;
        MineWorldData.get(server).setOrigin(worldId, GlobalPos.of(from.dimension(), pos));
        Vec3 dest = Vec3.atBottomCenterOf(DynamicLevels.ARRIVAL);
        player.teleport(new TeleportTransition(target, dest, Vec3.ZERO, 180.0F, 0.0F, TeleportTransition.PLAY_PORTAL_SOUND));
        player.removeEffect(MobEffects.NAUSEA);
        Component name = be.customName() != null ? be.customName() : Component.literal(worldId);
        player.sendSystemMessage(Component.translatable("mineworld.entered", name), true);
    }

    private static void leave(MinecraftServer server, ServerPlayer player, String worldId) {
        MineWorldData.WorldInfo info = MineWorldData.get(server).world(worldId);
        ServerLevel dest = server.overworld();
        BlockPos base = server.overworld().getRespawnData().pos();
        if (info != null && info.origin().isPresent()) {
            ServerLevel originLevel = server.getLevel(info.origin().get().dimension());
            if (originLevel != null) { dest = originLevel; base = info.origin().get().pos(); }
        }
        Vec3 spot = freeSpotAbove(dest, base);
        player.teleport(new TeleportTransition(dest, spot, Vec3.ZERO, player.getYRot(), 0.0F, TeleportTransition.PLAY_PORTAL_SOUND));
        player.removeEffect(MobEffects.NAUSEA);
    }

    /** On top of the cauldron, or the nearest spot where the player fits when something is in the way. */
    static Vec3 freeSpotAbove(ServerLevel level, BlockPos cauldron) {
        for (int r = 0; r <= 4; r++) {
            for (int dy = 1; dy <= 3; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                        BlockPos feet = cauldron.offset(dx, dy, dz);
                        if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                            && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()) {
                            return new Vec3(feet.getX() + 0.5, feet.getY() + (r == 0 && dy == 1 ? 0.0 : 0.0), feet.getZ() + 0.5);
                        }
                    }
                }
            }
        }
        return Vec3.atBottomCenterOf(cauldron.above());
    }
}
