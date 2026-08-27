package cc.aerial.client.features.impl.world;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.NumberProperty;

public final class TimerModule extends Module {
    public static final TimerModule INSTANCE = new TimerModule();

    private final NumberProperty gameSpeed = new NumberProperty("Game speed", 2.0, 0.05, 10.0, 0.05);

    private TimerModule() {
        super("Timer", "Modifies your game speed.", ModuleCategory.WORLD);
        addProperties(gameSpeed);
    }

    private static float requested;

    public static void request(float multiplier) {
        requested = multiplier;
    }

    public static void clearRequest() {
        requested = 0.0f;
    }

    public float getTimer() {
        if (requested > 0.0f) {
            return requested;
        }
        return isEnabled() ? gameSpeed.getValue().floatValue() : 1.0f;
    }
}
