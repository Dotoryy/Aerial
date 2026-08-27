package cc.aerial.client.render;

import cc.aerial.client.features.impl.visual.PostProcessingModule;
import cc.aerial.client.mixin.GuiGraphicsExtractorAccessor;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import org.joml.Vector4f;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

public final class AerialBlur {
    private static final CrossFrameResourcePool RESOURCE_POOL = new CrossFrameResourcePool(3);

    private static final GpuTextureView[] BACKDROP_VIEWS = new GpuTextureView[PostProcessingModule.BlurMode.VALUES.length];

    private static TextureTarget glowTarget;

    private static final boolean[] BACKDROP_REQUESTED = new boolean[PostProcessingModule.BlurMode.VALUES.length];

    private static final long GLOW_UPDATE_INTERVAL_NS = 1_000_000_000L / 60L;
    private static long lastGlowUpdate;

    private AerialBlur() {
    }

    public static boolean shouldUpdateGlow() {
        long now = System.nanoTime();
        if (now - lastGlowUpdate < GLOW_UPDATE_INTERVAL_NS) {
            return false;
        }
        lastGlowUpdate = now;
        return true;
    }

    public static void endFrame() {
        RESOURCE_POOL.endFrame();

        AerialBlurChains.closePending();
        AerialBloomFilter.endFrame();

        for (PostProcessingModule.BlurMode mode : PostProcessingModule.BlurMode.VALUES) {
            RenderTarget out = AerialBlurChains.outputTarget(AerialBlurChains.Slot.backdrop(mode));
            BACKDROP_VIEWS[mode.ordinal()] = out == null ? null : out.getColorTextureView();
        }
    }

    @Nullable
    public static TextureTarget glowTarget() {
        RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        int width = main.width;
        int height = main.height;
        if (width <= 0 || height <= 0) {
            return null;
        }
        boolean fresh = false;
        if (glowTarget == null) {
            glowTarget = new TextureTarget("aerial_glow", width, height, false, GpuFormat.RGBA8_UNORM);
            fresh = true;
        } else if (glowTarget.width != width || glowTarget.height != height) {
            glowTarget.resize(width, height);
            fresh = true;
        }
        if (fresh) {
            RenderSystem.getDevice().createCommandEncoder()
                    .clearColorTexture(glowTarget.getColorTexture(), new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));
        }
        return glowTarget;
    }

    public static void blurGlow() {
        Minecraft mc = Minecraft.getInstance();
        if (!mc.isGameLoadFinished() || glowTarget == null) {
            return;
        }

        PostChain chain = AerialBlurChains.get(AerialBlurChains.Slot.GLOW,
                PostProcessingModule.INSTANCE.getBloomMode(), PostProcessingModule.INSTANCE.getBloomRadius(),
                glowTarget.width, glowTarget.height);
        if (chain != null) {
            chain.process(glowTarget, RESOURCE_POOL);
        }
    }

    public static void compositeBloom(GuiGraphicsExtractor extractor) {
        if (!PostProcessingModule.INSTANCE.isBloom() || glowTarget == null) {
            return;
        }

        AerialBloomFilter.submit(extractor,
                BloomCompositeRenderState.of(extractor.pose(), 0, 0, extractor.guiWidth(), extractor.guiHeight(),
                        0xFFFFFFFF, glowTarget.getColorTextureView(),
                        RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)));
    }

    public static void drawBlurredRound(GuiGraphicsExtractor extractor, BlurConsumer consumer, float x, float y,
                                        float width, float height, float cornerRadius) {
        drawBlurredRound(extractor, consumer, x, y, width, height, cornerRadius, 1.0f, null);
    }

    public static void drawBlurredRound(GuiGraphicsExtractor extractor, BlurConsumer consumer, float x, float y,
                                        float width, float height, float cornerRadius, float alpha,
                                        @Nullable ScreenRectangle scissorArea) {
        if (!PostProcessingModule.INSTANCE.isBlur() || width <= 0.0f || height <= 0.0f || alpha <= 0.0f) {
            return;
        }

        PostProcessingModule.BlurMode mode = PostProcessingModule.INSTANCE.getMode(consumer);
        if (mode == null) {
            return;
        }
        BACKDROP_REQUESTED[mode.ordinal()] = true;
        GpuTextureView backdropView = BACKDROP_VIEWS[mode.ordinal()];
        if (backdropView == null) {
            return;
        }

        int alphaByte = Math.round(Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f);
        int color = (alphaByte << 24) | 0xFFFFFF;

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        if (cornerRadius < FLAT_RADIUS_THRESHOLD) {
            int x0 = Math.round(x);
            int y0 = Math.round(y);
            int x1 = x0 + Math.max(1, Math.round(width));
            int y1 = y0 + Math.max(1, Math.round(height));
            AerialBloomFilter.submit(extractor,
                    BlurRectFlatGroupRenderState.of(extractor.pose(),
                            java.util.List.of(new BlurRectFlatGroupRenderState.Rect(x0, y0, x1, y1)),
                            color, backdropView, sampler, scissorArea));
        } else {
            int rx0 = Math.round(x);
            int ry0 = Math.round(y);
            int rx1 = rx0 + Math.max(1, Math.round(width));
            int ry1 = ry0 + Math.max(1, Math.round(height));
            AerialBloomFilter.submit(extractor,
                    BlurRectRenderState.of(extractor.pose(), rx0, ry0, rx1, ry1,
                            cornerRadius, color, backdropView, sampler, scissorArea));
        }
    }

    public static float snap(float value) {
        return Math.round(value);
    }

    public static void drawGlass(GuiGraphicsExtractor extractor, BlurConsumer consumer,
                                 float x, float y, float width, float height, float cornerRadius,
                                 int tintArgb, float alpha, @Nullable ScreenRectangle scissorArea) {
        float sx = snap(x);
        float sy = snap(y);
        float sw = Math.max(1.0f, snap(width));
        float sh = Math.max(1.0f, snap(height));

        drawBlurredRound(extractor, consumer, sx, sy, sw, sh, cornerRadius, alpha, scissorArea);

        RenderUtil.roundedRect(extractor, sx, sy, sw, sh, cornerRadius,
                fade(tintArgb, alpha), scissorArea);
    }

    public static void drawGlassGradient(GuiGraphicsExtractor extractor, BlurConsumer consumer,
                                         float x, float y, float width, float height,
                                         float cornerRadius, int topArgb, int bottomArgb,
                                         boolean vertical, float alpha,
                                         @Nullable ScreenRectangle scissorArea) {
        float sx = snap(x);
        float sy = snap(y);
        float sw = Math.max(1.0f, snap(width));
        float sh = Math.max(1.0f, snap(height));

        drawBlurredRound(extractor, consumer, sx, sy, sw, sh, cornerRadius, alpha, scissorArea);
        RenderUtil.roundedRectGradient(extractor, sx, sy, sw, sh, cornerRadius,
                fade(topArgb, alpha), fade(bottomArgb, alpha), vertical, scissorArea);
    }

    public static void drawGlassFlat(GuiGraphicsExtractor extractor, BlurConsumer consumer,
                                     float x, float y, float width, float height,
                                     int tintArgb, float alpha, @Nullable ScreenRectangle scissorArea) {
        float sx = snap(x);
        float sy = snap(y);
        float sw = Math.max(1.0f, snap(width));
        float sh = Math.max(1.0f, snap(height));

        drawBlurredRound(extractor, consumer, sx, sy, sw, sh, 0.01f, alpha, scissorArea);
        RenderUtil.flatRect(extractor, sx, sy, sw, sh, fade(tintArgb, alpha), scissorArea);
    }

    private static int fade(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, alpha)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static final float FLAT_RADIUS_THRESHOLD = 0.5f;

    public static void drawBlurredRects(GuiGraphicsExtractor extractor, BlurConsumer consumer,
                                        java.util.List<float[]> boxes, float cornerRadius, float alpha) {
        if (!PostProcessingModule.INSTANCE.isBlur() || boxes.isEmpty() || alpha <= 0.0f) {
            return;
        }

        PostProcessingModule.BlurMode mode = PostProcessingModule.INSTANCE.getMode(consumer);
        if (mode == null) {
            return;
        }
        BACKDROP_REQUESTED[mode.ordinal()] = true;
        GpuTextureView backdropView = BACKDROP_VIEWS[mode.ordinal()];
        if (backdropView == null) {
            return;
        }

        int alphaByte = Math.round(Math.max(0.0f, Math.min(1.0f, alpha)) * 255.0f);
        int color = (alphaByte << 24) | 0xFFFFFF;

        GpuSampler sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

        if (cornerRadius < FLAT_RADIUS_THRESHOLD) {
            java.util.List<BlurRectFlatGroupRenderState.Rect> rects = new java.util.ArrayList<>(boxes.size());
            for (float[] box : boxes) {
                float x = box[0], y = box[1], width = box[2], height = box[3];
                if (width <= 0.0f || height <= 0.0f) {
                    continue;
                }
                int x0 = Math.round(x);
                int y0 = Math.round(y);
                int x1 = x0 + Math.max(1, Math.round(width));
                int y1 = y0 + Math.max(1, Math.round(height));
                rects.add(new BlurRectFlatGroupRenderState.Rect(x0, y0, x1, y1));
            }
            if (rects.isEmpty()) {
                return;
            }
            AerialBloomFilter.submit(extractor,
                    BlurRectFlatGroupRenderState.of(extractor.pose(), rects, color, backdropView, sampler, null));
            return;
        }

        java.util.List<BlurRectGroupRenderState.Rect> rects = new java.util.ArrayList<>(boxes.size());
        for (float[] box : boxes) {
            float x = box[0], y = box[1], width = box[2], height = box[3];
            if (width <= 0.0f || height <= 0.0f) {
                continue;
            }

            int x0 = Math.round(x);
            int y0 = Math.round(y);
            int x1 = x0 + Math.max(1, Math.round(width));
            int y1 = y0 + Math.max(1, Math.round(height));
            rects.add(new BlurRectGroupRenderState.Rect(x0, y0, x1, y1, cornerRadius));
        }
        if (rects.isEmpty()) {
            return;
        }

        AerialBloomFilter.submit(extractor,
                BlurRectGroupRenderState.of(extractor.pose(), rects, color, backdropView, sampler, null));
    }

    public static void captureNow() {
        Minecraft mc = Minecraft.getInstance();

        if (!mc.isGameLoadFinished()) {
            return;
        }

        boolean blurring = PostProcessingModule.INSTANCE.isBlur();
        RenderTarget main = mc.gameRenderer.mainRenderTarget();

        for (PostProcessingModule.BlurMode mode : PostProcessingModule.BlurMode.VALUES) {
            boolean requested = BACKDROP_REQUESTED[mode.ordinal()];
            BACKDROP_REQUESTED[mode.ordinal()] = false;
            if (!requested || !blurring) {
                continue;
            }

            PostChain chain = AerialBlurChains.get(AerialBlurChains.Slot.backdrop(mode),
                    mode, PostProcessingModule.INSTANCE.getBlurRadius(), main.width, main.height);
            if (chain != null) {
                chain.process(main, RESOURCE_POOL);
            }
        }
    }
}
