package cc.aerial.client.features.impl.movement;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.notification.NotificationManager;
import cc.aerial.client.notification.NotificationType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class VClipModule extends Module {
    public static final VClipModule INSTANCE = new VClipModule();

    private final NumberProperty distance = new NumberProperty("Distance", 3.0, -20.0, 20.0, 0.5);
    private final BooleanProperty sendMessage = new BooleanProperty("Send message", true);

    private VClipModule() {
        super("VClip", "Teleports you vertically", ModuleCategory.MOVEMENT);
        addProperties(distance, sendMessage);
    }

    @Override
    public String getSuffix() {
        return String.format("%.1f", distance.getValue().doubleValue());
    }

    @Override
    protected void onEnable() {
        LocalPlayer player = Minecraft.getInstance().player;
        double offset = distance.getValue().doubleValue();
        if (player != null && offset != 0.0) {
            player.setPos(player.getX(), player.getY() + offset, player.getZ());
            if (sendMessage.getValue()) {
                NotificationManager.INSTANCE.builder(NotificationType.INFO)
                        .title("VClip")
                        .description("Teleported " + (offset > 0.0 ? "up" : "down")
                                + " by " + Math.abs(offset) + " blocks")
                        .buildAndPublish();
            }
        }

        setEnabled(false);
    }
}
