package cc.aerial.client.features.impl.visual;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;

public final class FullBrightModule extends Module {
    public static final FullBrightModule INSTANCE = new FullBrightModule();

    private FullBrightModule() {
        super("Full Bright", "Enhances your game brightness", ModuleCategory.VISUAL);
    }
}
