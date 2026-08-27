package cc.aerial.client.features.impl.movement;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

public final class StepModule extends Module {
    public static final StepModule INSTANCE = new StepModule();

    private static final double VANILLA_DEFAULT_STEP_HEIGHT = 0.6;

    public enum Mode {
        VANILLA("Vanilla"),
        MATRIX("Matrix");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.VANILLA);
    private final NumberProperty vanillaHeight = new NumberProperty("Height", 1, 1, 10, 0.1).hideIf(() -> mode.getValue() != Mode.VANILLA);
    private final BooleanProperty matrixTwoBlock = new BooleanProperty("2 Block", true).hideIf(() -> mode.getValue() != Mode.MATRIX);

    private StepModule() {
        super("Step", "Walk up blocks without jumping", ModuleCategory.MOVEMENT);
        addProperties(mode, vanillaHeight, matrixTwoBlock);
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    @Override
    protected void onDisable() {
        setStepHeight(VANILLA_DEFAULT_STEP_HEIGHT);
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        double height = switch (mode.getValue()) {
            case VANILLA -> vanillaHeight.getValue().doubleValue();
            case MATRIX -> matrixTwoBlock.getValue() ? 2.0 : 1.0;
        };
        setStepHeight(height);
    }

    private static void setStepHeight(double height) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        AttributeInstance attribute = player.getAttribute(Attributes.STEP_HEIGHT);
        if (attribute != null) {
            attribute.setBaseValue(height);
        }
    }
}
