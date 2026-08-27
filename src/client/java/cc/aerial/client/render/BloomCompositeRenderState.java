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

public record BloomCompositeRenderState(
        Matrix3x2fc pose,
        float x0, float y0, float x1, float y1,
        int color,
        GpuTextureView view,
        GpuSampler sampler,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {
    public static BloomCompositeRenderState of(Matrix3x2fc pose, float x0, float y0, float x1, float y1,
                                                int color, GpuTextureView view, GpuSampler sampler) {
        ScreenRectangle bounds = new ScreenRectangle(
                (int) Math.floor(x0), (int) Math.floor(y0),
                (int) Math.ceil(x1 - x0) + 1, (int) Math.ceil(y1 - y0) + 1).transformMaxBounds(pose);
        return new BloomCompositeRenderState(new Matrix3x2f(pose), x0, y0, x1, y1, color, view, sampler, null, bounds);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        consumer.addVertexWith2DPose(pose, x0, y0).setUv(0.0f, 0.0f).setColor(color);
        consumer.addVertexWith2DPose(pose, x0, y1).setUv(0.0f, 1.0f).setColor(color);
        consumer.addVertexWith2DPose(pose, x1, y1).setUv(1.0f, 1.0f).setColor(color);
        consumer.addVertexWith2DPose(pose, x1, y0).setUv(1.0f, 0.0f).setColor(color);
    }

    @Override
    public RenderPipeline pipeline() {
        return AerialPipelines.BLOOM_COMPOSITE;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.singleTexture(view, sampler);
    }
}
