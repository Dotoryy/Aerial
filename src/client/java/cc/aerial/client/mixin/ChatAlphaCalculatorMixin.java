package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.ChatModule;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$AlphaCalculator")
public interface ChatAlphaCalculatorMixin {
    @ModifyConstant(method = "lambda$timeBased$0", constant = @Constant(doubleValue = 200.0))
    private static double aerial$fadeWindow(double original) {
        return ChatModule.INSTANCE.isEnabled() ? ChatModule.INSTANCE.getDisappearTicks() : original;
    }
}
