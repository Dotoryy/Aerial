package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.combat.AutoBlockModule;
import cc.aerial.client.features.impl.movement.NoSlowModule;
import cc.aerial.client.features.impl.utility.AutoToolModule;
import cc.aerial.client.features.impl.world.ScaffoldModule;
import cc.aerial.client.features.impl.visual.AnimationsModule;
import cc.aerial.client.features.impl.visual.BlockUtility;
import cc.aerial.client.rotation.RotationHelper;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.ShieldItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class ItemInHandRendererMixin {
    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getMainHandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack aerial$autoToolSilentHandOverride(LocalPlayer player) {
        ItemStack cover = AutoToolModule.INSTANCE.getSilentDisplayStack();
        if (cover.isEmpty()) {
            cover = ScaffoldModule.INSTANCE.getSpoofDisplayStack();
        }
        return cover.isEmpty() ? player.getMainHandItem() : cover;
    }

    @Redirect(method = "submitHandsWithItems", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getViewXRot(F)F"))
    private float aerial$redirectHandsViewXRot(LocalPlayer instance, float partialTicks) {
        return RotationHelper.getScreenPitch(instance.getViewXRot(partialTicks));
    }

    @Redirect(method = "submitHandsWithItems", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getViewYRot(F)F"))
    private float aerial$redirectHandsViewYRot(LocalPlayer instance, float partialTicks) {
        return RotationHelper.getScreenYaw(instance.getViewYRot(partialTicks));
    }

    @Inject(method = "submitArmWithItem", at = @At("HEAD"), cancellable = true)
    private void aerial$onSubmitArmWithItem(AbstractClientPlayer player, float frameInterp, float xRot,
                                              InteractionHand hand, float attackValue, ItemStack itemStack,
                                              float inverseArmHeight, PoseStack poseStack,
                                              SubmitNodeCollector submitNodeCollector, int lightCoords,
                                              CallbackInfo ci) {
        AnimationsModule module = AnimationsModule.INSTANCE;
        if (!module.isEnabled()) {
            return;
        }
        if (module.isHideShield() && hand == InteractionHand.OFF_HAND && itemStack.getItem() instanceof ShieldItem) {
            ci.cancel();
            return;
        }
        if (hand == InteractionHand.MAIN_HAND) {
            poseStack.translate(module.getMainHandX(), module.getMainHandY(), module.getMainHandScale());
        }
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;getItemSwapScale(F)F"))
    private float aerial$oldCooldownAnimation(LocalPlayer player, float partialTick) {
        AnimationsModule module = AnimationsModule.INSTANCE;
        if (module.isEnabled() && module.isOldCooldownAnimation()) {
            return 1.0f;
        }
        return player.getItemSwapScale(partialTick);
    }

    @Shadow
    private void applyItemArmTransform(PoseStack poseStack, HumanoidArm arm, float armHeight) {
        throw new AssertionError();
    }

    @Redirect(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;applyItemArmTransform("
                    + "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/HumanoidArm;F)V"))
    private void aerial$stableBlockArmHeight(ItemInHandRenderer renderer, PoseStack transformPoseStack,
                                                HumanoidArm arm, float armHeight,
                                                AbstractClientPlayer player, float frameInterp, float xRot,
                                                InteractionHand hand, float attack, ItemStack itemStack,
                                                float inverseArmHeight, PoseStack poseStack,
                                                SubmitNodeCollector submitNodeCollector, int lightCoords) {
        if (aerial$isBlockingPose(player, hand, itemStack)) {
            this.applyItemArmTransform(transformPoseStack, arm, 0.0f);
            return;
        }
        this.applyItemArmTransform(transformPoseStack, arm, armHeight);
    }

    @Unique
    private boolean aerial$isBlockingPose(AbstractClientPlayer player, InteractionHand hand, ItemStack itemStack) {
        AnimationsModule module = AnimationsModule.INSTANCE;
        if (!module.isEnabled() || !module.isSwordBlocking() || !itemStack.is(ItemTags.SWORDS)) {
            return false;
        }
        if (hand == InteractionHand.MAIN_HAND) {
            if (NoSlowModule.INSTANCE.isFakeBlockingState()) {
                return true;
            }
            LocalPlayer local = Minecraft.getInstance().player;
            if (local != null && player == local && BlockUtility.isForceBlockUseState(local)) {
                return true;
            }
            if (local != null && player == local && AutoBlockModule.INSTANCE.isForcingAnimation()) {
                return true;
            }
        }
        return player.isUsingItem() && player.getUsedItemHand() == hand
                && itemStack.getUseAnimation() == ItemUseAnimation.BLOCK;
    }

    @Redirect(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;getUseAnimation()Lnet/minecraft/world/item/ItemUseAnimation;"))
    private ItemUseAnimation aerial$replaceVanillaBlockAnimation(ItemStack instance,
                                                                    AbstractClientPlayer player, float frameInterp, float xRot,
                                                                    InteractionHand hand, float attack, ItemStack itemStack,
                                                                    float inverseArmHeight, PoseStack poseStack,
                                                                    SubmitNodeCollector submitNodeCollector, int lightCoords) {
        ItemUseAnimation real = instance.getUseAnimation();
        AnimationsModule module = AnimationsModule.INSTANCE;
        boolean reallyBlocking = player.isUsingItem() && player.getUsedItemHand() == hand;
        boolean fakeBlocking = hand == InteractionHand.MAIN_HAND
                && (NoSlowModule.INSTANCE.isFakeBlockingState() || AutoBlockModule.INSTANCE.isForcingAnimation());
        if (module.isEnabled() && module.isSwordBlocking() && instance.is(ItemTags.SWORDS)
                && real == ItemUseAnimation.BLOCK
                && (reallyBlocking || fakeBlocking)) {
            return ItemUseAnimation.NONE;
        }
        return real;
    }

    @Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;renderItem("
                    + "Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;"
                    + "Lnet/minecraft/world/item/ItemDisplayContext;Lcom/mojang/blaze3d/vertex/PoseStack;"
                    + "Lnet/minecraft/client/renderer/SubmitNodeCollector;I)V", ordinal = 1))
    private void aerial$applySwordBlockingTransform(AbstractClientPlayer player, float frameInterp, float xRot,
                                                       InteractionHand hand, float attack, ItemStack itemStack,
                                                       float inverseArmHeight, PoseStack poseStack,
                                                       SubmitNodeCollector submitNodeCollector, int lightCoords,
                                                       CallbackInfo ci) {
        if (aerial$isBlockingPose(player, hand, itemStack)) {
            AnimationsModule.INSTANCE.applyTransformations(poseStack, attack);
        }
    }

    @Redirect(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;isUsingItem()Z", ordinal = 1))
    private boolean aerial$fakeBlockingUsingItemGate(AbstractClientPlayer instance,
                                                        AbstractClientPlayer player, float frameInterp, float xRot,
                                                        InteractionHand hand, float attack, ItemStack itemStack,
                                                        float inverseArmHeight, PoseStack poseStack,
                                                        SubmitNodeCollector submitNodeCollector, int lightCoords) {
        if (instance.isUsingItem()) {
            return true;
        }
        return aerial$isBlockingPose(instance, hand, itemStack);
    }

    @Redirect(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;getUseItemRemainingTicks()I", ordinal = 2))
    private int aerial$fakeBlockingRemainingTicksGate(AbstractClientPlayer instance,
                                                         AbstractClientPlayer player, float frameInterp, float xRot,
                                                         InteractionHand hand, float attack, ItemStack itemStack,
                                                         float inverseArmHeight, PoseStack poseStack,
                                                         SubmitNodeCollector submitNodeCollector, int lightCoords) {
        int real = instance.getUseItemRemainingTicks();
        if (real > 0) {
            return real;
        }
        return aerial$isBlockingPose(instance, hand, itemStack) ? 1 : real;
    }

    @Redirect(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/AbstractClientPlayer;getUsedItemHand()Lnet/minecraft/world/InteractionHand;", ordinal = 1))
    private InteractionHand aerial$fakeBlockingUsedHandGate(AbstractClientPlayer instance,
                                                               AbstractClientPlayer player, float frameInterp, float xRot,
                                                               InteractionHand hand, float attack, ItemStack itemStack,
                                                               float inverseArmHeight, PoseStack poseStack,
                                                               SubmitNodeCollector submitNodeCollector, int lightCoords) {
        InteractionHand real = instance.getUsedItemHand();
        if (real == hand) {
            return real;
        }
        return aerial$isBlockingPose(instance, hand, itemStack) ? hand : real;
    }

    @Shadow
    private void swingArm(float attack, PoseStack poseStack, int invert, HumanoidArm arm) {
        throw new AssertionError();
    }

    @Redirect(method = "submitArmWithItem", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ItemInHandRenderer;swingArm("
                    + "FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V"))
    private void aerial$suppressSwingWhileBlocking(ItemInHandRenderer renderer, float swingAttack, PoseStack swingPoseStack,
                                                     int invert, HumanoidArm arm,
                                                     AbstractClientPlayer player, float frameInterp, float xRot,
                                                     InteractionHand hand, float attack, ItemStack itemStack,
                                                     float inverseArmHeight, PoseStack poseStack,
                                                     SubmitNodeCollector submitNodeCollector, int lightCoords) {
        if (aerial$isBlockingPose(player, hand, itemStack)) {
            return;
        }
        this.swingArm(swingAttack, swingPoseStack, invert, arm);
    }
}
