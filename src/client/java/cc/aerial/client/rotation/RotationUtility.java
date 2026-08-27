package cc.aerial.client.rotation;

import cc.aerial.client.features.impl.combat.PiercingModule;
import cc.aerial.client.utility.RandomUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;

import java.util.function.Predicate;

public final class RotationUtility {
    private RotationUtility() {
    }

    public static float getRotationDifference(Vec2 a, Vec2 b) {
        return Mth.degreesDifferenceAbs(a.x, b.x) + Math.abs(a.y - b.y);
    }

    public static double getCursorDelta(double rotationDelta, double sensitivityMultiplier) {
        return (float) (rotationDelta / sensitivityMultiplier) / 0.15F;
    }

    public static Vec2 patchConstantRotation(Vec2 rotation, Vec2 prevRotation) {
        Minecraft mc = Minecraft.getInstance();
        double sensitivity = mc.options.sensitivity().get() * 0.6F + 0.2F;
        double multiplier = (sensitivity * sensitivity * sensitivity) * 8.0D;
        double divisor = multiplier * 0.15F;

        float yawDelta = rotation.x - prevRotation.x;
        float pitchDelta = rotation.y - prevRotation.y;
        float yaw = prevRotation.x + (float) (Math.round(yawDelta / divisor) * divisor);
        float pitch = prevRotation.y + (float) (Math.round(pitchDelta / divisor) * divisor);
        return new Vec2(yaw, pitch);
    }

    public static float getSensitivityModifiedRotation(double original) {
        Minecraft mc = Minecraft.getInstance();
        double sensitivity = mc.options.sensitivity().get() * 0.6F + 0.2F;
        double multiplier = (sensitivity * sensitivity * sensitivity) * 8.0D;
        return (float) (getCursorDelta(original, multiplier) * multiplier) * 0.15F;
    }

    public static Vec2 getSentRotation(Vec2 original) {
        return getSensitivityModifiedRotation(patchConstantRotation(original, getRotation()));
    }

    public static Vec2 getSensitivityModifiedRotation(Vec2 original) {
        return new Vec2(getSensitivityModifiedRotation((double) original.x), getSensitivityModifiedRotation((double) original.y));
    }

    public static Vec2 getVanillaRotation(Vec2 original) {
        Minecraft mc = Minecraft.getInstance();
        Vec2 sentRotation = getSentRotation(original);
        float wrappedYaw = getDuplicateWrapped(sentRotation.x, mc.player.getYRot());
        return new Vec2(wrappedYaw, sentRotation.y);
    }

    public static float getDuplicateWrapped(float value, float target) {
        return target + Mth.wrapDegrees(value - target);
    }

    public static Vec2 getRotation() {
        Minecraft mc = Minecraft.getInstance();
        return new Vec2(mc.player.getYRot(), mc.player.getXRot());
    }

    private static boolean isBlockedByWall(Vec2 rotation, HitResult entityHit, double interactionRange) {
        if (PiercingModule.INSTANCE.isEnabled()) {
            return false;
        }
        return RaycastUtility.isWallCloserThan(rotation.x, rotation.y, interactionRange, entityHit.getLocation());
    }

    public static RaytracedRotation getRotationFromRaycastedEntity(LivingEntity entity, Vec3 closestVector, double entityInteractionRange) {
        Predicate<Entity> targetPredicate = e -> e == entity;

        AABB box = entity.getBoundingBox().inflate(entity.getPickRadius());
        Vec3 facedVector = box.getCenter();
        double widthX = box.getXsize();
        double height = box.getYsize();
        double widthZ = box.getZsize();

        RaytracedRotation best = null;
        double bestDifference = Double.MAX_VALUE;

        Vec2 rotationFromPosition = RotationUtility.getRotationFromPosition(closestVector);
        float range = (float) RandomUtility.getJoinRandomDouble(0.01D, 0.05D);
        Vec2 randomAddition = new Vec2(
                RandomUtility.getRandomFloat(-range, range),
                RandomUtility.getRandomFloat(-range, range)
        );
        Vec2 randomClosestRotation = rotationFromPosition.add(randomAddition);
        Vec2 closestVectorRotation = getVanillaRotation(randomClosestRotation);
        HitResult closestHitResult = RaycastUtility.raycastEntity(entityInteractionRange, 1F, closestVectorRotation.x, closestVectorRotation.y, targetPredicate);
        if (closestHitResult != null && !isBlockedByWall(closestVectorRotation, closestHitResult, entityInteractionRange)) {
            return new RaytracedRotation(closestVectorRotation, closestHitResult);
        }

        float step = 8.F - (RandomUtility.RANDOM.nextFloat() * 0.25F);
        for (double vx = widthX, x = -vx; x < vx; x += vx / step) {
            for (double vy = height, y = -vy; y < vy; y += vy / step) {
                for (double vz = widthZ, z = -vz; z < vz; z += vz / step) {
                    Vec3 offsetVector = new Vec3(x, y, z);
                    Vec3 raytraceVector = facedVector.add(offsetVector);

                    Vec2 raytraceRotation = getVanillaRotation(RotationUtility.getRotationFromPosition(raytraceVector));

                    HitResult hitResult = RaycastUtility.raycastEntity(entityInteractionRange, 1F, raytraceRotation.x, raytraceRotation.y, targetPredicate);

                    if (hitResult != null && !isBlockedByWall(raytraceRotation, hitResult, entityInteractionRange)) {
                        double difference = RotationUtility.getRotationDifference(raytraceRotation, closestVectorRotation);
                        if (difference < bestDifference) {
                            bestDifference = difference;
                            best = new RaytracedRotation(raytraceRotation, hitResult);
                        }
                    }
                }
            }
        }

        return best;
    }

    public static RaytracedRotation getRotationFromRaycastedBlock(net.minecraft.core.BlockPos blockPos, net.minecraft.core.Direction side, Vec2 priorityRotation, Vec3 playerPos) {
        AABB box = new AABB(blockPos);
        Vec3 facedVector = box.getCenter();

        double widthX = box.getXsize();
        double height = box.getYsize();
        double widthZ = box.getZsize();

        RaytracedRotation best = null;
        double bestDifference = Double.MAX_VALUE;
        Minecraft mc = Minecraft.getInstance();

        float step = 12.0F;
        for (double vx = widthX, x = -vx; x < vx; x += vx / step) {
            for (double vy = height, y = -vy; y < vy; y += vy / step) {
                for (double vz = widthZ, z = -vz; z < vz; z += vz / step) {
                    Vec3 raytraceVector = facedVector.add(x, y, z);

                    Vec2 raytraceRotation = getVanillaRotation(getRotationFromPosition(playerPos, raytraceVector));

                    net.minecraft.world.phys.BlockHitResult hitResult = RaycastUtility.rayTraceBlockFrom(playerPos, raytraceRotation.x, raytraceRotation.y, mc.player.blockInteractionRange());

                    if (hitResult != null && hitResult.getBlockPos().equals(blockPos) && hitResult.getDirection() == side) {
                        double difference = getRotationDifference(raytraceRotation, priorityRotation);
                        if (difference < bestDifference) {
                            bestDifference = difference;
                            best = new RaytracedRotation(raytraceRotation, hitResult);
                        }
                    }
                }
            }
        }

        return best;
    }

    public static Vec2 getRotationFromPosition(Vec3 pos) {
        return getRotationFromPosition(Minecraft.getInstance().player.getEyePosition(), pos);
    }

    public static Vec2 getRotationFromPosition(Vec3 from, Vec3 to) {
        double xDiff = to.x - from.x;
        double yDiff = to.y - from.y;
        double zDiff = to.z - from.z;

        double distance = Math.sqrt(xDiff * xDiff + zDiff * zDiff);

        float yaw = (float) Math.toDegrees(-Math.atan2(xDiff, zDiff));
        float pitch = (float) -Math.toDegrees(Math.atan2(yDiff, distance));

        return new Vec2(yaw, pitch);
    }

    public static Vec3 getRotationVector(float pitch, float yaw) {
        float f = pitch * (float) (Math.PI / 180.0);
        float g = -yaw * (float) (Math.PI / 180.0);
        float h = Mth.cos(g);
        float i = Mth.sin(g);
        float j = Mth.cos(f);
        float k = Mth.sin(f);
        return new Vec3(i * j, -k, h * j);
    }

    public static double getEntityFOV(Entity entity) {
        Minecraft mc = Minecraft.getInstance();
        double yawDiff = (RotationHelper.getClientHandler().getYawOr(mc.player.getYRot()) - getRotationFromPosition(entity.position()).x) % 360.0 + 540.0;
        return yawDiff % 360.0 - 180.0;
    }

    public static boolean isEntityInFOV(Entity entity, float fov) {
        if (fov >= 180.F) {
            return true;
        }
        double angle = getEntityFOV(entity);
        return Math.abs(angle) < fov;
    }

    public record RaytracedRotation(Vec2 rotation, HitResult hitResult) {
    }

    public static Vec2 getRotationsToBox(AABB boundingBox, float yaw, float pitch, float maxAngle, float smoothFactor) {
        Vec3 eyePos = Minecraft.getInstance().player.getEyePosition();
        double minTargetY = boundingBox.minY + 0.05 * (boundingBox.maxY - boundingBox.minY);
        double maxTargetY = boundingBox.minY + 0.75 * (boundingBox.maxY - boundingBox.minY);
        double deltaX = (boundingBox.minX + boundingBox.maxX) / 2.0 - eyePos.x;
        double deltaY = eyePos.y >= maxTargetY ? maxTargetY - eyePos.y : (eyePos.y <= minTargetY ? minTargetY - eyePos.y : 0.0);
        double deltaZ = (boundingBox.minZ + boundingBox.maxZ) / 2.0 - eyePos.z;
        return getRegularRotations(deltaX, deltaY, deltaZ, yaw, pitch, maxAngle, smoothFactor);
    }

    private static Vec2 getRegularRotations(double targetX, double targetY, double targetZ,
                                             float currentYaw, float currentPitch, float maxAngle, float smoothFactor) {
        double horizontalDistance = Math.sqrt(targetX * targetX + targetZ * targetZ);
        float yawDelta = Mth.wrapDegrees((float) (Math.atan2(targetZ, targetX) * 180.0 / Math.PI) - 90.0f - currentYaw);
        float pitchDelta = Mth.wrapDegrees((float) (-Math.atan2(targetY, horizontalDistance) * 180.0 / Math.PI) - currentPitch);
        yawDelta = Math.abs(yawDelta) <= 1.0f ? 0.0f : smoothAngle(clampAngle(yawDelta, maxAngle), smoothFactor);
        pitchDelta = Math.abs(pitchDelta) <= 1.0f ? 0.0f : smoothAngle(clampAngle(pitchDelta, maxAngle), smoothFactor);
        return new Vec2(quantizeAngle(currentYaw + yawDelta), quantizeAngle(currentPitch + pitchDelta));
    }

    public static Vec2 getRotationsTo(double targetX, double targetY, double targetZ, float currentYaw, float currentPitch) {
        return getRegularRotations(targetX, targetY, targetZ, currentYaw, currentPitch, 180.0f, 0.0f);
    }

    public static float clampAngle(float angle, float maxAngle) {
        maxAngle = Math.max(0.0f, Math.min(180.0f, maxAngle));
        if (angle > maxAngle) {
            return maxAngle;
        }
        if (angle < -maxAngle) {
            return -maxAngle;
        }
        return angle;
    }

    private static float smoothAngle(float angle, float smoothFactor) {
        return angle * (0.5f + 0.5f * (1.0f - Math.max(0.0f, Math.min(1.0f, smoothFactor + RandomUtility.getRandomFloat(-0.1f, 0.1f)))));
    }

    public static float quantizeAngle(float angle) {
        return (float) ((double) angle - (double) angle % 0.0096);
    }

    public static Vec2 getQuantizedRotation(Vec2 rotation) {
        Vec2 vanilla = getVanillaRotation(rotation);
        return new Vec2(vanilla.x, Mth.clamp(vanilla.y, -90.0f, 90.0f));
    }

    public static void setRotationSilently(LocalPlayer player, float yaw, float pitch) {
        Vec2 tracked = RotationHelper.getClientHandler().getRotation();
        player.setYRot(yaw);
        player.setXRot(pitch);
        if (tracked != null) {
            RotationHelper.getClientHandler().setRotation(tracked);
        }
    }
}
