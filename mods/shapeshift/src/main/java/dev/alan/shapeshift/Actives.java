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
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.projectile.EvokerFangs;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.entity.projectile.hurtingprojectile.LargeFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.SmallFireball;
import net.minecraft.world.entity.projectile.hurtingprojectile.WitherSkull;
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
            case DIVE -> rush(player, look, power, DIVE_DAMAGE, SoundEvents.PHANTOM_SWOOP);
            case DASH -> rush(player, new Vec3(look.x, 0, look.z), power, 0f, SoundEvents.CAMEL_DASH);
            case HEAL_PULSE -> healPulse(player, power);
            case TONGUE -> {
                return tongue(player, power);
            }
            case SLAM -> slam(player, power);
            case SHELL -> shell(player, power);
            case WITHER_SKULL -> {
                var skull = new WitherSkull(level, player, look);
                launchFromEyes(player, skull, look.scale(2));
                sound(player, SoundEvents.WITHER_SHOOT, 1f);
            }
            case SOOTHE -> soothe(player, power);
            case POUNCE -> {
                player.setDeltaMovement(look.x * power, 0.55, look.z * power);
                launched(player, SoundEvents.OCELOT_HURT);
            }
            case BOUNCE -> {
                bounceShock(player);
                player.setDeltaMovement(look.x * 0.3, power, look.z * 0.3);
                launched(player, SoundEvents.SLIME_JUMP);
            }
            case SING -> sing(player, power);
            case GALLOP -> {
                int ticks = Math.max(1, Math.round(power * 20));
                player.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 2));
                player.addEffect(new MobEffectInstance(MobEffects.JUMP_BOOST, ticks, 1));
                sound(player, SoundEvents.HORSE_GALLOP, 1f);
            }
            case TRAMPLE -> trample(player, power);
            case INK_CLOUD -> inkCloud(player, power);
            case SPLASH -> splash(player, power);
            case BUBBLES -> bubbles(player, power);
            case FLASH -> flash(player, power);
            case TUNNEL -> {
                return tunnel(player, Math.max(1, Math.round(power)));
            }
        }
        return true;
    }

    private static final float TRAMPLE_DAMAGE = 5f;

    private static void launched(ServerPlayer player, SoundEvent sound) {
        player.syncVelocity = true;
        player.resetFallDistance();
        sound(player, sound, 1f);
    }

    /** The slime's stomp as it leaves the ground: everything next to it is knocked back. */
    private static void bounceShock(ServerPlayer player) {
        ServerLevel level = player.level();
        for (var target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(3, 1, 3),
                e -> e != player && e.isAlive() && !(e instanceof net.minecraft.world.entity.player.Player))) {
            target.knockback(1.2, player.getX() - target.getX(), player.getZ() - target.getZ(), level.damageSources().playerAttack(player), 0f);
        }
        level.sendParticles(ParticleTypes.ITEM_SLIME, player.getX(), player.getY() + 0.1, player.getZ(), 20, 0.8, 0.1, 0.8, 0.05);
    }

    /** Speed and haste for the user and the players within eight blocks. */
    private static void sing(ServerPlayer player, float seconds) {
        ServerLevel level = player.level();
        int ticks = Math.max(1, Math.round(seconds * 20));
        for (var friend : level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, player.getBoundingBox().inflate(8), e -> e.isAlive() && !e.isSpectator())) {
            friend.addEffect(new MobEffectInstance(MobEffects.SPEED, ticks, 1));
            friend.addEffect(new MobEffectInstance(MobEffects.HASTE, ticks, 1));
            level.sendParticles(ParticleTypes.NOTE, friend.getX(), friend.getY(1.2), friend.getZ(), 3, 0.3, 0.3, 0.3, 1.0);
        }
        sound(player, SoundEvents.NOTE_BLOCK_CHIME.value(), 1f);
    }

    /** A cone in front of the user, {@code range} blocks long and about ninety degrees wide. */
    private static void trample(ServerPlayer player, float range) {
        ServerLevel level = player.level();
        Vec3 look = player.getLookAngle();
        Vec3 dir = new Vec3(look.x, 0, look.z);
        if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 0, 1);
        dir = dir.normalize();
        Vec3 forward = dir;
        var box = player.getBoundingBox().inflate(range, 1.0, range);
        for (var target : level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive() && !(e instanceof net.minecraft.world.entity.player.Player))) {
            Vec3 to = new Vec3(target.getX() - player.getX(), 0, target.getZ() - player.getZ());
            double distance = to.length();
            if (distance > range || distance < 1.0E-4 || to.scale(1 / distance).dot(forward) < 0.7) continue;
            target.hurtServer(level, level.damageSources().playerAttack(player), TRAMPLE_DAMAGE);
            target.knockback(1.4, -forward.x, -forward.z, level.damageSources().playerAttack(player), 0f);
        }
        level.sendParticles(ParticleTypes.POOF, player.getX() + dir.x * 2, player.getY() + 0.2, player.getZ() + dir.z * 2, 20, 1.0, 0.1, 1.0, 0.05);
        sound(player, SoundEvents.ZOMBIE_HORSE_ANGRY, 1f);
    }

    /** Blinds and lights up every creature but the players within {@code radius} blocks. */
    private static void inkCloud(ServerPlayer player, float radius) {
        ServerLevel level = player.level();
        for (var target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && !(e instanceof net.minecraft.world.entity.player.Player))) {
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0));
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0));
        }
        level.sendParticles(ParticleTypes.GLOW_SQUID_INK, player.getX(), player.getY(0.5), player.getZ(), 60, radius / 3, 0.8, radius / 3, 0.05);
        sound(player, SoundEvents.GLOW_SQUID_SQUIRT, 1f);
    }

    /** Puts out fire within four blocks and gives water breathing to the user and the players within six blocks. */
    private static void splash(ServerPlayer player, float seconds) {
        ServerLevel level = player.level();
        int ticks = Math.max(1, Math.round(seconds * 20));
        for (var friend : level.getEntitiesOfClass(net.minecraft.world.entity.player.Player.class, player.getBoundingBox().inflate(6), e -> e.isAlive() && !e.isSpectator())) {
            friend.clearFire();
            friend.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, ticks, 0));
        }
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 3, 4))) {
            if (level.getBlockState(pos).is(net.minecraft.tags.BlockTags.FIRE)) level.removeBlock(pos, false);
        }
        level.sendParticles(ParticleTypes.SPLASH, player.getX(), player.getY(0.5), player.getZ(), 40, 1.0, 0.6, 1.0, 0.1);
        sound(player, SoundEvents.PLAYER_SPLASH, 1f);
    }

    /** A line of bubbles in front of the user: everything inside is pushed along it, unhurt. */
    private static void bubbles(ServerPlayer player, float length) {
        ServerLevel level = player.level();
        Vec3 dir = player.getLookAngle().normalize();
        Vec3 start = player.getEyePosition(), end = start.add(dir.scale(length));
        for (int i = 1; i <= length; i++) {
            Vec3 p = start.add(dir.scale(i));
            level.sendParticles(ParticleTypes.BUBBLE, p.x, p.y, p.z, 3, 0.2, 0.2, 0.2, 0.02);
        }
        var path = new net.minecraft.world.phys.AABB(start, end).inflate(1.5);
        for (var target : level.getEntitiesOfClass(LivingEntity.class, path, e -> e != player && e.isAlive() && !e.isSpectator())) {
            if (distanceToSegment(target.position().add(0, target.getBbHeight() / 2, 0), start, end) > 1.8) continue;
            target.setDeltaMovement(target.getDeltaMovement().add(dir.x * 1.2, dir.y * 1.2 + 0.2, dir.z * 1.2));
            target.syncVelocity = true;
        }
        sound(player, SoundEvents.BUBBLE_COLUMN_UPWARDS_AMBIENT, 1f);
    }

    /** Every creature but the players within {@code radius} blocks glows for ten seconds, and the user sees in the dark. */
    private static void flash(ServerPlayer player, float radius) {
        ServerLevel level = player.level();
        for (var target : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius),
                e -> e != player && e.isAlive() && !(e instanceof net.minecraft.world.entity.player.Player))) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, 200, 0));
        }
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 300, 0));
        level.sendParticles(ParticleTypes.END_ROD, player.getX(), player.getY(0.6), player.getZ(), 40, 1.0, 0.8, 1.0, 0.15);
        sound(player, SoundEvents.BEACON_ACTIVATE, 1f);
    }

    /** Digs a one wide, two high tunnel straight ahead; false (no cooldown) when there was nothing to dig. */
    private static boolean tunnel(ServerPlayer player, int length) {
        ServerLevel level = player.level();
        net.minecraft.core.Direction dir = player.getDirection();
        BlockPos feet = player.blockPosition();
        int dug = 0;
        for (int i = 1; i <= length; i++) {
            BlockPos base = feet.relative(dir, i);
            for (BlockPos pos : new BlockPos[] {base, base.above()}) {
                if (diggable(level, pos) && level.destroyBlock(pos, true, player)) dug++;
            }
        }
        if (dug == 0) {
            player.sendOverlayMessage(Component.translatable("shapeshift.tunnel_miss"));
            return false;
        }
        player.setDeltaMovement(dir.getStepX() * 0.35, player.getDeltaMovement().y, dir.getStepZ() * 0.35);
        player.syncVelocity = true;
        sound(player, SoundEvents.GRAVEL_BREAK, 1f);
        return true;
    }

    /** Stone, dirt, sand, gravel and ore, but nothing with an inventory, nothing unbreakable, nothing harder than ore. */
    private static boolean diggable(ServerLevel level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.isAir() || !state.getFluidState().isEmpty() || level.getBlockEntity(pos) != null) return false;
        float hardness = state.getDestroySpeed(level, pos);
        return hardness >= 0 && hardness <= 5
            && (state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_PICKAXE) || state.is(net.minecraft.tags.BlockTags.MINEABLE_WITH_SHOVEL));
    }

    private static final float DIVE_DAMAGE = 6f, SLAM_DAMAGE = 4f;
    private static final double HEAL_RADIUS = 6, SOOTHE_RADIUS = 16;

    /** Throws the player along {@code direction}; living things within reach of the path are hit and thrown ahead of the rush. */
    private static void rush(ServerPlayer player, Vec3 direction, double speed, float damage, SoundEvent sound) {
        if (direction.lengthSqr() < 1.0E-4) return;
        ServerLevel level = player.level();
        Vec3 dir = direction.normalize();
        Vec3 start = player.position(), end = start.add(dir.scale(speed * 5));
        var path = new net.minecraft.world.phys.AABB(start, end).inflate(1.5, 1.0, 1.5);
        for (var target : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, path, e -> e != player && e.isAlive() && !e.isSpectator())) {
            if (distanceToSegment(target.position().add(0, target.getBbHeight() / 2, 0), start, end) > 1.8) continue;
            if (damage > 0) target.hurtServer(level, level.damageSources().playerAttack(player), damage);
            target.setDeltaMovement(target.getDeltaMovement().add(dir.x * 0.9, 0.3, dir.z * 0.9));
            target.syncVelocity = true;
        }
        player.setDeltaMovement(dir.scale(speed).add(0, direction.y == 0 ? 0.2 : 0, 0));
        player.syncVelocity = true;
        player.resetFallDistance();
        level.sendParticles(ParticleTypes.CLOUD, start.x, start.y + 1, start.z, 12, 0.3, 0.4, 0.3, 0.05);
        sound(player, sound, 1f);
    }

    private static double distanceToSegment(Vec3 point, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = Mth.clamp(point.subtract(a).dot(ab) / Math.max(1.0E-6, ab.lengthSqr()), 0, 1);
        return point.distanceTo(a.add(ab.scale(t)));
    }

    /** Heals the user and every player and animal within six blocks. */
    private static void healPulse(ServerPlayer player, float amount) {
        ServerLevel level = player.level();
        player.heal(amount);
        var around = level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, player.getBoundingBox().inflate(HEAL_RADIUS),
            e -> e != player && e.isAlive() && (e instanceof net.minecraft.world.entity.player.Player || e instanceof net.minecraft.world.entity.animal.Animal));
        for (var target : around) {
            target.heal(amount);
            level.sendParticles(ParticleTypes.HEART, target.getX(), target.getY(0.8), target.getZ(), 4, 0.3, 0.3, 0.3, 0.02);
        }
        level.sendParticles(ParticleTypes.HEART, player.getX(), player.getY(0.8), player.getZ(), 6, 0.4, 0.4, 0.4, 0.02);
        sound(player, SoundEvents.ALLAY_ITEM_GIVEN, 1f);
    }

    /** Pulls the creature or item in the crosshair toward the user; false (no cooldown) when there is nothing to grab. */
    private static boolean tongue(ServerPlayer player, float range) {
        Vec3 eye = player.getEyePosition(), look = player.getLookAngle();
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        var box = player.getBoundingBox().expandTowards(look.scale(range)).inflate(1.5);
        for (Entity e : player.level().getEntities(player, box, x -> x.isAlive() && !x.isSpectator()
                && (x instanceof net.minecraft.world.entity.LivingEntity || x instanceof net.minecraft.world.entity.item.ItemEntity))) {
            Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
            double distance = to.length();
            if (distance > range || distance < 1.5 || to.normalize().dot(look) < 0.96 || !player.hasLineOfSight(e)) continue;
            if (distance < bestDistance) {
                best = e;
                bestDistance = distance;
            }
        }
        if (best == null) {
            player.sendOverlayMessage(Component.translatable("shapeshift.tongue_miss"));
            return false;
        }
        // Snatched to a spot in front of the user (a velocity would overshoot: items and mobs have very different drag).
        for (double reach : new double[] {1.8, 2.6}) {
            Vec3 spot = eye.add(look.scale(reach));
            Vec3 feet = new Vec3(spot.x, spot.y - best.getBbHeight() / 2, spot.z);
            if (!best.level().noCollision(best, best.getDimensions(best.getPose()).makeBoundingBox(feet).deflate(1.0E-7))) continue;
            best.teleportTo(feet.x, feet.y, feet.z);
            best.setDeltaMovement(Vec3.ZERO);
            player.level().sendParticles(ParticleTypes.SPLASH, feet.x, feet.y + 0.5, feet.z, 10, 0.2, 0.2, 0.2, 0.05);
            sound(player, SoundEvents.FROG_TONGUE, 1f);
            return true;
        }
        player.sendOverlayMessage(Component.translatable("shapeshift.no_space"));
        return false;
    }

    /** Hurts, shoves and slows every creature (but not other players) within {@code radius} blocks. */
    private static void slam(ServerPlayer player, float radius) {
        ServerLevel level = player.level();
        var box = player.getBoundingBox().inflate(radius, 1.0, radius);
        for (var target : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box,
                e -> e != player && e.isAlive() && !(e instanceof net.minecraft.world.entity.player.Player) && e.distanceToSqr(player) <= radius * radius)) {
            target.hurtServer(level, level.damageSources().playerAttack(player), SLAM_DAMAGE);
            target.knockback(1.0, player.getX() - target.getX(), player.getZ() - target.getZ(), level.damageSources().playerAttack(player), 0f);
            target.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 60, 1));
        }
        level.sendParticles(ParticleTypes.POOF, player.getX(), player.getY() + 0.1, player.getZ(), 40, radius / 2, 0.1, radius / 2, 0.05);
        sound(player, SoundEvents.POLAR_BEAR_WARNING, 1f);
    }

    /** Heavy damage reduction, almost no movement. */
    private static void shell(ServerPlayer player, float seconds) {
        int ticks = Math.max(1, Math.round(seconds * 20));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, ticks, 3));
        player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, ticks, 5));
        sound(player, SoundEvents.SHIELD_BLOCK.value(), 1f);
    }

    /** Hostile mobs stop targeting the user for {@code seconds}; see {@link Reactions#friendly}. */
    private static void soothe(ServerPlayer player, float seconds) {
        ServerLevel level = player.level();
        player.setAttached(ShapeshiftMod.CALM_UNTIL, level.getGameTime() + Math.max(1, Math.round(seconds * 20)));
        for (var mob : level.getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(SOOTHE_RADIUS),
                e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.getTarget() == player)) mob.setTarget(null);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(), player.getY(0.6), player.getZ(), 20, 1.5, 1.0, 1.5, 0.02);
        sound(player, SoundEvents.AMETHYST_BLOCK_CHIME, 1f);
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
