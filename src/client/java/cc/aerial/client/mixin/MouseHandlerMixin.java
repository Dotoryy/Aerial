package cc.aerial.client.mixin;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.game.input.MouseUpdateEvent;
import cc.aerial.client.event.impl.press.MousePressEvent;
import cc.aerial.client.rotation.RotationHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Options;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void aerial$onButton(long window, MouseButtonInfo buttonInfo, int action, CallbackInfo ci) {
        if ((action == 0 || action == 1) && buttonInfo.button() != -1) {
            MousePressEvent dispatched = new MousePressEvent(buttonInfo.button(), action == 1);
            EventDispatcher.dispatch(dispatched);
            if (dispatched.isCancelled()) {
                ci.cancel();
            }
        }
    }

    @Shadow
    private double accumulatedDX;
    @Shadow
    private double accumulatedDY;

    @Unique
    private MouseUpdateEvent aerial$event;

    @Redirect(method = "turnPlayer", at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;smoothCamera:Z"))
    private boolean aerial$dispatchMouseUpdate(Options instance) {
        Minecraft mc = Minecraft.getInstance();
        double sensitivityBase = mc.options.sensitivity().get() * 0.6D + 0.2D;
        double sens = (sensitivityBase * sensitivityBase * sensitivityBase) * 8.0D;
        this.aerial$event = new MouseUpdateEvent(this.accumulatedDX, this.accumulatedDY, sens, false);
        EventDispatcher.dispatch(this.aerial$event);
        return instance.smoothCamera && !this.aerial$event.isHandled();
    }

    @Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;isScoping()Z"))
    private boolean aerial$redirectScoping(LocalPlayer instance) {
        return instance.isScoping() && (this.aerial$event == null || !this.aerial$event.isHandled());
    }

    @Redirect(method = "turnPlayer", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MouseHandler;accumulatedDX:D"))
    private double aerial$redirectAccumulatedDX(MouseHandler instance) {
        return this.aerial$event == null ? this.accumulatedDX : this.aerial$event.getDeltaX();
    }

    @Redirect(method = "turnPlayer", at = @At(value = "FIELD", target = "Lnet/minecraft/client/MouseHandler;accumulatedDY:D"))
    private double aerial$redirectAccumulatedDY(MouseHandler instance) {
        return this.aerial$event == null ? this.accumulatedDY : this.aerial$event.getDeltaY();
    }

    @Inject(method = "turnPlayer", at = @At("TAIL"))
    private void aerial$turnPlayerTail(double mousea, CallbackInfo ci) {
        RotationHelper.getClientHandler().onPostMouseUpdate();
        this.aerial$event = null;
    }
}
