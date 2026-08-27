package cc.aerial.client.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class ServerRotation {
    private static float yaw;
    private static int priority = Integer.MIN_VALUE;
    private static int submittedTick = -1;

    private ServerRotation() {
    }

    public static void submit(float yaw, int priority) {
        int tick = currentTick();
        if (tick != submittedTick) {
            submittedTick = tick;
            ServerRotation.priority = Integer.MIN_VALUE;
        }
        if (priority < ServerRotation.priority) {
            return;
        }
        ServerRotation.priority = priority;
        ServerRotation.yaw = yaw;
    }

    public static boolean isActive() {
        return submittedTick == currentTick();
    }

    public static float getYaw() {
        return yaw;
    }

    public static float getYawOr(float fallback) {
        return isActive() ? yaw : fallback;
    }

    private static int currentTick() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? -1 : player.tickCount;
    }
}
