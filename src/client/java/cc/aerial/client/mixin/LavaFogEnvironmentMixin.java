package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.VanillaFixModule;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.environment.LavaFogEnvironment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LavaFogEnvironment.class)
public abstract class LavaFogEnvironmentMixin {
    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
    private void aerial$disableFog(FogData data, Camera camera, ClientLevel level, float partialTick,
                                     DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!VanillaFixModule.INSTANCE.isFogDisabled()) {
            return;
        }
        data.environmentalStart = Float.MAX_VALUE;
        data.environmentalEnd = Float.MAX_VALUE;
        data.skyEnd = data.environmentalEnd;
        data.cloudEnd = data.environmentalEnd;
        ci.cancel();
    }
}
