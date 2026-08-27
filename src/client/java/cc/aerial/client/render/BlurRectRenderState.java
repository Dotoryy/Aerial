package cc.aerial.client.render;

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

public record BlurRectRenderState(
        Matrix3x2fc pose,
        float x0,
        float y0,
        float x1,
        float y1,
        float radius,
        int color,
        GpuTextureView view,
        GpuSampler sampler,
        @Nullable ScreenRectangle scissorArea,

        @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {
    public static BlurRectRenderState of(Matrix3x2fc pose, float x0, float y0, float x1, float y1,
                                         float radius, int color, GpuTextureView view, GpuSampler sampler,
                                         @Nullable ScreenRectangle scissorArea) {
        float clamped = Math.max(0.01f, Math.min(radius, Math.min(x1 - x0, y1 - y0) * 0.5f));

        ScreenRectangle transformed = new ScreenRectangle(
                (int) Math.floor(x0), (int) Math.floor(y0),
                (int) Math.ceil(x1 - x0) + 1, (int) Math.ceil(y1 - y0) + 1)
                .transformMaxBounds(pose);
        ScreenRectangle bounds = scissorArea == null ? transformed : scissorArea.intersection(transformed);

        return new BlurRectRenderState(new Matrix3x2f(pose), x0, y0, x1, y1,
                clamped, color, view, sampler, scissorArea, bounds);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        float halfU = (x1 - x0) * 0.5f / radius;
        float halfV = (y1 - y0) * 0.5f / radius;

        consumer.addVertexWith2DPose(pose, x0, y0).setUv(-halfU, -halfV).setColor(color);
        consumer.addVertexWith2DPose(pose, x0, y1).setUv(-halfU, halfV).setColor(color);
        consumer.addVertexWith2DPose(pose, x1, y1).setUv(halfU, halfV).setColor(color);
        consumer.addVertexWith2DPose(pose, x1, y0).setUv(halfU, -halfV).setColor(color);
    }

    @Override
    public RenderPipeline pipeline() {
        return AerialPipelines.BLUR_RECT;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.doubleTexture(view, sampler, RoundedFieldAtlas.view(), RoundedFieldAtlas.sampler());
    }
}
