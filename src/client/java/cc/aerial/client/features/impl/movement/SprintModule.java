package cc.aerial.client.features.impl.movement;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;

public final class SprintModule extends Module {
    public static final SprintModule INSTANCE = new SprintModule();

    private final BooleanProperty omniSprint = new BooleanProperty("Omnidirectional", false);
    private final BooleanProperty keepSprint = new BooleanProperty("Keep sprint", true);

    private SprintModule() {
        super("Sprint", "", ModuleCategory.MOVEMENT);
        addProperties(omniSprint, keepSprint);
        setEnabled(true);
    }

    public boolean isOmniSprint() {
        return isEnabled() && omniSprint.getValue();
    }

    public boolean isKeepSprint() {
        return keepSprint.getValue();
    }
}
