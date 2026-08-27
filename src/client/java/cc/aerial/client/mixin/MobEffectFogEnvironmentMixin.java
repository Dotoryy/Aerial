package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.world.AntiDebuffModule;
import net.minecraft.client.renderer.fog.environment.MobEffectFogEnvironment;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEffectFogEnvironment.class)
public abstract class MobEffectFogEnvironmentMixin {
    @Inject(method = "isApplicable", at = @At("HEAD"), cancellable = true)
    private void aerial$antiBlindness(FogType fogType, Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!AntiDebuffModule.INSTANCE.isBlindnessRemoved()) {
            return;
        }
        Holder<MobEffect> effect = ((MobEffectFogEnvironment) (Object) this).getMobEffect();
        if (effect == MobEffects.BLINDNESS && entity == net.minecraft.client.Minecraft.getInstance().player) {
            cir.setReturnValue(false);
        }
    }
}
