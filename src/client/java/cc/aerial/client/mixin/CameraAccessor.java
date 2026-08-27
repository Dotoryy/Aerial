package cc.aerial.client.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("eyeHeight")
    void aerial$setEyeHeight(float value);

    @Accessor("eyeHeightOld")
    void aerial$setEyeHeightOld(float value);
}
