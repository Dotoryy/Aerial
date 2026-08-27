package cc.aerial.client.event.impl.game.chat;

import cc.aerial.client.event.EventCancellable;
import net.minecraft.network.chat.Component;

public final class ChatReceivedEvent extends EventCancellable {
    private final Component text;

    public ChatReceivedEvent(Component text) {
        this.text = text;
    }

    public Component getText() {
        return text;
    }
}
