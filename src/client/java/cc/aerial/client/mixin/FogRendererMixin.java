package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.VanillaFixModule;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Final;
import cc.aerial.client.features.impl.visual.ViewClipModule;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class FogRendererMixin {
    @Shadow
    @Final
    private GpuBuffer emptyBuffer;

    @Inject(method = "setupFog", at = @At("TAIL"))
    private void aerial$overrideRenderDistanceFog(Camera camera, int renderDistance, DeltaTracker deltaTracker,
                                                     float partialTick, ClientLevel level, CallbackInfoReturnable<FogData> cir) {
        if (VanillaFixModule.INSTANCE.isFogDisabled()) {
            FogData data = cir.getReturnValue();
            data.renderDistanceStart = Float.MAX_VALUE;
            data.renderDistanceEnd = Float.MAX_VALUE;
        }
    }

    @Inject(method = "getBuffer", at = @At("HEAD"), cancellable = true)
    private void aerial$disableFog(FogRenderer.FogMode mode, CallbackInfoReturnable<GpuBufferSlice> cir) {
        if (VanillaFixModule.INSTANCE.isFogDisabled()) {
            cir.setReturnValue(emptyBuffer.slice(0, FogRenderer.FOG_UBO_SIZE));
        }
    }

    @Inject(method = "getFogType", at = @At("HEAD"), cancellable = true)
    private void aerial$viewClipFog(Camera camera, CallbackInfoReturnable<FogType> cir) {
        if (ViewClipModule.INSTANCE.isEnabled()) {
            cir.setReturnValue(FogType.ATMOSPHERIC);
        }
    }
}
