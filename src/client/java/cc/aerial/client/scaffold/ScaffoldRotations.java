package cc.aerial.client.scaffold;

import cc.aerial.client.features.impl.world.ScaffoldModule;
import cc.aerial.client.rotation.RaycastUtility;
import cc.aerial.client.utility.MoveUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ScaffoldRotations {
    private static final float BASE_PITCH = 82.0f;
    private static final float[] PITCH_OFFSETS = {0.0f, 1.5f, -1.5f, 3.0f, -3.0f, 4.5f, -4.5f};

    private ScaffoldRotations() {
    }

    public static Vec2 findPlacementRotation(BlockPos target, Direction facing, boolean strictFace,
                                             float currentYaw, float currentPitch, float yawOffset) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || facing == null) {
            return new Vec2(currentYaw, currentPitch);
        }

        float realYaw = player.getYRot();
        float travelYaw = (float) Math.toDegrees(MoveUtility.getDirectionRadians());

        List<Float> candidates = new ArrayList<>(4);
        addUnique(candidates, snapTo45(realYaw + yawOffset));
        addUnique(candidates, snapTo45(realYaw + 45.0f + yawOffset));
        addUnique(candidates, snapTo45(realYaw - 45.0f + yawOffset));
        addUnique(candidates, snapTo45(travelYaw + yawOffset));

        Vec2 best = null;
        float bestScore = Float.MAX_VALUE;
        for (float yaw : candidates) {
            Vec2 found = findPitch(target, facing, yaw, strictFace);
            if (found == null) {
                continue;
            }
            float yawDelta = Math.abs(Mth.wrapDegrees(found.x - currentYaw));
            float pitchDelta = Math.abs(found.y - currentPitch);
            float distance = yawDelta * yawDelta + pitchDelta * pitchDelta;
            float offGrid = Math.abs(Mth.wrapDegrees(found.x - snapTo45(found.x)));
            float offReal = Math.abs(Mth.wrapDegrees(found.x - realYaw));
            float score = distance + (offGrid * 0.001f + offReal * 5.0E-4f);
            if (score < bestScore) {
                bestScore = score;
                best = found;
            }
        }

        if (best != null) {
            return best;
        }

        return faceCentreRotation(target, facing);
    }

    public static Vec2 findPitch(BlockPos target, Direction facing, float yaw, boolean strictFace) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return null;
        }
        for (float offset : PITCH_OFFSETS) {
            float pitch = clampPitch(BASE_PITCH + offset);
            Vec2 rotation = new Vec2(yaw, pitch);
            if (hitsFace(target, facing, rotation, strictFace, player.blockInteractionRange())
                    || hitsFace(target, facing, rotation, true,
                            ScaffoldModule.INSTANCE.getFallbackReach())) {
                return rotation;
            }
        }
        return null;
    }

    private static boolean hitsFace(BlockPos target, Direction facing, Vec2 rotation,
                                    boolean strictFace, double reach) {
        BlockHitResult hit = RaycastUtility.rayTraceBlock(rotation.x, rotation.y, reach);
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return false;
        }
        if (!hit.getBlockPos().equals(target)) {
            return false;
        }
        return !strictFace || hit.getDirection() == facing;
    }

    public static Vec3 faceClickVec(BlockPos pos, Direction facing) {
        LocalPlayer player = Minecraft.getInstance().player;
        AABB bounds = player == null
                ? new AABB(0.0, 0.0, 0.0, 1.0, 1.0, 1.0)
                : player.level().getBlockState(pos).getShape(player.level(), pos).bounds();
        double x = pos.getX() + Mth.clamp(Math.random(), bounds.minX, bounds.maxX);
        double y = pos.getY() + Mth.clamp(Math.random(), bounds.minY, bounds.maxY);
        double z = pos.getZ() + Mth.clamp(Math.random(), bounds.minZ, bounds.maxZ);
        return switch (facing) {
            case UP -> new Vec3(x, pos.getY() + bounds.maxY, z);
            case NORTH -> new Vec3(x, y, pos.getZ() + bounds.minZ);
            case EAST -> new Vec3(pos.getX() + bounds.maxX, y, z);
            case SOUTH -> new Vec3(x, y, pos.getZ() + bounds.maxZ);
            case WEST -> new Vec3(pos.getX() + bounds.minX, y, z);
            default -> new Vec3(x, pos.getY() + bounds.minY, z);
        };
    }

    public static Vec2 stepRotationToPoint(Vec3 point, float fromYaw, float fromPitch) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return new Vec2(fromYaw, fromPitch);
        }
        return stepRotationTowards(point.x - player.getX(),
                point.y - player.getY() - player.getEyeHeight(),
                point.z - player.getZ(), fromYaw, fromPitch);
    }

    private static Vec2 stepRotationTowards(double relX, double relY, double relZ,
                                            float currentYaw, float currentPitch) {
        double horizontal = Math.sqrt(relX * relX + relZ * relZ);
        float yawDelta = Mth.wrapDegrees((float) (Math.atan2(relZ, relX) * 180.0 / Math.PI) - 90.0f - currentYaw);
        float pitchDelta = Mth.wrapDegrees((float) (-Math.atan2(relY, horizontal) * 180.0 / Math.PI) - currentPitch);
        yawDelta = Math.abs(yawDelta) <= 1.0f ? 0.0f : smoothAngle(yawDelta);
        pitchDelta = Math.abs(pitchDelta) <= 1.0f ? 0.0f : smoothAngle(pitchDelta);
        return new Vec2(quantizeAngle(currentYaw + yawDelta), quantizeAngle(currentPitch + pitchDelta));
    }

    private static float smoothAngle(float angle) {
        float jitter = (float) (Math.random() * 0.2 - 0.1);
        return angle * (0.5f + 0.5f * Mth.clamp(jitter, 0.0f, 1.0f));
    }

    private static float quantizeAngle(float angle) {
        return (float) (angle - angle % 0.0096f);
    }

    public static Vec2 rotationsBetween(Vec3 from, Vec3 to) {
        double dx = to.x - from.x;
        double dy = to.y - from.y;
        double dz = to.z - from.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f);
        float pitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(dy, horizontal)), -90.0f, 90.0f);
        return new Vec2(yaw, pitch);
    }

    public static Vec2 rotationsTo(Vec3 point) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return Vec2.ZERO;
        }
        Vec3 eye = player.getEyePosition();
        double dx = point.x - eye.x;
        double dy = point.y - eye.y;
        double dz = point.z - eye.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f);
        float pitch = Mth.clamp((float) -Math.toDegrees(Math.atan2(dy, horizontal)), -90.0f, 90.0f);
        return new Vec2(yaw, pitch);
    }

    public static Vec2 computeNormalRotation(BlockPos blockFace, Direction facing, Vec3 targetBlock,
                                             float currentYaw, float currentPitch, float yawOffset,
                                             double reach, float targetYaw, float targetPitch) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || blockFace == null || facing == null || targetBlock == null) {
            return new Vec2(currentYaw, currentPitch);
        }

        float playerYaw = player.getYRot();
        float wrapped = Mth.wrapDegrees(playerYaw);
        boolean nearCardinal = Math.abs(wrapped % 90.0f) <= 10.0f || Math.abs(wrapped % 90.0f) >= 80.0f;
        int from = (nearCardinal ? -135 : -180) + (int) yawOffset;
        int to = nearCardinal ? 135 : 180;

        double drop = player.getY() + player.getEyeHeight() - targetBlock.y - 0.5
                - (Math.random() - 0.5) * 0.1;

        List<Vec2> candidates = new ArrayList<>();
        for (int step = from; step <= to; step += 45) {
            Vec3 eye = player.getEyePosition().subtract(0.0, drop, 0.0);

            BlockHitResult hit = RaycastUtility.rayTraceBlockFrom(eye, playerYaw + step * 3, 0.0f,
                    ScaffoldModule.INSTANCE.getFallbackReach());
            if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            Vec2 rotation = rotationsTo(hit.getLocation());
            if (hitsFace(blockFace, facing, rotation, true, reach)) {
                candidates.add(rotation);
            }
        }

        ScaffoldDebug.normalScan(candidates.size(), from, to);
        if (!candidates.isEmpty()) {
            Vec2 best = candidates.getFirst();
            float bestScore = Float.MAX_VALUE;
            for (Vec2 candidate : candidates) {
                float yawDelta = Math.abs(Mth.wrapDegrees(candidate.x - currentYaw));
                float pitchDelta = Math.abs(candidate.y - currentPitch);
                float score = yawDelta * yawDelta + pitchDelta * pitchDelta;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
            return best;
        }

        Vec2 pending = new Vec2(targetYaw, targetPitch);
        if (hitsFace(blockFace, facing, pending, true, reach)) {
            return pending;
        }
        return faceCentreRotation(blockFace, facing);
    }

    private static Vec2 faceCentreRotation(BlockPos blockFace, Direction facing) {
        return rotationsTo(new Vec3(
                blockFace.getX() + 0.5 + facing.getStepX() * 0.5,
                blockFace.getY() + 0.5 + facing.getStepY() * 0.5,
                blockFace.getZ() + 0.5 + facing.getStepZ() * 0.5));
    }

    public static float snapTo45(float yaw) {
        return Math.round(Mth.wrapDegrees(yaw) / 45.0f) * 45.0f;
    }

    public static float clampPitch(float pitch) {
        if (pitch > 89.9f) {
            return 89.9f;
        }
        return pitch < -89.9f ? -89.9f : pitch;
    }

    private static void addUnique(List<Float> candidates, float yaw) {
        for (float existing : candidates) {
            if (Math.abs(Mth.wrapDegrees(yaw - existing)) < 0.1f) {
                return;
            }
        }
        candidates.add(yaw);
    }
}
