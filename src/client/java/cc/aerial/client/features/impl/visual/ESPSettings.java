package cc.aerial.client.features.impl.visual;

import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.GroupProperty;
import cc.aerial.client.property.MultipleBooleanProperty;
import cc.aerial.client.target.TargetProperty;

final class ESPSettings {
    private final TargetProperty targetProperty;

    private final BooleanProperty box, boxStroke;
    private final BooleanProperty healthBar, healthBarStroke;

    private final BooleanProperty nameTags;
    private final MultipleBooleanProperty nameTagElements;
    private final MultipleBooleanProperty nameTagIndicators;

    ESPSettings(ESPModule module) {
        this.targetProperty = new TargetProperty(true, true, true, false, false, true);

        this.box = new BooleanProperty("Enabled", true);
        this.boxStroke = new BooleanProperty("Stroke", true).hideIf(() -> !this.box.getValue());

        this.healthBar = new BooleanProperty("Enabled", true);
        this.healthBarStroke = new BooleanProperty("Stroke", true).hideIf(() -> !this.healthBar.getValue());

        this.nameTags = new BooleanProperty("Enabled", true);

        this.nameTagElements = new MultipleBooleanProperty("Elements",
                new BooleanProperty("Name", true),
                new BooleanProperty("Health", true),
                new BooleanProperty("Distance", true),
                new BooleanProperty("Equipment", false)
        ).hideIf(() -> !this.nameTags.getValue());

        this.nameTagIndicators = new MultipleBooleanProperty("Indicators",
                new BooleanProperty("Sneaking", true),
                new BooleanProperty("Strength", true),
                new BooleanProperty("Invisible", true),
                new BooleanProperty("Blocking", true)
        ).hideIf(() -> !this.nameTags.getValue());

        module.addProperties(
                new GroupProperty("Box", this.box, this.boxStroke),
                new GroupProperty("Health Bar", this.healthBar, this.healthBarStroke),
                new GroupProperty("Name Tags", this.nameTags, this.nameTagElements, this.nameTagIndicators),
                this.targetProperty.get()
        );
    }

    TargetProperty getTargetProperty() {
        return targetProperty;
    }

    boolean areNameTagsEnabled() {
        return nameTags.getValue();
    }

    MultipleBooleanProperty getNameTagElements() {
        return nameTagElements;
    }

    MultipleBooleanProperty getNameTagIndicators() {
        return nameTagIndicators;
    }

    boolean getHealthBarStroke() {
        return healthBarStroke.getValue() && getHealthBar();
    }

    boolean getHealthBar() {
        return healthBar.getValue();
    }

    boolean getBoxStroke() {
        return boxStroke.getValue() && getBox();
    }

    boolean getBox() {
        return box.getValue();
    }
}
