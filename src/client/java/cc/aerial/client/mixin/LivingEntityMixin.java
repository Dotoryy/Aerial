package cc.aerial.client.mixin;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.game.player.interaction.SwingEvent;
import cc.aerial.client.event.impl.game.player.movement.JumpEvent;
import cc.aerial.client.event.impl.game.player.interaction.VisualSwingEvent;
import cc.aerial.client.features.impl.movement.MovementFixModule;
import cc.aerial.client.features.impl.movement.NoJumpDelayModule;
import cc.aerial.client.features.impl.visual.AnimationsModule;
import cc.aerial.client.features.impl.visual.FreeLookModule;
import cc.aerial.client.features.impl.world.ScaffoldModule;
import cc.aerial.client.rotation.RotationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import cc.aerial.client.features.impl.world.AntiDebuffModule;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V", at = @At("TAIL"))
    private void aerial$swingTail(InteractionHand hand, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player) {
            EventDispatcher.dispatch(new SwingEvent(hand));
            EventDispatcher.dispatch(new VisualSwingEvent(hand));
        }
    }

    @ModifyExpressionValue(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/Mth;abs(F)F"))
    private float aerial$modifyBackwardsWalkingRotation(float original) {
        AnimationsModule module = AnimationsModule.INSTANCE;
        if (module.isEnabled() && module.isOldBackwardsWalking()) {
            return 0.0f;
        }
        return original;
    }

    @Inject(method = "getCurrentSwingDuration", at = @At("RETURN"), cancellable = true)
    private void aerial$modifySwingDuration(CallbackInfoReturnable<Integer> cir) {
        AnimationsModule module = AnimationsModule.INSTANCE;
        if (module.isEnabled()) {
            cir.setReturnValue((int) (cir.getReturnValue() * module.getSwingSlowdown()));
        }
    }

    @Inject(method = "jumpFromGround", at = @At("HEAD"), cancellable = true)
    private void aerial$jumpFromGround(CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        JumpEvent event = new JumpEvent(((LivingEntity) (Object) this).getYRot());
        EventDispatcher.dispatch(event);
        this.aerial$jumpEvent = event;
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Unique
    private JumpEvent aerial$jumpEvent;

    @Redirect(method = "jumpFromGround", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float aerial$redirectJumpYaw(LivingEntity instance) {
        if ((Object) this == Minecraft.getInstance().player) {
            if (FreeLookModule.INSTANCE.isFreeLooking()) {
                return instance.getYRot();
            }
            if (!MovementFixModule.INSTANCE.isFixMovement()) {
                return RotationHelper.getScreenYaw(instance.getYRot());
            }

            if (this.aerial$jumpEvent != null) {
                return this.aerial$jumpEvent.getYaw();
            }
        }
        return instance.getYRot();
    }

    @Redirect(method = "tickHeadTurn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;getYRot()F"))
    private float aerial$scaffoldHeadTurnYaw(LivingEntity instance) {
        if (instance == Minecraft.getInstance().player) {
            return ScaffoldModule.INSTANCE.getModelYaw(instance.getYRot());
        }
        return instance.getYRot();
    }

    @ModifyReturnValue(method = "getMaxHeadRotationRelativeToBody", at = @At("RETURN"))
    private float aerial$maxHeadRotation(float original) {
        if ((Object) this == Minecraft.getInstance().player) {
            return 75.0f;
        }
        return original;
    }

    @ModifyConstant(method = "aiStep", constant = @Constant(intValue = 10))
    private int aerial$modifyJumpDelay(int original) {
        if ((Object) this == Minecraft.getInstance().player && NoJumpDelayModule.INSTANCE.isEnabled()) {
            if (cc.aerial.client.features.impl.world.ScaffoldModule.INSTANCE.wantsVanillaJumpDelay()) {
                return original;
            }
            return NoJumpDelayModule.INSTANCE.getMaxCooldown();
        }
        return original;
    }

    @ModifyReturnValue(method = "getEffectBlendFactor", at = @At("RETURN"))
    private float aerial$antiNausea(float original, Holder<MobEffect> effect, float partialTick) {
        if (original != 0.0f && effect == MobEffects.NAUSEA
                && (Object) this == Minecraft.getInstance().player
                && AntiDebuffModule.INSTANCE.isNauseaRemoved()) {
            return 0.0f;
        }
        return original;
    }
}
