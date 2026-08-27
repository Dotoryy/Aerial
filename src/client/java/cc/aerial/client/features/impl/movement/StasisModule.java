package cc.aerial.client.features.impl.movement;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.packet.InstantaneousSendPacketEvent;
import cc.aerial.client.event.impl.game.input.MoveInputEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public final class StasisModule extends Module {
    public static final StasisModule INSTANCE = new StasisModule();

    private StasisModule() {
        super("Stasis", "Freezes you where the server last saw you", ModuleCategory.MOVEMENT);
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.setDeltaMovement(Vec3.ZERO);
        }
    }

    @Subscribe
    public void onMoveInput(MoveInputEvent event) {
        event.setForward(0.0f);
        event.setSideways(0.0f);
    }

    @Subscribe
    public void onSendPacket(InstantaneousSendPacketEvent event) {
        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundMovePlayerPacket move && move.hasPosition()) {
            event.setCancelled();
        }
    }
}
