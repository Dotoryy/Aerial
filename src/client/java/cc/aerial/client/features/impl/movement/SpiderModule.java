package cc.aerial.client.features.impl.movement;

import cc.aerial.client.event.impl.game.player.movement.PostMoveEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.NumberProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class SpiderModule extends Module {
    public static final SpiderModule INSTANCE = new SpiderModule();

    private final NumberProperty speed = new NumberProperty("Speed", 0.5, 0.1, 10.0, 0.1);

    private SpiderModule() {
        super("Spider", "Lets you climb walls", ModuleCategory.MOVEMENT);
        addProperties(speed);
    }

    @Subscribe(priority = 5)
    public void onPostMove(PostMoveEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player.horizontalCollision && !player.onClimbable()) {
            player.setDeltaMovement(player.getDeltaMovement().x, speed.getValue(), player.getDeltaMovement().z);
        }
    }
}
