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

public record RoundedOutlineRectRenderState(
        Matrix3x2fc pose,
        float x0,
        float y0,
        float x1,
        float y1,
        float radius,
        float thickness,
        int color,
        @Nullable ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState, AerialBloomElement {
    public static RoundedOutlineRectRenderState of(Matrix3x2fc pose, float x0, float y0, float x1, float y1,
                                                   float radius, float thickness, int color,
                                                   @Nullable ScreenRectangle scissorArea) {
        float halfShort = Math.min(x1 - x0, y1 - y0) * 0.5f;
        float clampedRadius = Math.max(0.0f, Math.min(radius, halfShort));

        float clampedThickness = Math.max(0.1f, Math.min(thickness, Math.max(0.1f, halfShort)));

        ScreenRectangle bounds = new ScreenRectangle(
                (int) Math.floor(x0) - 1, (int) Math.floor(y0) - 1,
                (int) Math.ceil(x1 - x0) + 3, (int) Math.ceil(y1 - y0) + 3);

        return new RoundedOutlineRectRenderState(new Matrix3x2f(pose), x0, y0, x1, y1,
                clampedRadius, clampedThickness, color, scissorArea, bounds);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        float halfU = (x1 - x0) * 0.5f;
        float halfV = (y1 - y0) * 0.5f;

        float packed = radius + Math.min(thickness, 255.0f) / 256.0f;

        Vector2f scratch = new Vector2f();
        addVertex(consumer, scratch, x0, y0, -halfU, -halfV, packed);
        addVertex(consumer, scratch, x0, y1, -halfU, halfV, packed);
        addVertex(consumer, scratch, x1, y1, halfU, halfV, packed);
        addVertex(consumer, scratch, x1, y0, halfU, -halfV, packed);
    }

    private void addVertex(VertexConsumer consumer, Vector2f scratch, float x, float y,
                           float u, float v, float packed) {
        pose.transformPosition(x, y, scratch);
        consumer.addVertex(scratch.x, scratch.y, packed).setUv(u, v).setColor(color);
    }

    @Override
    public RenderPipeline pipeline() {
        return AerialPipelines.ROUNDED_OUTLINE;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.singleTexture(RoundedFieldAtlas.view(), RoundedFieldAtlas.sampler());
    }
}
