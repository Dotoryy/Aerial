package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.AnimationsModule;
import cc.aerial.client.features.impl.visual.BlockUtility;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import org.joml.Quaternionfc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(ItemInHandLayer.class)
public abstract class ItemInHandLayerMixin<S extends ArmedEntityRenderState> {
    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void aerial$hideShieldThirdPerson(S state, ItemStackRenderState item, ItemStack itemStack,
                                                HumanoidArm arm, PoseStack poseStack,
                                                SubmitNodeCollector submitNodeCollector, int lightCoords,
                                                CallbackInfo ci) {
        AnimationsModule module = AnimationsModule.INSTANCE;
        if (module.isEnabled() && module.isHideShield() && itemStack.getItem() instanceof ShieldItem) {
            ci.cancel();
        }
    }

    @ModifyArgs(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;translate(FFF)V"))
    private void aerial$blockTranslate(Args args, S state, ItemStackRenderState item, ItemStack itemStack,
                                         HumanoidArm arm, PoseStack poseStack,
                                         SubmitNodeCollector submitNodeCollector, int lightCoords) {
        if (!BlockUtility.isThirdPersonBlockingState(state)) {
            return;
        }
        args.set(0, (float) args.get(0) * -1.0f);
        args.set(1, 0.4375f);
        args.set(2, (float) args.get(2) / 10.0f * -1.0f);
    }

    @WrapWithCondition(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/vertex/PoseStack;mulPose(Lorg/joml/Quaternionfc;)V"))
    private boolean aerial$skipBlockRotations(PoseStack instance, Quaternionfc quaternion,
                                                S state, ItemStackRenderState item, ItemStack itemStack,
                                                HumanoidArm arm, PoseStack poseStack,
                                                SubmitNodeCollector submitNodeCollector, int lightCoords) {
        return !BlockUtility.isThirdPersonBlockingState(state);
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
    private void aerial$applyBlockRotation(S state, ItemStackRenderState item, ItemStack itemStack,
                                             HumanoidArm arm, PoseStack poseStack,
                                             SubmitNodeCollector submitNodeCollector, int lightCoords,
                                             CallbackInfo ci) {
        if (!BlockUtility.isThirdPersonBlockingState(state)) {
            return;
        }
        int direction = arm == HumanoidArm.RIGHT ? 1 : -1;
        float scale = 0.625f;

        poseStack.translate(direction * 0.05f, 0.0f, -0.1f);
        poseStack.mulPose(Axis.YP.rotationDegrees(direction * -50.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(-10.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(direction * -60.0f));

        poseStack.translate(direction * -0.0625f, 0.1875f, 0.0f);
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(Axis.XP.rotationDegrees(180.0f));
        poseStack.mulPose(Axis.XP.rotationDegrees(100.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(direction * -145.0f));
        poseStack.translate(-0.011765625f, 0.0f, 0.002125f);

        poseStack.translate(0.0f, -0.3f, 0.0f);
        poseStack.scale(1.5f, 1.5f, 1.5f);
        poseStack.mulPose(Axis.YP.rotationDegrees(direction * 50.0f));
        poseStack.mulPose(Axis.ZP.rotationDegrees(direction * 335.0f));
        poseStack.translate(direction * -0.9375f, -0.0625f, 0.0f);

        poseStack.mulPose(Axis.YP.rotationDegrees(direction * 180.0f));
        poseStack.translate(direction * -0.5f, 0.5f, 0.03125f);

        poseStack.scale(1.0f / 0.85f, 1.0f / 0.85f, 1.0f / 0.85f);
        poseStack.mulPose(Axis.ZP.rotationDegrees(direction * -55.0f));
        poseStack.mulPose(Axis.YP.rotationDegrees(direction * 90.0f));

        poseStack.translate(0.0f, -4.0f * 0.0625f, -0.5f * 0.0625f);
    }
}
