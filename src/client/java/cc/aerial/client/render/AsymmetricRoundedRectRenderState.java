package cc.aerial.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

public record AsymmetricRoundedRectRenderState(
        Matrix3x2fc pose,
        float x0,
        float y0,
        float x1,
        float y1,
        float radius,
        boolean roundBottom,
        int colorLeft,
        int colorRight,
        @Nullable ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState, AerialBloomElement {
    public static AsymmetricRoundedRectRenderState of(Matrix3x2fc pose, float x0, float y0, float x1, float y1,
                                                       float radius, boolean roundBottom, int color,
                                                       @Nullable ScreenRectangle scissorArea) {
        return of(pose, x0, y0, x1, y1, radius, roundBottom, color, color, scissorArea);
    }

    public static AsymmetricRoundedRectRenderState of(Matrix3x2fc pose, float x0, float y0, float x1, float y1,
                                                       float radius, boolean roundBottom,
                                                       int colorLeft, int colorRight,
                                                       @Nullable ScreenRectangle scissorArea) {
        float clamped = Math.max(0.0f, Math.min(radius, Math.min(x1 - x0, y1 - y0) * 0.5f));

        ScreenRectangle bounds = new ScreenRectangle(
                (int) Math.floor(x0), (int) Math.floor(y0),
                (int) Math.ceil(x1 - x0) + 1, (int) Math.ceil(y1 - y0) + 1);

        return new AsymmetricRoundedRectRenderState(
                new Matrix3x2f(pose), x0, y0, x1, y1, clamped, roundBottom, colorLeft, colorRight, scissorArea, bounds);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        float halfU = (x1 - x0) * 0.5f;
        float halfV = (y1 - y0) * 0.5f;

        float topV = roundBottom ? -halfV : halfV;
        float bottomV = roundBottom ? halfV : -halfV;

        Vector2f p = new Vector2f();
        addVertex(consumer, p, x0, y0, -halfU, topV, colorLeft);
        addVertex(consumer, p, x0, y1, -halfU, bottomV, colorLeft);
        addVertex(consumer, p, x1, y1, halfU, bottomV, colorRight);
        addVertex(consumer, p, x1, y0, halfU, topV, colorRight);
    }

    private void addVertex(VertexConsumer consumer, Vector2f scratch, float x, float y, float u, float v, int color) {
        pose.transformPosition(x, y, scratch);
        consumer.addVertex(scratch.x, scratch.y, radius).setUv(u, v).setColor(color);
    }

    @Override
    public RenderPipeline pipeline() {
        return AerialPipelines.ROUNDED_RECT_ASYM;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.singleTexture(RoundedFieldAtlas.view(), RoundedFieldAtlas.sampler());
    }
}
