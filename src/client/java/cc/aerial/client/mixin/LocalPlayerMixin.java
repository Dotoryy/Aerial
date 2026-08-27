package cc.aerial.client.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.world.InteractionHand;
import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.game.player.movement.PostMovementPacketEvent;
import cc.aerial.client.event.impl.game.player.movement.PreMovementPacketEvent;
import cc.aerial.client.event.impl.game.player.movement.SlowdownEvent;
import cc.aerial.client.features.impl.combat.AutoPotModule;
import cc.aerial.client.features.impl.combat.DisplaceModule;
import cc.aerial.client.features.impl.combat.RodAimbotModule;
import cc.aerial.client.features.impl.combat.WTapModule;
import cc.aerial.client.features.impl.movement.NoSlowModule;
import cc.aerial.client.rotation.RotationHelper;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import cc.aerial.client.features.impl.world.ScaffoldModule;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Unique
    private PreMovementPacketEvent aerial$currentEvent;

    @Shadow
    private boolean isSlowDueToUsingItem() {
        throw new AssertionError();
    }

    @Inject(method = "sendPosition", at = @At("HEAD"), cancellable = true)
    private void aerial$sendPositionHead(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        PreMovementPacketEvent event = new PreMovementPacketEvent(
                self.getX(), self.getY(), self.getZ(),
                self.getYRot(), self.getXRot(),
                self.onGround(), self.isSprinting(), self.horizontalCollision);
        EventDispatcher.dispatch(event);
        this.aerial$currentEvent = event;
        if (event.isCancelled()) {
            ci.cancel();
        }
    }

    @Redirect(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float aerial$sendPositionYaw(LocalPlayer instance) {
        return this.aerial$currentEvent != null ? this.aerial$currentEvent.getYaw() : instance.getYRot();
    }

    @Redirect(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float aerial$sendPositionPitch(LocalPlayer instance) {
        return this.aerial$currentEvent != null ? this.aerial$currentEvent.getPitch() : instance.getXRot();
    }

    @Redirect(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;onGround()Z"))
    private boolean aerial$sendPositionOnGround(LocalPlayer instance) {
        return this.aerial$currentEvent != null ? this.aerial$currentEvent.isOnGround() : instance.onGround();
    }

    @Redirect(method = "sendPosition", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getY()D"))
    private double aerial$sendPositionY(LocalPlayer instance) {
        return this.aerial$currentEvent != null ? this.aerial$currentEvent.getY() : instance.getY();
    }

    @Inject(method = "sendPosition", at = @At("TAIL"))
    private void aerial$sendPositionTail(CallbackInfo ci) {
        LocalPlayer self = (LocalPlayer) (Object) this;
        EventDispatcher.dispatch(new PostMovementPacketEvent(
                self.getX(), self.getY(), self.getZ(),
                self.getYRot(), self.getXRot(),
                self.onGround(), self.isSprinting()));
        this.aerial$currentEvent = null;
    }

    @Redirect(method = "modifyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/phys/Vec2;scale(F)Lnet/minecraft/world/phys/Vec2;", ordinal = 1))
    private Vec2 aerial$noSlowSlowdown(Vec2 instance, float value) {
        SlowdownEvent event = new SlowdownEvent(value);
        EventDispatcher.dispatch(event);
        return instance.scale(event.isCancelled() ? 1.0f : event.getSlowdown());
    }

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"), cancellable = true)
    private void aerial$scaffoldSilentSwing(InteractionHand hand, CallbackInfo ci) {
        if (!ScaffoldModule.INSTANCE.shouldSilenceVanillaSwing()) {
            return;
        }
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.send(new ServerboundSwingPacket(hand));
        }
        ci.cancel();
    }

    @Redirect(method = "modifyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getAttributeValue(Lnet/minecraft/core/Holder;)D"))
    private double aerial$scaffoldSneakSpeed(LocalPlayer instance, Holder<Attribute> attribute) {
        double override = ScaffoldModule.INSTANCE.getSneakSlowdownOverride();
        return override >= 0.0 ? override : instance.getAttributeValue(attribute);
    }

    @Redirect(method = "canStartSprinting", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isSlowDueToUsingItem()Z"))
    private boolean aerial$noSlowAllowSprint(LocalPlayer instance) {
        NoSlowModule module = NoSlowModule.INSTANCE;
        if (module.isEnabled() && module.isSprintingAllowed()) {
            return false;
        }
        return this.isSlowDueToUsingItem();
    }

    @Redirect(method = "applyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getXRot()F"))
    private float aerial$applyInputBobPitch(LocalPlayer instance) {
        return RotationHelper.getScreenPitch(instance.getXRot());
    }

    @Redirect(method = "applyInput", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;getYRot()F"))
    private float aerial$applyInputBobYaw(LocalPlayer instance) {
        return RotationHelper.getScreenYaw(instance.getYRot());
    }

    @ModifyReturnValue(method = "shouldStopRunSprinting", at = @At("RETURN"))
    private boolean aerial$wtapForceStopSprint(boolean original) {
        if (original) {
            return true;
        }
        WTapModule module = WTapModule.INSTANCE;
        LocalPlayer self = (LocalPlayer) (Object) this;
        if (module.isEnabled() && self.isSprinting() && module.consumeStopSprint()) {
            return true;
        }
        return false;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void aerial$displaceApply(CallbackInfo ci) {
        DisplaceModule.INSTANCE.applyRotation((LocalPlayer) (Object) this);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void aerial$displaceRestore(CallbackInfo ci) {
        DisplaceModule.INSTANCE.restoreRotation((LocalPlayer) (Object) this);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void aerial$rodAimbotApply(CallbackInfo ci) {
        RodAimbotModule.INSTANCE.applyRotation((LocalPlayer) (Object) this);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void aerial$rodAimbotRestore(CallbackInfo ci) {
        RodAimbotModule.INSTANCE.restoreRotation((LocalPlayer) (Object) this);
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void aerial$autoPotApply(CallbackInfo ci) {
        AutoPotModule.INSTANCE.applyRotation((LocalPlayer) (Object) this);
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void aerial$autoPotRestore(CallbackInfo ci) {
        AutoPotModule.INSTANCE.restoreRotation((LocalPlayer) (Object) this);
    }
}
