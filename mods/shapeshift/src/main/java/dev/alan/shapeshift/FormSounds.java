package dev.alan.shapeshift;

import dev.alan.shapeshift.mixin.LivingEntityInvoker;
import dev.alan.shapeshift.mixin.MobInvoker;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/** Shapeshifted players sound like their form: hurt, death and the occasional ambient noise. Works on both sides. */
public final class FormSounds {
    /** One detached sample entity per level and type, only used to ask which sound it would make. */
    private static final Map<Level, Map<EntityType<?>, LivingEntity>> samples = new WeakHashMap<>();
    /** Mob.getAmbientSoundInterval is 80 ticks; a 1-in-120 chance per tick keeps players a little quieter. */
    private static final int AMBIENT_CHANCE = 120;
    private FormSounds() {}

    private static @Nullable LivingEntity sample(Player player) {
        var type = Forms.current(player).orElse(null);
        if (type == null) return null;
        return samples.computeIfAbsent(player.level(), l -> new HashMap<>())
            .computeIfAbsent(type, t -> t.create(player.level(), EntitySpawnReason.LOAD) instanceof LivingEntity living ? living : null);
    }

    /** Returns null to keep the normal player sound. */
    public static @Nullable SoundEvent hurt(Player player, DamageSource source) {
        var sample = sample(player);
        return sample == null ? null : ((LivingEntityInvoker) sample).shapeshift$getHurtSound(source);
    }

    public static @Nullable SoundEvent death(Player player) {
        var sample = sample(player);
        return sample == null ? null : ((LivingEntityInvoker) sample).shapeshift$getDeathSound();
    }

    static void tickAmbient(ServerPlayer player) {
        if (player.isSilent() || player.isSpectator() || player.getRandom().nextInt(AMBIENT_CHANCE) != 0) return;
        if (!(sample(player) instanceof Mob mob)) return;
        SoundEvent sound = ((MobInvoker) mob).shapeshift$getAmbientSound();
        if (sound != null)
            player.level().playSound(null, player.getX(), player.getY(), player.getZ(), sound, mob.getSoundSource(), 1f, mob.getVoicePitch());
    }
}
