package cc.aerial.client.features.impl.hud;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public interface IslandTrigger extends Comparable<IslandTrigger> {
    void renderIsland(GuiGraphicsExtractor extractor, float x, float y, float width, float height, float progress);

    float getIslandWidth();

    float getIslandHeight();

    default int getIslandPriority() {
        return 0;
    }

    @Override
    default int compareTo(IslandTrigger other) {
        return Integer.compare(other.getIslandPriority(), getIslandPriority());
    }
}
