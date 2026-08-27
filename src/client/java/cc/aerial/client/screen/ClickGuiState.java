package cc.aerial.client.screen;

import cc.aerial.client.features.impl.visual.ClickGuiModule;
import net.minecraft.client.Minecraft;

public final class ClickGuiState {
    private ClickGuiState() {
    }

    public static boolean isAllowMovement() {
        return ClickGuiModule.INSTANCE.isAllowMovement();
    }

    public static boolean isRailGui() {
        return ClickGuiModule.INSTANCE.isRailLayout();
    }

    public static void toggleScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.screen() instanceof RailClickGui rail) {
            rail.requestClose();
        } else if (mc.gui.screen() instanceof AerialClickGui gui) {
            gui.requestClose();
        } else if (mc.gui.screen() == null) {
            mc.setScreenAndShow(isRailGui() ? new RailClickGui() : new AerialClickGui());
        }
    }
}
