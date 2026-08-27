package cc.aerial.client.mouse;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

import java.util.HashMap;
import java.util.Map;

public final class MouseHelper {
    private final Map<KeyMapping, MouseButton> mouseButtonMap = new HashMap<>();
    private final MouseButton leftButton, rightButton;

    private MouseHelper() {
        Minecraft mc = Minecraft.getInstance();
        this.leftButton = this.register(new MouseButton(mc.options.keyAttack));
        this.rightButton = this.register(new MouseButton(mc.options.keyUse));
    }

    private MouseButton register(MouseButton button) {
        this.mouseButtonMap.put(button.getKeyMapping(), button);
        return button;
    }

    public void tick() {
        this.leftButton.tick();
        this.rightButton.tick();
    }

    public static MouseButton getButtonFromBinding(KeyMapping binding) {
        return getInstance().mouseButtonMap.get(binding);
    }

    public static MouseButton getLeftButton() {
        return getInstance().leftButton;
    }

    public static MouseButton getRightButton() {
        return getInstance().rightButton;
    }

    private static MouseHelper instance;

    public static MouseHelper getInstance() {
        if (instance == null) {
            instance = new MouseHelper();
        }
        return instance;
    }
}
