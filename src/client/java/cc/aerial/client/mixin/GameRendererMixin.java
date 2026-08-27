package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.NoHurtCameraModule;
import cc.aerial.client.render.CameraRenderStateHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Shadow
    @Final
    private GameRenderState gameRenderState;

    @Inject(method = "bobHurt", at = @At("HEAD"), cancellable = true)
    private void aerial$noHurtCam(CameraRenderState cameraState, PoseStack poseStack, CallbackInfo ci) {
        if (NoHurtCameraModule.INSTANCE.isEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLevel", at = @At("TAIL"))
    private void aerial$captureCameraRenderState(DeltaTracker deltaTracker, CallbackInfo ci) {
        CameraRenderStateHelper.set(gameRenderState.levelRenderState.cameraRenderState);
    }
}
