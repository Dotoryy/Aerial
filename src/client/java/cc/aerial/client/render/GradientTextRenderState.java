package cc.aerial.client.render;

import cc.aerial.client.render.font.GlyphQuad;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

public record GradientTextRenderState(
        Matrix3x2fc pose,
        GlyphQuad[] glyphs,
        int colorLeft,
        int colorRight,
        float minX,
        float maxX,
        GpuTextureView atlasView,
        GpuSampler atlasSampler,
        @Nullable ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState {
    public static GradientTextRenderState of(Matrix3x2fc pose, GlyphQuad[] glyphs, int colorLeft, int colorRight,
                                              GpuTextureView atlasView, GpuSampler atlasSampler,
                                              @Nullable ScreenRectangle scissorArea) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (GlyphQuad q : glyphs) {
            minX = Math.min(minX, q.x0);
            minY = Math.min(minY, q.y0);
            maxX = Math.max(maxX, q.x1);
            maxY = Math.max(maxY, q.y1);
        }
        if (glyphs.length == 0) {
            minX = minY = maxX = maxY = 0;
        }
        ScreenRectangle bounds = new ScreenRectangle(
                (int) Math.floor(minX), (int) Math.floor(minY),
                (int) Math.ceil(maxX - minX) + 1, (int) Math.ceil(maxY - minY) + 1);

        return new GradientTextRenderState(new Matrix3x2f(pose), glyphs, colorLeft, colorRight, minX, maxX,
                atlasView, atlasSampler, scissorArea, bounds);
    }

    private int colorAt(float x) {
        float t = maxX > minX ? (x - minX) / (maxX - minX) : 0.0f;
        t = Math.max(0.0f, Math.min(1.0f, t));

        int a0 = (colorLeft >>> 24) & 0xFF, r0 = (colorLeft >> 16) & 0xFF, g0 = (colorLeft >> 8) & 0xFF, b0 = colorLeft & 0xFF;
        int a1 = (colorRight >>> 24) & 0xFF, r1 = (colorRight >> 16) & 0xFF, g1 = (colorRight >> 8) & 0xFF, b1 = colorRight & 0xFF;
        int a = Math.round(a0 + (a1 - a0) * t);
        int r = Math.round(r0 + (r1 - r0) * t);
        int g = Math.round(g0 + (g1 - g0) * t);
        int b = Math.round(b0 + (b1 - b0) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (GlyphQuad q : glyphs) {
            int left = colorAt(q.x0);
            int right = colorAt(q.x1);
            consumer.addVertexWith2DPose(pose, q.x0, q.y0).setUv(q.u0, q.v0).setColor(left);
            consumer.addVertexWith2DPose(pose, q.x0, q.y1).setUv(q.u0, q.v1).setColor(left);
            consumer.addVertexWith2DPose(pose, q.x1, q.y1).setUv(q.u1, q.v1).setColor(right);
            consumer.addVertexWith2DPose(pose, q.x1, q.y0).setUv(q.u1, q.v0).setColor(right);
        }
    }

    @Override
    public RenderPipeline pipeline() {
        return AerialPipelines.TEXT;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.singleTexture(atlasView, atlasSampler);
    }
}
