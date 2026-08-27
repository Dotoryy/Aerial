package cc.aerial.client.screen;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class MovementPassthrough implements IEventSubscriber {
    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();

        Screen screen = mc.gui.screen();
        boolean ours = screen instanceof AerialClickGui || screen instanceof RailClickGui;
        if (!ClickGuiState.isAllowMovement() || !ours) {
            return;
        }

        for (KeyMapping key : movementKeys(mc)) {
            boolean physicallyDown = InputConstants.isKeyDown(mc.getWindow(), key.getDefaultKey().getValue());
            KeyMapping.set(key.getDefaultKey(), physicallyDown);
        }
    }

    private static KeyMapping[] movementKeys(Minecraft mc) {
        return new KeyMapping[]{
                mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight, mc.options.keyJump
        };
    }
}
