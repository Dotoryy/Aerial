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
import org.joml.Vector2f;

public record RoundedHeadRenderState(
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
        ScreenRectangle bounds
) implements GuiElementRenderState, AerialBloomElement {
    public static RoundedHeadRenderState of(Matrix3x2fc pose, float x0, float y0, float x1, float y1,
                                            float radius, int color,
                                            GpuTextureView view, GpuSampler sampler,
                                            @Nullable ScreenRectangle scissorArea) {
        float clamped = Math.max(0.0f, Math.min(radius, Math.min(x1 - x0, y1 - y0) * 0.5f));
        ScreenRectangle bounds = new ScreenRectangle(
                (int) Math.floor(x0), (int) Math.floor(y0),
                (int) Math.ceil(x1 - x0) + 1, (int) Math.ceil(y1 - y0) + 1);
        return new RoundedHeadRenderState(new Matrix3x2f(pose), x0, y0, x1, y1,
                clamped, color, view, sampler, scissorArea, bounds);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        float halfU = (x1 - x0) * 0.5f;
        float halfV = (y1 - y0) * 0.5f;

        Vector2f scratch = new Vector2f();
        addVertex(consumer, scratch, x0, y0, -halfU, -halfV);
        addVertex(consumer, scratch, x0, y1, -halfU, halfV);
        addVertex(consumer, scratch, x1, y1, halfU, halfV);
        addVertex(consumer, scratch, x1, y0, halfU, -halfV);
    }

    private void addVertex(VertexConsumer consumer, Vector2f scratch, float x, float y, float u, float v) {
        pose.transformPosition(x, y, scratch);

        consumer.addVertex(scratch.x, scratch.y, radius).setUv(u, v).setColor(color);
    }

    @Override
    public RenderPipeline pipeline() {
        return AerialPipelines.ROUNDED_TEXTURE;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.doubleTexture(view, sampler, RoundedFieldAtlas.view(), RoundedFieldAtlas.sampler());
    }
}
