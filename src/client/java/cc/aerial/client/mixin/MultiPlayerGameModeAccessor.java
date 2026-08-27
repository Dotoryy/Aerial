package cc.aerial.client.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
    @Invoker("ensureHasSentCarriedItem")
    void aerial$ensureHasSentCarriedItem();

    @Accessor("destroyProgress")
    float aerial$getDestroyProgress();

    @Accessor("destroyProgress")
    void aerial$setDestroyProgress(float value);

    @Accessor("destroyDelay")
    int aerial$getDestroyDelay();

    @Accessor("destroyDelay")
    void aerial$setDestroyDelay(int value);

    @Accessor("destroyBlockPos")
    BlockPos aerial$getDestroyBlockPos();

    @Accessor("isDestroying")
    boolean aerial$isDestroying();
}
