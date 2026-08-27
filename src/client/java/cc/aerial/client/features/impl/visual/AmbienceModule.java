package cc.aerial.client.features.impl.visual;

import cc.aerial.client.event.impl.game.packet.ReceivePacketEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.NumberProperty;
import net.minecraft.network.protocol.game.ClientboundSetTimePacket;

import java.time.LocalTime;

public final class AmbienceModule extends Module {
    public static final AmbienceModule INSTANCE = new AmbienceModule();

    private final BooleanProperty useRealTime = new BooleanProperty("Use Real Time", false);
    private final NumberProperty time = new NumberProperty("Time", 1000, 0, 23450, 50).hideIf(useRealTime::getValue);

    private AmbienceModule() {
        super("Ambience", "Changes the time of day", ModuleCategory.VISUAL);
        addProperties(useRealTime, time);
    }

    public long getForcedTime() {
        if (!useRealTime.getValue()) {
            return time.getValue().longValue();
        }
        LocalTime localTime = LocalTime.now();
        long totalMinutes = localTime.getHour() * 60L + localTime.getMinute();
        long minecraftTime = (totalMinutes * 1000L / 1440L) * 24L;
        return (minecraftTime + 18000L) % 24000L;
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
        if (event.getPacket() instanceof ClientboundSetTimePacket) {
            event.setCancelled();
        }
    }
}
