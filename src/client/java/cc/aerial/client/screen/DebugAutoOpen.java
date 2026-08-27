package cc.aerial.client.screen;

import cc.aerial.client.event.impl.game.JoinWorldEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import net.minecraft.client.Minecraft;

public final class DebugAutoOpen implements IEventSubscriber {
    @Subscribe
    public void onJoinWorld(JoinWorldEvent event) {
        Minecraft.getInstance().setScreenAndShow(new AerialClickGui());
    }
}
