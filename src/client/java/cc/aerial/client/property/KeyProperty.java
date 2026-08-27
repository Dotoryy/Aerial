package cc.aerial.client.property;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class KeyProperty extends Property<Integer> {
    public static final int UNBOUND = GLFW.GLFW_KEY_UNKNOWN;

    public KeyProperty(String name) {
        this(name, UNBOUND);
    }

    public KeyProperty(String name, int defaultKey) {
        super(name);
        setValue(defaultKey);
    }

    public boolean isBound() {
        return getValue() != null && getValue() != UNBOUND;
    }

    public boolean isDown() {
        if (!isBound()) {
            return false;
        }
        long window = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(window, getValue()) == GLFW.GLFW_PRESS;
    }

    public String getDisplayName() {
        if (!isBound()) {
            return "None";
        }
        return InputConstants.Type.KEYSYM.getOrCreate(getValue())
                .getDisplayName().getString().toUpperCase();
    }
}
