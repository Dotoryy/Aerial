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

import java.util.List;

public record BlurRectGroupRenderState(
        Matrix3x2fc pose,
        List<Rect> rects,
        int color,
        GpuTextureView view,
        GpuSampler sampler,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {
    public record Rect(float x0, float y0, float x1, float y1, float radius) {
        public Rect {
            radius = Math.max(0.01f, Math.min(radius, Math.min(x1 - x0, y1 - y0) * 0.5f));
        }
    }

    public static BlurRectGroupRenderState of(Matrix3x2fc pose, List<Rect> rects, int color,
                                               GpuTextureView view, GpuSampler sampler,
                                               @Nullable ScreenRectangle scissorArea) {
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = Integer.MIN_VALUE, bottom = Integer.MIN_VALUE;
        for (Rect rect : rects) {
            ScreenRectangle transformed = new ScreenRectangle(
                    (int) Math.floor(rect.x0), (int) Math.floor(rect.y0),
                    (int) Math.ceil(rect.x1 - rect.x0) + 1, (int) Math.ceil(rect.y1 - rect.y0) + 1)
                    .transformMaxBounds(pose);
            left = Math.min(left, transformed.left());
            top = Math.min(top, transformed.top());
            right = Math.max(right, transformed.right());
            bottom = Math.max(bottom, transformed.bottom());
        }
        ScreenRectangle bounds = rects.isEmpty() ? null : new ScreenRectangle(left, top, right - left, bottom - top);
        if (bounds != null && scissorArea != null) {
            bounds = scissorArea.intersection(bounds);
        }
        return new BlurRectGroupRenderState(new Matrix3x2f(pose), List.copyOf(rects), color, view, sampler, scissorArea, bounds);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        for (Rect rect : rects) {
            float halfU = (rect.x1 - rect.x0) * 0.5f / rect.radius;
            float halfV = (rect.y1 - rect.y0) * 0.5f / rect.radius;

            consumer.addVertexWith2DPose(pose, rect.x0, rect.y0).setUv(-halfU, -halfV).setColor(color);
            consumer.addVertexWith2DPose(pose, rect.x0, rect.y1).setUv(-halfU, halfV).setColor(color);
            consumer.addVertexWith2DPose(pose, rect.x1, rect.y1).setUv(halfU, halfV).setColor(color);
            consumer.addVertexWith2DPose(pose, rect.x1, rect.y0).setUv(halfU, -halfV).setColor(color);
        }
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
