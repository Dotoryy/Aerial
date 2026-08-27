package cc.aerial.client.features.impl.hud;

import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.render.AerialBloomFilter;
import cc.aerial.client.render.AerialBlur;
import cc.aerial.client.render.BlurConsumer;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.utility.ScaffoldBlockFilter;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.features.impl.world.ScaffoldModule;
import cc.aerial.client.screen.animation.Animation;
import cc.aerial.client.screen.animation.Easing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

public final class ScaffoldBlockCounter implements IEventSubscriber {
    public static final ScaffoldBlockCounter INSTANCE = new ScaffoldBlockCounter();

    private static final float CENTER_OFFSET = 34.0f;

    private static final float VERTICAL_OFFSET = 45.0f;

    private static final float ITEM_SCALE = 0.625f;
    private static final float ITEM_SIZE = 16.0f * ITEM_SCALE;
    private static final float ICON_GAP = 4.0f;
    private static final float TEXT_SIZE = 8.0f;

    private static final float PADDING_X = 5.0f;
    private static final float PADDING_Y = 3.5f;

    private static final int BACKGROUND_COLOR = 0x80090909;

    private static final float CORNER_RADIUS = 4.0f;

    private static final int COUNT_COLOR = 0xFFFFFFFF;

    private static final int LABEL_COLOR = 0xFFAAAAAA;

    private static final String LABEL_TEXT = " blocks";

    private static final float SLIDE_DISTANCE = 14.0f;

    private static final float VISIBLE_THRESHOLD = 0.01f;

    private final Animation visibility = new Animation(Easing.EASE_IN_OUT_CUBIC, 320);

    private ItemStack lastIcon = ItemStack.EMPTY;
    private int lastCount;

    private ScaffoldBlockCounter() {
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        AerialBloomFilter.begin(BlurConsumer.SCAFFOLD_COUNTER);
        try {
            renderBody(event);
        } finally {
            AerialBloomFilter.end();
        }
    }

    private void renderBody(Render2DEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            visibility.setValue(0.0f);
            return;
        }

        ItemStack icon = ItemStack.EMPTY;
        int count = 0;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!ScaffoldBlockFilter.isPlaceable(stack)) {
                continue;
            }
            if (icon.isEmpty()) {
                icon = stack;
            }
            count += stack.getCount();
        }

        boolean shown = ScaffoldModule.INSTANCE.isEnabled() && !icon.isEmpty();
        if (shown) {
            lastIcon = icon;
            lastCount = count;
        }
        visibility.run(shown ? 1.0f : 0.0f);

        float fade = visibility.getValue();
        if (fade <= VISIBLE_THRESHOLD || lastIcon.isEmpty()) {
            return;
        }
        icon = lastIcon;
        count = lastCount;

        ensureFontLoaded();
        GuiGraphicsExtractor extractor = event.extractor();

        String countText = Integer.toString(count);
        float countWidth = font.stringWidth(countText, TEXT_SIZE);
        float labelWidth = font.stringWidth(LABEL_TEXT, TEXT_SIZE);
        float textHeight = font.height(TEXT_SIZE);

        float contentWidth = ITEM_SIZE + ICON_GAP + countWidth + labelWidth;
        float contentHeight = Math.max(ITEM_SIZE, textHeight);
        float width = contentWidth + PADDING_X * 2.0f;
        float height = contentHeight + PADDING_Y * 2.0f;

        float x = event.width() * 0.5f + CENTER_OFFSET - SLIDE_DISTANCE * (1.0f - fade);
        float y = event.height() * 0.5f + VERTICAL_OFFSET - height * 0.5f;

        AerialBlur.drawGlass(extractor, BlurConsumer.SCAFFOLD_COUNTER,
                x, y, width, height, CORNER_RADIUS, BACKGROUND_COLOR, fade, null);

        float panelX = AerialBlur.snap(x);
        float centerY = AerialBlur.snap(y) + AerialBlur.snap(height) * 0.5f;

        float iconX = panelX + PADDING_X;
        float itemScale = ITEM_SCALE * fade;
        extractor.pose().pushMatrix();
        extractor.pose().translate(
                iconX + ITEM_SIZE * 0.5f - 8.0f * itemScale,
                centerY - 8.0f * itemScale);
        extractor.pose().scale(itemScale, itemScale);
        extractor.item(icon, 0, 0);
        extractor.pose().popMatrix();

        float textX = iconX + ITEM_SIZE + ICON_GAP;
        float textY = Math.round(centerY - textHeight * 0.5f);
        TextRenderUtil.drawString(extractor, font, countText, textX, textY, TEXT_SIZE,
                fade(COUNT_COLOR, fade));
        TextRenderUtil.drawString(extractor, font, LABEL_TEXT,
                textX + countWidth, textY, TEXT_SIZE, fade(LABEL_COLOR, fade));
    }

    private static int fade(int argb, float factor) {
        int alpha = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, factor)));
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    private AerialFont font;

    private void ensureFontLoaded() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
        }
    }
}
