package cc.aerial.client.utility;

import cc.aerial.client.event.impl.game.JoinWorldEvent;
import cc.aerial.client.event.impl.game.PostGameTickEvent;
import cc.aerial.client.event.impl.game.player.teleport.PostTeleportEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import net.minecraft.client.Minecraft;

public final class TeleportTickTracker implements IEventSubscriber {
    public static final TeleportTickTracker INSTANCE = new TeleportTickTracker();

    private static int ticksSinceTeleport;

    private TeleportTickTracker() {
    }

    public static int getTicksSinceTeleport() {
        return ticksSinceTeleport;
    }

    @Subscribe
    public void onPostTeleport(PostTeleportEvent event) {
        ticksSinceTeleport = 0;
    }

    @Subscribe
    public void onPostGameTick(PostGameTickEvent event) {
        if (Minecraft.getInstance().player == null) {
            ticksSinceTeleport = 0;
            return;
        }
        ticksSinceTeleport++;
    }

    @Subscribe
    public void onJoinWorld(JoinWorldEvent event) {
        ticksSinceTeleport = 0;
    }
}
