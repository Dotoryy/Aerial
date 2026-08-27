package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.AmbienceModule;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.ClientClockManager;
import net.minecraft.core.Holder;
import net.minecraft.world.clock.WorldClock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientClockManager.class)
public final class ClientClockManagerMixin {
    @ModifyReturnValue(method = "getTotalTicks", at = @At("RETURN"))
    private long aerial$ambience(long original, Holder<WorldClock> clock) {
        AmbienceModule module = AmbienceModule.INSTANCE;
        return module.isEnabled() ? module.getForcedTime() : original;
    }
}
