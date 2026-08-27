package cc.aerial.client.features.impl.visual;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;

public final class NoHurtCameraModule extends Module {
    public static final NoHurtCameraModule INSTANCE = new NoHurtCameraModule();

    private NoHurtCameraModule() {
        super("No Hurt Camera", "Disables the camera tilt when damaged.", ModuleCategory.VISUAL);
    }
}
