package cc.aerial.client.features.impl.combat;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.player.movement.JumpEvent;
import cc.aerial.client.event.impl.game.player.movement.StrafeEvent;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.features.impl.movement.SprintModule;
import net.minecraft.world.entity.player.Player;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.combat.killaura.KillauraModule;
import cc.aerial.client.features.impl.combat.killaura.target.CurrentTarget;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.utility.GroundTickTracker;
import cc.aerial.client.utility.KeyMappingUtility;
import cc.aerial.client.utility.MoveUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

public final class KeepSprintModule extends Module {
    public static final KeepSprintModule INSTANCE = new KeepSprintModule();

    private static final int MIN_TICKS_SINCE_KNOCKBACK = 8;

    private static final int OLD_MIN_TICKS_SINCE_KNOCKBACK = 7;

    private static final double OLD_BASE_RANGE = 3.0;

    private static final double RANGE_SLACK = 0.5;

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.WATCHDOG);

    private final NumberProperty predictionSlowdown = new NumberProperty("Slowdown", 0, 0, 100, 1)
            .hideIf(() -> mode.getValue() != Mode.PREDICTION);

    private int predictionState;

    private int predictionTicks;

    private boolean predictionHandled;

    private boolean sprintCancelled;

    private boolean sprintKeyReleased;

    private KeepSprintModule() {
        super("Keep Sprint", "Drops sprint the tick before a hit so it does not cost knockback",
                ModuleCategory.COMBAT);
        addProperties(mode, predictionSlowdown);
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    @Override
    protected void onDisable() {
        resetPrediction();
        this.sprintCancelled = false;

        if (this.sprintKeyReleased) {
            this.sprintKeyReleased = false;
            KeyMappingUtility.press(Minecraft.getInstance().options.keySprint);
        }
    }

    public boolean deferAttack(Entity target, double range) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (!isEnabled() || player == null || target == null) {
            return false;
        }
        if (mode.getValue() == Mode.OLD_PREDICTION) {
            return deferOldPrediction(player, target);
        }
        if (mode.getValue() == Mode.PREDICTION) {
            return deferPrediction(player, target);
        }
        if (GroundTickTracker.getTicksSinceKnockback() < MIN_TICKS_SINCE_KNOCKBACK) {
            return false;
        }
        if (player.distanceTo(target) > range + RANGE_SLACK) {
            return false;
        }

        if (GroundTickTracker.getGroundTicks() == 1) {
            return true;
        }
        if (!player.isSprinting()) {
            return false;
        }

        player.setSprinting(false);

        KeyMappingUtility.release(minecraft.options.keySprint);
        this.sprintKeyReleased = true;
        this.sprintCancelled = true;
        return true;
    }

    private boolean deferOldPrediction(LocalPlayer player, Entity target) {
        if (player.tickCount % 2 != 0) {
            return false;
        }
        if (!withinOldRange(player, target)) {
            return false;
        }
        if (GroundTickTracker.getTicksSinceKnockback() < OLD_MIN_TICKS_SINCE_KNOCKBACK) {
            return false;
        }

        return true;
    }

    @Subscribe
    public void onOldPredictionStrafe(StrafeEvent event) {
        if (mode.getValue() != Mode.OLD_PREDICTION) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !player.isSprinting()) {
            return;
        }
        if (player.tickCount % 2 != 0) {
            return;
        }
        CurrentTarget target = KillauraModule.INSTANCE.getTargeting().getTarget();
        if (target == null || !withinOldRange(player, target.getEntity())) {
            return;
        }
        if (GroundTickTracker.getTicksSinceKnockback() < OLD_MIN_TICKS_SINCE_KNOCKBACK) {
            return;
        }
        player.setSprinting(false);
    }

    private boolean deferPrediction(LocalPlayer player, Entity target) {
        if (this.predictionHandled) {
            return false;
        }

        if (target instanceof Player) {
            switch (this.predictionState) {
                case 0 -> {
                    this.predictionTicks = 0;
                    if (player.isSprinting()) {
                        this.predictionState = 1;
                        this.predictionHandled = true;
                        return true;
                    }

                    this.predictionState = 2;
                }
                case 1 -> {
                    player.setSprinting(false);
                    this.predictionTicks = 0;
                    this.predictionState = 2;
                }
                default -> {
                }
            }
        }
        this.predictionHandled = true;
        return false;
    }

    @Subscribe
    public void onPredictionTick(PreGameTickEvent event) {
        if (mode.getValue() != Mode.PREDICTION) {
            return;
        }
        this.predictionHandled = false;
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            resetPrediction();
            return;
        }
        if (this.predictionTicks > 5) {
            resetPrediction();
        }
        switch (this.predictionState) {
            case 1 -> {
                player.setSprinting(false);
                this.predictionTicks++;
            }
            case 2 -> {
                restorePredictionSprint(player);
                this.predictionTicks = 0;
                this.predictionState = 0;
            }
            default -> {
            }
        }
    }

    @Subscribe
    public void onPredictionStrafe(StrafeEvent event) {
        if (mode.getValue() != Mode.PREDICTION) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        switch (this.predictionState) {
            case 1 -> player.setSprinting(false);
            case 2 -> restorePredictionSprint(player);
            default -> {
            }
        }
    }

    private void restorePredictionSprint(LocalPlayer player) {
        if (player.isUsingItem() && !SprintModule.INSTANCE.isEnabled()) {
            return;
        }
        player.setSprinting(true);
    }

    private void resetPrediction() {
        this.predictionState = 0;
        this.predictionTicks = 0;
        this.predictionHandled = false;
    }

    public boolean isPredictionSlowdownActive() {
        LocalPlayer player = Minecraft.getInstance().player;
        return isEnabled() && mode.getValue() == Mode.PREDICTION
                && player != null && player.isSprinting();
    }

    public double predictionVelocityFactor() {
        if (this.predictionState != 2) {
            return 0.6;
        }
        return 1.0 - 0.4 * predictionSlowdown.getValue().doubleValue() / 100.0;
    }

    public boolean predictionShouldDropSprint() {
        return this.predictionState != 2
                || predictionSlowdown.getValue().intValue() == 60;
    }

    private static boolean withinOldRange(LocalPlayer player, Entity target) {
        return player.distanceTo(target) <= OLD_BASE_RANGE + MoveUtility.getSpeed();
    }

    @Subscribe
    public void onJump(JumpEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        if (mode.getValue() == Mode.OLD_PREDICTION) {
            CurrentTarget target = KillauraModule.INSTANCE.getTargeting().getTarget();
            if (player.tickCount % 2 == 0 && target != null
                    && withinOldRange(player, target.getEntity())) {
                event.setCancelled();
            }
            return;
        }
        if (this.sprintCancelled && !player.isSprinting()) {
            event.setCancelled();
        }
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;

        if (this.sprintKeyReleased) {
            this.sprintKeyReleased = false;
            if (KeyMappingUtility.isPhysicallyDown(minecraft.options.keySprint)) {
                KeyMappingUtility.press(minecraft.options.keySprint);
            }
        }

        if (player == null) {
            this.sprintCancelled = false;
            return;
        }
        CurrentTarget target = KillauraModule.INSTANCE.getTargeting().getTarget();
        if (player.isSprinting() || target == null) {
            this.sprintCancelled = false;
        }
    }

    public enum Mode {
        OLD_PREDICTION("Old Prediction"),

        WATCHDOG("Hypixel"),

        PREDICTION("Old Prediction 2");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
