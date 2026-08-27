package cc.aerial.client.features.impl.visual;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.MultipleBooleanProperty;

public final class VanillaFixModule extends Module {
    public static final VanillaFixModule INSTANCE = new VanillaFixModule();

    private final BooleanProperty disableFog = new BooleanProperty("Disable Fog", false);
    private final BooleanProperty disableVanillaNameTags = new BooleanProperty("Disable Vanilla NameTags", false);

    private final BooleanProperty itemPhysics = new BooleanProperty("Item Physics", false);
    private final BooleanProperty removeBlur = new BooleanProperty("Remove Blur", false);
    private final BooleanProperty removeWeather = new BooleanProperty("Remove Weather", false);
    private final BooleanProperty removeParticles = new BooleanProperty("Remove All Particles", false);
    private final MultipleBooleanProperty options = new MultipleBooleanProperty("Options", disableFog, disableVanillaNameTags, itemPhysics, removeBlur, removeWeather, removeParticles);

    private final BooleanProperty ignoreHitParticles = new BooleanProperty("Ignore Hit Particles", true)
            .hideIf(() -> !removeParticles.getValue());

    private VanillaFixModule() {
        super("Vanilla Fix", "Tweaks specific vanilla rendering behavior", ModuleCategory.VISUAL);
        addProperties(options, ignoreHitParticles);
    }

    public boolean isFogDisabled() {
        return isEnabled() && disableFog.getValue();
    }

    public boolean isVanillaNameTagsDisabled() {
        return isEnabled() && disableVanillaNameTags.getValue();
    }

    public boolean isItemPhysicsEnabled() {
        return isEnabled() && itemPhysics.getValue();
    }

    public boolean isBlurRemoved() {
        return isEnabled() && removeBlur.getValue();
    }

    public boolean isWeatherRemoved() {
        return isEnabled() && removeWeather.getValue();
    }

    public boolean areParticlesRemoved() {
        return isEnabled() && removeParticles.getValue();
    }

    public boolean areHitParticlesKept() {
        return ignoreHitParticles.getValue();
    }
}
