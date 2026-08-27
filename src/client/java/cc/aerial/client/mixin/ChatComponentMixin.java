package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.ChatModule;
import cc.aerial.client.utility.ScreenshotHandler;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;
import java.util.List;

@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "getWidth()I", at = @At("HEAD"), cancellable = true)
    private void aerial$chatWidth(CallbackInfoReturnable<Integer> cir) {
        if (ChatModule.INSTANCE.isEnabled()) {
            cir.setReturnValue(ChatModule.INSTANCE.getChatWidth());
        }
    }

    @Inject(method = "getHeight()I", at = @At("HEAD"), cancellable = true)
    private void aerial$chatHeight(CallbackInfoReturnable<Integer> cir) {
        if (ChatModule.INSTANCE.isEnabled()) {
            cir.setReturnValue(ChatModule.INSTANCE.getChatHeight(
                    ((ChatComponent) (Object) this).isChatFocused()));
        }
    }

    @Redirect(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
            at = @At(value = "INVOKE", ordinal = 1,
                    target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object aerial$noLineBackground(OptionInstance<?> instance) {
        if (ChatModule.INSTANCE.isBackground()) {
            return 0.0;
        }
        return instance.get();
    }

    @Inject(method = "addMessageToDisplayQueue", at = @At("RETURN"))
    private void aerial$countAddedLines(GuiMessage message, CallbackInfo ci) {
        List<GuiMessage.Line> trimmed = ((ChatComponentAccessor) this).aerial$trimmedMessages();
        int added = 0;
        while (added < trimmed.size() && trimmed.get(added).parent() == message) {
            added++;
        }
        if (added > 0) {
            ChatModule.INSTANCE.onLinesAdded(added, ((ChatComponent) (Object) this).isChatFocused());
        }
    }

    @Inject(method = "addClientSystemMessage", at = @At("HEAD"), cancellable = true)
    private void aerial$replaceScreenshotMessage(Component message, CallbackInfo ci) {
        if (!(message.getContents() instanceof TranslatableContents translatable)
                || !translatable.getKey().equals("screenshot.success")) {
            return;
        }
        File file = aerial$fileFrom(translatable);
        if (file == null) {
            return;
        }
        ci.cancel();
        ((ChatComponent) (Object) this).addClientSystemMessage(ScreenshotHandler.buildMessage(file));
    }

    private static File aerial$fileFrom(TranslatableContents translatable) {
        for (Object argument : translatable.getArgs()) {
            if (argument instanceof Component component
                    && component.getStyle().getClickEvent() instanceof ClickEvent.OpenFile open) {
                return open.file();
            }
        }
        return null;
    }
}
