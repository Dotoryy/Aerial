package cc.aerial.client.render.particle;

import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.theme.ColorUtil;
import cc.aerial.client.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class HudParticle {
    private float x;
    private float y;
    private float velocityX;
    private float velocityY;
    private final float scale;
    private final int baseColor;

    private float alpha;
    private long lastUpdate = System.currentTimeMillis();
    private final long spawned = System.currentTimeMillis();

    public HudParticle(float x, float y, float velocityX, float velocityY) {
        this.x = x;
        this.y = y;
        this.velocityX = velocityX;
        this.velocityY = velocityY;
        this.scale = (float) (2.0 + Math.random() * 3.0);

        int blended = ColorUtil.mixColors(
                ThemeManager.getTheme().getFirstColor(),
                ThemeManager.getTheme().getSecondColor(),
                Math.random()).getRGB();
        int startAlpha = (int) (Math.random() * 255.0);
        this.baseColor = (blended & 0x00FFFFFF) | (startAlpha << 24);
        this.alpha = startAlpha;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - spawned > 3000L;
    }

    public void update() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastUpdate;
        for (int i = 0; i <= elapsed; i++) {
            x += velocityX / 10.0f;
            y += velocityY / 10.0f;
            velocityX *= 0.999f;
            velocityY *= 0.999f;
        }
        alpha = Math.max(alpha - elapsed / 18.0f, 0.0f);
        lastUpdate = now;
    }

    public void draw(GuiGraphicsExtractor extractor) {
        RenderUtil.roundedRect(extractor, x, y, scale, scale, scale / 2.0f, withAlpha((int) alpha), null);
    }

    public void drawGlow(GuiGraphicsExtractor extractor) {
        RenderUtil.roundedRect(extractor, x, y, scale, scale, scale / 2.0f, withAlpha((int) alpha * 3), null);
    }

    private int withAlpha(int value) {
        return (baseColor & 0x00FFFFFF) | (Math.max(0, Math.min(255, value)) << 24);
    }
}
