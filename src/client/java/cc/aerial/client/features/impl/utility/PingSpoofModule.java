package cc.aerial.client.features.impl.utility;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.packet.blockage.BlockHolder;
import cc.aerial.client.packet.blockage.NetworkDirection;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.utility.RandomUtility;
import net.minecraft.network.protocol.game.ClientboundMoveEntityPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;

public final class PingSpoofModule extends Module {
    public static final PingSpoofModule INSTANCE = new PingSpoofModule();

    private final NumberProperty minimumDelay = new NumberProperty("Min Delay", 1000, 50, 30000, 50);
    private final NumberProperty maximumDelay = new NumberProperty("Max Delay", 1500, 50, 30000, 50);
    private final BooleanProperty delayTeleports = new BooleanProperty("Delay Teleports", false);
    private final BooleanProperty delayVelocity = new BooleanProperty("Delay Velocity", false);
    private final BooleanProperty delayEntities = new BooleanProperty("Delay Entity Movements", false);

    private final BlockHolder holder = new BlockHolder(NetworkDirection.INBOUND);

    private long releaseAt;

    private PingSpoofModule() {
        super("Ping Spoof", "Delays incoming packets to inflate the reported ping", ModuleCategory.UTILITY);
        addProperties(minimumDelay, maximumDelay, delayTeleports, delayVelocity, delayEntities);
    }

    @Override
    protected void onDisable() {
        holder.release();
        releaseAt = 0L;
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        long now = System.currentTimeMillis();
        if (holder.isBlocking() && now < releaseAt) {
            return;
        }
        if (holder.isBlocking()) {
            holder.release();
        }
        int min = Math.min(minimumDelay.getValue().intValue(), maximumDelay.getValue().intValue());
        int max = Math.max(minimumDelay.getValue().intValue(), maximumDelay.getValue().intValue());
        releaseAt = now + (min == max ? min : RandomUtility.getRandomInt(min, max + 1));
        holder.block(null, packet -> {
            if (packet instanceof ClientboundPlayerPositionPacket) {
                return delayTeleports.getValue();
            }
            if (packet instanceof ClientboundSetEntityMotionPacket) {
                return delayVelocity.getValue();
            }
            if (packet instanceof ClientboundMoveEntityPacket || packet instanceof ClientboundTeleportEntityPacket) {
                return delayEntities.getValue();
            }
            return true;
        });
    }
}
