package cc.aerial.client.features.impl.visual;

import java.awt.Color;

final class PulseSpark {
    private static final long LIFETIME_MS = 450L;
    private static final float GRAVITY = 260f;

    private float x, y;
    private float vx, vy;
    private final float size;
    private final Color color;
    private final long spawnTime;

    PulseSpark(float x, float y, float vx, float vy, float size, Color color) {
        this.x = x;
        this.y = y;
        this.vx = vx;
        this.vy = vy;
        this.size = size;
        this.color = color;
        this.spawnTime = System.currentTimeMillis();
    }

    boolean tick(float deltaSeconds) {
        vy += GRAVITY * deltaSeconds;
        x += vx * deltaSeconds;
        y += vy * deltaSeconds;
        return System.currentTimeMillis() - spawnTime < LIFETIME_MS;
    }

    float x() {
        return x;
    }

    float y() {
        return y;
    }

    float size() {
        return size;
    }

    int colorArgb() {
        float lifeFrac = 1f - Math.min(1f, (System.currentTimeMillis() - spawnTime) / (float) LIFETIME_MS);
        int alpha = Math.max(0, Math.min(255, (int) (color.getAlpha() * lifeFrac)));
        return (alpha << 24) | (color.getRed() << 16) | (color.getGreen() << 8) | color.getBlue();
    }
}
