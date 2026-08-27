package cc.aerial.client.render;

import net.minecraft.resources.Identifier;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.Minecraft;
import cc.aerial.client.mixin.GuiGraphicsExtractorAccessor;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.ColoredRectangleRenderState;
import org.jetbrains.annotations.Nullable;

public final class RenderUtil {
    private RenderUtil() {
    }

    public static void roundedRect(GuiGraphicsExtractor extractor,
                                   float x, float y, float width, float height,
                                   float radius, int color) {
        roundedRect(extractor, x, y, width, height, radius, color, null);
    }

    public static void sharpRect(GuiGraphicsExtractor extractor,
                                 float x0, float y0, float x1, float y1, int color) {
        sharpRect(extractor, x0, y0, x1, y1, color, null);
    }

    public static void sharpRect(GuiGraphicsExtractor extractor,
                                 float x0, float y0, float x1, float y1, int color,
                                 @Nullable ScreenRectangle scissorArea) {
        if (x1 - x0 <= 0.0f || y1 - y0 <= 0.0f) {
            return;
        }

        if (!AerialPipelines.ready()) {
            return;
        }
        AerialBloomFilter.submit(extractor,
                RoundedRectRenderState.of(extractor.pose(), x0, y0, x1, y1, 0.01f, color, scissorArea));
    }

    public static void sharpRectGradient(GuiGraphicsExtractor extractor,
                                         float x, float y, float width, float height,
                                         int colorLeft, int colorRight, @Nullable ScreenRectangle scissorArea) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        AerialBloomFilter.submit(extractor,
                RoundedRectRenderState.of(extractor.pose(), x, y, x + width, y + height,
                        0.01f, colorLeft, colorRight, scissorArea));
    }

    public static void roundedRect(GuiGraphicsExtractor extractor,
                                   float x, float y, float width, float height,
                                   float radius, int color, @Nullable ScreenRectangle scissorArea) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }

        if (!AerialPipelines.ready()) {
            return;
        }

        AerialBloomFilter.submit(extractor,
                RoundedRectRenderState.of(
                        extractor.pose(),
                        x, y, x + width, y + height,
                        radius, color,
                        scissorArea));
    }

    public static void roundedRectGradient(GuiGraphicsExtractor extractor,
                                           float x, float y, float width, float height,
                                           float radius, int colorA, int colorB, boolean vertical,
                                           @Nullable ScreenRectangle scissorArea) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        if (!AerialPipelines.ready()) {
            return;
        }
        AerialBloomFilter.submit(extractor,
                vertical
                        ? RoundedRectRenderState.ofVertical(extractor.pose(),
                                x, y, x + width, y + height, radius, colorA, colorB, scissorArea)
                        : RoundedRectRenderState.of(extractor.pose(),
                                x, y, x + width, y + height, radius, colorA, colorB, scissorArea));
    }

    public static void roundedOutline(GuiGraphicsExtractor extractor,
                                      float x, float y, float width, float height,
                                      float radius, float thickness, int color,
                                      @Nullable ScreenRectangle scissorArea) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        if (!AerialPipelines.ready()) {
            return;
        }
        AerialBloomFilter.submit(extractor,
                RoundedOutlineRectRenderState.of(extractor.pose(),
                        x, y, x + width, y + height, radius, thickness, color, scissorArea));
    }

    public static void roundedHead(GuiGraphicsExtractor extractor, Identifier skin,
                                   float x, float y, float size, float radius, int color,
                                   @Nullable ScreenRectangle scissorArea) {
        if (size <= 0.0f || !AerialPipelines.ready()) {
            return;
        }
        AbstractTexture texture = Minecraft.getInstance().getTextureManager().getTexture(skin);
        if (texture == null) {
            return;
        }
        AerialBloomFilter.submit(extractor,
                RoundedHeadRenderState.of(extractor.pose(), x, y, x + size, y + size,
                        radius, color, texture.getTextureView(), texture.getSampler(), scissorArea));
    }

    public static void dropShadow(GuiGraphicsExtractor extractor, int steps,
                                  float x, float y, float width, float height,
                                  double spread, float radius, @Nullable ScreenRectangle scissorArea) {
        for (float f = 0.0f; f <= steps / 2.0f; f += 0.5f) {
            int alpha = (int) Math.max(0.5, (spread - f * 1.2) / 5.5);
            if (alpha <= 0) {
                continue;
            }
            roundedRect(extractor, x - f / 2.0f, y - f / 2.0f, width + f, height + f,
                    radius, (Math.min(alpha, 255) << 24), scissorArea);
        }
    }

    public static void roundedRectAsym(GuiGraphicsExtractor extractor,
                                       float x, float y, float width, float height,
                                       float radius, boolean roundBottom, int color,
                                       @Nullable ScreenRectangle scissorArea) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }

        if (!AerialPipelines.ready()) {
            return;
        }

        AerialBloomFilter.submit(extractor,
                AsymmetricRoundedRectRenderState.of(
                        extractor.pose(),
                        x, y, x + width, y + height,
                        radius, roundBottom, color,
                        scissorArea));
    }

    public static void roundedRectAsymGradient(GuiGraphicsExtractor extractor,
                                               float x, float y, float width, float height,
                                               float radius, boolean roundBottom, int colorLeft, int colorRight,
                                               @Nullable ScreenRectangle scissorArea) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }

        if (!AerialPipelines.ready()) {
            return;
        }

        AerialBloomFilter.submit(extractor,
                AsymmetricRoundedRectRenderState.of(
                        extractor.pose(),
                        x, y, x + width, y + height,
                        radius, roundBottom, colorLeft, colorRight,
                        scissorArea));
    }

    public static void flatRect(GuiGraphicsExtractor extractor,
                                float x, float y, float width, float height, int color) {
        flatRect(extractor, x, y, width, height, color, null);
    }

    public static void flatRect(GuiGraphicsExtractor extractor,
                                float x, float y, float width, float height, int color,
                                @Nullable ScreenRectangle scissorArea) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }

        int x0 = Math.round(x);
        int y0 = Math.round(y);
        int x1 = x0 + Math.max(1, Math.round(width));
        int y1 = y0 + Math.max(1, Math.round(height));

        AerialBloomFilter.submit(extractor,
                FlatRectRenderState.of(extractor.pose(), x0, y0, x1, y1, color, scissorArea));
    }

    public static void flatRectGradient(GuiGraphicsExtractor extractor,
                                        float x, float y, float width, float height,
                                        int colorLeft, int colorRight, @Nullable ScreenRectangle scissorArea) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }

        int x0 = Math.round(x);
        int y0 = Math.round(y);
        int x1 = x0 + Math.max(1, Math.round(width));
        int y1 = y0 + Math.max(1, Math.round(height));

        AerialBloomFilter.submit(extractor,
                GradientRectRenderState.of(extractor.pose(), x0, y0, x1, y1, colorLeft, colorRight, scissorArea));
    }

    public static void image(GuiGraphicsExtractor extractor, AerialImage image,
                             float x, float y, float width, float height) {
        image(extractor, image, x, y, width, height, 0xFFFFFFFF);
    }

    public static void image(GuiGraphicsExtractor extractor, AerialImage image,
                             float x, float y, float width, float height, int color) {
        image(extractor, image, x, y, width, height, color, color);
    }

    public static void image(GuiGraphicsExtractor extractor, AerialImage image,
                             float x, float y, float width, float height, int colorLeft, int colorRight) {
        image(extractor, image, x, y, width, height, colorLeft, colorRight, null);
    }

    public static void image(GuiGraphicsExtractor extractor, AerialImage image,
                             float x, float y, float width, float height,
                             int colorLeft, int colorRight, @Nullable ScreenRectangle scissorArea) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }

        AerialImage.Entry entry = image.entryFor(width, height);
        AerialBloomFilter.submit(extractor,
                ImageRenderState.of(
                        extractor.pose(),
                        x, y, x + width, y + height,
                        colorLeft, colorRight, entry.view(), entry.sampler(),
                        scissorArea));
    }

    public static void image(GuiGraphicsExtractor extractor, AerialImage image,
                             float x, float y, float width, float height,
                             @Nullable ScreenRectangle scissorArea) {
        image(extractor, image, x, y, width, height, 0xFFFFFFFF, 0xFFFFFFFF, scissorArea);
    }
}
