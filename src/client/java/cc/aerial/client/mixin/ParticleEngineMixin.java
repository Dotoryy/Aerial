package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.VanillaFixModule;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Unique
    private static boolean aerial$keepPending;

    @Inject(method = "createParticle", at = @At("HEAD"))
    private void aerial$decideParticle(ParticleOptions options, double x, double y, double z,
                                        double xSpeed, double ySpeed, double zSpeed,
                                        CallbackInfoReturnable<Particle> cir) {
        aerial$keepPending = !VanillaFixModule.INSTANCE.areParticlesRemoved()
                || (VanillaFixModule.INSTANCE.areHitParticlesKept() && aerial$isHitParticle(options));
    }

    @Inject(method = "createParticle", at = @At("RETURN"))
    private void aerial$clearDecision(ParticleOptions options, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed,
                                       CallbackInfoReturnable<Particle> cir) {
        aerial$keepPending = false;
    }

    @Inject(method = "add", at = @At("HEAD"), cancellable = true)
    private void aerial$dropParticle(Particle particle, CallbackInfo ci) {
        if (VanillaFixModule.INSTANCE.areParticlesRemoved() && !aerial$keepPending) {
            ci.cancel();
        }
    }

    @Unique
    private static boolean aerial$isHitParticle(ParticleOptions options) {
        return options.getType() == ParticleTypes.CRIT
                || options.getType() == ParticleTypes.ENCHANTED_HIT
                || options.getType() == ParticleTypes.DAMAGE_INDICATOR;
    }
}
