package cc.aerial.client.mixin;

import cc.aerial.client.accountmanager.SessionManager;
import cc.aerial.client.screen.server.AerialServerScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
public abstract class JoinMultiplayerScreenMixin {
    @Shadow
    @Final
    private Screen lastScreen;

    @Inject(method = "init", at = @At("TAIL"))
    private void aerial$replaceServerList(CallbackInfo ci) {
        SessionManager.captureLaunchSession();
        Minecraft.getInstance().setScreenAndShow(new AerialServerScreen(lastScreen));
    }
}
