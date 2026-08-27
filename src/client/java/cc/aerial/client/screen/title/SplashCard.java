package cc.aerial.client.screen.title;

import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.screen.server.LoadingCard;
import cc.aerial.client.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class SplashCard {
    private static final int ACCENT_DARK = 0xFF68478D;
    private static final int ACCENT_LIGHT = 0xFFB1A2CA;
    private static final int BASE_TOP = 0xFF0B0A12;
    private static final int BASE_BOTTOM = 0xFF06060A;

    private static final String NOTICE = "Copyright Mojang AB. Do not distribute!";
    private static final float NOTICE_SIZE = 8.0f;
    private static final float NOTICE_MARGIN = 8.0f;
    private static final int NOTICE_COLOR = 0x8A8C96;

    private static final float WORDMARK_SIZE = 34.0f;
    private static final float BAR_WIDTH = 150.0f;
    private static final float BAR_GAP = 18.0f;

    private static volatile AerialFont font;
    private static boolean fontRequested;

    private SplashCard() {
    }

    public static void requestFont() {
        if (fontRequested) {
            return;
        }
        fontRequested = true;
        Thread loader = new Thread(
                () -> font = AerialFont.createFromResource("OpalProductSansBold.ttf"),
                "Aerial Splash Font");
        loader.setDaemon(true);
        loader.start();
    }

    public static int backgroundColor() {
        return BASE_TOP;
    }

    public static void draw(GuiGraphicsExtractor extractor, int width, int height,
                            float progress, float alpha) {
        int fade = Math.round(Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f) << 24;
        if (fade == 0) {
            return;
        }

        requestFont();

        RenderUtil.flatRectGradient(extractor, 0.0f, 0.0f, width, height,
                fade | (BASE_TOP & 0xFFFFFF), fade | (BASE_BOTTOM & 0xFFFFFF), null);

        float blockHeight = WORDMARK_SIZE + BAR_GAP + 2.5f;
        float top = (height - blockHeight) * 0.5f;

        float shown = progress > 0.005f ? progress : -1.0f;
        LoadingCard.drawBar(extractor, (width - BAR_WIDTH) * 0.5f, BAR_WIDTH,
                top + WORDMARK_SIZE + BAR_GAP, shown, fade | (ACCENT_LIGHT & 0xFFFFFF));

        AerialFont loaded = font;
        if (loaded == null) {
            return;
        }

        String wordmark = "aerial";
        TextRenderUtil.drawGradientString(extractor, loaded, wordmark,
                (width - loaded.stringWidth(wordmark, WORDMARK_SIZE)) * 0.5f, top, WORDMARK_SIZE,
                fade | (ACCENT_DARK & 0xFFFFFF), fade | (ACCENT_LIGHT & 0xFFFFFF));

        TextRenderUtil.drawString(extractor, loaded, NOTICE,
                width - NOTICE_MARGIN - loaded.stringWidth(NOTICE, NOTICE_SIZE), NOTICE_MARGIN,
                NOTICE_SIZE, fade | NOTICE_COLOR);
    }
}
