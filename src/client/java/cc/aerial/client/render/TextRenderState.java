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

public record TextRenderState(
        Matrix3x2fc pose,
        GlyphQuad[] glyphs,
        int color,
        GpuTextureView atlasView,
        GpuSampler atlasSampler,
        @Nullable ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState {
    public static TextRenderState of(Matrix3x2fc pose, GlyphQuad[] glyphs, int color,
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

        return new TextRenderState(new Matrix3x2f(pose), glyphs, color,
                atlasView, atlasSampler, scissorArea, bounds);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (GlyphQuad q : glyphs) {
            consumer.addVertexWith2DPose(pose, q.x0, q.y0).setUv(q.u0, q.v0).setColor(color);
            consumer.addVertexWith2DPose(pose, q.x0, q.y1).setUv(q.u0, q.v1).setColor(color);
            consumer.addVertexWith2DPose(pose, q.x1, q.y1).setUv(q.u1, q.v1).setColor(color);
            consumer.addVertexWith2DPose(pose, q.x1, q.y0).setUv(q.u1, q.v0).setColor(color);
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
