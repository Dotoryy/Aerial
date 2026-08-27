package cc.aerial.client.features.impl.combat.killaura;

import cc.aerial.client.mouse.CPSProperty;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.GroupProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.MultipleBooleanProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.property.Property;
import cc.aerial.client.rotation.RotationProperty;
import cc.aerial.client.rotation.model.IRotationModel;
import cc.aerial.client.rotation.model.impl.InstantRotationModel;
import cc.aerial.client.target.TargetProperty;

public final class KillauraSettings {
    private final RotationProperty rotationProperty;
    private final NumberProperty horizontalMultipoint, verticalMultipoint;
    private final BooleanProperty silent;
    private final ModeProperty<Mode> mode;
    private final TargetProperty targetProperty;
    private final CPSProperty cpsProperty, swingCpsProperty;

    private final NumberProperty rotationRange, swingRange;
    private final BooleanProperty hideFakeSwings;
    private final BooleanProperty hitSelect;
    private final BooleanProperty cancelMissedHit;
    private final NumberProperty cancelMissedHitChance;

    private final BooleanProperty requireAttackKey, requireWeapon;
    private final BooleanProperty disableWhileBlocking;
    private final BooleanProperty disableWhileMining;
    private final BooleanProperty overrideRaycast, tickLookahead;
    private final NumberProperty fov;

    private final MultipleBooleanProperty visuals;

    private final Property<?>[] properties;

    public KillauraSettings() {
        this.horizontalMultipoint = new NumberProperty("Horizontal Multipoint", 100, 0, 100, 1);
        this.verticalMultipoint = new NumberProperty("Vertical Multipoint", 100, 0, 100, 1);

        this.silent = new BooleanProperty("Silent", true);
        this.rotationProperty = new RotationProperty(InstantRotationModel.INSTANCE, horizontalMultipoint, verticalMultipoint, silent);

        this.horizontalMultipoint.hideIf(() -> this.rotationProperty.isRegular() || this.rotationProperty.isLegitNormal());
        this.verticalMultipoint.hideIf(() -> this.rotationProperty.isRegular() || this.rotationProperty.isLegitNormal());
        this.targetProperty = new TargetProperty(true, false, false, false, false, true);
        this.cpsProperty = new CPSProperty("Attack CPS", true);
        this.swingCpsProperty = new CPSProperty("Swing CPS", false).hideIf(this.cpsProperty::isModernDelay);

        this.rotationRange = new NumberProperty("Rotation range", 5.0, 3.0, 8.0, 0.1);
        this.swingRange = new NumberProperty("Swing range", 5.0, 3.0, 8.0, 0.1).hideIf(this.cpsProperty::isModernDelay);
        this.hideFakeSwings = new BooleanProperty("Hide fake swings", true).hideIf(this.cpsProperty::isModernDelay);

        this.hitSelect = new BooleanProperty("Hit Select", false).hideIf(this.cpsProperty::isModernDelay);

        this.cancelMissedHit = new BooleanProperty("Cancel Missed Hit", false);

        this.cancelMissedHitChance = new NumberProperty("Cancel Missed Hit Chance", 100, 0, 100, 1)
                .hideIf(() -> !this.cancelMissedHit.getValue());

        this.requireAttackKey = new BooleanProperty("Require attack key", false);
        this.requireWeapon = new BooleanProperty("Require weapon", false);

        this.disableWhileBlocking = new BooleanProperty("Disable while blocking", true);
        this.disableWhileMining = new BooleanProperty("Disable while mining", false);
        this.overrideRaycast = new BooleanProperty("Override raycast", true);
        this.tickLookahead = new BooleanProperty("Tick lookahead", false).hideIf(() -> !this.isOverrideRaycast());
        this.mode = new ModeProperty<>("Mode", Mode.SWITCH);
        this.fov = new NumberProperty("FOV", 180, 1, 180, 1);

        this.visuals = new MultipleBooleanProperty("Visuals",
                new BooleanProperty("Box", false)
        );

        this.properties = new Property<?>[]{
                rotationProperty.get(), new GroupProperty("Requirements", requireWeapon, requireAttackKey, disableWhileBlocking, disableWhileMining),
                mode, cpsProperty.get(), swingCpsProperty.get(), rotationRange, swingRange, hideFakeSwings, hitSelect, cancelMissedHit, cancelMissedHitChance, targetProperty.get(),
                fov, overrideRaycast, tickLookahead, visuals
        };
    }

    public Property<?>[] getProperties() {
        return properties;
    }

    public double getSwingRange() {
        return this.swingRange.getValue();
    }

    public boolean isHitSelect() {
        return this.hitSelect.getValue();
    }

    public boolean shouldCancelMissedHit() {
        if (!this.cancelMissedHit.getValue()) {
            return false;
        }
        double chance = this.cancelMissedHitChance.getValue().doubleValue();
        return chance >= 100.0 || (chance > 0.0 && Math.random() * 100.0 < chance);
    }

    public boolean isHideFakeSwings() {
        return this.hideFakeSwings.getValue();
    }

    public boolean isOverrideRaycast() {
        return this.overrideRaycast.getValue();
    }

    public boolean isTickLookahead() {
        return this.tickLookahead.getValue();
    }

    public double getRotationRange() {
        return this.rotationRange.getValue();
    }

    public MultipleBooleanProperty getVisuals() {
        return visuals;
    }

    public TargetProperty getTargetProperty() {
        return targetProperty;
    }

    public CPSProperty getCpsProperty() {
        return cpsProperty;
    }

    public CPSProperty getSwingCpsProperty() {
        return swingCpsProperty;
    }

    public boolean isRequireAttackKey() {
        return requireAttackKey.getValue();
    }

    public boolean isRequireWeapon() {
        return requireWeapon.getValue();
    }

    public boolean isDisableWhileBlocking() {
        return disableWhileBlocking.getValue();
    }

    public boolean isDisableWhileMining() {
        return disableWhileMining.getValue();
    }

    public IRotationModel createRotationModel() {
        return rotationProperty.createModel();
    }

    public boolean isRegularRotation() {
        return rotationProperty.isRegular();
    }

    public boolean isLegitNormalRotation() {
        return rotationProperty.isLegitNormal();
    }

    public double getLegitSpeedMin() {
        return rotationProperty.getLegitSpeedMin();
    }

    public double getLegitSpeedMax() {
        return rotationProperty.getLegitSpeedMax();
    }

    public float getRegularMaxAngle() {
        return rotationProperty.getRegularMaxAngle();
    }

    public float getRegularSmoothness() {
        return rotationProperty.getRegularSmoothness();
    }

    public double getHorizontalMultipoint() {
        return horizontalMultipoint.getValue();
    }

    public double getVerticalMultipoint() {
        return verticalMultipoint.getValue();
    }

    public boolean isSilent() {
        return silent.getValue();
    }

    public Mode getMode() {
        return mode.getValue();
    }

    public float getFov() {
        return this.fov.getValue().floatValue();
    }

    public enum Mode {
        SINGLE("Single"),
        SWITCH("Switch");

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
