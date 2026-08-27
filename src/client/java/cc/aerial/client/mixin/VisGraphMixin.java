package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.ViewClipModule;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(VisGraph.class)
public abstract class VisGraphMixin {
    @Inject(method = "setOpaque", at = @At("HEAD"), cancellable = true)
    private void aerial$neverOpaque(BlockPos pos, CallbackInfo ci) {
        if (ViewClipModule.INSTANCE.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "resolve", at = @At("HEAD"), cancellable = true)
    private void aerial$allVisible(CallbackInfoReturnable<VisibilitySet> cir) {
        if (ViewClipModule.INSTANCE.isEnabled()) {
            VisibilitySet set = new VisibilitySet();
            set.setAll(true);
            cir.setReturnValue(set);
        }
    }
}
