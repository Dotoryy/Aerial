package cc.aerial.client.mixin;

import cc.aerial.client.utility.ThemeText;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.ARGB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.minecraft.client.gui.Font$PreparedTextBuilder")
public abstract class FontPreparedTextBuilderMixin {
    @Shadow
    private int color;

    @Inject(method = "getTextColor", at = @At("HEAD"), cancellable = true)
    private void aerial$resolveThemeColor(TextColor color, CallbackInfoReturnable<Integer> cir) {
        if (color != null && ThemeText.isSentinel(color.getValue())) {
            cir.setReturnValue(ARGB.color(ARGB.alpha(this.color), ThemeText.resolve(color.getValue())));
        }
    }
}
