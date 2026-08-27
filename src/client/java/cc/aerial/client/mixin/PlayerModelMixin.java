package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.BlockUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @ModifyVariable(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("HEAD"), argsOnly = true)
    private AvatarRenderState aerial$forceThirdPersonBlockPose(AvatarRenderState state) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || state.id != player.getId()) {
            return state;
        }
        if (!BlockUtility.isThirdPersonBlockingState(state)) {
            return state;
        }

        state.isUsingItem = true;
        state.useItemHand = InteractionHand.MAIN_HAND;
        if (state.mainArm == HumanoidArm.RIGHT) {
            state.leftArmPose = HumanoidModel.ArmPose.EMPTY;
            state.rightArmPose = HumanoidModel.ArmPose.BLOCK;
        } else {
            state.leftArmPose = HumanoidModel.ArmPose.BLOCK;
            state.rightArmPose = HumanoidModel.ArmPose.EMPTY;
        }
        return state;
    }
}
