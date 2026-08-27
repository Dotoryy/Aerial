package cc.aerial.client.render;

import cc.aerial.client.features.impl.visual.PostProcessingModule;
import cc.aerial.client.mixin.GuiGraphicsExtractorAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class AerialBloomFilter {
    private static final Set<GuiElementRenderState> SUPPRESSED =
            Collections.newSetFromMap(new IdentityHashMap<>());

    @Nullable
    private static BlurConsumer current;

    private AerialBloomFilter() {
    }

    public static void begin(BlurConsumer consumer) {
        current = consumer;
    }

    public static void end() {
        current = null;
    }

    public static void submit(GuiGraphicsExtractor extractor, GuiElementRenderState element) {
        BlurConsumer consumer = current;
        if (consumer != null && PostProcessingModule.INSTANCE.getMode(consumer) == null) {
            SUPPRESSED.add(element);
        }
        ((GuiGraphicsExtractorAccessor) extractor).aerial$guiRenderState().addGuiElement(element);
    }

    public static boolean isSuppressed(GuiElementRenderState element) {
        return !SUPPRESSED.isEmpty() && SUPPRESSED.contains(element);
    }

    public static void endFrame() {
        if (!SUPPRESSED.isEmpty()) {
            SUPPRESSED.clear();
        }
    }
}
