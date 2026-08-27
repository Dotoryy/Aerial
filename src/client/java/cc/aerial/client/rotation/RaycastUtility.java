package cc.aerial.client.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.function.Predicate;

public final class RaycastUtility {
    private RaycastUtility() {
    }

    public static BlockHitResult rayTraceBlock(float yaw, float pitch, double distance) {
        return rayTraceBlockFrom(Minecraft.getInstance().player.getEyePosition(), yaw, pitch, distance);
    }

    public static BlockHitResult rayTraceBlockFrom(Vec3 eyePos, float yaw, float pitch, double distance) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 lookVec = RotationUtility.getRotationVector(pitch, yaw);
        Vec3 targetPos = eyePos.add(lookVec.x * distance, lookVec.y * distance, lookVec.z * distance);
        ClipContext context = new ClipContext(eyePos, targetPos, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player);
        BlockHitResult result = mc.player.level().clip(context);
        return result.getType() == HitResult.Type.BLOCK ? result : null;
    }

    public static boolean isWallCloserThan(float yaw, float pitch, double distance, Vec3 point) {
        Vec3 eyePos = Minecraft.getInstance().player.getEyePosition();
        BlockHitResult blockHit = rayTraceBlock(yaw, pitch, distance);
        if (blockHit == null) {
            return false;
        }
        return blockHit.getLocation().distanceToSqr(eyePos) < point.distanceToSqr(eyePos);
    }

    public static EntityHitResult raycastEntity(double maxDistance, float tickDelta, float yaw, float pitch, Predicate<Entity> predicate) {
        Minecraft mc = Minecraft.getInstance();
        return raycastEntity(maxDistance, getCameraPosVec(tickDelta, mc.player), yaw, pitch, predicate);
    }

    public static EntityHitResult raycastEntity(double maxDistance, Vec3 start, float yaw, float pitch, Predicate<Entity> predicate) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 rotationVector = RotationUtility.getRotationVector(pitch, yaw);

        Vec3 end = start.add(rotationVector.x * maxDistance, rotationVector.y * maxDistance, rotationVector.z * maxDistance);

        AABB box = mc.player.getBoundingBox().expandTowards(rotationVector.scale(maxDistance)).inflate(1, 1, 1);

        return ProjectileUtil.getEntityHitResult(mc.player, start, end, box, predicate, maxDistance * maxDistance);
    }

    public static boolean rayTraceHits(AABB boundingBox, float yaw, float pitch, double distance) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 eyePos = mc.player.getEyePosition();
        Vec3 lookVec = RotationUtility.getRotationVector(pitch, yaw);
        Vec3 targetPos = eyePos.add(lookVec.x * distance, lookVec.y * distance, lookVec.z * distance);
        return boundingBox.clip(eyePos, targetPos).isPresent();
    }

    public static Vec3 getCameraPosVec(float tickDelta, Entity entity) {
        double x = Mth.lerp((double) tickDelta, entity.xOld, entity.getX());
        double y = Mth.lerp((double) tickDelta, entity.yOld, entity.getY()) + entity.getEyeHeight();
        double z = Mth.lerp((double) tickDelta, entity.zOld, entity.getZ());
        return new Vec3(x, y, z);
    }
}
