package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.ChatModule;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public class ChatLineConsumerMixin {
    @Redirect(method = "accept", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage$Line;tag()Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;"))
    private GuiMessageTag aerial$hideTagBar(GuiMessage.Line line) {
        return ChatModule.INSTANCE.isBackground() ? null : line.tag();
    }
}
