package cc.aerial.client.mixin;
import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.game.player.movement.HitSlowdownEvent;

import cc.aerial.client.features.impl.combat.ReachModule;
import cc.aerial.client.features.impl.combat.KeepSprintModule;
import cc.aerial.client.features.impl.movement.SprintModule;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.Minecraft;
import cc.aerial.client.features.impl.world.ScaffoldModule;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Player.class)
public abstract class PlayerMixin {
    @Redirect(method = "causeExtraKnockback", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", ordinal = 0))
    private void aerial$keepSprintVelocity(Player instance, Vec3 vec) {
        KeepSprintModule keepSprint = KeepSprintModule.INSTANCE;
        if (instance == Minecraft.getInstance().player && keepSprint.isPredictionSlowdownActive()) {
            double factor = keepSprint.predictionVelocityFactor();
            instance.setDeltaMovement(instance.getDeltaMovement().multiply(factor, 1.0, factor));
            return;
        }
        SprintModule module = SprintModule.INSTANCE;
        if (module.isEnabled() && module.isKeepSprint() && instance.isSprinting()) {
            return;
        }
        instance.setDeltaMovement(vec);
    }

    @Redirect(method = "causeExtraKnockback", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;setSprinting(Z)V"))
    private void aerial$keepSprintFlag(Player instance, boolean sprinting) {
        KeepSprintModule keepSprint = KeepSprintModule.INSTANCE;
        if (instance == Minecraft.getInstance().player && keepSprint.isPredictionSlowdownActive()) {
            if (keepSprint.predictionShouldDropSprint()) {
                instance.setSprinting(sprinting);
            }
            return;
        }
        SprintModule module = SprintModule.INSTANCE;
        if (module.isEnabled() && module.isKeepSprint() && instance.isSprinting()) {
            return;
        }
        instance.setSprinting(sprinting);
    }

    @Inject(method = "causeExtraKnockback", at = @At("TAIL"))
    private void aerial$hitSlowdown(CallbackInfo ci) {
        if ((Object) this == Minecraft.getInstance().player) {
            EventDispatcher.dispatch(new HitSlowdownEvent());
        }
    }

    @ModifyReturnValue(method = "entityInteractionRange", at = @At("RETURN"))
    private double aerial$reach(double original) {
        if ((Object) this != Minecraft.getInstance().player) {
            return original;
        }
        ReachModule module = ReachModule.INSTANCE;
        if (module.isEnabled() && module.isExpanding()) {
            return Math.max(original, module.getRange());
        }
        return original;
    }

    @Redirect(method = "maybeBackOffFromEdge", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;isStayingOnGroundSurface()Z"))
    private boolean aerial$scaffoldSafeWalk(Player instance) {
        if (instance == Minecraft.getInstance().player && ScaffoldModule.INSTANCE.wantsSafeWalk()) {
            return true;
        }
        return this.isStayingOnGroundSurface();
    }

    @Shadow
    protected abstract boolean isStayingOnGroundSurface();
}
