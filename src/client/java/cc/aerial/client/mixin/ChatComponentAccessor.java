package cc.aerial.client.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
    @Accessor("trimmedMessages")
    List<GuiMessage.Line> aerial$trimmedMessages();

    @Accessor("chatScrollbarPos")
    int aerial$scrollPos();

    @Invoker("getScale")
    double aerial$scale();

    @Invoker("getWidth")
    int aerial$width();
}
