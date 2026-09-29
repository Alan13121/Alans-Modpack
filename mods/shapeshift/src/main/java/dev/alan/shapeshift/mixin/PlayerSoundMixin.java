package dev.alan.shapeshift.mixin;

import dev.alan.shapeshift.FormSounds;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A shapeshifted player oinks, hisses or rattles instead of making the player hurt/death sound. */
@Mixin(Player.class)
abstract class PlayerSoundMixin {
    @Inject(method = "getHurtSound", at = @At("HEAD"), cancellable = true)
    private void shapeshift$hurtSound(DamageSource source, CallbackInfoReturnable<SoundEvent> cir) {
        SoundEvent sound = FormSounds.hurt((Player) (Object) this, source);
        if (sound != null) cir.setReturnValue(sound);
    }

    @Inject(method = "getDeathSound", at = @At("HEAD"), cancellable = true)
    private void shapeshift$deathSound(CallbackInfoReturnable<SoundEvent> cir) {
        SoundEvent sound = FormSounds.death((Player) (Object) this);
        if (sound != null) cir.setReturnValue(sound);
    }
}
