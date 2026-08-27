package cc.aerial.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

public record RoundedRectRenderState(
        Matrix3x2fc pose,
        float x0,
        float y0,
        float x1,
        float y1,
        float radius,
        int colorLeft,
        int colorRight,
        boolean vertical,
        @Nullable ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState, AerialBloomElement {
    public static RoundedRectRenderState of(Matrix3x2fc pose, float x0, float y0, float x1, float y1,
                                            float radius, int color, @Nullable ScreenRectangle scissorArea) {
        return of(pose, x0, y0, x1, y1, radius, color, color, scissorArea);
    }

    public static RoundedRectRenderState ofVertical(Matrix3x2fc pose, float x0, float y0, float x1, float y1,
                                                    float radius, int colorTop, int colorBottom,
                                                    @Nullable ScreenRectangle scissorArea) {
        return build(pose, x0, y0, x1, y1, radius, colorTop, colorBottom, true, scissorArea);
    }

    public static RoundedRectRenderState of(Matrix3x2fc pose, float x0, float y0, float x1, float y1,
                                            float radius, int colorLeft, int colorRight,
                                            @Nullable ScreenRectangle scissorArea) {
        float clamped = Math.max(0.01f, Math.min(radius, Math.min(x1 - x0, y1 - y0) * 0.5f));

        ScreenRectangle bounds = new ScreenRectangle(
                (int) Math.floor(x0), (int) Math.floor(y0),
                (int) Math.ceil(x1 - x0) + 1, (int) Math.ceil(y1 - y0) + 1);

        return new RoundedRectRenderState(new Matrix3x2f(pose), x0, y0, x1, y1, clamped,
                colorLeft, colorRight, false, scissorArea, bounds);
    }

    private static RoundedRectRenderState build(Matrix3x2fc pose, float x0, float y0, float x1, float y1,
                                                float radius, int colorA, int colorB, boolean vertical,
                                                @Nullable ScreenRectangle scissorArea) {
        float clamped = Math.max(0.01f, Math.min(radius, Math.min(x1 - x0, y1 - y0) * 0.5f));
        ScreenRectangle bounds = new ScreenRectangle(
                (int) Math.floor(x0), (int) Math.floor(y0),
                (int) Math.ceil(x1 - x0) + 1, (int) Math.ceil(y1 - y0) + 1);
        return new RoundedRectRenderState(new Matrix3x2f(pose), x0, y0, x1, y1, clamped,
                colorA, colorB, vertical, scissorArea, bounds);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        float halfU = (x1 - x0) * 0.5f / radius;
        float halfV = (y1 - y0) * 0.5f / radius;

        int topLeft = colorLeft;
        int bottomLeft = vertical ? colorRight : colorLeft;
        int bottomRight = colorRight;
        int topRight = vertical ? colorLeft : colorRight;

        consumer.addVertexWith2DPose(pose, x0, y0).setUv(-halfU, -halfV).setColor(topLeft);
        consumer.addVertexWith2DPose(pose, x0, y1).setUv(-halfU, halfV).setColor(bottomLeft);
        consumer.addVertexWith2DPose(pose, x1, y1).setUv(halfU, halfV).setColor(bottomRight);
        consumer.addVertexWith2DPose(pose, x1, y0).setUv(halfU, -halfV).setColor(topRight);
    }

    @Override
    public RenderPipeline pipeline() {
        return AerialPipelines.ROUNDED_RECT;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.singleTexture(RoundedFieldAtlas.view(), RoundedFieldAtlas.sampler());
    }
}
