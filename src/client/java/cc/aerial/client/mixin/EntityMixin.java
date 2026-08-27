package cc.aerial.client.mixin;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.game.player.movement.PostMoveEvent;
import cc.aerial.client.event.impl.game.player.movement.PreMoveEvent;
import cc.aerial.client.event.impl.game.player.movement.StrafeEvent;
import cc.aerial.client.event.impl.game.player.movement.knockback.KnockbackEvent;
import cc.aerial.client.features.impl.movement.MovementFixModule;
import cc.aerial.client.features.impl.movement.NoSlowModule;
import cc.aerial.client.features.impl.world.ScaffoldModule;
import cc.aerial.client.features.impl.visual.FreeLookModule;
import cc.aerial.client.rotation.RotationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Inject(method = "moveRelative", at = @At("HEAD"), cancellable = true)
    private void aerial$moveRelativeHead(float speed, Vec3 movementInput, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player) {
            PreMoveEvent event = new PreMoveEvent(speed, movementInput);
            EventDispatcher.dispatch(event);
            if (event.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @Redirect(method = "moveRelative", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;getYRot()F"))
    private float aerial$redirectMoveRelativeYaw(Entity instance) {
        if ((Object) this == Minecraft.getInstance().player) {
            if (FreeLookModule.INSTANCE.isFreeLooking()) {
                return instance.getYRot();
            }

            if (ScaffoldModule.INSTANCE.isCorrectingMovement()) {
                return ScaffoldModule.INSTANCE.getModelYaw(instance.getYRot());
            }

            Float noSlowYaw = NoSlowModule.INSTANCE.getGrimMovementYaw();
            if (noSlowYaw != null) {
                return noSlowYaw;
            }
            if (!MovementFixModule.INSTANCE.isFixMovement()) {
                return RotationHelper.getScreenYaw(instance.getYRot());
            }

            StrafeEvent event = new StrafeEvent(instance.getYRot());
            EventDispatcher.dispatch(event);
            return event.getYaw();
        }
        return instance.getYRot();
    }

    @Inject(method = "moveRelative", at = @At("TAIL"))
    private void aerial$moveRelativeTail(float speed, Vec3 movementInput, CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player) {
            EventDispatcher.dispatch(new PostMoveEvent(speed, movementInput));
        }
    }

    @Inject(method = "lerpMotion", at = @At("HEAD"), cancellable = true)
    private void aerial$knockback(Vec3 movement, CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player) {
            return;
        }
        KnockbackEvent event = new KnockbackEvent(movement.x, movement.y, movement.z);
        EventDispatcher.dispatch(event);
        if (event.isOverridden()) {
            ci.cancel();
            ((Entity) (Object) this).setDeltaMovement(event.getX(), event.getY(), event.getZ());
        }
    }

    @Inject(method = "turn", at = @At("HEAD"), cancellable = true)
    private void aerial$freeLookTurn(double xDelta, double yDelta, CallbackInfo ci) {
        if ((Object) this != Minecraft.getInstance().player || !FreeLookModule.INSTANCE.isFreeLooking()) {
            return;
        }
        if (RotationHelper.getHandler().isSubstituting()) {
            return;
        }
        ci.cancel();
    }

    @Inject(method = "setYRot", at = @At("HEAD"))
    private void aerial$setYRot(float yaw, CallbackInfo ci) {
        this.aerial$checkRotation();
    }

    @Inject(method = "setXRot", at = @At("HEAD"))
    private void aerial$setXRot(float pitch, CallbackInfo ci) {
        this.aerial$checkRotation();
    }

    @Unique
    private void aerial$checkRotation() {
        Entity self = (Entity) (Object) this;
        if (self == Minecraft.getInstance().player && self.level() != null && self.level().isClientSide()) {
            RotationHelper.getClientHandler().onRotationSet();
        }
    }
}
