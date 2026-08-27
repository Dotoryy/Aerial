package cc.aerial.client.render;

import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;

public final class ESPUtility {
    private ESPUtility() {
    }

    public record ScreenBox(float x, float y, float width, float height) {
    }

    @Nullable
    public static Vector4f project(Vec3 worldPos, CameraRenderState camera, int screenWidth, int screenHeight) {
        Vec3 relative = worldPos.subtract(camera.pos);
        Vector4f clip = new Vector4f((float) relative.x, (float) relative.y, (float) relative.z, 1.0f);

        Matrix4f viewProjection = new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix);
        viewProjection.transform(clip);

        if (clip.w <= 0.3f) {
            return null;
        }

        float ndcX = clip.x / clip.w;
        float ndcY = clip.y / clip.w;
        if (Math.abs(ndcX) > 3.0f || Math.abs(ndcY) > 3.0f) {
            return null;
        }
        float screenX = (ndcX + 1.0f) / 2.0f * screenWidth;
        float screenY = (1.0f - ndcY) / 2.0f * screenHeight;
        return new Vector4f(screenX, screenY, 0.0f, 0.0f);
    }

    @Nullable
    public static ScreenBox project(AABB box, CameraRenderState camera, int screenWidth, int screenHeight) {
        float minX = Float.MAX_VALUE, minY = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        boolean any = false;

        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? box.minX : box.maxX;
            double y = (i & 2) == 0 ? box.minY : box.maxY;
            double z = (i & 4) == 0 ? box.minZ : box.maxZ;
            Vector4f screen = project(new Vec3(x, y, z), camera, screenWidth, screenHeight);
            if (screen == null) {
                continue;
            }
            any = true;
            minX = Math.min(minX, screen.x);
            minY = Math.min(minY, screen.y);
            maxX = Math.max(maxX, screen.x);
            maxY = Math.max(maxY, screen.y);
        }

        if (!any) {
            return null;
        }

        float width = maxX - minX;
        float height = maxY - minY;
        if (width > screenWidth * 4.0f || height > screenHeight * 4.0f) {
            return null;
        }

        return new ScreenBox(minX, minY, width, height);
    }
}
