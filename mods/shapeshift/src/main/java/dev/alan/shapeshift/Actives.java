package dev.alan.shapeshift;

import dev.alan.shapeshift.api.SkillHooks;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.windcharge.WindCharge;
import net.minecraft.world.entity.projectile.throwableitemprojectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server-side R-key abilities. */
final class Actives {
    /** Game time at which each player may use their ability again. */
    private static final Map<UUID, Long> readyAt = new HashMap<>();
    private Actives() {}

    static void use(ServerPlayer player) {
        var type = Forms.current(player).orElse(null);
        if (type == null || !player.isAlive() || player.isSpectator()) return;
        var active = FormDefinitions.resolve(type, false).active().orElse(null);
        if (active == null) {
            player.sendOverlayMessage(Component.translatable("shapeshift.no_active"));
            return;
        }
        long now = player.level().getGameTime();
        if (active.type() == ActiveType.EXPLODE && player.hasAttached(ShapeshiftMod.FUSE)) {
            player.removeAttached(ShapeshiftMod.FUSE);   // pressing again defuses
            return;
        }
        long ready = readyAt.getOrDefault(player.getUUID(), 0L);
        if (now < ready) {
            player.sendOverlayMessage(Component.translatable("shapeshift.cooldown", (ready - now + 19) / 20));
            return;
        }
        var scale = SkillHooks.scaleFor(player);
        if (fire(player, active, scale)) {
            readyAt.put(player.getUUID(), now + SkillHooks.scaledCooldown(active.cooldownTicks(), scale));
            Guide.grant(player, "ch5/first_skill");
        }
    }

    /** Runs every tick for shapeshifted players: counts down a creeper fuse. */
    static void tick(ServerPlayer player, FormDefinitions.Resolved form) {
        Long start = player.getAttached(ShapeshiftMod.FUSE);
        if (start == null) return;
        var active = form.active().filter(a -> a.type() == ActiveType.EXPLODE).orElse(null);
        if (active == null) {
            player.removeAttached(ShapeshiftMod.FUSE);
            return;
        }
        if (player.level().getGameTime() - start >= active.fuseTicks()) explode(player, active.powerValue() * SkillHooks.scaleFor(player).power());
    }

    static void forget(ServerPlayer player) { readyAt.remove(player.getUUID()); }

    private static boolean fire(ServerPlayer player, ActiveAbility active, SkillHooks.Scale scale) {
        ServerLevel level = player.level();
        Vec3 look = player.getLookAngle();
        float power = active.powerValue() * scale.power();
        switch (active.type()) {
            case EXPLODE -> {
                if (active.fuseTicks() == 0) {
                    explode(player, power);
                } else {
                    player.setAttached(ShapeshiftMod.FUSE, level.getGameTime());
                    sound(player, SoundEvents.CREEPER_PRIMED, 1f);
                }
            }
            case SMALL_FIREBALL -> {
                var fireball = new SmallFireball(level, player, look);
                launchFromEyes(player, fireball, look);
                sound(player, SoundEvents.BLAZE_SHOOT, 1f);
            }
            case FIREBALL -> {
                var fireball = new LargeFireball(level, player, look, Math.max(0, Math.round(power)));
                launchFromEyes(player, fireball, look.scale(2));
                sound(player, SoundEvents.GHAST_SHOOT, 1f);
            }
            case ARROW -> {
                var arrow = new Arrow(level, player, new ItemStack(Items.ARROW), null);
                arrow.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
                shoot(player, arrow, power, 1f);
                sound(player, SoundEvents.SKELETON_SHOOT, 1f);
            }
            case SNOWBALL -> {
                shoot(player, new Snowball(level, player, new ItemStack(Items.SNOWBALL)), 1.5f, 1f);
                sound(player, SoundEvents.SNOW_GOLEM_SHOOT, 1f);
            }
            case WIND_CHARGE -> {
                shoot(player, new WindCharge(player, level, player.getX(), player.getEyeY(), player.getZ()), 1.5f, 1f);
                sound(player, SoundEvents.BREEZE_SHOOT, 1f);
            }
            case SPIT -> {
                var spit = new LlamaSpit(EntityTypes.LLAMA_SPIT, level);
                spit.setOwner(player);
                spit.setPos(player.getX(), player.getEyeY() - 0.1, player.getZ());
                shoot(player, spit, 1.5f, 5f);
                sound(player, SoundEvents.LLAMA_SPIT, 1f);
            }
            case TELEPORT -> {
                return teleport(player, power);
            }
            case FANGS -> fangs(player, Math.max(1, Math.round(power)));
        }
        return true;
    }

    private static void launchFromEyes(ServerPlayer player, Entity projectile, Vec3 offset) {
        projectile.setPos(player.getX() + offset.x, player.getEyeY() + offset.y - 0.2, player.getZ() + offset.z);
        SkillHooks.launched(player, projectile);
        player.level().addFreshEntity(projectile);
    }

    private static void shoot(ServerPlayer player, Projectile projectile, float speed, float inaccuracy) {
        projectile.shootFromRotation(player, player.getXRot(), player.getYRot(), 0, speed, inaccuracy);
        SkillHooks.launched(player, projectile);
        player.level().addFreshEntity(projectile);
    }

    /** Creeper blast: hurts everything around except the user, then the user becomes human again. */
    static void explode(ServerPlayer player, float power) {
        player.removeAttached(ShapeshiftMod.FUSE);
        var spareUser = new ExplosionDamageCalculator() {
            @Override public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                return entity != player && super.shouldDamageEntity(explosion, entity);
            }
            @Override public float getKnockbackMultiplier(Entity entity) {
                return entity == player ? 0 : super.getKnockbackMultiplier(entity);
            }
        };
        player.level().explode(player, null, spareUser, player.getX(), player.getY(), player.getZ(), power, false,
            ShapeshiftConfig.explosionsBreakBlocks() ? Level.ExplosionInteraction.MOB : Level.ExplosionInteraction.NONE);
        Shapeshifter.apply(player, null, false);
        Shapeshifter.startCooldown(player, ShapeshiftConfig.explodeCooldownTicks());
    }

    private static boolean teleport(ServerPlayer player, float range) {
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getLookAngle().scale(range));
        BlockHitResult hit = player.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS) {
            player.sendOverlayMessage(Component.translatable("shapeshift.teleport_miss"));
            return false;
        }
        BlockPos base = hit.getBlockPos().relative(hit.getDirection());
        for (int up = 0; up <= 3; up++) {
            Vec3 feet = Vec3.atBottomCenterOf(base.above(up));
            var box = player.getDimensions(player.getPose()).makeBoundingBox(feet).deflate(1.0E-7);
            if (!player.level().noCollision(player, box)) continue;
            Vec3 old = player.position();
            player.teleportTo(feet.x, feet.y, feet.z);
            player.resetFallDistance();
            portalEffects(player.level(), old);
            portalEffects(player.level(), feet);
            player.level().playSound(null, old.x, old.y, old.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1f, 1f);
            sound(player, SoundEvents.ENDERMAN_TELEPORT, 1f);
            return true;
        }
        player.sendOverlayMessage(Component.translatable("shapeshift.no_space"));
        return false;
    }

    private static void portalEffects(ServerLevel level, Vec3 at) {
        level.sendParticles(ParticleTypes.PORTAL, at.x, at.y + 1, at.z, 40, 0.4, 0.8, 0.4, 0.2);
    }

    /** Evoker fangs in a line along the look direction, each placed on the ground like the evoker does. */
    private static void fangs(ServerPlayer player, int count) {
        float yaw = player.getYRot() * Mth.DEG_TO_RAD;
        double dx = -Mth.sin(yaw), dz = Mth.cos(yaw);
        float angle = (float) Mth.atan2(dz, dx);
        for (int i = 1; i <= count; i++) {
            double x = player.getX() + dx * 1.25 * i, z = player.getZ() + dz * 1.25 * i;
            Double y = groundY(player.level(), x, player.getY(), z);
            if (y != null) player.level().addFreshEntity(new EvokerFangs(player.level(), x, y, z, angle, i, player));
        }
        sound(player, SoundEvents.EVOKER_CAST_SPELL, 1f);
    }

    private static Double groundY(ServerLevel level, double x, double startY, double z) {
        BlockPos pos = BlockPos.containing(x, startY + 2, z);
        for (int i = 0; i < 6; i++, pos = pos.below()) {
            BlockPos below = pos.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, net.minecraft.core.Direction.UP)
                && !level.getBlockState(pos).isCollisionShapeFullBlock(level, pos)) {
                var shape = level.getBlockState(pos).getCollisionShape(level, pos);
                return pos.getY() + (shape.isEmpty() ? 0 : shape.max(net.minecraft.core.Direction.Axis.Y));
            }
        }
        return null;
    }

    private static void sound(ServerPlayer player, SoundEvent sound, float volume) {
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, SoundSource.PLAYERS, volume, 1f);
    }
}
