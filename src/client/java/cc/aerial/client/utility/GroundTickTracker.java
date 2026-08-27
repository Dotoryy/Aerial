package cc.aerial.client.utility;

import cc.aerial.client.event.impl.game.JoinWorldEvent;
import cc.aerial.client.event.impl.game.PostGameTickEvent;
import cc.aerial.client.event.impl.game.packet.ReceivePacketEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;

public final class GroundTickTracker implements IEventSubscriber {
    public static final GroundTickTracker INSTANCE = new GroundTickTracker();

    private static final int START_HIGH = 100_000;

    private static int groundTicks;
    private static int airTicks;

    private static volatile int ticksSinceKnockback = START_HIGH;
    private static volatile int ticksSinceSetback = START_HIGH;

    private GroundTickTracker() {
    }

    public static int getGroundTicks() {
        return groundTicks;
    }

    public static int getAirTicks() {
        return airTicks;
    }

    public static int getTicksSinceKnockback() {
        return ticksSinceKnockback;
    }

    public static int getTicksSinceSetback() {
        return ticksSinceSetback;
    }

    @Subscribe(priority = Integer.MIN_VALUE + 1)
    public void onReceivePacket(ReceivePacketEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !(event.getPacket() instanceof ClientboundSetEntityMotionPacket motion)) {
            return;
        }
        if (motion.id() == player.getId()) {
            ticksSinceKnockback = 0;
        }
    }

    @Subscribe(priority = Integer.MIN_VALUE + 1)
    public void onSetbackPacket(ReceivePacketEvent event) {
        if (event.getPacket() instanceof ClientboundPlayerPositionPacket) {
            ticksSinceSetback = 0;
        }
    }

    @Subscribe
    public void onPostGameTick(PostGameTickEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            groundTicks = 0;
            airTicks = 0;
            return;
        }
        if (player.onGround()) {
            airTicks = 0;
            groundTicks++;
        } else {
            groundTicks = 0;
            airTicks++;
        }
        if (ticksSinceKnockback < START_HIGH) {
            ticksSinceKnockback++;
        }
        if (ticksSinceSetback < START_HIGH) {
            ticksSinceSetback++;
        }
    }

    @Subscribe
    public void onJoinWorld(JoinWorldEvent event) {
        groundTicks = 0;
        airTicks = 0;
        ticksSinceKnockback = START_HIGH;
        ticksSinceSetback = START_HIGH;
    }
}
