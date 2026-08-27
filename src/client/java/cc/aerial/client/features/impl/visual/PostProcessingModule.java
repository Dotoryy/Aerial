package cc.aerial.client.features.impl.visual;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.GroupProperty;
import cc.aerial.client.property.MultipleBooleanProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.render.BlurConsumer;
import org.jetbrains.annotations.Nullable;

public final class PostProcessingModule extends Module {
    public static final PostProcessingModule INSTANCE = new PostProcessingModule();

    private final BooleanProperty blur = new BooleanProperty("Enabled", true);
    private final NumberProperty blurRadius = new NumberProperty("Radius", 7, 1, 20, 1);

    private final BooleanProperty[][] chips =
            new BooleanProperty[BlurMode.VALUES.length][BlurConsumer.VALUES.length];

    private final MultipleBooleanProperty gaussianTargets;
    private final MultipleBooleanProperty kawaseTargets;
    private final MultipleBooleanProperty riseTargets;

    private final BooleanProperty bloom = new BooleanProperty("Enabled", true);
    private final NumberProperty bloomRadius = new NumberProperty("Radius", 7, 1, 20, 1);

    private PostProcessingModule() {
        super("Post Processing", "Allows you to configure post processing effects", ModuleCategory.VISUAL);
        setEnabled(true);

        gaussianTargets = buildTargets(BlurMode.GAUSSIAN, BlurConsumer.ARRAYLIST,
                BlurConsumer.SCOREBOARD, BlurConsumer.DYNAMIC_ISLAND, BlurConsumer.CLICK_GUI,
                BlurConsumer.NOTIFICATION, BlurConsumer.OVERLAY, BlurConsumer.CHAT,
                BlurConsumer.SCAFFOLD_COUNTER);
        kawaseTargets = buildTargets(BlurMode.KAWASE, BlurConsumer.TARGET_HUD);
        riseTargets = buildTargets(BlurMode.RISE, BlurConsumer.RISE_CAPSULE);

        addProperties(
                new GroupProperty("Blur", blur, blurRadius, gaussianTargets, kawaseTargets, riseTargets),
                new GroupProperty("Bloom", bloom, bloomRadius).hideIf(() -> !blur.getValue()));
    }

    private MultipleBooleanProperty buildTargets(BlurMode mode, BlurConsumer... enabledFor) {
        BooleanProperty[] row = chips[mode.ordinal()];
        for (BlurConsumer consumer : BlurConsumer.VALUES) {
            boolean on = false;
            for (BlurConsumer candidate : enabledFor) {
                if (candidate == consumer) {
                    on = true;
                    break;
                }
            }
            row[consumer.ordinal()] = new BooleanProperty(consumer.getLabel(), on);
        }
        return new MultipleBooleanProperty(mode.toString(), row);
    }

    public boolean isBlur() {
        return isEnabled() && blur.getValue();
    }

    public boolean isBloom() {
        return isBlur() && bloom.getValue();
    }

    @Nullable
    public BlurMode getMode(BlurConsumer consumer) {
        int index = consumer.ordinal();
        for (BlurMode mode : BlurMode.VALUES) {
            if (chips[mode.ordinal()][index].getValue()) {
                return mode;
            }
        }
        return null;
    }

    public int getBlurRadius() {
        return blurRadius.getValue().intValue();
    }

    public int getBloomRadius() {
        return bloomRadius.getValue().intValue();
    }

    public BlurMode getBloomMode() {
        return BlurMode.GAUSSIAN;
    }

    public enum BlurMode {
        GAUSSIAN("Gaussian"),
        KAWASE("Kawase"),
        RISE("Rise");

        public static final BlurMode[] VALUES = values();

        private final String label;

        BlurMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
