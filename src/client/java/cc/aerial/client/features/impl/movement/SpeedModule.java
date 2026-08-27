package cc.aerial.client.features.impl.movement;

import cc.aerial.client.event.impl.game.input.MoveInputEvent;
import cc.aerial.client.event.impl.game.player.movement.PostMoveEvent;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import cc.aerial.client.event.impl.game.player.movement.PreMovementPacketEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.utility.GroundTickTracker;
import cc.aerial.client.utility.MoveUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.client.player.LocalPlayer;

public final class SpeedModule extends Module {
    public static final SpeedModule INSTANCE = new SpeedModule();

    public enum Mode {
        VANILLA("Vanilla"),
        STRAFE("Strafe"),
        MUSHMC("MushMC"),
        BHOP("BHop"),
        BHOP_9TICK("BHop 9 Tick"),
        BHOP_8TICK("BHop 8 Tick"),
        BHOP_7TICK("BHop 7 Tick"),
        CUBECRAFT("Cubecraft");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.VANILLA);
    private final NumberProperty vanillaSpeed = new NumberProperty("Speed", 1.0, 0.1, 10.0, 0.1).hideIf(() -> mode.getValue() != Mode.VANILLA);
    private final BooleanProperty autoJump = new BooleanProperty("Auto Jump", true).hideIf(() -> mode.getValue() != Mode.VANILLA);
    private final BooleanProperty fastStop = new BooleanProperty("Fast Stop", true).hideIf(() -> mode.getValue() != Mode.STRAFE);

    private final NumberProperty bhopSpeed = new NumberProperty("BHop Speed", 1.2, 0.8, 1.2, 0.01)
            .hideIf(() -> !isBHopMode());
    private final BooleanProperty jumpWhenMoving = new BooleanProperty("Only Jump When Moving", true)
            .hideIf(() -> !isBHopMode());
    private final BooleanProperty disableInLiquid = new BooleanProperty("Disable In Liquid", true)
            .hideIf(() -> !isBHopMode());
    private final BooleanProperty disableWhileSneaking = new BooleanProperty("Disable While Sneaking", true)
            .hideIf(() -> mode.getValue() != Mode.BHOP);

    private final NumberProperty cubecraftSpeed = new NumberProperty("Cubecraft Speed", 1.0, 0.0, 10.0, 0.05)
            .hideIf(() -> mode.getValue() != Mode.CUBECRAFT);

    private boolean hopThisTick;

    private boolean didMove;

    private int inAirTicks;

    private boolean lowhopAir;

    private boolean slowArmed;
    private boolean slowApplied;
    private boolean slowRunning;

    private SpeedModule() {
        super("Speed", "You become a cheetah in real life", ModuleCategory.MOVEMENT);
        addProperties(mode, vanillaSpeed, autoJump, fastStop, bhopSpeed, jumpWhenMoving,
                disableInLiquid, disableWhileSneaking, cubecraftSpeed);
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    @Override
    protected void onDisable() {
        hopThisTick = false;
        inAirTicks = 0;
        slowArmed = false;
        slowApplied = false;
        slowRunning = false;
        resetLowhop();
        if (Minecraft.getInstance().player == null) {
            return;
        }
        double maxSpeed = MoveUtility.getSwiftnessSpeed(0.221);
        MoveUtility.setSpeed(Math.min(MoveUtility.getSpeed(), maxSpeed));
    }

    @Subscribe
    public void onPostMove(PostMoveEvent event) {
        switch (mode.getValue()) {
            case VANILLA -> MoveUtility.setSpeed(MoveUtility.isMoving() ? vanillaSpeed.getValue() : 0);
            case STRAFE -> {
                if (MoveUtility.isMoving()) {
                    MoveUtility.setSpeed(MoveUtility.getSpeed());
                } else if (fastStop.getValue()) {
                    MoveUtility.setSpeed(0);
                }
            }
            case MUSHMC -> {
            }
            case CUBECRAFT -> onCubecraftMove();
            case BHOP, BHOP_9TICK, BHOP_8TICK, BHOP_7TICK -> {
                if (!hopThisTick) {
                    return;
                }
                hopThisTick = false;
                if (MoveUtility.isMoving()) {
                    MoveUtility.setSpeed(bhopTargetSpeed());
                    didMove = true;
                }
            }
        }
    }

    private double bhopTargetSpeed() {
        double speed = bhopSpeed.getValue().doubleValue() - 0.52;
        speed += switch (speedAmplifier()) {
            case 0 -> 0.0;
            case 1 -> 0.02;
            case 2 -> 0.04;
            default -> 0.1;
        };
        return speed - (0.0001 + Math.random() * 0.0002);
    }

    private static int speedAmplifier() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        MobEffectInstance effect = player.getEffect(MobEffects.SPEED);
        return effect == null ? 0 : 1 + effect.getAmplifier();
    }

    private boolean bhopBlocked(LocalPlayer player) {
        if (disableInLiquid.getValue() && (player.isInWater() || player.isInLava())) {
            return true;
        }
        return disableWhileSneaking.getValue() && player.isShiftKeyDown();
    }

    @Subscribe
    public void onMoveInput(MoveInputEvent event) {
        switch (mode.getValue()) {
            case VANILLA -> {
                if (autoJump.getValue() && MoveUtility.isMoving()) {
                    event.setJump(true);
                }
            }
            case STRAFE -> {
                if (MoveUtility.isMoving()) {
                    event.setJump(true);
                }
            }
            case MUSHMC -> {
            }
            case CUBECRAFT -> {
            }
            case BHOP, BHOP_9TICK, BHOP_8TICK, BHOP_7TICK -> {
                LocalPlayer player = Minecraft.getInstance().player;
                hopThisTick = false;
                if (player == null || !player.onGround() || bhopBlocked(player)) {
                    return;
                }

                if (event.getForward() <= 0.5f) {
                    slowArmed = true;
                    slowRunning = false;
                }
                if (jumpWhenMoving.getValue() && !MoveUtility.isMoving()) {
                    return;
                }

                event.setJump(true);
                hopThisTick = true;
            }
        }
    }

    private void onCubecraftMove() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !MoveUtility.isMoving()) {
            return;
        }
        if (player.onGround()) {
            player.jumpFromGround();
        }
        if (!player.horizontalCollision) {
            int airTicks = GroundTickTracker.getAirTicks();
            if (airTicks == 1) {
                setMotionY(player, player.getDeltaMovement().y - 0.5);
            } else if (airTicks == 5) {
                setMotionY(player, player.getDeltaMovement().y - 0.4);
            }
        }
        if (player.hurtTime != 0) {
            MoveUtility.strafe(cubecraftSpeed.getValue().floatValue());
        } else {
            MoveUtility.strafe(player.onGround() ? 0.55 : MoveUtility.getBaseSpeed());
        }
    }

    private boolean isBHopMode() {
        return switch (mode.getValue()) {
            case BHOP, BHOP_9TICK, BHOP_8TICK, BHOP_7TICK -> true;
            default -> false;
        };
    }

    @Subscribe
    public void onPreMovementPacket(PreMovementPacketEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !isBHopMode()) {
            return;
        }
        inAirTicks = player.onGround() ? 0 : inAirTicks + 1;

        applyLowhop(player, event.getY());
        applyAirBrake(player);

        if (!player.onGround()) {
            lowhopAir = true;
        } else if (lowhopAir) {
            resetLowhop();
        }
        if (player.onGround()) {
            slowApplied = false;
        }
    }

    private void applyLowhop(LocalPlayer player, double reportedY) {
        if (!didMove || player.horizontalCollision) {
            return;
        }
        Level level = player.level();
        BlockPos at = player.blockPosition();

        if (!level.getBlockState(at).isAir()
                || (level.getBlockState(at.below()).isAir() && level.getBlockState(at.below(2)).isAir())) {
            resetLowhop();
            return;
        }

        int simpleY = (int) Math.round((reportedY % 1.0) * 10000.0);
        double motionY = player.getDeltaMovement().y;

        switch (mode.getValue()) {
            case BHOP_9TICK -> {
                switch (simpleY) {
                    case 13 -> setMotionY(player, motionY - 0.02483);
                    case 2000 -> setMotionY(player, motionY - 0.1913);
                    case 7016 -> setMotionY(player, motionY + 0.08);
                    default -> { }
                }

                if (inAirTicks > 6 && MoveUtility.isMoving()) {
                    MoveUtility.setSpeed(MoveUtility.getSpeed());
                }
                if (inAirTicks > 8) {
                    resetLowhop();
                }
            }
            case BHOP_8TICK -> {
                switch (simpleY) {
                    case 13 -> setMotionY(player, motionY - 0.045);
                    case 2000 -> {
                        setMotionY(player, motionY - 0.175);
                        resetLowhop();
                    }
                    default -> { }
                }
            }
            case BHOP_7TICK -> {
                switch (simpleY) {
                    case 4200 -> setMotionY(player, 0.39);
                    case 1138 -> setMotionY(player, motionY - 0.13);
                    case 2031 -> {
                        setMotionY(player, motionY - 0.2);
                        resetLowhop();
                    }
                    default -> { }
                }
            }
            default -> { }
        }
    }

    private void applyAirBrake(LocalPlayer player) {
        if (slowArmed && !player.onGround()) {
            double factor = 0.9 - (inAirTicks / 10000.0) - (0.00001 + Math.random() * 0.00005);
            if (player.hurtTime == 0 && inAirTicks > 4 && !slowApplied) {
                player.setDeltaMovement(player.getDeltaMovement().multiply(factor, 1.0, factor));
                slowApplied = true;
            }
            slowRunning = true;
        } else if (slowRunning) {
            slowArmed = false;
            slowRunning = false;
        }
    }

    private static void setMotionY(LocalPlayer player, double motionY) {
        player.setDeltaMovement(player.getDeltaMovement().x, motionY, player.getDeltaMovement().z);
    }

    private void resetLowhop() {
        didMove = false;
        lowhopAir = false;
    }
}
