package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.PostProcessingModule;
import cc.aerial.client.render.AerialBloomElement;
import cc.aerial.client.render.AerialBloomFilter;
import cc.aerial.client.render.AerialBlur;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Supplier;

@Mixin(GuiRenderer.class)
public abstract class GuiRendererMixin {
    @Shadow
    private int firstDrawIndexAfterBlur;

    @Unique
    private int aerial$bloomStart = -1;
    @Unique
    private int aerial$bloomEnd = -1;

    @Inject(method = "prepare()V", at = @At("TAIL"))
    private void aerial$prepareBloomDraws(CallbackInfo ci) {
        aerial$bloomStart = -1;
        aerial$bloomEnd = -1;
        if (!PostProcessingModule.INSTANCE.isBloom() || !AerialBlur.shouldUpdateGlow()) {
            return;
        }

        GuiRendererAccessor self = (GuiRendererAccessor) this;

        self.aerial$setPreviousScissorArea(null);
        self.aerial$setPreviousPipeline(null);
        self.aerial$setPreviousTextureSetup(null);
        self.aerial$setPreviousDraw(null);

        int start = self.aerial$draws().size();
        self.aerial$renderState().forEachElement(element -> {
            if (element instanceof AerialBloomElement && !AerialBloomFilter.isSuppressed(element)) {
                self.aerial$addElementToMesh(element);
            }
        }, GuiRenderState.TraverseRange.ALL);
        int end = self.aerial$draws().size();

        if (end > start) {
            aerial$bloomStart = start;
            aerial$bloomEnd = end;
        }
    }

    @Redirect(method = "draw()V", at = @At(value = "INVOKE", target = "Ljava/util/List;size()I"))
    private int aerial$hideBloomDrawsFromMainPass(List<?> instance) {
        int size = instance.size();
        return aerial$bloomStart >= 0 ? Math.min(size, aerial$bloomStart) : size;
    }

    @Inject(method = "draw()V", at = @At("HEAD"))
    private void aerial$captureBlur(CallbackInfo ci) {
        AerialBlur.captureNow();
    }

    @Inject(method = "draw()V", at = @At("RETURN"))
    private void aerial$renderBloom(CallbackInfo ci) {
        try {
            if (aerial$bloomStart < 0 || !PostProcessingModule.INSTANCE.isBloom()) {
                return;
            }
            aerial$doRenderBloom();
        } finally {
            AerialBlur.endFrame();

            cc.aerial.client.render.font.AerialFont.endFrame();
            cc.aerial.client.render.AerialImage.endFrame();
        }
    }

    @Unique
    private void aerial$doRenderBloom() {
        TextureTarget glow = AerialBlur.glowTarget();
        if (glow == null) {
            return;
        }

        RenderSystem.getDevice().createCommandEncoder()
                .clearColorTexture(glow.getColorTexture(), new Vector4f(0.0f, 0.0f, 0.0f, 0.0f));

        GpuBufferSlice transform = RenderSystem.getDynamicUniforms()
                .writeTransform(new Matrix4f().setTranslation(0.0f, 0.0f, -11000.0f));

        ((GuiRendererAccessor) this).aerial$executeDrawRange(
                (Supplier<String>) () -> "aerial/bloom", (RenderTarget) glow, transform,
                aerial$bloomStart, aerial$bloomEnd);

        AerialBlur.blurGlow();
    }
}
