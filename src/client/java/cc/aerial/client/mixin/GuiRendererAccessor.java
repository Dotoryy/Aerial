package cc.aerial.client.mixin;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.function.Supplier;

@Mixin(GuiRenderer.class)
public interface GuiRendererAccessor {
    @Accessor("draws")
    List<?> aerial$draws();

    @Accessor("renderState")
    GuiRenderState aerial$renderState();

    @Invoker("addElementToMesh")
    void aerial$addElementToMesh(GuiElementRenderState element);

    @Accessor("previousScissorArea")
    void aerial$setPreviousScissorArea(ScreenRectangle value);

    @Accessor("previousPipeline")
    void aerial$setPreviousPipeline(RenderPipeline value);

    @Accessor("previousTextureSetup")
    void aerial$setPreviousTextureSetup(TextureSetup value);

    @Accessor("previousDraw")
    void aerial$setPreviousDraw(StagedVertexBuffer.Draw value);

    @Invoker("executeDrawRange")
    void aerial$executeDrawRange(Supplier<String> name, RenderTarget target, GpuBufferSlice transform, int from, int to);
}
