package cc.aerial.client.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

public record GradientRectRenderState(
        Matrix3x2fc pose,
        int x0, int y0, int x1, int y1,
        int colorLeft, int colorRight,
        @Nullable ScreenRectangle scissorArea,
        ScreenRectangle bounds
) implements GuiElementRenderState, AerialBloomElement {
    public static GradientRectRenderState of(Matrix3x2fc pose, int x0, int y0, int x1, int y1,
                                              int colorLeft, int colorRight, @Nullable ScreenRectangle scissorArea) {
        ScreenRectangle rawBounds = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0).transformMaxBounds(pose);
        ScreenRectangle bounds = scissorArea != null ? scissorArea.intersection(rawBounds) : rawBounds;
        return new GradientRectRenderState(new Matrix3x2f(pose), x0, y0, x1, y1, colorLeft, colorRight, scissorArea, bounds);
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        consumer.addVertexWith2DPose(pose, x0, y0).setColor(colorLeft);
        consumer.addVertexWith2DPose(pose, x0, y1).setColor(colorLeft);
        consumer.addVertexWith2DPose(pose, x1, y1).setColor(colorRight);
        consumer.addVertexWith2DPose(pose, x1, y0).setColor(colorRight);
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }
}
