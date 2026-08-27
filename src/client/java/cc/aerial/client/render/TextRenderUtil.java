package cc.aerial.client.render;

import cc.aerial.client.render.font.GlyphQuad;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.mixin.GuiGraphicsExtractorAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.jetbrains.annotations.Nullable;

public final class TextRenderUtil {
    private TextRenderUtil() {
    }

    public static float drawString(GuiGraphicsExtractor extractor, AerialFont font,
                                    CharSequence text, float x, float y, float size, int color) {
        return drawString(extractor, font, text, x, y, size, color, null);
    }

    public static float drawStringWithShadow(GuiGraphicsExtractor extractor, AerialFont font,
                                             CharSequence text, float x, float y, float size,
                                             int color) {
        return drawStringWithShadow(extractor, font, text, x, y, size, color,
                shadowOffset(size), null);
    }

    private static float shadowOffset(float size) {
        return Math.max(0.5f, size * 0.125f);
    }

    public static float drawStringWithShadow(GuiGraphicsExtractor extractor, AerialFont font,
                                             CharSequence text, float x, float y, float size,
                                             int color, float offset,
                                             @Nullable ScreenRectangle scissorArea) {
        if (text.length() == 0 || !AerialPipelines.ready()) {
            return 0.0f;
        }
        int shadow = ((color & 0xFCFCFC) >> 2) | (color & 0xFF000000);
        drawString(extractor, font, text, x + offset, y + offset, size, shadow, scissorArea);
        return drawString(extractor, font, text, x, y, size, color, scissorArea);
    }

    public static float drawString(GuiGraphicsExtractor extractor, AerialFont font,
                                    CharSequence text, float x, float y, float size, int color,
                                    @Nullable ScreenRectangle scissorArea) {
        if (text.length() == 0 || !AerialPipelines.ready()) {
            return 0.0f;
        }

        GlyphQuad[] glyphs = font.layout(text, x, y, size);
        if (glyphs.length > 0) {
            ((GuiGraphicsExtractorAccessor) extractor).aerial$guiRenderState().addGuiElement(
                    TextRenderState.of(extractor.pose(), glyphs, color,
                            font.textureView(size), font.sampler(size), scissorArea));
        }

        return font.stringWidth(text, size);
    }

    public static float drawGradientString(GuiGraphicsExtractor extractor, AerialFont font,
                                            CharSequence text, float x, float y, float size,
                                            int colorLeft, int colorRight) {
        return drawGradientString(extractor, font, text, x, y, size, colorLeft, colorRight, null);
    }

    public static float drawGradientString(GuiGraphicsExtractor extractor, AerialFont font,
                                            CharSequence text, float x, float y, float size,
                                            int colorLeft, int colorRight, @Nullable ScreenRectangle scissorArea) {
        if (text.length() == 0 || !AerialPipelines.ready()) {
            return 0.0f;
        }

        GlyphQuad[] glyphs = font.layout(text, x, y, size);
        if (glyphs.length > 0) {
            ((GuiGraphicsExtractorAccessor) extractor).aerial$guiRenderState().addGuiElement(
                    GradientTextRenderState.of(extractor.pose(), glyphs, colorLeft, colorRight,
                            font.textureView(size), font.sampler(size), scissorArea));
        }

        return font.stringWidth(text, size);
    }
}
