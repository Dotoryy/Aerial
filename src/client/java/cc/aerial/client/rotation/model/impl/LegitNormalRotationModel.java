package cc.aerial.client.rotation.model.impl;

import cc.aerial.client.rotation.RotationAimTarget;
import cc.aerial.client.rotation.RotationUtility;
import cc.aerial.client.rotation.model.EnumRotationModel;
import cc.aerial.client.rotation.model.IRotationModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

public final class LegitNormalRotationModel implements IRotationModel {
    private static final LegitNormalRotationModel INSTANCE = new LegitNormalRotationModel();

    private double speedMin = 5.0;
    private double speedMax = 10.0;

    private float searchAngle;
    private float offsetX;
    private float offsetY;

    private Vec2 tickStart;
    private Vec2 tickResult;
    private int computedTick = Integer.MIN_VALUE;

    private LegitNormalRotationModel() {
    }

    public static LegitNormalRotationModel of(double speedMin, double speedMax) {
        INSTANCE.speedMin = speedMin;
        INSTANCE.speedMax = speedMax;
        return INSTANCE;
    }

    private double drawSpeed() {
        double low = this.speedMin;
        double high = this.speedMax;
        if (low == high) {
            return low;
        }
        if (low > high) {
            double swap = low;
            low = high;
            high = swap;
        }
        return low + (high - low) * Math.random() * Math.random();
    }

    @Override
    public Vec2 tick(Vec2 from, Vec2 to, float timeDelta) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return to;
        }

        if (player.tickCount != this.computedTick) {
            this.computedTick = player.tickCount;
            this.tickStart = from;
            this.tickResult = compute(from, to, player);
        }
        if (this.tickResult == null) {
            return to;
        }
        if (this.tickStart == null) {
            return this.tickResult;
        }

        float progress = Mth.clamp(timeDelta, 0.0f, 1.0f);
        float yaw = this.tickStart.x + Mth.wrapDegrees(this.tickResult.x - this.tickStart.x) * progress;
        float pitch = this.tickStart.y + (this.tickResult.y - this.tickStart.y) * progress;
        return new Vec2(yaw, pitch);
    }

    private Vec2 compute(Vec2 from, Vec2 to, LocalPlayer player) {
        float yaw = to.x;
        float pitch = to.y;

        if (RotationAimTarget.isActive()
                && (Math.abs(yaw - from.x) > 5.0f || Math.abs(pitch - from.y) > 5.0f)) {
            float trueYaw = to.x;
            float truePitch = to.y;

            double step = Math.random() * Math.random() * Math.random() * 20.0;

            this.searchAngle += (float) ((20.0 + (Math.random() - 0.5)
                    * (Math.random() * Math.random() * Math.random() * 360.0))
                    * (player.tickCount / 10 % 2 == 0 ? -1 : 1));
            this.offsetX += (float) (-Mth.sin((float) Math.toRadians(this.searchAngle)) * step);
            this.offsetY += (float) (Mth.cos((float) Math.toRadians(this.searchAngle)) * step);
            float candidateYaw = yaw + this.offsetX;
            float candidatePitch = pitch + this.offsetY;

            if (!RotationAimTarget.hits(new Vec2(candidateYaw, candidatePitch))) {
                this.searchAngle = (float) Math.toDegrees(
                        Math.atan2(trueYaw - candidateYaw, candidatePitch - truePitch)) - 180.0f;
                this.offsetX += (float) (-Mth.sin((float) Math.toRadians(this.searchAngle)) * step);
                this.offsetY += (float) (Mth.cos((float) Math.toRadians(this.searchAngle)) * step);
                candidateYaw = yaw + this.offsetX;
                candidatePitch = pitch + this.offsetY;
            }

            if (!RotationAimTarget.hits(new Vec2(candidateYaw, candidatePitch))) {
                this.offsetX = 0.0f;
                this.offsetY = 0.0f;
                candidateYaw = (float) (to.x + Math.random() * 2.0);
                candidatePitch = (float) (to.y + Math.random() * 2.0);
            }

            yaw = candidateYaw;
            pitch = candidatePitch;
        } else {
            this.offsetX = 0.0f;
            this.offsetY = 0.0f;
        }

        return step(from, new Vec2(yaw, pitch), drawSpeed() * 36.0 + Math.random());
    }

    private static Vec2 step(Vec2 from, Vec2 to, double speed) {
        if (speed == 0.0) {
            return to;
        }

        float deltaYaw = Mth.wrapDegrees(to.x - from.x);
        float deltaPitch = to.y - from.y;
        double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (distance < 1.0E-4) {
            return from;
        }

        double maxYaw = speed * Math.abs(deltaYaw / distance);
        double maxPitch = speed * Math.abs(deltaPitch / distance);
        float moveYaw = (float) Math.max(Math.min(deltaYaw, maxYaw), -maxYaw);
        float movePitch = (float) Math.max(Math.min(deltaPitch, maxPitch), -maxPitch);

        float yaw = from.x + moveYaw;
        float pitch = from.y + movePitch;

        int substeps = (int) (Minecraft.getInstance().getFps() / 20.0f + Math.random() * 10.0);
        for (int i = 1; i <= substeps; i++) {
            if (Math.abs(moveYaw) + Math.abs(movePitch) > 1.0E-4) {
                yaw += (float) ((Math.random() - 0.5) / 1000.0);
                pitch -= (float) (Math.random() / 200.0);
            }
            Vec2 snapped = RotationUtility.patchConstantRotation(new Vec2(yaw, pitch), from);
            yaw = snapped.x;
            pitch = Mth.clamp(snapped.y, -90.0f, 90.0f);
        }

        return new Vec2(yaw, pitch);
    }

    @Override
    public EnumRotationModel getEnum() {
        throw new UnsupportedOperationException(
                "Legit/Normal is a Rotation Mode, not an EnumRotationModel entry");
    }
}
