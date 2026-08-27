package cc.aerial.client.theme;

import java.awt.Color;

public final class ColorUtil {
    private ColorUtil() {
    }

    public static Color darker(Color color, float factor) {
        return new Color(
                Math.max((int) (color.getRed() * factor), 0),
                Math.max((int) (color.getGreen() * factor), 0),
                Math.max((int) (color.getBlue() * factor), 0),
                color.getAlpha());
    }

    public static Color brighter(Color color, float factor) {
        int red = color.getRed();
        int green = color.getGreen();
        int blue = color.getBlue();
        int alpha = color.getAlpha();

        int i = (int) (1 / (1 - factor));
        if (red == 0 && green == 0 && blue == 0) {
            return new Color(i, i, i, alpha);
        }

        if (red > 0 && red < i) red = i;
        if (green > 0 && green < i) green = i;
        if (blue > 0 && blue < i) blue = i;

        return new Color(Math.min((int) (red / factor), 255), Math.min((int) (green / factor), 255), Math.min((int) (blue / factor), 255), alpha);
    }

    public static Color withAlpha(Color color, int alpha) {
        if (alpha == color.getAlpha()) {
            return color;
        }
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    public static int mixRgb(Color color1, Color color2, double percent) {
        double inversePercent = 1.0 - percent;
        int redPart = (int) (color1.getRed() * percent + color2.getRed() * inversePercent);
        int greenPart = (int) (color1.getGreen() * percent + color2.getGreen() * inversePercent);
        int bluePart = (int) (color1.getBlue() * percent + color2.getBlue() * inversePercent);
        return 0xFF000000 | (redPart << 16) | (greenPart << 8) | bluePart;
    }

    public static Color mixColors(Color color1, Color color2, double percent) {
        double inversePercent = 1.0 - percent;
        int redPart = (int) (color1.getRed() * percent + color2.getRed() * inversePercent);
        int greenPart = (int) (color1.getGreen() * percent + color2.getGreen() * inversePercent);
        int bluePart = (int) (color1.getBlue() * percent + color2.getBlue() * inversePercent);
        return new Color(redPart, greenPart, bluePart);
    }

    public static Color rainbow(int delay) {
        double rainbowState = Math.ceil((System.currentTimeMillis() + delay) / 10.0);
        rainbowState %= 360;
        return Color.getHSBColor((float) (rainbowState / 360.0f), 0.6f, 1f);
    }
}
