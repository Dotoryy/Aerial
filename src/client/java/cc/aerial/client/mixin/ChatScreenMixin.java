package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.ChatModule;
import cc.aerial.client.render.BlurConsumer;
import cc.aerial.client.render.ChatDecoration;
import cc.aerial.client.render.AerialBlur;
import cc.aerial.client.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {
    private static final float INPUT_RADIUS = 4.0f;
    private static final int INPUT_BACKGROUND = 0x8C0F0F0F;

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", shift = At.Shift.BEFORE,
            target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"))
    private void aerial$beforeChat(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                                    float partialTick, CallbackInfo ci) {
        ChatDecoration.before(extractor);
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"))
    private void aerial$afterChat(GuiGraphicsExtractor extractor, int mouseX, int mouseY,
                                   float partialTick, CallbackInfo ci) {
        ChatDecoration.after(extractor);
    }

    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void aerial$inputBackground(GuiGraphicsExtractor extractor,
                                         int x0, int y0, int x1, int y1, int color) {
        if (!ChatModule.INSTANCE.isBackground()) {
            extractor.fill(x0, y0, x1, y1, color);
            return;
        }
        float width = x1 - x0;
        float height = y1 - y0;
        AerialBlur.drawGlass(extractor, BlurConsumer.CHAT, x0, y0, width, height, INPUT_RADIUS,
                INPUT_BACKGROUND, 1.0f, null);
    }
}
