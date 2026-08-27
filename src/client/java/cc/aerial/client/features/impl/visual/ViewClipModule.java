package cc.aerial.client.features.impl.visual;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import net.minecraft.client.Minecraft;

public final class ViewClipModule extends Module {
    public static final ViewClipModule INSTANCE = new ViewClipModule();

    private ViewClipModule() {
        super("View Clip", "Sees through walls in third person", ModuleCategory.VISUAL);
    }

    @Override
    protected void onEnable() {
        rebuildChunks();
    }

    @Override
    protected void onDisable() {
        rebuildChunks();
    }

    private static void rebuildChunks() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        mc.levelRenderer.invalidateCompiledGeometry(
                mc.level, mc.options, mc.gameRenderer.mainCamera(), mc.getBlockColors());
    }
}
