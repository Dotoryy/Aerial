package cc.aerial.client.utility;

import cc.aerial.client.mixin.ClientInputAccessor;
import cc.aerial.client.rotation.RotationHelper;
import cc.aerial.client.features.impl.world.ScaffoldModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec2;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.item.enchantment.Enchantments;

public final class MoveUtility {
    private MoveUtility() {
    }

    public static double getSpeed() {
        LocalPlayer player = Minecraft.getInstance().player;
        return Math.hypot(player.getDeltaMovement().x, player.getDeltaMovement().z);
    }

    public static void setSpeed(double speed) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (speed == 0.0) {
            player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
            return;
        }
        float yaw = (float) getDirectionRadians();
        player.setDeltaMovement(
                -Mth.sin(yaw) * speed,
                player.getDeltaMovement().y,
                Mth.cos(yaw) * speed
        );
    }

    public static double getSwiftnessSpeed(double speed) {
        return getSwiftnessSpeed(speed, 0.2);
    }

    public static double getSwiftnessSpeed(double speed, double swiftnessMultiplier) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!player.hasEffect(MobEffects.SPEED)) {
            return speed;
        }

        if (ScaffoldModule.INSTANCE.isIgnoringSpeedEffect()) {
            return speed;
        }
        return speed * (1 + swiftnessMultiplier * (player.getEffect(MobEffects.SPEED).getAmplifier() + 1));
    }

    public static double getDirectionRadians() {
        return getDirectionRadians(RotationHelper.getClientHandler().getYawOr(Minecraft.getInstance().player.getYRot()));
    }

    public static double getDirectionRadians(float yaw) {
        return Math.toRadians(getDirectionDegrees(yaw));
    }

    public static float getMoveYaw() {
        net.minecraft.client.player.LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0.0f;
        }
        float diffX = (float) (player.getX() - player.xOld);
        float diffZ = (float) (player.getZ() - player.zOld);
        return (float) Math.toDegrees((Math.atan2(-diffX, diffZ) + Math.PI * 2.0) % (Math.PI * 2.0));
    }

    public static float getDirectionDegrees() {
        return getDirectionDegrees(RotationHelper.getClientHandler().getYawOr(Minecraft.getInstance().player.getYRot()));
    }

    public static float getDirectionDegrees(float yaw) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.options.keyDown.isDown()) {
            yaw += 180.0f;
        }

        float forward = 1.0f;
        if (mc.options.keyDown.isDown()) {
            forward = -0.5f;
        } else if (mc.options.keyUp.isDown()) {
            forward = 0.5f;
        }

        if (mc.options.keyLeft.isDown()) {
            yaw -= 90.0f * forward;
        } else if (mc.options.keyRight.isDown()) {
            yaw += 90.0f * forward;
        }

        return yaw;
    }

    public static boolean isMoving() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        Input keyPresses = ((ClientInputAccessor) player.input).aerial$getKeyPresses();
        return keyPresses.forward() || keyPresses.backward() || keyPresses.left() || keyPresses.right();
    }

    public static boolean enoughMovementForSprinting() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        if (player.isShiftKeyDown() || player.isUsingItem()) {
            return false;
        }
        Input keyPresses = ((ClientInputAccessor) player.input).aerial$getKeyPresses();
        float forward = (keyPresses.forward() ? 1.0f : 0.0f) - (keyPresses.backward() ? 1.0f : 0.0f);
        float strafe = (keyPresses.left() ? 1.0f : 0.0f) - (keyPresses.right() ? 1.0f : 0.0f);
        return Math.abs(forward) >= 0.8f || Math.abs(strafe) >= 0.8f;
    }

    private static final double[] MOD_DEPTH_STRIDER =
            {1.0, 1.4304347400741908, 1.7347825295420372, 1.9217390955733897};

    public static void strafe() {
        strafe(getSpeed());
    }

    public static void strafe(float yaw) {
        strafe(getSpeed(), yaw);
    }

    public static void strafe(double speed) {
        strafe(speed, Minecraft.getInstance().player == null
                ? 0.0f : Minecraft.getInstance().player.getYRot());
    }

    public static void strafe(double speed, float yaw) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !isMoving()) {
            return;
        }
        double direction = getDirectionRadians(yaw);
        player.setDeltaMovement(
                -Math.sin(direction) * speed,
                player.getDeltaMovement().y,
                Math.cos(direction) * speed
        );
    }

    public static void stop() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        player.setDeltaMovement(0.0, player.getDeltaMovement().y, 0.0);
    }

    public static double getBaseSpeed() {
        LocalPlayer player = Minecraft.getInstance().player;
        double speed = 0.2873;
        if (player != null && player.hasEffect(MobEffects.SPEED)) {
            speed *= 1.0 + 0.2 * (player.getEffect(MobEffects.SPEED).getAmplifier() + 1);
        }
        return speed;
    }

    public static double predictedMotion(double motionY) {
        return (motionY - 0.08) * 0.9800000190734863;
    }

    public static double predictedMotion(double motionY, int ticks) {
        double motion = motionY;
        for (int i = 0; i < ticks; i++) {
            motion = predictedMotion(motion);
        }
        return motion;
    }

    public static double getAllowedHorizontalDistance() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0.221;
        }
        boolean modifiable = false;
        double distance;
        if (player.isInWall()) {
            distance = 0.105;
        } else if (player.isInWater() || player.isInLava()) {
            distance = 0.11500000208616258;
            int depthStrider = getDepthStriderLevel(player);
            if (depthStrider > 0) {
                distance *= MOD_DEPTH_STRIDER[Math.min(depthStrider, MOD_DEPTH_STRIDER.length - 1)];
                modifiable = true;
            }
        } else if (player.isShiftKeyDown()) {
            distance = 0.0663000026345253;
        } else {
            distance = 0.221;
            modifiable = true;
        }

        if (modifiable) {
            if (player.isSprinting()) {
                distance *= 1.3f;
            }
            if (player.hasEffect(MobEffects.SPEED) && !ScaffoldModule.INSTANCE.isIgnoreSpeedEffect()) {
                distance *= 1.0 + 0.2 * (player.getEffect(MobEffects.SPEED).getAmplifier() + 1);
            }
            if (player.hasEffect(MobEffects.SLOWNESS)) {
                distance = 0.29;
            }
        }
        return distance;
    }

    private static int getDepthStriderLevel(LocalPlayer player) {
        return InventoryUtility.calculateEnchantmentLevel(player.getItemBySlot(EquipmentSlot.FEET), Enchantments.DEPTH_STRIDER);
    }
}
