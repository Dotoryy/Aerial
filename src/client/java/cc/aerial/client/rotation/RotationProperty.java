package cc.aerial.client.rotation;

import cc.aerial.client.property.GroupProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.property.Property;
import cc.aerial.client.rotation.model.EnumRotationModel;
import cc.aerial.client.rotation.model.IRotationModel;

import java.util.ArrayList;
import java.util.List;

public final class RotationProperty {
    public enum RotationMode {
        ADVANCED("Advanced"),
        REGULAR("Regular"),

        LEGIT_NORMAL("Legit/Normal");

        private final String label;

        RotationMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final ModeProperty<RotationMode> rotationMode;
    private final ModeProperty<EnumRotationModel> modelProperty;

    private final NumberProperty maxAngle, driftIntensity, jitterIntensity;
    private final NumberProperty legitSpeedMin, legitSpeedMax;
    private final NumberProperty regularMaxAngle, regularSmoothness;

    private final GroupProperty groupProperty;

    public RotationProperty(IRotationModel defaultModel, Property<?>... customProperties) {
        this.rotationMode = new ModeProperty<>("Rotation Mode", RotationMode.ADVANCED);

        this.modelProperty = new ModeProperty<>("Model", defaultModel.getEnum())
                .hideIf(() -> this.rotationMode.getValue() != RotationMode.ADVANCED);

        this.maxAngle = new NumberProperty("Max angle", 90, 5, 360, 5)
                .hideIf(() -> this.rotationMode.getValue() != RotationMode.ADVANCED
                        || this.modelProperty.getValue() == EnumRotationModel.INSTANT);

        this.driftIntensity = new NumberProperty("Drift intensity", 1.2, 0.5, 2, 0.1)
                .hideIf(() -> this.rotationMode.getValue() != RotationMode.ADVANCED
                        || this.modelProperty.getValue() != EnumRotationModel.ORGANIC);
        this.jitterIntensity = new NumberProperty("Jitter intensity", 0.12, 0, 0.3, 0.01)
                .hideIf(() -> this.rotationMode.getValue() != RotationMode.ADVANCED
                        || this.modelProperty.getValue() != EnumRotationModel.ORGANIC);

        this.legitSpeedMin = new NumberProperty("Legit Speed", 5, 0, 10, 1)
                .hideIf(() -> this.rotationMode.getValue() != RotationMode.LEGIT_NORMAL);
        this.legitSpeedMax = new NumberProperty("Legit Speed Max", 10, 0, 10, 1)
                .hideIf(() -> this.rotationMode.getValue() != RotationMode.LEGIT_NORMAL);

        this.regularMaxAngle = new NumberProperty("Regular Max Angle", 90, 30, 180, 1)
                .hideIf(() -> this.rotationMode.getValue() != RotationMode.REGULAR);
        this.regularSmoothness = new NumberProperty("Regular Smoothness", 0, 0, 100, 1)
                .hideIf(() -> this.rotationMode.getValue() != RotationMode.REGULAR);

        List<Property<?>> properties = new ArrayList<>(List.of(
                this.rotationMode, this.modelProperty, this.maxAngle, this.driftIntensity, this.jitterIntensity,
                this.legitSpeedMin, this.legitSpeedMax,
                this.regularMaxAngle, this.regularSmoothness));
        properties.addAll(List.of(customProperties));

        this.groupProperty = new GroupProperty("Rotation", properties.toArray(new Property<?>[0]));
    }

    public double getLegitSpeedMin() {
        return this.legitSpeedMin.getValue();
    }

    public double getLegitSpeedMax() {
        return this.legitSpeedMax.getValue();
    }

    public GroupProperty get() {
        return this.groupProperty;
    }

    public int getMaxAngle() {
        return this.maxAngle.getValue().intValue();
    }

    public double getDriftIntensity() {
        return this.driftIntensity.getValue();
    }

    public double getJitterIntensity() {
        return this.jitterIntensity.getValue();
    }

    public IRotationModel createModel() {
        return this.modelProperty.getValue().supply(this);
    }

    public boolean isLegitNormal() {
        return this.rotationMode.getValue() == RotationMode.LEGIT_NORMAL;
    }

    public boolean isRegular() {
        return this.rotationMode.getValue() == RotationMode.REGULAR;
    }

    public float getRegularMaxAngle() {
        return this.regularMaxAngle.getValue().floatValue();
    }

    public float getRegularSmoothness() {
        return this.regularSmoothness.getValue().floatValue() / 100.0f;
    }
}
