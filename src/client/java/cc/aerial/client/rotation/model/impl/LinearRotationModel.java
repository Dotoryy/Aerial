package cc.aerial.client.rotation.model.impl;

import cc.aerial.client.rotation.model.EnumRotationModel;
import cc.aerial.client.rotation.model.IRotationModel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

public final class LinearRotationModel implements IRotationModel {
    private final double speed;

    public LinearRotationModel(double speed) {
        this.speed = speed;
    }

    @Override
    public Vec2 tick(Vec2 from, Vec2 to, float timeDelta) {
        float deltaYaw = Mth.wrapDegrees(to.x - from.x) * timeDelta;
        float deltaPitch = (to.y - from.y) * timeDelta;

        double distance = Math.sqrt(deltaYaw * deltaYaw + deltaPitch * deltaPitch);
        if (distance == 0.0) {
            return new Vec2(from.x + deltaYaw, from.y + deltaPitch);
        }
        double distributionYaw = Math.abs(deltaYaw / distance);
        double distributionPitch = Math.abs(deltaPitch / distance);

        double maxYaw = this.speed * distributionYaw;
        double maxPitch = this.speed * distributionPitch;

        float moveYaw = (float) Math.max(Math.min(deltaYaw, maxYaw), -maxYaw);
        float movePitch = (float) Math.max(Math.min(deltaPitch, maxPitch), -maxPitch);

        return new Vec2(from.x + moveYaw, from.y + movePitch);
    }

    @Override
    public EnumRotationModel getEnum() {
        return EnumRotationModel.LINEAR;
    }
}
