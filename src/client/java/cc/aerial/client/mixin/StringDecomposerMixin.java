package cc.aerial.client.mixin;

import cc.aerial.client.features.impl.visual.StreamerModule;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.StringDecomposer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(StringDecomposer.class)
public final class StringDecomposerMixin {
    @ModifyVariable(method = "iterateFormatted(Ljava/lang/String;ILnet/minecraft/network/chat/Style;Lnet/minecraft/network/chat/Style;Lnet/minecraft/util/FormattedCharSink;)Z",
            at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static String aerial$filterUsername(String text) {
        StreamerModule module = StreamerModule.INSTANCE;
        return module.isEnabled() ? module.filter(text) : text;
    }
}
