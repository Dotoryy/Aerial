package cc.aerial.client.mixin;

import cc.aerial.client.screen.server.LoadingCard;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin {
    @Shadow
    private LevelLoadTracker loadTracker;
    @Shadow
    private float smoothedProgress;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void aerial$drawLoading(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                                     float partialTick, CallbackInfo ci) {
        ci.cancel();

        boolean known = loadTracker != null && loadTracker.hasProgress();
        LoadingCard.draw(extractor, extractor.guiWidth(), extractor.guiHeight(),
                "loading terrain", known ? smoothedProgress : -1.0f);
    }
}
