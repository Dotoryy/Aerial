package cc.aerial.client.features.impl.visual;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.NumberProperty;

public final class ScoreboardModule extends Module {
    public static final ScoreboardModule INSTANCE = new ScoreboardModule();

    private final BooleanProperty enabled = new BooleanProperty("Enabled", true);
    private final BooleanProperty textShadow = new BooleanProperty("Text Shadow", true).hideIf(() -> !enabled.getValue());
    private final NumberProperty scale = new NumberProperty("Scale", 1.0, 0.5, 2.0, 0.05).hideIf(() -> !enabled.getValue());
    private final BooleanProperty avoidArraylist = new BooleanProperty("Avoid Arraylist", true).hideIf(() -> !enabled.getValue());
    private final BooleanProperty removeBackground = new BooleanProperty("Remove Background", false).hideIf(() -> !enabled.getValue());

    private final BooleanProperty customFont = new BooleanProperty("Custom Font", false).hideIf(() -> !enabled.getValue());

    private ScoreboardModule() {
        super("Scoreboard", "Tweaks the vanilla scoreboard sidebar", ModuleCategory.VISUAL);
        addProperties(enabled, textShadow, scale, avoidArraylist, removeBackground, customFont);
    }

    public boolean isScoreboardEnabled() {
        return !isEnabled() || enabled.getValue();
    }

    public boolean isTextShadow() {
        return !isEnabled() || textShadow.getValue();
    }

    public float getScale() {
        return isEnabled() ? scale.getValue().floatValue() : 1.0F;
    }

    public boolean isAvoidingArraylist() {
        return isEnabled() && avoidArraylist.getValue();
    }

    public boolean isBackgroundRemoved() {
        return isEnabled() && removeBackground.getValue();
    }

    public boolean isCustomFont() {
        return isEnabled() && enabled.getValue() && customFont.getValue();
    }
}
