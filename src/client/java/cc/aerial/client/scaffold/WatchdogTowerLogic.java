package cc.aerial.client.scaffold;

import cc.aerial.client.utility.GroundTickTracker;
import cc.aerial.client.utility.MoveUtility;
import cc.aerial.client.utility.TeleportTickTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class WatchdogTowerLogic {
    private WatchdogTowerLogic() {
    }

    public static float towerYaw;
    public static int moveTicks;
    public static int jumpStage;

    private static boolean aligning;
    private static int alignStep;
    private static int lastPlaceY;

    private static double targetX = Double.NaN;
    private static double targetZ = Double.NaN;

    public static void onEnable() {
        LocalPlayer player = Minecraft.getInstance().player;
        moveTicks = 0;
        alignStep = 0;
        aligning = false;
        targetX = Double.NaN;
        targetZ = Double.NaN;
        if (player == null) {
            jumpStage = 100;
            return;
        }
        towerYaw = player.getYRot();

        jumpStage = player.onGround() ? 0 : 100;
    }

    public static void onDisable() {
        LocalPlayer player = Minecraft.getInstance().player;
        aligning = false;
        alignStep = 0;
        jumpStage = 100;
        if (player == null) {
            return;
        }
        towerYaw = player.getYRot();
        if (Minecraft.getInstance().options.keyJump.isDown()) {
            MoveUtility.strafe(0.23, player.getYRot());
        }
    }

    public static void onPreMovementPacket() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        if (isHandlerSuppressed(player)) {
            return;
        }

        boolean jumpDown = minecraft.options.keyJump.isDown();
        boolean moving = MoveUtility.isMoving();

        if (!jumpDown && moving) {
            towerYaw = player.getYRot();
            jumpStage = 0;
            moveTicks = 0;
        } else if (!jumpDown) {
            jumpStage = 100;
            return;
        }

        if (moving) {
            moveTicks++;
        } else if (moveTicks > 20) {
            moveTicks--;
        }

        if (jumpDown) {
            jumpStage++;
        }

        if (moveTicks >= 23) {
            moveTicks = 1;
            towerYaw = player.getYRot();
            jumpStage = 99;
        }

        if (player.onGround()) {
            jumpStage = 0;
            towerYaw = player.getYRot();
        } else if (jumpStage == 100) {
            MoveUtility.strafe(0.0, player.getYRot());
        }

        moveTicks = 0;

        float step = jumpStage == 1 ? 90.0f : 0.0f;
        float difference = Mth.wrapDegrees(player.getYRot() - towerYaw);
        if (difference < step) {
            towerYaw = player.getYRot();
        } else if (difference > 0.0f) {
            towerYaw += step;
        }

        if (jumpDown) {
            switch (jumpStage) {
                case 0 -> {
                    MoveUtility.strafe(player.getYRot());
                    applySpeedEffectBoost(player, 1.045, 1.035);
                    Vec3 motion = player.getDeltaMovement();
                    player.setDeltaMovement(motion.x, 0.42, motion.z);
                }
                case 1 -> {
                    MoveUtility.strafe(player.getYRot());
                    Vec3 motion = player.getDeltaMovement();
                    player.setDeltaMovement(motion.x, 0.33, motion.z);
                    applySpeedEffectBoost(player, 1.015, 1.005);
                }
                case 2 -> {
                    Vec3 motion = player.getDeltaMovement();
                    player.setDeltaMovement(motion.x, 1.0 - player.getY() % 1.0, motion.z);
                }
                default -> {
                }
            }
        }

        if (jumpStage == 2) {
            jumpStage = -1;
        }
    }

    public static void onPreGameTick(Runnable placeUpwards) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || isHandlerSuppressed(player)) {
            return;
        }

        if (!minecraft.options.keyJump.isDown()) {
            return;
        }
        if (player.onGround()) {
            alignStep = 0;
        }

        if (TeleportTickTracker.getTicksSinceTeleport() < 1) {
            aligning = false;
        }
        if (MoveUtility.isMoving()) {
            aligning = false;
            return;
        }
        align(player, placeUpwards);
    }

    private static boolean isHandlerSuppressed(LocalPlayer player) {
        return player.tickCount % 3 == 0 && player.onGround() && GroundTickTracker.getGroundTicks() > 2;
    }

    public static void onStrafe() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.options.keyJump.isDown()) {
            return;
        }
        if (minecraft.options.keyRight.isDown() || minecraft.options.keyLeft.isDown()) {
            MoveUtility.strafe(0.25, minecraft.player.getYRot());
        }

        if (MoveUtility.getSpeed() < 0.19 && TeleportTickTracker.getTicksSinceTeleport() > 9) {
            MoveUtility.strafe(0.19, minecraft.player.getYRot());
        }
    }

    public static float modifyForwardInput(float forward) {
        Minecraft minecraft = Minecraft.getInstance();
        if (MoveUtility.isMoving() && minecraft.options.keyJump.isDown()) {
            return forward * 5.0f;
        }
        return forward;
    }

    public static void onJump() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (minecraft.options.keyJump.isDown() && !player.hasEffect(MobEffects.SPEED)) {
            MoveUtility.strafe(0.15, player.getYRot());
        }
    }

    private static void applySpeedEffectBoost(LocalPlayer player, double strong, double weak) {
        if (!player.hasEffect(MobEffects.SPEED)) {
            return;
        }
        double multiplier = player.getEffect(MobEffects.SPEED).getAmplifier() + 1 >= 2 ? strong : weak;
        Vec3 motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x * multiplier, motion.y, motion.z * multiplier);
    }

    private static void align(LocalPlayer player, Runnable placeUpwards) {
        double radians = Math.toRadians(player.getYRot());
        boolean alignX = Math.cos(radians) < 0.0;

        if (!aligning) {
            if (alignX) {
                targetX = Math.floor(player.getX()) + 0.999999999999;
                targetZ = Double.NaN;
            } else {
                targetZ = Math.floor(player.getZ()) + 0.999999999999;
                targetX = Double.NaN;
            }
            aligning = true;
        }

        alignStep++;

        if (Math.abs(lastPlaceY - player.getY()) < 1.0) {
            updateRotation(player, placeUpwards);
            alignStep = 0;
            aligning = false;
            return;
        }

        MoveUtility.stop();
        if (isEnclosed(player)) {
            updateRotation(player, placeUpwards);
            return;
        }

        if (alignStep >= 1 && alignStep <= 3) {
            double fraction = alignStep / 3.0;
            if (!Double.isNaN(targetX)) {
                double x = alignStep == 3 ? targetX : player.getX() + (targetX - player.getX()) * fraction;
                if (canFitAt(player, x, player.getY(), player.getZ())) {
                    player.setPos(x, player.getY(), player.getZ());
                }
            } else if (!Double.isNaN(targetZ)) {
                double z = alignStep == 3 ? targetZ : player.getZ() + (targetZ - player.getZ()) * fraction;
                if (canFitAt(player, player.getX(), player.getY(), z)) {
                    player.setPos(player.getX(), player.getY(), z);
                }
            }
        }

        if (alignStep >= 2) {
            updateRotation(player, placeUpwards);
        }
        if (alignStep >= 3) {
            alignStep = 0;
            aligning = false;
        }
    }

    private static void updateRotation(LocalPlayer player, Runnable placeUpwards) {
        lastPlaceY = (int) Math.floor(player.getY());
        placeUpwards.run();
    }

    private static boolean isEnclosed(LocalPlayer player) {
        int x = (int) Math.floor(player.getX());
        int y = (int) Math.floor(player.getY());
        int z = (int) Math.floor(player.getZ());
        return !isAir(x + 1, y, z) && !isAir(x - 1, y, z) && !isAir(x, y, z + 1) && !isAir(x, y, z - 1);
    }

    private static boolean isAir(int x, int y, int z) {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null || minecraft.level.getBlockState(new BlockPos(x, y, z)).isAir();
    }

    private static boolean canFitAt(LocalPlayer player, double x, double y, double z) {
        if (Minecraft.getInstance().level == null) {
            return false;
        }
        float half = player.getBbWidth() / 2.0f;
        AABB box = new AABB(x - half, y, z - half, x + half, y + player.getBbHeight(), z + half);
        for (int i = Mth.floor(box.minX); i <= Mth.floor(box.maxX); i++) {
            for (int j = Mth.floor(box.minY); j <= Mth.floor(box.maxY); j++) {
                for (int k = Mth.floor(box.minZ); k <= Mth.floor(box.maxZ); k++) {
                    if (!isAir(i, j, k)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
