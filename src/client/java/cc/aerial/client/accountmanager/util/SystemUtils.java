package cc.aerial.client.accountmanager.util;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.net.URI;

public final class SystemUtils {
    private SystemUtils() {
    }

    public static void openWebLink(URI url) {
        try {
            Util.getPlatform().openUri(url);
        } catch (Exception ignored) {
        }
    }

    public static void setClipboard(String text) {
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
        } catch (Exception ignored) {
        }
    }
}
