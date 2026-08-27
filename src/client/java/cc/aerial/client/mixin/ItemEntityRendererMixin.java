package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.VanillaFixModule;
import cc.aerial.client.render.ItemEntityRenderStateExtender;
import cc.aerial.client.render.ItemPhysicsUtility;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemEntityRenderer.class)
public abstract class ItemEntityRendererMixin {
    @Shadow
    @Final
    private RandomSource random;

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void aerial$submit(ItemEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
        if (VanillaFixModule.INSTANCE.isItemPhysicsEnabled() && ItemPhysicsUtility.submit(state, poseStack, collector, random)) {
            ci.cancel();
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void aerial$extractRenderState(ItemEntity entity, ItemEntityRenderState state, float partialTicks, CallbackInfo ci) {
        if (VanillaFixModule.INSTANCE.isItemPhysicsEnabled()) {
            ((ItemEntityRenderStateExtender) state).aerial$extractPhysics(entity);
        } else if (entity.getXRot() != 0.0f) {
            entity.setXRot(0.0f);
        }
    }
}
