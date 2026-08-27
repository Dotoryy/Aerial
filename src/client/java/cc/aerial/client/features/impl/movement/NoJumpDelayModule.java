package cc.aerial.client.features.impl.movement;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.NumberProperty;

public final class NoJumpDelayModule extends Module {
    public static final NoJumpDelayModule INSTANCE = new NoJumpDelayModule();

    private final NumberProperty maxCooldown = new NumberProperty("Max cooldown", 0, 0, 9, 1);

    private NoJumpDelayModule() {
        super("No Jump Delay", "Modifies the vanilla jump cooldown.", ModuleCategory.MOVEMENT);
        addProperties(maxCooldown);
    }

    public int getMaxCooldown() {
        return maxCooldown.getValue().intValue();
    }
}
