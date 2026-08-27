package cc.aerial.client.features.impl.combat;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;

public final class PiercingModule extends Module {
    public static final PiercingModule INSTANCE = new PiercingModule();

    private PiercingModule() {
        super("Piercing", "Allows you to attack players through blocks", ModuleCategory.COMBAT);
    }
}
