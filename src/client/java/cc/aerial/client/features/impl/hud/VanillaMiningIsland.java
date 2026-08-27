package cc.aerial.client.features.impl.hud;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.impl.visual.InterfaceModule;
import cc.aerial.client.features.impl.world.BreakerModule;
import cc.aerial.client.mixin.MultiPlayerGameModeAccessor;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.screen.animation.Animation;
import cc.aerial.client.screen.animation.Easing;
import cc.aerial.client.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public final class VanillaMiningIsland implements IslandTrigger, IEventSubscriber {
    public static final VanillaMiningIsland INSTANCE = new VanillaMiningIsland();

    private static final float ISLAND_WIDTH = 140.0f;
    private static final float ISLAND_HEIGHT = 25.0f;
    private static final float SWATCH_SIZE = 17.0f;
    private static final float BAR_WIDTH = 85.0f;

    private static final float BAR_HEIGHT = 3.0f;
    private static final float PADDING = 5.5f;

    private Animation progressAnimation;

    private VanillaMiningIsland() {
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        if (BreakerModule.INSTANCE.isBreaking()) {
            DynamicIsland.removeTrigger(this);
            return;
        }

        var gameMode = Minecraft.getInstance().gameMode;
        if (gameMode == null) {
            DynamicIsland.removeTrigger(this);
            return;
        }
        float progress = ((MultiPlayerGameModeAccessor) gameMode).aerial$getDestroyProgress();
        if (progress > 0.0f) {
            DynamicIsland.addTrigger(this);
        } else {
            DynamicIsland.removeTrigger(this);
            progressAnimation = null;
        }
    }

    @Override
    public float getIslandWidth() {
        return ISLAND_WIDTH;
    }

    @Override
    public float getIslandHeight() {
        return ISLAND_HEIGHT;
    }

    @Override
    public int getIslandPriority() {
        return 1;
    }

    @Override
    public void renderIsland(GuiGraphicsExtractor extractor, float x, float y, float width, float height, float progress) {
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        var gameMode = mc.gameMode;
        if (level == null || gameMode == null) {
            return;
        }
        BlockPos target = ((MultiPlayerGameModeAccessor) gameMode).aerial$getDestroyBlockPos();
        if (target == null) {
            return;
        }

        Theme theme = InterfaceModule.INSTANCE.getTheme();
        int accent = theme.getAccentColor(0, 0).getRGB() | 0xFF000000;

        float swatchX = x + PADDING;
        float swatchY = y + (height - SWATCH_SIZE) * 0.5f;
        RenderUtil.roundedRect(extractor, swatchX, swatchY, SWATCH_SIZE, SWATCH_SIZE, SWATCH_SIZE * 0.5f, withAlpha(accent, 0.5f));

        ItemStack targetItem = level.getBlockState(target).getBlock().asItem().getDefaultInstance();
        if (!targetItem.isEmpty()) {
            float itemScale = 0.75f;
            extractor.pose().pushMatrix();
            extractor.pose().translate(swatchX + SWATCH_SIZE * 0.5f - 8f * itemScale, swatchY + SWATCH_SIZE * 0.5f - 8f * itemScale);
            extractor.pose().scale(itemScale, itemScale);
            extractor.item(targetItem, 0, 0);
            extractor.pose().popMatrix();
        }

        float barX = swatchX + SWATCH_SIZE + 5.0f;
        float barY = y + (height - BAR_HEIGHT) * 0.5f + 0.5f;

        float breakProgress = Math.min(1.0f, ((MultiPlayerGameModeAccessor) gameMode).aerial$getDestroyProgress());

        if (progressAnimation == null) {
            progressAnimation = new Animation(Easing.EASE_OUT_EXPO, 200);
            progressAnimation.setValue(breakProgress * BAR_WIDTH);
        } else {
            progressAnimation.run(breakProgress * BAR_WIDTH);
        }

        RenderUtil.roundedRect(extractor, barX, barY, BAR_WIDTH, BAR_HEIGHT, BAR_HEIGHT * 0.5f, 0x40FFFFFF);
        if (breakProgress > 0.0f) {
            RenderUtil.roundedRect(extractor, barX, barY, progressAnimation.getValue(), BAR_HEIGHT, BAR_HEIGHT * 0.5f, accent);
        }

        ensureFontLoaded();
        String percentText = (int) (breakProgress * 100) + "%";
        TextRenderUtil.drawString(extractor, font, percentText,
                barX + BAR_WIDTH + 6.0f, y + (height - 7f) * 0.5f, 7f, withAlpha(0xFFFFFFFF, progress));
    }

    private static AerialFont font;

    private static void ensureFontLoaded() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
        }
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0x00FFFFFF);
    }
}
