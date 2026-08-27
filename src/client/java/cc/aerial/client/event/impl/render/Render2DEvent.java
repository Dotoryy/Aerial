package cc.aerial.client.event.impl.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public record Render2DEvent(GuiGraphicsExtractor extractor, float partialTick) {
    public int width() {
        return extractor.guiWidth();
    }

    public int height() {
        return extractor.guiHeight();
    }
}
