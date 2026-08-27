package cc.aerial.client.theme;

import java.awt.Color;
import java.util.function.BiFunction;

public enum Theme {
    AUBERGINE("Aubergine", new Color(170, 7, 107), new Color(97, 4, 95)),
    AQUA("Aqua", new Color(185, 250, 255), new Color(79, 199, 200)),
    BANANA("Banana", new Color(253, 236, 177), new Color(255, 255, 255)),
    BLEND("Blend", new Color(71, 148, 253), new Color(71, 253, 160)),
    BLOSSOM("Blossom", new Color(226, 208, 249), new Color(49, 119, 115)),
    BUBBLEGUM("Bubblegum", new Color(243, 145, 216), new Color(152, 165, 243)),
    CANDY_CANE("Candy Cane", new Color(255, 0, 0), new Color(255, 255, 255)),
    CHERRY("Cherry", new Color(187, 55, 125), new Color(251, 211, 233)),
    CHRISTMAS("Christmas", new Color(255, 64, 64), new Color(255, 255, 255), new Color(64, 255, 64)),
    CORAL("Coral", new Color(244, 168, 150), new Color(52, 133, 151)),
    DIGITAL_HORIZON("Digital Horizon", new Color(95, 195, 228), new Color(229, 93, 135)),
    EXPRESS("Express", new Color(173, 83, 137), new Color(60, 16, 83)),
    LIME_WATER("Lime Water", new Color(18, 255, 247), new Color(179, 255, 171)),
    LUSH("Lush", new Color(168, 224, 99), new Color(86, 171, 47)),
    HALOGEN("Halogen", new Color(255, 65, 108), new Color(255, 75, 43)),
    HYPER("Hyper", new Color(236, 110, 173), new Color(52, 148, 230)),
    MAGIC("Magic", new Color(74, 0, 224), new Color(142, 45, 226)),
    MAY("May", new Color(238, 79, 238), new Color(253, 219, 245)),
    ORANGE_JUICE("Orange Juice", new Color(252, 74, 26), new Color(247, 183, 51)),
    PASTEL("Pastel", new Color(243, 155, 178), new Color(207, 196, 243)),
    PUMPKIN("Pumpkin", new Color(241, 166, 98), new Color(255, 216, 169), new Color(227, 139, 42)),
    SATIN("Satin", new Color(215, 60, 67), new Color(140, 23, 39)),
    SNOWY_SKY("Snowy Sky", new Color(1, 171, 179), new Color(234, 234, 234), new Color(18, 232, 232)),
    STEEL_FADE("Steel Fade", new Color(66, 134, 244), new Color(55, 59, 68)),
    SUNDAE("Sundae", new Color(206, 74, 126), new Color(122, 44, 77)),
    SUNKIST("Sunkist", new Color(242, 201, 76), new Color(242, 153, 74)),
    WATER("Water", new Color(12, 232, 199), new Color(12, 163, 232)),
    LEGACY("Legacy", new Color(0x70CEFF), new Color(0x70CEFF)),
    WINTER("Winter", Color.WHITE, Color.WHITE),
    PEONY("Peony", new Color(226, 208, 249), new Color(207, 171, 255)),
    SHADOW("Shadow", new Color(97, 131, 255), new Color(206, 212, 255)),
    WOOD("Wood", new Color(79, 109, 81), new Color(170, 139, 87), new Color(240, 235, 206)),

    CREIDA("Creida", new Color(0xff4e5270).brighter().brighter(), new Color(0xff4e5270).darker()),
    CREIDA_TWO("Creida Two", new Color(0xff9ACAEB), new Color(0xff7FBBE6).darker()),
    GOTHIC("Gothic", new Color(31, 30, 30), new Color(196, 190, 190)),
    RUE("Rue", new Color(234, 118, 176), new Color(31, 30, 30)),
    PURPLE("Purple", new Color(0x524391), new Color(0x524391).brighter()),

    OPAL("Opal", new Color(45, 191, 254), new Color(36, 153, 203)),
    SPEARMINT("Spearmint", new Color(97, 194, 162), new Color(65, 130, 108)),
    JADE_GREEN("Jade Green", new Color(0, 168, 107), new Color(0, 105, 66)),
    GREEN_SPIRIT("Green Spirit", new Color(159, 226, 191), new Color(0, 135, 62)),
    ROSY_PINK("Rosy Pink", new Color(255, 102, 204), new Color(191, 77, 153)),
    MAGENTA("Magenta", new Color(213, 63, 119), new Color(157, 68, 110)),
    HOT_PINK("Hot Pink", new Color(231, 84, 128), new Color(172, 79, 198)),
    LAVENDER("Lavender", new Color(219, 166, 247), new Color(152, 115, 172)),
    AMETHYST("Amethyst", new Color(144, 99, 205), new Color(98, 67, 140)),
    PURPLE_FIRE("Purple Fire", new Color(177, 162, 202), new Color(104, 71, 141)),
    SUNSET_PINK("Sunset Pink", new Color(255, 145, 20), new Color(245, 105, 231)),
    BLAZE_ORANGE("Blaze Orange", new Color(255, 169, 77), new Color(255, 130, 0)),
    PINK_BLOOD("Pink Blood", new Color(255, 166, 201), new Color(228, 0, 70)),

    PASTEL_RED("Pastel Red", new Color(255, 109, 106), new Color(191, 82, 80)),
    NEON_RED("Neon Red", new Color(210, 39, 48), new Color(184, 25, 42)),
    RED_COFFEE("Red Coffee", new Color(225, 34, 59), new Color(75, 19, 19)),
    DEEP_OCEAN("Deep Ocean", new Color(60, 82, 145), new Color(0, 20, 64)),
    CHAMBRAY_BLUE("Chambray Blue", new Color(60, 82, 145), new Color(33, 46, 182)),
    MINT_BLUE("Mint Blue", new Color(66, 158, 157), new Color(40, 94, 93)),
    PACIFIC_BLUE("Pacific Blue", new Color(5, 169, 199), new Color(4, 115, 135)),
    TROPICAL_ICE("Tropical Ice", new Color(102, 255, 209), new Color(6, 149, 255)),
    BLUE_PURPLE("Blue Purple", new Color(104, 77, 178), new Color(4, 60, 174)),

    RAINBOW("Rainbow", (x, y) -> ColorUtil.rainbow((int) ((x + y) * 10)));

    private final String themeName;
    private Color firstColor, secondColor, thirdColor;
    private final BiFunction<Double, Double, Color> custom;
    private final boolean triColor;

    Theme(String themeName, Color firstColor, Color secondColor) {
        this.themeName = themeName;
        this.firstColor = this.thirdColor = firstColor;
        this.secondColor = secondColor;
        this.custom = null;
        this.triColor = false;
    }

    Theme(String themeName, Color firstColor, Color secondColor, Color thirdColor) {
        this.themeName = themeName;
        this.firstColor = firstColor;
        this.secondColor = secondColor;
        this.thirdColor = thirdColor;
        this.custom = null;
        this.triColor = true;
    }

    Theme(String themeName, BiFunction<Double, Double, Color> custom) {
        this.themeName = themeName;
        this.custom = custom;
        this.triColor = true;
    }

    @Override
    public String toString() {
        return themeName;
    }

    public String getThemeName() {
        return themeName;
    }

    public boolean isTriColor() {
        return triColor;
    }

    public Color getFirstColor() {
        return custom == null ? firstColor : getAccentColor(0, 0);
    }

    public Color getSecondColor() {
        return custom == null ? secondColor : getAccentColor(0, 50);
    }

    public Color getThirdColor() {
        return custom == null ? thirdColor : getAccentColor(0, 100);
    }

    public Color getAccentColor(double x, double y) {
        if (this.custom != null) {
            return custom.apply(x, y);
        }

        if (this.triColor) {
            double blendFactor = this.getBlendFactor(x, y);
            if (blendFactor <= 0.5) {
                return ColorUtil.mixColors(getSecondColor(), getFirstColor(), blendFactor * 2.0);
            }
            return ColorUtil.mixColors(getThirdColor(), getSecondColor(), (blendFactor - 0.5) * 2.0);
        }

        return ColorUtil.mixColors(getFirstColor(), getSecondColor(), getBlendFactor(x, y));
    }

    public Color getAccentColor() {
        return getAccentColor(0.0, 0.0);
    }

    public int getAccentRgb(double x, double y) {
        if (this.custom != null) {
            return custom.apply(x, y).getRGB();
        }

        if (this.triColor) {
            double blendFactor = this.getBlendFactor(x, y);
            if (blendFactor <= 0.5) {
                return ColorUtil.mixRgb(getSecondColor(), getFirstColor(), blendFactor * 2.0);
            }
            return ColorUtil.mixRgb(getThirdColor(), getSecondColor(), (blendFactor - 0.5) * 2.0);
        }

        return ColorUtil.mixRgb(getFirstColor(), getSecondColor(), getBlendFactor(x, y));
    }

    public Color getDropShadow() {
        return new Color(0, 0, 0, 190);
    }

    public float getPadding() {
        return 4.5f;
    }

    public double getBlendFactor(double x, double y) {
        double period = 600.0D / ThemeManager.getFadeSpeed();
        return Math.sin(System.currentTimeMillis() / period + x * 0.005D + y * 0.06D) * 0.5D + 0.5D;
    }
}
