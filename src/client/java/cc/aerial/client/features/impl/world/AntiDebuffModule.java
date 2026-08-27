package cc.aerial.client.features.impl.world;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;

public final class AntiDebuffModule extends Module {
    public static final AntiDebuffModule INSTANCE = new AntiDebuffModule();

    private final BooleanProperty blindness = new BooleanProperty("Blindness", true);
    private final BooleanProperty nausea = new BooleanProperty("Nausea", true);

    private AntiDebuffModule() {
        super("Anti Debuff", "Ignores blindness and nausea", ModuleCategory.WORLD);
        addProperties(blindness, nausea);
    }

    public boolean isBlindnessRemoved() {
        return isEnabled() && blindness.getValue();
    }

    public boolean isNauseaRemoved() {
        return isEnabled() && nausea.getValue();
    }
}
