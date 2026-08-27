package cc.aerial.client.utility;

public final class HudDrag {
    public static final float KEEP_VISIBLE = 12.0f;

    private HudDrag() {
    }

    public static float clamp(float position, float size, float screenSize) {
        float min = KEEP_VISIBLE - size;
        float max = screenSize - KEEP_VISIBLE;
        return Math.max(min, Math.min(position, max));
    }
}
