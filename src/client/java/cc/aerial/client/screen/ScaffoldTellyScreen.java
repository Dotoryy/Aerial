package cc.aerial.client.screen;

import cc.aerial.client.property.Property;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class ScaffoldTellyScreen extends Screen {
    private static final float PADDING = 10.0f;
    private static final float RADIUS = 6.0f;
    private static final float TITLE_SIZE = 11.0f;

    private static final float PANEL_WIDTH = 240.0f;
    private static final float MAX_PANEL_HEIGHT = 260.0f;
    private static final int PANEL_COLOR = 0xF01A1A1E;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final @Nullable Screen previousScreen;
    private final SimplePropertyPanel panel;
    private AerialFont font;
    private float scroll;
    private float contentHeight;

    public ScaffoldTellyScreen(@Nullable Screen previousScreen, Property<?>[] properties) {
        super(Component.literal("Telly"));
        this.previousScreen = previousScreen;
        this.panel = new SimplePropertyPanel(properties);
    }

    private void ensureFont() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
        }
    }

    private float panelHeight() {
        return Math.min(MAX_PANEL_HEIGHT, PADDING * 2.0f + TITLE_SIZE + PADDING + contentHeight);
    }

    private float panelLeft() {
        return (width - PANEL_WIDTH) / 2.0f;
    }

    private float panelTop() {
        return (height - panelHeight()) / 2.0f;
    }

    private float listTop() {
        return panelTop() + PADDING + TITLE_SIZE + PADDING;
    }

    private float listHeight() {
        return panelHeight() - (PADDING * 2.0f + TITLE_SIZE + PADDING);
    }

    private float listWidth() {
        return PANEL_WIDTH - PADDING * 2.0f;
    }

    private float maxScroll() {
        return Math.max(0.0f, contentHeight - listHeight());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        ensureFont();

        float listX = panelLeft() + PADDING;

        this.contentHeight = panel.measure(listX, listTop() - scroll, listWidth(), mouseX, mouseY);
        this.scroll = Math.max(0.0f, Math.min(maxScroll(), scroll));

        float left = panelLeft();
        float top = panelTop();
        RenderUtil.roundedRect(extractor, left, top, PANEL_WIDTH, panelHeight(), RADIUS, PANEL_COLOR);
        TextRenderUtil.drawString(extractor, font, "Telly",
                left + PADDING, top + PADDING, TITLE_SIZE, TEXT_COLOR);

        ScreenRectangle clip = new ScreenRectangle(
                Math.round(listX), Math.round(listTop()),
                Math.round(listWidth()), Math.round(listHeight()));
        panel.draw(extractor, listX, listTop() - scroll, listWidth(), 1.0f, mouseX, mouseY, clip, true);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        float listX = panelLeft() + PADDING;
        if (panel.mouseClicked(listX, listTop() - scroll, listWidth(), event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        panel.mouseReleased(event.button());
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0.0f, Math.min(maxScroll(), scroll - (float) verticalAmount * 12.0f));
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(previousScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
