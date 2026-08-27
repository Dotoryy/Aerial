package cc.aerial.client.scaffold;

import cc.aerial.client.features.impl.movement.SpeedModule;
import cc.aerial.client.utility.GroundTickTracker;
import cc.aerial.client.utility.MoveUtility;
import cc.aerial.client.utility.TeleportTickTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;

public final class WatchdogJumpSprintLogic {
    private WatchdogJumpSprintLogic() {
    }

    public static boolean boostPending;
    public static boolean boostNextJump;
    public static boolean blockChanged;

    private static boolean wasJumping;
    private static boolean justEnabled;

    public static void onEnable(boolean autoJump) {
        LocalPlayer player = Minecraft.getInstance().player;
        blockChanged = false;
        wasJumping = false;
        boostNextJump = true;
        if (player == null) {
            return;
        }

        if (!autoJump && GroundTickTracker.getGroundTicks() > 9) {
            boostPending = true;
            justEnabled = true;
        }

        if (!player.onGround() && !Minecraft.getInstance().options.keyJump.isDown()) {
            MoveUtility.stop();
        }
    }

    public static void onDisable() {
        blockChanged = false;
        boostPending = false;
        boostNextJump = false;
        justEnabled = false;
    }

    public static void onPreMovementPacket() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        float yaw = Mth.wrapDegrees(player.getYRot());

        boolean nearCardinal = Math.abs(yaw % 90.0f) <= 10.0f || Math.abs(yaw % 90.0f) >= 80.0f;

        double steering = Mth.wrapDegrees(Math.toDegrees(MoveUtility.getDirectionRadians(player.getYRot())));
        double travelling = Mth.wrapDegrees(
                Math.toDegrees(Math.atan2(player.getDeltaMovement().z, player.getDeltaMovement().x)) - 90.0);

        boolean aligned = Math.abs(steering - travelling) < 5.0;
        boolean serverQuiet = TeleportTickTracker.getTicksSinceTeleport() > 7;
        boolean noOverride = !minecraft.options.keySprint.isDown() && !minecraft.options.keyShift.isDown();

        if (aligned && serverQuiet && noOverride) {
            boolean boostable = GroundTickTracker.getGroundTicks() > 2
                    && !minecraft.options.keyJump.isDown()
                    && !SpeedModule.INSTANCE.isEnabled();
            if (boostable) {
                int speedLevel = player.hasEffect(MobEffects.SPEED)
                        ? player.getEffect(MobEffects.SPEED).getAmplifier() + 1
                        : 0;
                double multiplier;
                if (speedLevel == 0) {
                    multiplier = nearCardinal ? 1.1225 : 1.129;
                } else if (speedLevel >= 2) {
                    multiplier = 1.1055;
                } else {
                    multiplier = 1.098;
                }
                MoveUtility.strafe(player.getYRot());
                var motion = player.getDeltaMovement();
                player.setDeltaMovement(motion.x * multiplier, motion.y, motion.z * multiplier);
            }
        }

        if (minecraft.options.keyShift.isDown()) {
            return;
        }

        if (player.onGround()) {
            MoveUtility.strafe(player.getYRot());
        }
    }

    public static boolean onPreGameTick(boolean autoJump, Runnable onJumpReleased) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return false;
        }

        if (!minecraft.options.keyShift.isDown() && wasJumping && !minecraft.options.keyJump.isDown()) {
            WatchdogTowerLogic.towerYaw = player.getYRot();
            WatchdogTowerLogic.moveTicks = 17;
            WatchdogTowerLogic.jumpStage = 0;
            MoveUtility.stop();
            wasJumping = false;
            onJumpReleased.run();
        }
        if (minecraft.options.keyJump.isDown()) {
            wasJumping = true;
        }

        if (GroundTickTracker.getAirTicks() > 8) {
            justEnabled = false;
        }

        boolean suppressSafeWalk = (hasCollisionBelow(player, 0.0)
                || hasCollisionBelow(player, 1.0)
                || hasCollisionBelow(player, 2.0)
                || hasCollisionBelow(player, 3.0)
                || minecraft.options.keyJump.isDown()
                || player.hasEffect(MobEffects.SPEED)
                || wasJumping)
                && GroundTickTracker.getGroundTicks() < 1;

        if (minecraft.options.keyJump.isDown()
                && player.onGround()
                && !wasJumping
                && (boostNextJump || autoJump)
                && !SpeedModule.INSTANCE.isEnabled()) {
            boostNextJump = false;
            MoveUtility.strafe(MoveUtility.getBaseSpeed() * 0.9, player.getYRot());
            if (autoJump) {
                MoveUtility.strafe(MoveUtility.getAllowedHorizontalDistance() - 0.001, player.getYRot());
            }
        }

        int airTicks = GroundTickTracker.getAirTicks();

        if (airTicks == 4
                && !minecraft.options.keyJump.isDown()
                && !SpeedModule.INSTANCE.isEnabled()
                && autoJump) {
            var motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, motion.y - 0.19, motion.z);
        }

        if (airTicks == 1 && !minecraft.options.keyJump.isDown()) {
            MoveUtility.strafe(player.getYRot());
        }

        return suppressSafeWalk;
    }

    public static void onBlockChanged() {
        blockChanged = true;
    }

    public static void onStrafe(boolean autoJump) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (!autoJump
                && player.onGround()
                && justEnabled
                && boostPending
                && !SpeedModule.INSTANCE.isEnabled()
                && !minecraft.options.keyJump.isDown()) {
            boostPending = false;
        }
    }

    private static boolean hasCollisionBelow(LocalPlayer player, double distance) {
        if (Minecraft.getInstance().level == null) {
            return false;
        }
        return !Minecraft.getInstance().level
                .noCollision(player, player.getBoundingBox().move(0.0, -distance, 0.0));
    }
}
