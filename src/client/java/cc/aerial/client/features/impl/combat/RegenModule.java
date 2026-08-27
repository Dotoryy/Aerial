package cc.aerial.client.features.impl.combat;

import cc.aerial.client.event.impl.game.player.movement.PreMovementPacketEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.NumberProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public final class RegenModule extends Module {
    public static final RegenModule INSTANCE = new RegenModule();

    private final NumberProperty minimumHealth = new NumberProperty("Minimum Health", 15, 1, 20, 1);

    private final NumberProperty speed = new NumberProperty("Speed", 20, 1, 100, 1);

    private RegenModule() {
        super("Regen", "Speeds up natural health regeneration", ModuleCategory.COMBAT);
        addProperties(minimumHealth, speed);
    }

    @Subscribe
    public void onPreMovementPacket(PreMovementPacketEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientPacketListener connection = mc.getConnection();
        if (player == null || connection == null) {
            return;
        }
        if (player.getHealth() >= minimumHealth.getValue().floatValue()) {
            return;
        }
        int count = speed.getValue().intValue();
        for (int i = 0; i < count; i++) {
            connection.send(new ServerboundMovePlayerPacket.PosRot(
                    player.getX(), player.getY(), player.getZ(),
                    player.getYRot(), player.getXRot(),
                    player.onGround(), player.horizontalCollision));
        }
    }
}
