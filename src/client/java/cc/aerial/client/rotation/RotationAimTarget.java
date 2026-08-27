package cc.aerial.client.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec2;

public final class RotationAimTarget {
    private static Entity target;
    private static double range;
    private static boolean throughWalls;
    private static int submittedTick = -1;

    private RotationAimTarget() {
    }

    public static void submit(Entity entity, double reach, boolean canHitThroughWalls) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        target = entity;
        range = reach;
        throughWalls = canHitThroughWalls;
        submittedTick = player.tickCount;
    }

    public static boolean isActive() {
        LocalPlayer player = Minecraft.getInstance().player;
        return target != null && player != null
                && player.tickCount - submittedTick >= 0
                && player.tickCount - submittedTick <= 1;
    }

    public static boolean hits(Vec2 rotation) {
        if (!isActive()) {
            return true;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        Entity entity = target;

        EntityHitResult hit = RaycastUtility.raycastEntity(range, 1.0f, rotation.x, rotation.y,
                candidate -> candidate == entity);
        if (hit == null || hit.getEntity() != entity) {
            return false;
        }
        if (throughWalls) {
            return true;
        }
        return !RaycastUtility.isWallCloserThan(rotation.x, rotation.y, range, hit.getLocation());
    }
}
