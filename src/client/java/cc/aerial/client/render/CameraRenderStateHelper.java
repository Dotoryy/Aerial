package cc.aerial.client.render;

import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.jetbrains.annotations.Nullable;

public final class CameraRenderStateHelper {
    @Nullable
    private static CameraRenderState current;

    private CameraRenderStateHelper() {
    }

    public static void set(CameraRenderState state) {
        current = state;
    }

    @Nullable
    public static CameraRenderState get() {
        return current;
    }
}
