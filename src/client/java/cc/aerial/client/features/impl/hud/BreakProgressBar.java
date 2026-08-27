package cc.aerial.client.features.impl.hud;

import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.features.impl.visual.InterfaceModule;
import cc.aerial.client.features.impl.world.BreakerModule;
import cc.aerial.client.mixin.MultiPlayerGameModeAccessor;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.screen.animation.Animation;
import cc.aerial.client.screen.animation.Easing;
import cc.aerial.client.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;

public final class BreakProgressBar {
    private static final float BAR_WIDTH = 170.0f;
    private static final float BAR_HEIGHT = 7.5f;

    private static final float CENTER_OFFSET = 70.0f;

    private static final int BAR_TRACK_COLOR = 0x40FFFFFF;

    private static Animation progressAnimation;

    private BreakProgressBar() {
    }

    public static void reset() {
        progressAnimation = null;
    }

    public static void render(Render2DEvent event) {
        BlockPos target = BreakerModule.INSTANCE.getCurrentTarget();
        Minecraft mc = Minecraft.getInstance();
        if (target == null || mc.level == null) {
            return;
        }

        GuiGraphicsExtractor extractor = event.extractor();
        Theme theme = InterfaceModule.INSTANCE.getTheme();
        int accent = theme.getAccentColor(0, 0).getRGB() | 0xFF000000;

        float breakProgress = Math.min(1.0f,
                ((MultiPlayerGameModeAccessor) mc.gameMode).aerial$getDestroyProgress());

        if (progressAnimation == null) {
            progressAnimation = new Animation(Easing.EASE_OUT_EXPO, 200);
            progressAnimation.setValue(breakProgress * BAR_WIDTH);
        } else {
            progressAnimation.run(breakProgress * BAR_WIDTH);
        }

        float barX = Math.round((event.width() - BAR_WIDTH) * 0.5f);
        float barY = Math.round(event.height() * 0.5f + CENTER_OFFSET);

        RenderUtil.roundedRect(extractor, barX, barY, BAR_WIDTH, BAR_HEIGHT, BAR_HEIGHT * 0.5f, BAR_TRACK_COLOR);
        float filled = progressAnimation.getValue();
        if (filled > 0.0f) {
            RenderUtil.roundedRect(extractor, barX, barY, filled, BAR_HEIGHT, BAR_HEIGHT * 0.5f, accent);
        }
    }
}
