package cc.aerial.client.mixin;

import cc.aerial.client.screen.title.SplashCard;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;

import java.util.function.IntSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {
    @Shadow
    private float currentProgress;
    @Shadow
    private long fadeOutStart;
    @Shadow
    private long fadeInStart;

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Ljava/util/function/IntSupplier;getAsInt()I"))
    private int aerial$brandColor(IntSupplier supplier) {
        return SplashCard.backgroundColor();
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void aerial$skipFill(GuiGraphicsExtractor extractor, int x0, int y0, int x1, int y1,
                                  int color) {
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
    private void aerial$skipLogo(GuiGraphicsExtractor extractor, RenderPipeline pipeline,
                                  Identifier texture, int x, int y, float u, float v,
                                  int width, int height, int regionWidth, int regionHeight,
                                  int textureWidth, int textureHeight, int color) {
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/LoadingOverlay;extractProgressBar(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIF)V"))
    private void aerial$skipProgressBar(LoadingOverlay overlay, GuiGraphicsExtractor extractor,
                                         int x0, int y0, int x1, int y1, float progress) {
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void aerial$drawSplash(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                                    float partialTick, CallbackInfo ci) {
        long now = Util.getMillis();
        float alpha = 1.0f;
        if (fadeInStart > -1L) {
            alpha = Mth.clamp((now - fadeInStart) / (float) LoadingOverlay.FADE_IN_TIME, 0.0f, 1.0f);
        }
        if (fadeOutStart > -1L) {
            alpha = 1.0f - Mth.clamp((now - fadeOutStart) / (float) LoadingOverlay.FADE_OUT_TIME, 0.0f, 1.0f);
        }
        SplashCard.draw(extractor, extractor.guiWidth(), extractor.guiHeight(),
                currentProgress, alpha);
    }
}
