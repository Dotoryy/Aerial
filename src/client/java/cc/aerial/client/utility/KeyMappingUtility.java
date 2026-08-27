package cc.aerial.client.utility;

import cc.aerial.client.mixin.KeyMappingAccessor;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ToggleKeyMapping;
import org.lwjgl.glfw.GLFW;

public final class KeyMappingUtility {
    private KeyMappingUtility() {
    }

    public static void press(KeyMapping mapping) {
        if (!mapping.isDown()) {
            mapping.setDown(true);
        }
    }

    public static boolean isPhysicallyDown(KeyMapping mapping) {
        InputConstants.Key key = ((KeyMappingAccessor) mapping).aerial$getKey();
        if (key == null || key.getValue() == InputConstants.UNKNOWN.getValue()) {
            return false;
        }
        long window = Minecraft.getInstance().getWindow().handle();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return GLFW.glfwGetKey(window, key.getValue()) == GLFW.GLFW_PRESS;
    }

    public static void release(KeyMapping mapping) {
        if (!mapping.isDown()) {
            return;
        }
        if (mapping instanceof ToggleKeyMapping) {
            mapping.setDown(true);
        } else {
            mapping.setDown(false);
        }
    }
}
