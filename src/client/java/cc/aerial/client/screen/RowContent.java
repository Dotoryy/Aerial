package cc.aerial.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;

interface RowContent {
    float measure(float x, float y, float width, double mouseX, double mouseY);

    void draw(GuiGraphicsExtractor extractor, float x, float y, float width, float rowAlpha,
              int mouseX, int mouseY, ScreenRectangle scissor, boolean isLastRow);

    boolean mouseClicked(float x, float y, float width, double mouseX, double mouseY, int button);

    default void mouseReleased(int button) {
    }
}
