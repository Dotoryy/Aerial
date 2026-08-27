package cc.aerial.client.screen.server;

import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.screen.title.TitleBackground;
import cc.aerial.client.theme.Theme;
import cc.aerial.client.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

public final class LoadingCard {
    private static final float CARD_WIDTH = 220.0f;
    private static final float PADDING = 16.0f;
    private static final float TITLE_SIZE = 13.0f;
    private static final float STATUS_SIZE = 8.0f;
    private static final float BAR_HEIGHT = 2.5f;
    private static final float ACTION_SIZE = 8.5f;
    private static final float TITLE_GAP = 7.0f;
    private static final float BAR_GAP = 11.0f;
    private static final float ACTION_GAP = 11.0f;

    private static final float SWEEP_WIDTH = 0.32f;
    private static final long SWEEP_PERIOD_MS = 1400L;

    private static final int CARD_COLOR = 0xE60D0D14;

    private static final int TRACK_COLOR = 0x59FFFFFF;
    private static final int STATUS_COLOR = 0xFF9698A4;
    private static final int ACTION_COLOR = 0xFFB9BAC4;

    private static AerialFont font;
    private static AerialFont boldFont;

    private LoadingCard() {
    }

    private static void ensureFonts() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
            boldFont = AerialFont.createFromResource("OpalProductSansBold.ttf");
        }
    }

    private static float cardHeight(boolean hasStatus, boolean hasAction) {
        float height = PADDING * 2 + TITLE_SIZE + BAR_GAP + BAR_HEIGHT;
        if (hasStatus) {
            height += TITLE_GAP + STATUS_SIZE;
        }
        if (hasAction) {
            height += ACTION_GAP + ACTION_SIZE;
        }
        return height;
    }

    private static float cardTop(int height, boolean hasStatus, boolean hasAction) {
        return (height - cardHeight(hasStatus, hasAction)) * 0.5f;
    }

    private static float cardLeft(int width) {
        return (width - CARD_WIDTH) * 0.5f;
    }

    public static void draw(GuiGraphicsExtractor extractor, int width, int height, String title,
                            @Nullable String status, float progress, @Nullable String action,
                            int mouseX, int mouseY) {
        ensureFonts();
        TitleBackground.draw(extractor, width, height);

        boolean hasStatus = status != null && !status.isEmpty();
        boolean hasAction = action != null;

        Theme theme = ThemeManager.getTheme();
        int accent = theme.getAccentColor(0, 0).getRGB() | 0xFF000000;
        int accentLeft = theme.getAccentColor(0, 50).getRGB() | 0xFF000000;

        float left = cardLeft(width);
        float top = cardTop(height, hasStatus, hasAction);
        RenderUtil.roundedRect(extractor, left, top, CARD_WIDTH,
                cardHeight(hasStatus, hasAction), 8.0f, CARD_COLOR);

        float cursorY = top + PADDING;
        TextRenderUtil.drawGradientString(extractor, boldFont, title,
                left + (CARD_WIDTH - boldFont.stringWidth(title, TITLE_SIZE)) * 0.5f,
                cursorY, TITLE_SIZE, accentLeft, accent);
        cursorY += TITLE_SIZE;

        if (hasStatus) {
            cursorY += TITLE_GAP;
            TextRenderUtil.drawString(extractor, font, status,
                    left + (CARD_WIDTH - font.stringWidth(status, STATUS_SIZE)) * 0.5f,
                    cursorY, STATUS_SIZE, STATUS_COLOR);
            cursorY += STATUS_SIZE;
        }

        cursorY += BAR_GAP;
        drawBar(extractor, left + PADDING, CARD_WIDTH - PADDING * 2, cursorY, progress, accent);
        cursorY += BAR_HEIGHT;

        if (hasAction) {
            cursorY += ACTION_GAP;
            float actionWidth = font.stringWidth(action, ACTION_SIZE);
            boolean hovered = isActionHovered(width, height, mouseX, mouseY, action, hasStatus);
            TextRenderUtil.drawString(extractor, font, action,
                    left + (CARD_WIDTH - actionWidth) * 0.5f, cursorY, ACTION_SIZE,
                    hovered ? 0xFFFFFFFF : ACTION_COLOR);
        }
    }

    public static void draw(GuiGraphicsExtractor extractor, int width, int height,
                            String title, float progress) {
        draw(extractor, width, height, title, null, progress, null, 0, 0);
    }

    public static void drawBar(GuiGraphicsExtractor extractor, float barLeft, float barWidth,
                               float barY, float progress, int accent) {
        RenderUtil.roundedRect(extractor, barLeft, barY, barWidth, BAR_HEIGHT, BAR_HEIGHT * 0.5f,
                TRACK_COLOR);
        if (progress >= 0.0f) {
            float filled = barWidth * Math.max(0.0f, Math.min(1.0f, progress));
            if (filled > 0.5f) {
                RenderUtil.roundedRect(extractor, barLeft, barY, filled, BAR_HEIGHT,
                        BAR_HEIGHT * 0.5f, accent);
            }
            return;
        }

        float phase = (System.currentTimeMillis() % SWEEP_PERIOD_MS) / (float) SWEEP_PERIOD_MS;
        float segment = barWidth * SWEEP_WIDTH;
        float segmentLeft = -segment + phase * (barWidth + segment);
        float clippedLeft = Math.max(0.0f, segmentLeft);
        float clippedRight = Math.min(barWidth, segmentLeft + segment);
        if (clippedRight > clippedLeft) {
            RenderUtil.roundedRect(extractor, barLeft + clippedLeft, barY,
                    clippedRight - clippedLeft, BAR_HEIGHT, BAR_HEIGHT * 0.5f, accent);
        }
    }

    public static boolean isActionHovered(int width, int height, double mouseX, double mouseY,
                                          String action, boolean hasStatus) {
        ensureFonts();
        float actionWidth = font.stringWidth(action, ACTION_SIZE);
        float left = cardLeft(width) + (CARD_WIDTH - actionWidth) * 0.5f;
        float top = cardTop(height, hasStatus, true) + PADDING + TITLE_SIZE
                + (hasStatus ? TITLE_GAP + STATUS_SIZE : 0.0f)
                + BAR_GAP + BAR_HEIGHT + ACTION_GAP;
        return mouseX >= left - 6.0f && mouseX <= left + actionWidth + 6.0f
                && mouseY >= top - 5.0f && mouseY <= top + ACTION_SIZE + 5.0f;
    }
}
