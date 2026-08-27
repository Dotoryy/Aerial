package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.world.TimerModule;
import net.minecraft.client.DeltaTracker;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(DeltaTracker.Timer.class)
public abstract class DeltaTrackerTimerMixin {
    @Shadow
    private float deltaTicks;

    @Inject(method = "advanceGameTime(J)I", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/DeltaTracker$Timer;lastMs:J",
            opcode = Opcodes.PUTFIELD, ordinal = 0))
    private void aerial$applyTimer(long timeMillis, CallbackInfoReturnable<Integer> cir) {
        float timer = TimerModule.INSTANCE.getTimer();
        if (timer > 0) {
            deltaTicks *= timer;
        }
    }
}
