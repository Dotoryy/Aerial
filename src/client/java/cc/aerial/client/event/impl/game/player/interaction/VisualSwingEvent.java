package cc.aerial.client.event.impl.game.player.interaction;

import cc.aerial.client.event.EventCancellable;
import net.minecraft.world.InteractionHand;

public final class VisualSwingEvent extends EventCancellable {
    private final InteractionHand hand;

    public VisualSwingEvent(InteractionHand hand) {
        this.hand = hand;
    }

    public InteractionHand getHand() {
        return hand;
    }
}
