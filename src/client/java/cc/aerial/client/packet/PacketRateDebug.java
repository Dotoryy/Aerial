package cc.aerial.client.packet;

import cc.aerial.client.event.impl.game.packet.SendPacketEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.utility.ChatUtility;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

public final class PacketRateDebug implements IEventSubscriber {
    public static final PacketRateDebug INSTANCE = new PacketRateDebug();

    private static boolean enabled;

    private final Map<String, Integer> counts = new HashMap<>();
    private long windowStart = System.currentTimeMillis();

    private PacketRateDebug() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
        if (!value) {
            INSTANCE.counts.clear();
        }
    }

    public static boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isHandlingEvents() {
        return enabled;
    }

    @Subscribe
    public void onSendPacket(SendPacketEvent event) {
        counts.merge(event.getPacket().getClass().getSimpleName(), 1, Integer::sum);

        long now = System.currentTimeMillis();
        if (now - windowStart < 1000L) {
            return;
        }
        windowStart = now;

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        StringBuilder line = new StringBuilder("packets/s: " + total);
        counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
                .limit(6)
                .forEach(entry -> line.append("  ")
                        .append(shorten(entry.getKey()))
                        .append('=')
                        .append(entry.getValue()));
        counts.clear();
        ChatUtility.print(line.toString());
    }

    private static String shorten(String name) {
        String trimmed = name.startsWith("Serverbound") ? name.substring("Serverbound".length()) : name;
        return trimmed.endsWith("Packet") ? trimmed.substring(0, trimmed.length() - "Packet".length()) : trimmed;
    }
}
