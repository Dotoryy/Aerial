package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.FullBrightModule;
import net.minecraft.client.renderer.LightmapRenderStateExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LightmapRenderStateExtractor.class)
public final class LightmapRenderStateExtractorMixin {
    @Redirect(
            method = "extract",
            at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0)
    )
    private float aerial$fullBright(Double gamma) {
        return gamma.floatValue() * (FullBrightModule.INSTANCE.isEnabled() ? 15.0f : 1.0f);
    }
}
