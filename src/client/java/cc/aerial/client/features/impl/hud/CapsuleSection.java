package cc.aerial.client.features.impl.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface CapsuleSection extends Comparable<CapsuleSection> {
    void renderSection(GuiGraphicsExtractor extractor, float x, float centerY, float progress);

    float getSectionWidth();

    default int getSectionPriority() {
        return 0;
    }

    @Override
    default int compareTo(CapsuleSection other) {
        return Integer.compare(other.getSectionPriority(), getSectionPriority());
    }
}
