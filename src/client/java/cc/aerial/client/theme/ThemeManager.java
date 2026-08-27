package cc.aerial.client.theme;

import cc.aerial.client.features.impl.visual.InterfaceModule;

public final class ThemeManager {
    private ThemeManager() {
    }

    public static Theme getTheme() {
        return InterfaceModule.INSTANCE.getTheme();
    }

    public static void setTheme(Theme newTheme) {
        InterfaceModule.INSTANCE.setTheme(newTheme);
    }

    public static double getFadeSpeed() {
        return InterfaceModule.INSTANCE.getFadeSpeed();
    }
}
