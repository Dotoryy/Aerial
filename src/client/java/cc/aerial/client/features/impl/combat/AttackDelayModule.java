package cc.aerial.client.features.impl.combat;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.NumberProperty;

public final class AttackDelayModule extends Module {
    public static final AttackDelayModule INSTANCE = new AttackDelayModule();

    private final NumberProperty maxCooldown = new NumberProperty("Max cooldown", 0, 0, 9, 1);

    private AttackDelayModule() {
        super("Attack Delay", "", ModuleCategory.COMBAT);
        addProperties(maxCooldown);
    }

    public int getMaxCooldown() {
        return maxCooldown.getValue().intValue();
    }
}
