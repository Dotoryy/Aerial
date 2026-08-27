package cc.aerial.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

public record ImageRenderState(
        Matrix3x2fc pose,
        float x0,
        float y0,
        float x1,
        float y1,
        float u0,
        float v0,
        float u1,
        float v1,
        int colorLeft,
        int colorRight,
        GpuTextureView view,
        GpuSampler sampler,
        @Nullable ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState, AerialBloomElement {
    public static ImageRenderState of(Matrix3x2fc pose, float x0, float y0, float x1, float y1,
                                       int color, GpuTextureView view, GpuSampler sampler,
                                       @Nullable ScreenRectangle scissorArea) {
        return of(pose, x0, y0, x1, y1, color, color, view, sampler, scissorArea);
    }

    public static ImageRenderState of(Matrix3x2fc pose, float x0, float y0, float x1, float y1,
                                       int colorLeft, int colorRight, GpuTextureView view, GpuSampler sampler,
                                       @Nullable ScreenRectangle scissorArea) {
        return of(pose, x0, y0, x1, y1, 0.0f, 0.0f, 1.0f, 1.0f,
                colorLeft, colorRight, view, sampler, scissorArea);
    }

    public static ImageRenderState of(Matrix3x2fc pose, float x0, float y0, float x1, float y1,
                                       float u0, float v0, float u1, float v1,
                                       int colorLeft, int colorRight, GpuTextureView view, GpuSampler sampler,
                                       @Nullable ScreenRectangle scissorArea) {
        ScreenRectangle bounds = new ScreenRectangle(
                (int) Math.floor(x0), (int) Math.floor(y0),
                (int) Math.ceil(x1 - x0) + 1, (int) Math.ceil(y1 - y0) + 1);
        return new ImageRenderState(new Matrix3x2f(pose), x0, y0, x1, y1, u0, v0, u1, v1,
                colorLeft, colorRight, view, sampler, scissorArea, bounds);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        consumer.addVertexWith2DPose(pose, x0, y0).setUv(u0, v0).setColor(colorLeft);
        consumer.addVertexWith2DPose(pose, x0, y1).setUv(u0, v1).setColor(colorLeft);
        consumer.addVertexWith2DPose(pose, x1, y1).setUv(u1, v1).setColor(colorRight);
        consumer.addVertexWith2DPose(pose, x1, y0).setUv(u1, v0).setColor(colorRight);
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI_TEXTURED;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.singleTexture(view, sampler);
    }
}
