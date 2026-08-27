package cc.aerial.client.features.impl.movement;

import cc.aerial.client.event.impl.game.PostGameTickEvent;
import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.input.MoveInputEvent;
import cc.aerial.client.event.impl.game.packet.InstantaneousSendPacketEvent;
import cc.aerial.client.event.impl.game.packet.ReceivePacketEvent;
import cc.aerial.client.event.impl.game.player.movement.StrafeEvent;
import cc.aerial.client.event.impl.game.player.movement.knockback.KnockbackEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.notification.NotificationManager;
import cc.aerial.client.notification.NotificationType;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.rotation.RotationUtility;
import cc.aerial.client.utility.MoveUtility;
import cc.aerial.client.utility.Stopwatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

public final class LongJumpModule extends Module {
    public static final LongJumpModule INSTANCE = new LongJumpModule();

    private static final long FIREBALL_WINDOW_MS = 1000L;

    private static final long ARM_TIMEOUT_MS = 1500L;
    private static final int RIDE_TICKS = 30;

    private static final int ABORT_GRACE_TICKS = 3;

    private static final double AIR_ACCELERATION_SPRINTING = 0.026;
    private static final double AIR_ACCELERATION = 0.02;

    private static final int JUMP_DELAY_TICKS = 2;
    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.FIREBALL);
    private final NumberProperty motion = new NumberProperty("Motion", 1.0, 1.0, 20.0, 0.1);
    private final NumberProperty speedMotion = new NumberProperty("Speed Motion", 1.0, 1.0, 20.0, 0.1);
    private final NumberProperty strafe = new NumberProperty("Strafe", 0, 0, 100, 1);

    private final Stopwatch fireballTimer = new Stopwatch();
    private final Stopwatch jumpTimer = new Stopwatch();

    private boolean isJumping;
    private int tickCounter;
    private int jumpModeStage;
    private boolean readyToUseFireball;
    private boolean fireballLaunched;
    private int savedHotbarSlot = -1;

    private boolean rotationApplied;
    private float savedPitch;

    private boolean jumpPending;

    private int jumpCountdown = -1;

    private boolean rideAirborne;

    private double preKnockbackSpeed;

    private LongJumpModule() {
        super("Long Jump", "Rides a fireball's own knockback as far as it will go", ModuleCategory.MOVEMENT);
        addProperties(mode, motion, speedMotion, strafe);
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    public boolean isAutoMode() {
        return mode.getValue() != Mode.FIREBALL_MANUAL;
    }

    public boolean isJumping() {
        return isJumping;
    }

    public boolean canStartJump() {
        return !fireballTimer.hasTimeElapsed(FIREBALL_WINDOW_MS) && !isJumping;
    }

    @Override
    protected void onEnable() {
        jumpTimer.reset();
        if (isAutoMode() && findFireballInHotbar() < 0) {
            setEnabled(false);
            notifyAutoDisabled();
        }
    }

    private void notifyAutoDisabled() {
        NotificationManager.INSTANCE.builder(NotificationType.WARNING)
                .title("Warning")
                .description("Long Jump was disabled to prevent flags/errors")
                .duration(4000)
                .buildAndPublish();
    }

    @Override
    protected void onDisable() {
        endJump();
        readyToUseFireball = false;
        fireballLaunched = false;
        jumpPending = false;
        jumpCountdown = -1;
        restoreRotation();
        restoreSlot();
    }

    private void endJump() {
        isJumping = false;
        tickCounter = 0;
        jumpModeStage = 0;
        rideAirborne = false;
    }

    @Subscribe(priority = 10)
    public void onKnockback(KnockbackEvent event) {
        if (!isEnabled() || event.isOverridden() || !canStartJump()) {
            return;
        }
        event.setOverridden();
        isJumping = true;
        tickCounter = 0;

        preKnockbackSpeed = MoveUtility.getSpeed();

        restoreRotation();
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        if (jumpCountdown > 0 && --jumpCountdown == 0) {
            jumpCountdown = -1;
            jumpPending = true;
        }

        if (isAutoMode() && !fireballLaunched && readyToUseFireball) {
            launchFireball(mc, player);
        }

        if (isJumping) {
            rideJump(player);
            return;
        }
        if (!isAutoMode()) {
            return;
        }
        if (jumpTimer.hasTimeElapsed(ARM_TIMEOUT_MS)) {
            setEnabled(false);
            notifyAutoDisabled();
            return;
        }
        readyToUseFireball = true;
        applyRotation(player);
    }

    @Subscribe
    public void onPostGameTick(PostGameTickEvent event) {
        restoreSlot();
    }

    private void launchFireball(Minecraft mc, LocalPlayer player) {
        int slot = findFireballInHotbar();
        if (slot < 0) {
            return;
        }

        jumpCountdown = JUMP_DELAY_TICKS;

        applyRotation(player);

        Inventory inventory = player.getInventory();
        savedHotbarSlot = inventory.getSelectedSlot();
        inventory.setSelectedSlot(slot);

        if (mc.gameMode != null) {
            mc.gameMode.useItem(player, InteractionHand.MAIN_HAND);
        }
        fireballTimer.reset();
        fireballLaunched = true;
    }

    private void restoreSlot() {
        if (savedHotbarSlot < 0) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.getInventory().setSelectedSlot(savedHotbarSlot);
        }
        savedHotbarSlot = -1;
    }

    @Subscribe(priority = -20)
    public void onMoveInput(MoveInputEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (jumpPending && player != null) {
            jumpPending = false;
            event.setJump(true);
        }
    }

    private int findFireballInHotbar() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return -1;
        }
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(Items.FIRE_CHARGE)) {
                return slot;
            }
        }
        return -1;
    }

    private void rideJump(LocalPlayer player) {
        tickCounter++;
        if (tickCounter == 1) {
            jumpModeStage = switch (mode.getValue()) {
                case FIREBALL, FIREBALL_MANUAL -> 0;
                case FIREBALL_HIGH -> 1;

                case FIREBALL_FLAT -> isForwardPressed() ? 2 : 1;
            };
        }

        if (!player.onGround()) {
            rideAirborne = true;
        } else if (rideAirborne) {
            endJump();
            if (isAutoMode()) {
                setEnabled(false);
                notifyAutoDisabled();
            }
            return;
        }

        if (tickCounter >= 2 && isForwardPressed()) {
            double target = preKnockbackSpeed * getMotionFactor();
            double current = MoveUtility.getSpeed();
            if (current < target) {
                double step = player.isSprinting() ? AIR_ACCELERATION_SPRINTING : AIR_ACCELERATION;
                setSpeedAlongTravel(player, Math.min(target, current + step));
            }
        }

        if (tickCounter >= 1 && tickCounter <= RIDE_TICKS) {
            Vec3 delta = player.getDeltaMovement();
            switch (jumpModeStage) {
                case 1 -> {
                    if (tickCounter == 1) {
                        player.setDeltaMovement(delta.x, delta.y * 0.75, delta.z);
                    } else {
                        double next = delta.y / 0.98 + 0.055;
                        if (next > 0.0) {
                            player.setDeltaMovement(delta.x, next, delta.z);
                        }
                    }
                }
                case 2 -> {
                    if (tickCounter == 1) {
                        player.setDeltaMovement(delta.x, delta.y * 0.75, delta.z);
                    } else {
                        player.setDeltaMovement(delta.x, 0.01 + tickCounter * 0.003, delta.z);
                    }
                }
                default -> {
                }
            }
        }

        if (tickCounter >= RIDE_TICKS) {
            endJump();
            if (isAutoMode()) {
                setEnabled(false);
                notifyAutoDisabled();
            }
        }
    }

    private static void setSpeedAlongTravel(LocalPlayer player, double speed) {
        Minecraft mc = Minecraft.getInstance();

        float forward = mc.options.keyUp.isDown() ? 1.0f : (mc.options.keyDown.isDown() ? -1.0f : 0.0f);
        float strafe = mc.options.keyLeft.isDown() ? 1.0f
                : (mc.options.keyRight.isDown() ? -1.0f : 0.0f);
        if (forward == 0.0f && strafe == 0.0f) {
            return;
        }

        double radians = Math.toRadians(player.getYRot());
        double sin = Math.sin(radians);
        double cos = Math.cos(radians);
        double dirX = strafe * cos - forward * sin;
        double dirZ = forward * cos + strafe * sin;
        double length = Math.hypot(dirX, dirZ);
        if (length < 1.0E-7) {
            return;
        }

        Vec3 delta = player.getDeltaMovement();
        player.setDeltaMovement(dirX / length * speed, delta.y, dirZ / length * speed);
    }

    @Subscribe
    public void onStrafeBlend(StrafeEvent event) {
        int percent = strafe.getValue().intValue();
        if (!isEnabled() || !isJumping || percent <= 0 || tickCounter < 5 || tickCounter > RIDE_TICKS) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        double speed = MoveUtility.getSpeed();
        if (speed <= 0.0) {
            return;
        }
        Vec3 delta = player.getDeltaMovement();

        double momentumYaw = Math.toRadians(Math.toDegrees(Math.atan2(delta.z, delta.x)) - 90.0);
        double inputYaw = MoveUtility.getDirectionRadians();
        double keep = speed * (100 - percent) / 100.0;
        double add = speed * percent / 100.0;

        double x = -Math.sin(momentumYaw) * keep - Math.sin(inputYaw) * add;
        double z = Math.cos(momentumYaw) * keep + Math.cos(inputYaw) * add;

        double blended = Math.hypot(x, z);
        if (blended > 0.0) {
            player.setDeltaMovement(x / blended * speed, delta.y, z / blended * speed);
        }
    }

    private void applyRotation(LocalPlayer player) {
        if (!rotationApplied) {
            savedPitch = player.getXRot();
            rotationApplied = true;
        }
        float pitch = RotationUtility.quantizeAngle(89.0f + (float) ((Math.random() - 0.5) * 0.5));
        RotationUtility.setRotationSilently(player, player.getYRot(), pitch);
    }

    private void restoreRotation() {
        if (!rotationApplied) {
            return;
        }
        rotationApplied = false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        RotationUtility.setRotationSilently(player, player.getYRot(), savedPitch);
        player.yRotO = player.getYRot();
        player.xRotO = player.getXRot();
    }

    private double getMotionFactor() {
        LocalPlayer player = Minecraft.getInstance().player;
        MobEffectInstance speed = player == null ? null : player.getEffect(MobEffects.SPEED);
        return speed != null
                ? speedMotion.getValue().doubleValue()
                : motion.getValue().doubleValue();
    }

    private static boolean isForwardPressed() {
        return Minecraft.getInstance().options.keyUp.isDown();
    }

    @Subscribe
    public void onSendPacket(InstantaneousSendPacketEvent event) {
        if (!isEnabled() || !(event.getPacket() instanceof ServerboundUseItemPacket)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.getMainHandItem().is(Items.FIRE_CHARGE)) {
            fireballTimer.reset();
        }
    }

    @Subscribe(priority = 5)
    public void onReceivePacket(ReceivePacketEvent event) {
        if (!isEnabled() || !(event.getPacket() instanceof ClientboundPlayerPositionPacket)
                || !isJumping || tickCounter <= ABORT_GRACE_TICKS) {
            return;
        }
        endJump();
        if (isAutoMode()) {
            setEnabled(false);

            notifyAutoDisabled();
        }
    }

    public enum Mode {
        FIREBALL("Fireball"),
        FIREBALL_MANUAL("Fireball Manual"),
        FIREBALL_HIGH("Fireball High"),
        FIREBALL_FLAT("Fireball Flat");

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
