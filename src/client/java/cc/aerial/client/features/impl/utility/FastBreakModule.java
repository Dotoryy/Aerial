package cc.aerial.client.features.impl.utility;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;

public final class FastBreakModule extends Module {
    public static final FastBreakModule INSTANCE = new FastBreakModule();

    public enum Mode {
        PERCENTAGE("Percentage"),
        TICKS("Ticks");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.TICKS);
    private final NumberProperty speed = new NumberProperty("Speed", 50, 0, 100, 1)
            .hideIf(() -> this.mode.getValue() != Mode.PERCENTAGE);
    private final NumberProperty ticks = new NumberProperty("Ticks", 1, 1, 100, 1)
            .hideIf(() -> this.mode.getValue() != Mode.TICKS);
    private final BooleanProperty ignoringMiningFatigue = new BooleanProperty("Ignoring Mining Fatigue", false);
    private final BooleanProperty equalAirGroundDig = new BooleanProperty("Equal Air Ground Dig", true);

    private int offGroundTicks;

    private FastBreakModule() {
        super("Fast Break", "Speeds up block breaking", ModuleCategory.UTILITY);
        addProperties(mode, speed, ticks, ignoringMiningFatigue, equalAirGroundDig);
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    @Override
    protected void onDisable() {
        offGroundTicks = 0;
    }

    public Mode getMode() {
        return mode.getValue();
    }

    public int getSpeed() {
        return speed.getValue().intValue();
    }

    public int getTicks() {
        return ticks.getValue().intValue();
    }

    public boolean isIgnoringMiningFatigue() {
        return ignoringMiningFatigue.getValue();
    }

    public boolean isEqualAirGroundDig() {
        return equalAirGroundDig.getValue();
    }

    public int tickOffGround(boolean onGround) {
        offGroundTicks = onGround ? 0 : offGroundTicks + 1;
        return offGroundTicks;
    }
}
