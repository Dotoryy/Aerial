package cc.aerial.client.features.impl.utility;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.packet.blockage.BlockHolder;
import cc.aerial.client.packet.blockage.NetworkDirection;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.MultipleBooleanProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.utility.RandomUtility;
import cc.aerial.client.utility.Stopwatch;
import net.minecraft.network.protocol.common.ClientboundPingPacket;

public final class BlinkModule extends Module {
    public static final BlinkModule INSTANCE = new BlinkModule();

    private final MultipleBooleanProperty blinkDirections = new MultipleBooleanProperty("Direction",
            new BooleanProperty("Inbound", true),
            new BooleanProperty("Outbound", true));

    private final BooleanProperty pulse = new BooleanProperty("Pulse", false);
    private final NumberProperty pulseDelayMin = new NumberProperty("Pulse Delay Min (ms)", 1000, 50, 10000, 50).hideIf(() -> !pulse.getValue());
    private final NumberProperty pulseDelayMax = new NumberProperty("Pulse Delay Max (ms)", 2000, 50, 10000, 50).hideIf(() -> !pulse.getValue());

    private final Stopwatch inboundPulseTimer = new Stopwatch();
    private final Stopwatch outboundPulseTimer = new Stopwatch();

    private final BlockHolder inboundHolder = new BlockHolder(NetworkDirection.INBOUND);
    private final BlockHolder outboundHolder = new BlockHolder(NetworkDirection.OUTBOUND);

    private BlinkModule() {
        super("Blink", "Blocks your network connection", ModuleCategory.UTILITY);
        addProperties(blinkDirections, pulse, pulseDelayMin, pulseDelayMax);
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        if (blinkDirections.getProperty("Inbound").getValue()) {
            inboundHolder.block(p -> p, p -> !(p instanceof ClientboundPingPacket));

            if (pulse.getValue() && inboundPulseTimer.hasTimeElapsed(
                    RandomUtility.getRandomInt(pulseDelayMin.getValue().intValue(), pulseDelayMax.getValue().intValue()), true)) {
                inboundHolder.release();
            }
        }
        if (blinkDirections.getProperty("Outbound").getValue()) {
            outboundHolder.block();

            if (pulse.getValue() && outboundPulseTimer.hasTimeElapsed(
                    RandomUtility.getRandomInt(pulseDelayMin.getValue().intValue(), pulseDelayMax.getValue().intValue()), true)) {
                outboundHolder.release();
            }
        }
    }

    @Override
    protected void onDisable() {
        inboundHolder.release();
        outboundHolder.release();
    }
}
