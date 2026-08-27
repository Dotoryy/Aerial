package cc.aerial.client.mixin;

import cc.aerial.client.screen.server.LoadingCard;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ProgressScreen.class)
public abstract class ProgressScreenMixin {
    @Shadow
    private Component header;
    @Shadow
    private Component stage;
    @Shadow
    private int progress;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void aerial$drawProgress(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                                      float partialTick, CallbackInfo ci) {
        ci.cancel();
        String title = header == null ? "please wait" : header.getString().toLowerCase(java.util.Locale.ROOT);
        LoadingCard.draw(extractor, extractor.guiWidth(), extractor.guiHeight(), title,
                stage == null ? null : stage.getString(),
                progress < 0 ? -1.0f : progress / 100.0f, null, mouseX, mouseY);
    }
}
