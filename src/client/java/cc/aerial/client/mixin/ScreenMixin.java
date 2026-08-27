package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.VanillaFixModule;
import cc.aerial.client.utility.ScreenshotHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {
    @Shadow
    protected Minecraft minecraft;

    @Inject(method = "extractBlurredBackground", at = @At("HEAD"), cancellable = true)
    private void aerial$removeBlur(GuiGraphicsExtractor extractor, CallbackInfo ci) {
        if (VanillaFixModule.INSTANCE.isBlurRemoved()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractTransparentBackground", at = @At("HEAD"), cancellable = true)
    private void aerial$removeTransparentDarken(GuiGraphicsExtractor extractor, CallbackInfo ci) {
        if (VanillaFixModule.INSTANCE.isBlurRemoved()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V",
            at = @At("HEAD"), cancellable = true)
    private void aerial$removeMenuDarken(GuiGraphicsExtractor extractor, int x0, int y0, int x1, int y1,
                                          CallbackInfo ci) {
        if (VanillaFixModule.INSTANCE.isBlurRemoved() && minecraft.level != null) {
            ci.cancel();
        }
    }

    @Inject(method = "defaultHandleGameClickEvent", at = @At("HEAD"), cancellable = true)
    private static void aerial$onGameClickEvent(ClickEvent event, Minecraft minecraft,
                                                 Screen screen, CallbackInfo ci) {
        if (aerial$handleClick(event)) {
            ci.cancel();
        }
    }

    @Inject(method = "defaultHandleClickEvent", at = @At("HEAD"), cancellable = true)
    private static void aerial$onClickEvent(ClickEvent event, Minecraft minecraft,
                                             Screen screen, CallbackInfo ci) {
        if (aerial$handleClick(event)) {
            ci.cancel();
        }
    }

    private static boolean aerial$handleClick(ClickEvent event) {
        if (event instanceof ClickEvent.Custom custom
                && custom.id().equals(ScreenshotHandler.COPY_ACTION)) {
            ScreenshotHandler.copyLastScreenshot();
            return true;
        }
        return false;
    }
}
