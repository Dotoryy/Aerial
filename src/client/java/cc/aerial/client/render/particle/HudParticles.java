package cc.aerial.client.render.particle;

import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public final class HudParticles {
    private static final Queue<HudParticle> PARTICLES = new ConcurrentLinkedQueue<>();

    private static final int MAX_PARTICLES = 512;

    private HudParticles() {
    }

    public static void spawn(HudParticle particle) {
        if (PARTICLES.size() < MAX_PARTICLES) {
            PARTICLES.add(particle);
        }
    }

    public static void drawGlow(GuiGraphicsExtractor extractor) {
        for (HudParticle particle : PARTICLES) {
            particle.drawGlow(extractor);
        }
    }

    public static void draw(GuiGraphicsExtractor extractor) {
        PARTICLES.removeIf(HudParticle::isExpired);
        for (HudParticle particle : PARTICLES) {
            particle.update();
            particle.draw(extractor);
        }
    }

    public static boolean isEmpty() {
        return PARTICLES.isEmpty();
    }

    public static void clear() {
        PARTICLES.clear();
    }
}
