package cc.aerial.client.features.impl.movement;

import cc.aerial.client.event.impl.game.input.MoveInputEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.visual.FreeLookModule;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.rotation.RotationHelper;
import cc.aerial.client.rotation.ServerRotation;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

public final class MovementFixModule extends Module {
    public static final MovementFixModule INSTANCE = new MovementFixModule();

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.NORMAL);

    private MovementFixModule() {
        super("Movement Fix", "", ModuleCategory.MOVEMENT);
        addProperties(mode);
    }

    public boolean isFixMovement() {
        return this.isEnabled() && this.mode.getValue() == Mode.NORMAL;
    }

    public Mode getMode() {
        return this.mode.getValue();
    }

    @Subscribe
    public void onMoveInput(MoveInputEvent event) {
        if (this.mode.getValue() != Mode.NORMAL) {
            return;
        }
        applyNormalFix(event);
    }

    public static void applyNormalFix(MoveInputEvent event) {
        if (FreeLookModule.INSTANCE.isFreeLooking()) {
            return;
        }
        float forward = event.getForward();
        float strafe = event.getSideways();
        if (forward == 0.0f && strafe == 0.0f) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();

        float realYaw = ServerRotation.getYawOr(mc.player.getYRot());

        float cameraYaw = RotationHelper.getScreenYaw(mc.player.getYRot());
        float angle = (float) Math.toDegrees(direction(cameraYaw, forward, strafe));

        float closestForward = 0.0f, closestSideways = 0.0f, closestDifference = Float.MAX_VALUE;
        for (float predictedForward = -1.0f; predictedForward <= 1.0f; predictedForward += 1.0f) {
            for (float predictedStrafe = -1.0f; predictedStrafe <= 1.0f; predictedStrafe += 1.0f) {
                if (predictedStrafe == 0.0f && predictedForward == 0.0f) {
                    continue;
                }
                float predictedAngle = (float) Math.toDegrees(direction(realYaw, predictedForward, predictedStrafe));
                float difference = Mth.degreesDifferenceAbs(angle, predictedAngle);
                if (difference < closestDifference) {
                    closestDifference = difference;
                    closestForward = predictedForward;
                    closestSideways = predictedStrafe;
                }
            }
        }

        event.setForward(closestForward);
        event.setSideways(closestSideways);
    }

    private static double direction(float rotationYaw, double moveForward, double moveStrafing) {
        if (moveForward < 0.0) {
            rotationYaw += 180.0f;
        }
        float forward = 1.0f;
        if (moveForward < 0.0) {
            forward = -0.5f;
        } else if (moveForward > 0.0) {
            forward = 0.5f;
        }
        if (moveStrafing > 0.0) {
            rotationYaw -= 90.0f * forward;
        } else if (moveStrafing < 0.0) {
            rotationYaw += 90.0f * forward;
        }
        return Math.toRadians(rotationYaw);
    }

    public enum Mode {
        NORMAL("Normal");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
