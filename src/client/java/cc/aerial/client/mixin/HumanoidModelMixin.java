package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.AnimationsModule;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin<T extends HumanoidRenderState> {
    @Shadow
    private void poseBlockingArm(ModelPart arm, boolean right) {
        throw new AssertionError();
    }

    @ModifyExpressionValue(method = "poseBlockingArm", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;clamp(FFF)F"))
    private float aerial$thirdPersonBlockClamp(float original) {
        AnimationsModule module = AnimationsModule.INSTANCE;
        if (module.isEnabled() && module.isSwordBlocking()) {
            return 0.0f;
        }
        return original;
    }

    @Redirect(method = {"poseLeftArm", "poseRightArm"}, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/model/HumanoidModel;poseBlockingArm(Lnet/minecraft/client/model/geom/ModelPart;Z)V"))
    private void aerial$thirdPersonBlockPose(HumanoidModel<T> instance, ModelPart arm, boolean right, T state) {
        this.poseBlockingArm(arm, right);

        AnimationsModule module = AnimationsModule.INSTANCE;
        if (!module.isEnabled() || !module.isSwordBlocking()) {
            return;
        }
        ItemStack stack = right ? state.rightHandItemStack : state.leftHandItemStack;
        if (stack.getItem() instanceof ShieldItem) {
            return;
        }
        arm.xRot = arm.xRot * 0.5f - ((float) Math.PI / 10f) * 2f;
        arm.yRot = 0.0f;
    }
}
