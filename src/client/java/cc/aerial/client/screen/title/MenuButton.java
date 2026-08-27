package cc.aerial.client.screen.title;

import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.screen.animation.Animation;
import cc.aerial.client.screen.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class MenuButton {
    public enum Weight {
        PRIMARY(28.0f, 11.0f),
        COMPACT(20.0f, 9.0f);

        final float height;
        final float textSize;

        Weight(float height, float textSize) {
            this.height = height;
            this.textSize = textSize;
        }
    }

    private static final float RADIUS = 5.0f;

    private static final int IDLE_BACKGROUND = 0x59101018;
    private static final int HOVER_BACKGROUND = 0x8C161622;
    private static final int IDLE_TEXT = 0xFFB9BAC4;
    private static final int HOVER_TEXT = 0xFFFFFFFF;

    private final String label;
    private final Runnable action;
    private final Weight weight;
    private final Animation hover = new Animation(Easing.EASE_OUT_EXPO, 220);

    private float x, y, width;

    public MenuButton(String label, Weight weight, Runnable action) {
        this.label = label;
        this.weight = weight;
        this.action = action;
    }

    public float getHeight() {
        return weight.height;
    }

    public void setBounds(float x, float y, float width) {
        this.x = x;
        this.y = y;
        this.width = width;
    }

    public boolean isInside(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + weight.height;
    }

    public void click() {
        action.run();
    }

    public void draw(GuiGraphicsExtractor extractor, AerialFont font, double mouseX, double mouseY) {
        hover.run(isInside(mouseX, mouseY) ? 1.0f : 0.0f);
        float progress = hover.getValue();

        RenderUtil.roundedRect(extractor, x, y, width, weight.height, RADIUS,
                blend(IDLE_BACKGROUND, HOVER_BACKGROUND, progress));

        float textWidth = font.stringWidth(label, weight.textSize);
        float textY = y + (weight.height - weight.textSize) * 0.5f;
        TextRenderUtil.drawString(extractor, font, label,
                x + (width - textWidth) * 0.5f, textY, weight.textSize,
                blend(IDLE_TEXT, HOVER_TEXT, progress));
    }

    private static int blend(int from, int to, float progress) {
        float t = Math.max(0.0f, Math.min(1.0f, progress));
        int out = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = (from >> shift) & 0xFF;
            int b = (to >> shift) & 0xFF;
            out |= Math.round(a + (b - a) * t) << shift;
        }
        return out;
    }
}
