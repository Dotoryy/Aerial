package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.VanillaFixModule;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "addWeatherPass", at = @At("HEAD"), cancellable = true)
    private void aerial$removeWeather(FrameGraphBuilder frameGraphBuilder, GpuBufferSlice fogBuffer, CallbackInfo ci) {
        if (VanillaFixModule.INSTANCE.isWeatherRemoved()) {
            ci.cancel();
        }
    }
}
