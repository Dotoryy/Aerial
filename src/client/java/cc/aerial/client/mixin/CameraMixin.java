package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.AnimationsModule;
import cc.aerial.client.features.impl.visual.FreeLookModule;
import cc.aerial.client.rotation.RotationHelper;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import cc.aerial.client.features.impl.visual.ViewClipModule;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Unique
    private Pose aerial$prevPose;

    @Inject(method = "tick", at = @At("TAIL"))
    private void aerial$oldSneaking(CallbackInfo ci) {
        Camera self = (Camera) (Object) this;
        Entity entity = self.entity();
        if (entity == null) {
            aerial$prevPose = null;
            return;
        }

        Pose pose = entity.getPose();
        AnimationsModule module = AnimationsModule.INSTANCE;
        Pose prevPose = aerial$prevPose;
        if (module.isOldSneaking() && prevPose != null
                && (pose == Pose.CROUCHING || (pose == Pose.STANDING && prevPose == Pose.CROUCHING))) {
            float eyeHeight = entity.getEyeHeight();
            if (pose == Pose.CROUCHING) {
                eyeHeight += 0.27f;
            }
            CameraAccessor accessor = (CameraAccessor) self;
            accessor.aerial$setEyeHeight(eyeHeight);
            accessor.aerial$setEyeHeightOld(eyeHeight);
        }
        aerial$prevPose = pose;
    }

    @Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewYRot(F)F"))
    private float aerial$redirectViewYaw(Entity instance, float partialTicks) {
        if (instance == Minecraft.getInstance().player && FreeLookModule.INSTANCE.isFreeLooking()) {
            return FreeLookModule.INSTANCE.getCameraYaw();
        }
        return RotationHelper.getScreenYaw(instance.getViewYRot(partialTicks));
    }

    @Redirect(method = "alignWithEntity", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getViewXRot(F)F"))
    private float aerial$redirectViewPitch(Entity instance, float partialTicks) {
        if (instance == Minecraft.getInstance().player && FreeLookModule.INSTANCE.isFreeLooking()) {
            return FreeLookModule.INSTANCE.getCameraPitch();
        }
        return RotationHelper.getScreenPitch(instance.getViewXRot(partialTicks));
    }

    @Inject(method = "getMaxZoom", at = @At("HEAD"), cancellable = true)
    private void aerial$viewClipZoom(float requested, CallbackInfoReturnable<Float> cir) {
        if (ViewClipModule.INSTANCE.isEnabled()) {
            cir.setReturnValue(requested);
        }
    }
}
