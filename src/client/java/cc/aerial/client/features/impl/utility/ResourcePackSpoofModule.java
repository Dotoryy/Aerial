package cc.aerial.client.features.impl.utility;

import cc.aerial.client.event.impl.game.packet.ReceivePacketEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.utility.ChatUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;

public final class ResourcePackSpoofModule extends Module {
    public static final ResourcePackSpoofModule INSTANCE = new ResourcePackSpoofModule();

    private ResourcePackSpoofModule() {
        super("Resource Pack Spoof", "Pretends a requested resource pack loaded", ModuleCategory.UTILITY);
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
        if (!(event.getPacket() instanceof ClientboundResourcePackPushPacket push)) {
            return;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        event.setCancelled();
        connection.send(new ServerboundResourcePackPacket(push.id(),
                ServerboundResourcePackPacket.Action.ACCEPTED));
        connection.send(new ServerboundResourcePackPacket(push.id(),
                ServerboundResourcePackPacket.Action.DOWNLOADED));
        connection.send(new ServerboundResourcePackPacket(push.id(),
                ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
        ChatUtility.print("§7Spoofed resource pack from §f" + push.url());
    }
}
