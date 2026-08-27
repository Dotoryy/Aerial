package cc.aerial.client.mixin;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.press.KeyPressEvent;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void aerial$onKeyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
        if ((action == 0 || action == 1) && event.key() != -1) {
            KeyPressEvent dispatched = new KeyPressEvent(event.key(), action == 1);
            EventDispatcher.dispatch(dispatched);

            if (dispatched.isCancelled()) {
                ci.cancel();
            }
        }
    }
}
