package cc.aerial.client.rotation.model;

import net.minecraft.world.phys.Vec2;

public interface IRotationModel {
    Vec2 tick(Vec2 from, Vec2 to, float timeDelta);

    EnumRotationModel getEnum();
}
