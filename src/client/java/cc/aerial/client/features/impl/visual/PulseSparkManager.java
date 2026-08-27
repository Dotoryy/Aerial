package cc.aerial.client.features.impl.visual;

import cc.aerial.client.render.RenderUtil;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

final class PulseSparkManager {
    private PulseSparkManager() {
    }

    private static final List<PulseSpark> sparks = new ArrayList<>();
    private static long lastTickMs = System.currentTimeMillis();

    static void add(PulseSpark spark) {
        sparks.add(spark);
    }

    static void render(GuiGraphicsExtractor extractor) {
        long now = System.currentTimeMillis();
        float deltaSeconds = Math.min(0.1f, (now - lastTickMs) / 1000f);
        lastTickMs = now;

        Iterator<PulseSpark> it = sparks.iterator();
        while (it.hasNext()) {
            PulseSpark spark = it.next();
            if (!spark.tick(deltaSeconds)) {
                it.remove();
                continue;
            }
            float size = spark.size();
            RenderUtil.roundedRect(extractor, spark.x() - size / 2f, spark.y() - size / 2f,
                    size, size, size / 2f, spark.colorArgb());
        }
    }
}
