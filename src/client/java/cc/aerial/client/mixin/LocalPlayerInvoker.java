package cc.aerial.client.mixin;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LocalPlayer.class)
public interface LocalPlayerInvoker {
    @Invoker("sendPosition")
    void aerial$sendPosition();

    @Accessor("xLast")
    double aerial$getLastX();

    @Accessor("yLast")
    double aerial$getLastY();

    @Accessor("zLast")
    double aerial$getLastZ();

    @Accessor("positionReminder")
    int aerial$getPositionReminder();

    @Accessor("positionReminder")
    void aerial$setPositionReminder(int value);

    @Accessor("wasSprinting")
    void aerial$setWasSprinting(boolean value);
}
