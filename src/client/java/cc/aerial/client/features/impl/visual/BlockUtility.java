package cc.aerial.client.features.impl.visual;

import cc.aerial.client.features.impl.movement.NoSlowModule;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel.ArmPose;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.ArmedEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ShieldItem;

public final class BlockUtility {
    private BlockUtility() {
    }

    public static boolean isThirdPersonBlockingState(ArmedEntityRenderState state) {
        AnimationsModule module = AnimationsModule.INSTANCE;
        if (!module.isEnabled() || !module.isSwordBlocking()) {
            return false;
        }
        if (!(state instanceof HumanoidRenderState humanoid)) {
            return false;
        }

        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && state instanceof AvatarRenderState avatar && avatar.id == player.getId()) {
            if (NoSlowModule.INSTANCE.isFakeBlockingState()) {
                return true;
            }
            if (cc.aerial.client.features.impl.combat.AutoBlockModule.INSTANCE.isForcingAnimation()) {
                return true;
            }
        }

        if (!humanoid.isUsingItem) {
            return false;
        }

        ItemStack mainStack = state.mainArm == HumanoidArm.RIGHT ? state.rightHandItemStack : state.leftHandItemStack;
        ItemStack offStack = state.mainArm == HumanoidArm.RIGHT ? state.leftHandItemStack : state.rightHandItemStack;
        if (!mainStack.is(ItemTags.SWORDS)) {
            return false;
        }

        if (mainStack.getUseAnimation() == ItemUseAnimation.BLOCK) {
            return true;
        }

        ArmPose offPose = state.mainArm == HumanoidArm.RIGHT ? state.leftArmPose : state.rightArmPose;
        return offStack.getItem() instanceof ShieldItem
                && (offPose == ArmPose.EMPTY || offPose == ArmPose.BLOCK);
    }

    public static boolean isBlockUseState(LocalPlayer player) {
        AnimationsModule module = AnimationsModule.INSTANCE;
        return module.isEnabled() && module.isSwordBlocking()
                && player.getMainHandItem().is(ItemTags.SWORDS)
                && player.getMainHandItem().getUseAnimation() == ItemUseAnimation.BLOCK
                && player.getTicksUsingItem() > 0;
    }

    public static boolean isForceBlockUseState(LocalPlayer player) {
        AnimationsModule module = AnimationsModule.INSTANCE;
        return module.isEnabled() && module.isSwordBlocking()
                && player.getMainHandItem().is(ItemTags.SWORDS)
                && player.getUseItem().getItem() instanceof ShieldItem
                && player.getTicksUsingItem() > 0
                && !isBlockUseState(player);
    }

    static void applyBlockTransformation(PoseStack matrices) {
        matrices.translate(-0.15f, 0.16f, 0.15f);
        matrices.mulPose(Axis.YP.rotationDegrees(-18.0f));
        matrices.mulPose(Axis.ZP.rotationDegrees(82.0f));
        matrices.mulPose(Axis.YP.rotationDegrees(112.0f));
    }

    static void applySwingTransformation(PoseStack matrices, float swingProgress, float convertedProgress) {
        float f = Mth.sin((double) (swingProgress * swingProgress * (float) Math.PI));
        matrices.mulPose(Axis.YP.rotationDegrees(45.0f + f * -20.0f));
        matrices.mulPose(Axis.ZP.rotationDegrees(convertedProgress * -20.0f));
        matrices.mulPose(Axis.XP.rotationDegrees(convertedProgress * -80.0f));
        matrices.mulPose(Axis.YP.rotationDegrees(-45.0f));
    }
}
