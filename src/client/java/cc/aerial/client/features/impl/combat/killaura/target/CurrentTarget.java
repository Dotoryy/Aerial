package cc.aerial.client.features.impl.combat.killaura.target;

import cc.aerial.client.rotation.RotationUtility;
import net.minecraft.world.entity.LivingEntity;

public final class CurrentTarget {
    private final KillauraTarget target;
    private final RotationUtility.RaytracedRotation rotations;

    public CurrentTarget(KillauraTarget target, RotationUtility.RaytracedRotation rotations) {
        this.target = target;
        this.rotations = rotations;
    }

    public KillauraTarget getKillauraTarget() {
        return target;
    }

    public LivingEntity getEntity() {
        return getKillauraTarget().getTarget();
    }

    public RotationUtility.RaytracedRotation getRotations() {
        return rotations;
    }
}
