package cc.aerial.client.features.impl.visual;

import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.hud.BreakProgressBar;
import cc.aerial.client.features.impl.hud.DynamicIsland;
import cc.aerial.client.features.impl.hud.RiseCapsuleModule;
import cc.aerial.client.property.ActionProperty;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.GroupProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.theme.Theme;
import cc.aerial.client.theme.ThemeScreen;
import net.minecraft.client.Minecraft;

public final class InterfaceModule extends Module {
    public static final InterfaceModule INSTANCE = new InterfaceModule();

    private final ModeProperty<Theme> theme =
            new ModeProperty<Theme>("Theme", Theme.BLEND).hideIf(() -> true);
    private final ActionProperty themePicker = new ActionProperty("Theme",
            () -> Minecraft.getInstance().setScreenAndShow(new ThemeScreen(Minecraft.getInstance().gui.screen())));
    private final NumberProperty fadeSpeed = new NumberProperty("Fade speed", 1.0, 0.1, 5.0, 0.1);

    private final BooleanProperty watermark = new BooleanProperty("Watermark", true);
    private final ModeProperty<WatermarkMode> watermarkMode =
            new ModeProperty<>("Watermark Mode", WatermarkMode.DYNAMIC_ISLAND);
    private final GroupProperty watermarkGroup =
            new GroupProperty("Watermark Settings", watermarkMode)
                    .hideIf(() -> !watermark.getValue());

    private final BooleanProperty arraylist = new BooleanProperty("Array List", true);
    private final ModeProperty<ArraylistModule.Mode> arraylistMode =
            new ModeProperty<>("Mode", ArraylistModule.Mode.AERIAL);
    private final ModeProperty<ArraylistModule.FpsSaverFont> arraylistFpsSaverFont =
            new ModeProperty<>("Font", ArraylistModule.FpsSaverFont.VANILLA)
                    .hideIf(() -> arraylistMode.getValue() != ArraylistModule.Mode.FPS_SAVER);

    private final NumberProperty arraylistFpsSaverScale =
            new NumberProperty("Scale", 1.0, 0.25, 1.5, 0.05)
                    .hideIf(() -> arraylistMode.getValue() != ArraylistModule.Mode.FPS_SAVER);
    private final BooleanProperty arraylistSuffix = new BooleanProperty("Suffix", true);
    private final BooleanProperty arraylistLowercase = new BooleanProperty("Lowercase", false);

    private final BooleanProperty arraylistRemoveSpaces = new BooleanProperty("Remove spaces", true);
    private final BooleanProperty arraylistSidebar = new BooleanProperty("Sidebar", true);
    private final BooleanProperty arraylistDropShadow = new BooleanProperty("Drop shadow", true);
    private final ModeProperty<ArraylistModule.Background> arraylistBackground =
            new ModeProperty<>("Background", ArraylistModule.Background.NORMAL);

    private final BooleanProperty arraylistBlur = new BooleanProperty("Blur", false)
            .hideIf(() -> arraylistMode.getValue() != ArraylistModule.Mode.AERIAL);
    private final ModeProperty<ArraylistModule.ColorMode> arraylistColorMode =
            new ModeProperty<>("Color mode", ArraylistModule.ColorMode.FADE);

    private final NumberProperty arraylistX = new NumberProperty("Array List X", 0.0, -10000.0, 10000.0, 1.0).hideIf(() -> true);
    private final NumberProperty arraylistY = new NumberProperty("Array List Y", 0.0, -10000.0, 10000.0, 1.0).hideIf(() -> true);
    private final GroupProperty arraylistGroup =
            new GroupProperty("Array List Settings", arraylistMode, arraylistFpsSaverFont,
                    arraylistFpsSaverScale, arraylistSuffix, arraylistLowercase,
                    arraylistRemoveSpaces, arraylistSidebar, arraylistDropShadow,
                    arraylistBackground, arraylistBlur, arraylistColorMode)
                    .hideIf(() -> !arraylist.getValue());

    private InterfaceModule() {
        super("Interface", "", ModuleCategory.VISUAL);
        addProperties(theme, themePicker, fadeSpeed,
                watermark, watermarkGroup,
                arraylist, arraylistGroup, arraylistX, arraylistY);
        setEnabled(true);
    }

    public enum WatermarkMode {
        DYNAMIC_ISLAND("Dynamic Island"),
        CAPSULE("Capsule");

        private final String label;

        WatermarkMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        if (watermark.getValue()) {
            switch (watermarkMode.getValue()) {
                case DYNAMIC_ISLAND -> DynamicIsland.INSTANCE.render(event);
                case CAPSULE -> {
                    RiseCapsuleModule.INSTANCE.render(event);

                    BreakProgressBar.render(event);
                }
            }
        }
        if (arraylist.getValue()) {
            ArraylistModule.INSTANCE.render(event);
        }
    }

    public boolean isWatermarkEnabled() {
        return isEnabled() && watermark.getValue();
    }

    public WatermarkMode getWatermarkMode() {
        return watermarkMode.getValue();
    }

    public boolean isArraylistEnabled() {
        return isEnabled() && arraylist.getValue();
    }

    public ArraylistModule.Mode getArraylistMode() {
        return arraylistMode.getValue();
    }

    public ArraylistModule.FpsSaverFont getArraylistFpsSaverFont() {
        return arraylistFpsSaverFont.getValue();
    }

    public float getArraylistFpsSaverScale() {
        return arraylistFpsSaverScale.getValue().floatValue();
    }

    public boolean isArraylistSuffix() {
        return arraylistSuffix.getValue();
    }

    public boolean isArraylistLowercase() {
        return arraylistLowercase.getValue();
    }

    public boolean isArraylistRemoveSpaces() {
        return arraylistRemoveSpaces.getValue();
    }

    public boolean isArraylistSidebar() {
        return arraylistSidebar.getValue();
    }

    public boolean isArraylistDropShadow() {
        return arraylistDropShadow.getValue();
    }

    public ArraylistModule.Background getArraylistBackground() {
        return arraylistBackground.getValue();
    }

    public boolean isArraylistBlur() {
        return arraylistBlur.getValue();
    }

    public ArraylistModule.ColorMode getArraylistColorMode() {
        return arraylistColorMode.getValue();
    }

    public float getArraylistOffsetX() {
        return arraylistX.getValue().floatValue();
    }

    public float getArraylistOffsetY() {
        return arraylistY.getValue().floatValue();
    }

    public void setArraylistOffset(float x, float y) {
        arraylistX.setValue((double) x);
        arraylistY.setValue((double) y);
    }

    public Theme getTheme() {
        return theme.getValue();
    }

    public void setTheme(Theme value) {
        theme.setValue(value);
    }

    public double getFadeSpeed() {
        return fadeSpeed.getValue();
    }
}
