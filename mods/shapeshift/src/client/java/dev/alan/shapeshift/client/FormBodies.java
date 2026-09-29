package dev.alan.shapeshift.client;

import dev.alan.shapeshift.ActiveAbility;
import dev.alan.shapeshift.FormDefinitions;
import dev.alan.shapeshift.Forms;
import dev.alan.shapeshift.ShapeshiftMod;
import dev.alan.shapeshift.client.mixin.CreeperAccessor;
import dev.alan.shapeshift.client.mixin.WalkAnimationStateAccessor;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.Creeper;
import org.jspecify.annotations.Nullable;

/**
 * Client-only stand-in entities that are drawn in place of shapeshifted players.
 * They never join the level and never tick; each frame they copy the player's position and animation.
 */
public final class FormBodies {
    private static final Map<UUID, Entity> bodies = new HashMap<>();
    /** Types whose renderer failed with a detached entity; those players fall back to the normal model. */
    private static final Set<EntityType<?>> broken = new HashSet<>();
    /** Negative ids never collide with real entities, which the server numbers from 1 upwards. */
    private static int nextId = -1_000_000;
    private FormBodies() {}

    /** Creates a detached entity that renderers can draw (they need an id even outside the level). */
    public static @Nullable Entity detached(EntityType<?> type, net.minecraft.world.level.Level level) {
        Entity entity = type.create(level, EntitySpawnReason.LOAD);
        if (entity != null) entity.setId(nextId--);
        return entity;
    }

    public static void clear() { bodies.clear(); }

    /** Render state of the form body, or null to draw the player normally. */
    public static @Nullable EntityRenderState extract(AbstractClientPlayer player, float partialTicks) {
        var type = Forms.current(player).orElse(null);
        if (type == null || broken.contains(type)) {
            bodies.remove(player.getUUID());
            return null;
        }
        Entity body = bodies.get(player.getUUID());
        if (body == null || body.getType() != type || body.level() != player.level()) {
            body = detached(type, player.level());
            if (body == null) {
                broken.add(type);
                return null;
            }
            bodies.put(player.getUUID(), body);
        }
        mirror(player, body);
        try {
            return Minecraft.getInstance().getEntityRenderDispatcher().extractEntity(body, partialTicks);
        } catch (RuntimeException e) {
            ShapeshiftMod.LOG.warn("Cannot render form {}; showing the player model instead", Forms.id(type), e);
            broken.add(type);
            bodies.remove(player.getUUID());
            return null;
        }
    }

    private static void mirror(AbstractClientPlayer player, Entity body) {
        body.setPos(player.getX(), player.getY(), player.getZ());
        body.xo = player.xo; body.yo = player.yo; body.zo = player.zo;
        body.xOld = player.xOld; body.yOld = player.yOld; body.zOld = player.zOld;
        body.setYRot(player.getYRot()); body.yRotO = player.yRotO;
        body.setXRot(player.getXRot()); body.xRotO = player.xRotO;
        body.tickCount = player.tickCount;
        body.setOnGround(player.onGround());
        body.setShiftKeyDown(player.isShiftKeyDown());
        body.setInvisible(player.isInvisible());
        // Other players keep their name tag; your own stays hidden like vanilla.
        body.setCustomName(player.getName());
        body.setCustomNameVisible(player != Minecraft.getInstance().player && !player.isInvisible());
        body.setSharedFlagOnFire(player.isOnFire());   // clients only know the flag, not the fire timer
        if (body instanceof LivingEntity living) {
            living.yBodyRot = player.yBodyRot; living.yBodyRotO = player.yBodyRotO;
            living.yHeadRot = player.yHeadRot; living.yHeadRotO = player.yHeadRotO;
            living.hurtTime = player.hurtTime;
            living.deathTime = player.deathTime;
            var from = (WalkAnimationStateAccessor) player.walkAnimation;
            var to = (WalkAnimationStateAccessor) living.walkAnimation;
            to.shapeshift$setSpeedOld(from.shapeshift$getSpeedOld());
            to.shapeshift$setSpeed(from.shapeshift$getSpeed());
            to.shapeshift$setPosition(from.shapeshift$getPosition());
            to.shapeshift$setPositionScale(from.shapeshift$getPositionScale());
        }
        // A new bat starts hanging upside down; a player-bat should always look like it is flying.
        if (body instanceof Bat bat) bat.setResting(false);
        if (body instanceof Creeper creeper) swell(player, creeper);
    }

    /** Scales the fuse progress onto the creeper's own swell counter so it puffs up exactly when it will explode. */
    private static void swell(AbstractClientPlayer player, Creeper creeper) {
        var accessor = (CreeperAccessor) creeper;
        Long start = player.getAttached(ShapeshiftMod.FUSE);
        int fuse = FormDefinitions.resolve(creeper.getType(), true).active().map(ActiveAbility::fuseTicks).orElse(0);
        if (start == null || fuse <= 0) {
            accessor.shapeshift$setSwell(0);
            accessor.shapeshift$setOldSwell(0);
            return;
        }
        int max = accessor.shapeshift$getMaxSwell();
        long elapsed = player.level().getGameTime() - start;
        accessor.shapeshift$setSwell((int) Math.min(max, elapsed * max / fuse));
        accessor.shapeshift$setOldSwell((int) Math.clamp((elapsed - 1) * max / fuse, 0, max));
    }
}
