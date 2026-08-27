package cc.aerial.client.utility;

import cc.aerial.client.features.impl.visual.InterfaceModule;
import cc.aerial.client.theme.ColorUtil;
import cc.aerial.client.theme.Theme;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;

import java.awt.Color;

public final class ThemeText {
    private static final int SENTINEL_MASK = 0xFFF0F0;
    private static final int SENTINEL_BASE = 0xFE0000;

    private static final int MAX_LENGTH = 16;

    private ThemeText() {
    }

    public static MutableComponent gradient(String text) {
        MutableComponent out = Component.empty();
        if (text.length() > MAX_LENGTH) {
            int flat = accentAt(0, 1);
            return out.append(Component.literal(text)
                    .withStyle(style -> style.withColor(TextColor.fromRgb(flat))));
        }
        for (int i = 0; i < text.length(); i++) {
            int color = sentinel(i, text.length());
            out.append(Component.literal(String.valueOf(text.charAt(i)))
                    .withStyle(style -> style.withColor(TextColor.fromRgb(color))));
        }
        return out;
    }

    private static int sentinel(int index, int length) {
        return SENTINEL_BASE | ((length - 1) << 8) | index;
    }

    public static boolean isSentinel(int color) {
        return (color & SENTINEL_MASK) == SENTINEL_BASE;
    }

    public static int resolve(int sentinelColor) {
        int index = sentinelColor & 0x0F;
        int length = ((sentinelColor >> 8) & 0x0F) + 1;
        return accentAt(index, length);
    }

    private static int accentAt(int index, int length) {
        Theme theme = InterfaceModule.INSTANCE.getTheme();
        Color left = theme.getAccentColor(0, 50);
        Color right = theme.getAccentColor(0, 0);
        double progress = length <= 1 ? 0.0 : (double) index / (length - 1);

        return ColorUtil.mixColors(right, left, progress).getRGB() & 0xFFFFFF;
    }
}
