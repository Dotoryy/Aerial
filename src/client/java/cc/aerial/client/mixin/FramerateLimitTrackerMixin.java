package cc.aerial.client.mixin;

import com.mojang.blaze3d.platform.FramerateLimitTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(FramerateLimitTracker.class)
public class FramerateLimitTrackerMixin {
    private static final int AERIAL_MENU_LIMIT = 240;

    @Shadow
    private int framerateLimit;

    @ModifyConstant(method = "getFramerateLimit", constant = @Constant(intValue = 60))
    private int aerial$menuFramerateLimit(int original) {
        return Math.min(framerateLimit, AERIAL_MENU_LIMIT);
    }
}
