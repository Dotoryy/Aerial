package cc.aerial.client.rotation.model.impl;

import cc.aerial.client.rotation.model.EnumRotationModel;
import cc.aerial.client.rotation.model.IRotationModel;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

public final class InstantRotationModel implements IRotationModel {
    public static final InstantRotationModel INSTANCE = new InstantRotationModel();

    private InstantRotationModel() {
    }

    @Override
    public Vec2 tick(Vec2 from, Vec2 to, float timeDelta) {
        float deltaYaw = Mth.wrapDegrees(to.x - from.x) * timeDelta;
        float deltaPitch = (to.y - from.y) * timeDelta;
        return new Vec2(from.x + deltaYaw, from.y + deltaPitch);
    }

    @Override
    public EnumRotationModel getEnum() {
        return EnumRotationModel.INSTANT;
    }
}
