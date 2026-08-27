package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.VanillaFixModule;
import cc.aerial.client.features.impl.world.ScaffoldModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
    @Redirect(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getXRot(F)F"))
    private float aerial$scaffoldRenderPitch(LivingEntity entity, float partialTick) {
        if (entity == Minecraft.getInstance().player && ScaffoldModule.INSTANCE.isRotating()) {
            return ScaffoldModule.INSTANCE.getRenderPitch(partialTick);
        }
        return entity.getXRot(partialTick);
    }

    @Inject(method = "shouldShowName(Lnet/minecraft/world/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void aerial$disableVanillaNameTags(LivingEntity entity, double distanceToSqr, CallbackInfoReturnable<Boolean> cir) {
        if (VanillaFixModule.INSTANCE.isVanillaNameTagsDisabled()) {
            cir.setReturnValue(false);
        }
    }
}
