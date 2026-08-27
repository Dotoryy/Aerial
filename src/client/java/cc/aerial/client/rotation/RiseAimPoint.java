package cc.aerial.client.rotation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class RiseAimPoint {
    private static final double[] ACROSS = {0.05, 0.25, 0.5, 0.75, 0.95};
    private static final double[] DOWN = {0.05, 0.2, 0.35, 0.5, 0.65, 0.8, 0.95};

    private RiseAimPoint() {
    }

    public static Vec2 computeRotations(Entity entity, double range, boolean throughWalls, float expand) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || entity == null) {
            return Vec2.ZERO;
        }

        AABB box = entity.getBoundingBox();
        if (box.hasNaN()) {
            return defaultRotation(entity);
        }

        AABB expanded = box.inflate(expand);
        Vec3 point = bestPoint(entity, expanded, nearestPoint(expanded), range, throughWalls, expand);
        return toRotation(point);
    }

    public static Vec2 defaultRotation(Entity entity) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || entity == null) {
            return Vec2.ZERO;
        }
        return toRotation(centrePoint(entity.getBoundingBox()));
    }

    public static Vec2 toRotation(Vec3 point) {
        return RotationUtility.getRotationFromPosition(point);
    }

    public static Vec3 bestPoint(Entity entity, AABB box, Vec3 fallback, double range,
                                 boolean throughWalls, float expand) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || entity == null || box == null || fallback == null) {
            return fallback;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 nearest = nearestPoint(box);
        Vec2 fallbackRotation = toRotation(fallback);
        if (hits(toRotation(nearest), entity, range, throughWalls, expand)) {
            return nearest;
        }

        Vec3 best = null;
        double bestScore = Double.MAX_VALUE;
        for (Vec3 candidate : candidates(box, fallback)) {
            Vec2 rotation = toRotation(candidate);
            if (!hits(rotation, entity, range, throughWalls, expand)) {
                continue;
            }
            double yawDelta = Math.abs(Mth.wrapDegrees(rotation.x - fallbackRotation.x));
            double pitchDelta = Math.abs(rotation.y - fallbackRotation.y);
            double score = candidate.distanceToSqr(eye) + (yawDelta + pitchDelta) * 1.0E-5;
            if (score < bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        return best == null ? fallback : best;
    }

    public static boolean hits(Vec2 rotation, Entity entity, double range, boolean throughWalls, float expand) {
        return rayCast(rotation, range, throughWalls, expand) == entity;
    }

    public static Entity rayCast(Vec2 rotation, double range, boolean throughWalls, float expand) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return null;
        }

        Vec3 eye = player.getEyePosition();
        Vec3 look = RotationUtility.getRotationVector(rotation.y, rotation.x);
        Vec3 end = eye.add(look.x * range, look.y * range, look.z * range);

        double limit = range;
        if (!throughWalls) {
            BlockHitResult blockHit = player.level().clip(
                    new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
            if (blockHit.getType() == HitResult.Type.BLOCK) {
                limit = blockHit.getLocation().distanceTo(eye);
            }
        }

        Entity hit = null;
        AABB search = player.getBoundingBox()
                .expandTowards(look.x * range, look.y * range, look.z * range)
                .inflate(1.0, 1.0, 1.0);
        for (Entity candidate : minecraft.level.getEntities(player, search, Entity::isPickable)) {
            float border = candidate.getPickRadius() + expand;
            AABB box = candidate.getBoundingBox().inflate(border);
            if (box.contains(eye)) {
                return candidate;
            }
            java.util.Optional<Vec3> intercept = box.clip(eye, end);
            if (intercept.isEmpty()) {
                continue;
            }
            double distance = eye.distanceTo(intercept.get());
            if (distance < limit) {
                limit = distance;
                hit = candidate;
            }
        }

        return hit;
    }

    public static Vec3 nearestPoint(AABB box) {
        LocalPlayer player = Minecraft.getInstance().player;
        Vec3 eye = player.getEyePosition();
        return new Vec3(
                clampInto(eye.x, box.minX + 0.03, box.maxX - 0.03),
                clampInto(eye.y, box.minY + 0.03, box.maxY - 0.03),
                clampInto(eye.z, box.minZ + 0.03, box.maxZ - 0.03));
    }

    public static Vec3 centrePoint(AABB box) {
        LocalPlayer player = Minecraft.getInstance().player;
        double height = box.maxY - box.minY;
        double y = box.minY + Math.max(0.0,
                Math.min(player.getY() - box.minY + player.getEyeHeight(), height * 0.9));
        return new Vec3(box.minX + (box.maxX - box.minX) / 2.0, y, box.minZ + (box.maxZ - box.minZ) / 2.0);
    }

    private static double clampInto(double value, double min, double max) {
        return min > max ? (min + max) / 2.0 : Math.max(min, Math.min(max, value));
    }

    private static List<Vec3> candidates(AABB box, Vec3 fallback) {
        List<Vec3> points = new ArrayList<>(ACROSS.length * DOWN.length * 4 + ACROSS.length * ACROSS.length * 2 + 2);
        points.add(fallback);
        points.add(centrePoint(box));

        for (double down : DOWN) {
            for (double across : ACROSS) {
                points.add(at(box, 0.01, down, across));
                points.add(at(box, 0.99, down, across));
                points.add(at(box, across, down, 0.01));
                points.add(at(box, across, down, 0.99));
            }
        }

        for (double x : ACROSS) {
            for (double z : ACROSS) {
                points.add(at(box, x, 0.02, z));
                points.add(at(box, x, 0.98, z));
            }
        }

        return points;
    }

    private static Vec3 at(AABB box, double x, double y, double z) {
        return new Vec3(
                box.minX + (box.maxX - box.minX) * x,
                box.minY + (box.maxY - box.minY) * y,
                box.minZ + (box.maxZ - box.minZ) * z);
    }
}
