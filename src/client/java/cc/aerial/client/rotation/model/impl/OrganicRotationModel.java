package cc.aerial.client.rotation.model.impl;

import cc.aerial.client.rotation.RotationUtility;
import cc.aerial.client.rotation.model.EnumRotationModel;
import cc.aerial.client.rotation.model.IRotationModel;
import cc.aerial.client.utility.RandomUtility;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;

import java.util.Random;

public final class OrganicRotationModel implements IRotationModel {
    private final double speed;
    private final double driftIntensity;
    private final double jitterIntensity;

    private final double freqYaw1, freqYaw2, freqPitch1, freqPitch2;
    private final double phaseYaw1, phaseYaw2, phasePitch1, phasePitch2;
    private double timeAccumulator;

    private final Random random;

    public OrganicRotationModel(double speed, double driftIntensity, double jitterIntensity) {
        this.speed = speed;
        this.driftIntensity = driftIntensity;
        this.jitterIntensity = jitterIntensity;

        random = new Random(System.nanoTime());
        this.freqYaw1 = random.nextDouble() * 0.3 + 0.1;
        this.freqYaw2 = random.nextDouble() * 0.5 + 0.5;
        this.freqPitch1 = random.nextDouble() * 0.3 + 0.1;
        this.freqPitch2 = random.nextDouble() * 0.5 + 0.5;
        this.phaseYaw1 = random.nextDouble() * Math.PI * 2;
        this.phaseYaw2 = random.nextDouble() * Math.PI * 2;
        this.phasePitch1 = random.nextDouble() * Math.PI * 2;
        this.phasePitch2 = random.nextDouble() * Math.PI * 2;
        this.timeAccumulator = 0.0;
    }

    @Override
    public Vec2 tick(Vec2 from, Vec2 to, float timeDelta) {
        float rawYaw = Mth.wrapDegrees(to.x - from.x);
        float rawPitch = to.y - from.y;
        float deltaYaw = rawYaw * timeDelta;
        float deltaPitch = rawPitch * timeDelta;

        double distance = Math.hypot(deltaYaw, deltaPitch);
        if (distance < driftIntensity) {
            return new Vec2(from.x + deltaYaw, from.y + deltaPitch);
        }

        if (distance > 0) {
            double ratioYaw = Math.abs(deltaYaw) / distance;
            double ratioPitch = Math.abs(deltaPitch) / distance;
            double maxYaw = speed * ratioYaw * timeDelta;
            double maxPitch = speed * ratioPitch * timeDelta;
            deltaYaw = Mth.clamp(deltaYaw, (float) -maxYaw, (float) maxYaw);
            deltaPitch = Mth.clamp(deltaPitch, (float) -maxPitch, (float) maxPitch);
        }

        timeAccumulator += timeDelta;

        double sinYaw = Math.sin(timeAccumulator * freqYaw1 + phaseYaw1)
                + RandomUtility.getRandomDouble(0.45, 0.55) * Math.sin(timeAccumulator * freqYaw2 + phaseYaw2);
        double sinPitch = Math.sin(timeAccumulator * freqPitch1 + phasePitch1)
                + RandomUtility.getRandomDouble(0.45, 0.55) * Math.sin(timeAccumulator * freqPitch2 + phasePitch2);
        double driftYaw = sinYaw * driftIntensity * timeDelta;
        double driftPitch = sinPitch * driftIntensity * timeDelta;

        double jitterYaw = (random.nextDouble() * 2 - 1) * jitterIntensity * timeDelta;
        double jitterPitch = (random.nextDouble() * 2 - 1) * jitterIntensity * timeDelta;

        float moveYaw = deltaYaw + (float) driftYaw + (float) jitterYaw;
        float movePitch = deltaPitch + (float) driftPitch + (float) jitterPitch;

        Vec2 rotation = new Vec2(from.x + moveYaw, from.y + movePitch);

        return RotationUtility.patchConstantRotation(rotation, from);
    }

    @Override
    public EnumRotationModel getEnum() {
        return EnumRotationModel.ORGANIC;
    }
}
