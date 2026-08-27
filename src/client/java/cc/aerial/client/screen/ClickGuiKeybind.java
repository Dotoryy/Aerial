package cc.aerial.client.screen;

import cc.aerial.client.event.impl.press.KeyPressEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class ClickGuiKeybind implements IEventSubscriber {
    @Subscribe
    public void onKeyPress(KeyPressEvent event) {
        if (!event.isPressed()) {
            return;
        }
        if (event.getInteractionCode() != GLFW.GLFW_KEY_RIGHT_SHIFT) {
            return;
        }

        ClickGuiState.toggleScreen();
    }
}
