package dev.alan.shapeshift.client.mixin;

import dev.alan.shapeshift.ShapeshiftMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A pig has no human arm: while shapeshifted, first person shows held items but no bare arm or map hands. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonHandsMixin {
    @Inject(method = {"renderPlayerArm", "renderPlayerHand"}, at = @At("HEAD"), cancellable = true)
    private void shapeshift$hideHumanArm(CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player != null && player.hasAttached(ShapeshiftMod.FORM)) ci.cancel();
    }
}
