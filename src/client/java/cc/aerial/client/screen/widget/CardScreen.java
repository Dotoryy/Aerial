package cc.aerial.client.screen.widget;

import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.screen.title.TitleBackground;
import cc.aerial.client.theme.Theme;
import cc.aerial.client.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

public abstract class CardScreen extends Screen {
    protected static final float CARD_RADIUS = 8.0f;
    protected static final float PADDING = 14.0f;
    protected static final float TITLE_SIZE = 15.0f;
    protected static final float STATUS_SIZE = 8.0f;
    protected static final float ACTION_SIZE = 8.5f;
    protected static final float ACTION_GAP = 16.0f;

    private static final int CARD_COLOR = 0xE60D0D14;
    private static final int STATUS_COLOR = 0xFF9698A4;
    private static final int STATUS_ERROR = 0xFFE05A5A;
    private static final int ACTION_COLOR = 0xFFB9BAC4;
    private static final int ACTION_DISABLED = 0xFF55565E;

    protected record CardAction(String label, Runnable action, BooleanSupplier enabled) {
        CardAction(String label, Runnable action) {
            this(label, action, () -> true);
        }
    }

    protected final @Nullable Screen previousScreen;
    private final List<CardAction> actions = new ArrayList<>();
    private final String cardTitle;

    protected AerialFont cardFont;
    protected AerialFont cardBoldFont;

    protected final AerialFont cardFont() {
        if (cardFont == null) {
            cardFont = AerialFont.createFromResource("OpalProductSansMedium.ttf");
            cardBoldFont = AerialFont.createFromResource("OpalProductSansBold.ttf");
        }
        return cardFont;
    }
    private String status = "";
    private boolean statusIsError;

    protected CardScreen(String title, @Nullable Screen previousScreen) {
        super(Component.literal(title));
        this.cardTitle = title;
        this.previousScreen = previousScreen;
    }

    protected float cardWidth() {
        return 300.0f;
    }

    protected abstract float contentHeight();

    protected final float cardHeight() {
        return PADDING * 2 + TITLE_SIZE + 6.0f + STATUS_SIZE + 12.0f
                + contentHeight() + 12.0f + ACTION_SIZE;
    }

    protected final float cardLeft() {
        return (width - cardWidth()) * 0.5f;
    }

    protected final float cardTop() {
        return (height - cardHeight()) * 0.5f;
    }

    protected final float contentTop() {
        return cardTop() + PADDING + TITLE_SIZE + 6.0f + STATUS_SIZE + 12.0f;
    }

    protected final float contentLeft() {
        return cardLeft() + PADDING;
    }

    protected final float contentWidth() {
        return cardWidth() - PADDING * 2;
    }

    private float actionsY() {
        return contentTop() + contentHeight() + 12.0f;
    }

    protected final void clearActions() {
        actions.clear();
    }

    protected final void addAction(String label, Runnable action) {
        actions.add(new CardAction(label, action));
    }

    protected final void addAction(String label, Runnable action, BooleanSupplier enabled) {
        actions.add(new CardAction(label, action, enabled));
    }

    protected void addBackAction() {
        addAction("Cancel", this::onClose);
    }

    protected final void setStatus(String formatted) {
        if (formatted == null) {
            this.status = "";
            return;
        }
        this.statusIsError = formatted.contains("&c") || formatted.contains("§c");
        this.status = formatted.replaceAll("[&§][0-9a-fk-orA-FK-OR]", "").trim();
    }

    protected final String getStatus() {
        return status;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        cardFont();
        TitleBackground.draw(extractor, width, height);

        Theme theme = ThemeManager.getTheme();
        int accent = theme.getAccentColor(0, 0).getRGB() | 0xFF000000;
        int accentLeft = theme.getAccentColor(0, 50).getRGB() | 0xFF000000;

        RenderUtil.roundedRect(extractor, cardLeft(), cardTop(), cardWidth(), cardHeight(),
                CARD_RADIUS, CARD_COLOR);
        TextRenderUtil.drawGradientString(extractor, cardBoldFont, cardTitle.toLowerCase(java.util.Locale.ROOT),
                contentLeft(), cardTop() + PADDING, TITLE_SIZE, accentLeft, accent);
        if (!status.isEmpty()) {
            TextRenderUtil.drawString(extractor, cardFont, status,
                    contentLeft(), cardTop() + PADDING + TITLE_SIZE + 6.0f, STATUS_SIZE,
                    statusIsError ? STATUS_ERROR : STATUS_COLOR);
        }

        drawCardContent(extractor, mouseX, mouseY, partialTick);
        drawActions(extractor, mouseX, mouseY);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    protected void drawCardContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
    }

    protected final void drawInputFrame(GuiGraphicsExtractor extractor, float x, float y,
                                        float width, float height, boolean focused) {
        if (focused) {
            int accent = ThemeManager.getTheme().getAccentColor(0, 0).getRGB() | 0xFF000000;
            RenderUtil.roundedRect(extractor, x - 1.0f, y - 1.0f, width + 2.0f, height + 2.0f,
                    5.0f, accent);
        }
        RenderUtil.roundedRect(extractor, x, y, width, height, 4.5f, 0x66101018);
    }

    private void drawActions(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        float y = actionsY();
        float cursor = contentLeft();
        for (CardAction action : actions) {
            float labelWidth = cardFont.stringWidth(action.label(), ACTION_SIZE);
            boolean enabled = action.enabled().getAsBoolean();
            boolean hovered = enabled && inside(mouseX, mouseY, cursor - 4.0f, y - 4.0f,
                    labelWidth + 8.0f, ACTION_SIZE + 8.0f);
            TextRenderUtil.drawString(extractor, cardFont, action.label(), cursor, y, ACTION_SIZE,
                    !enabled ? ACTION_DISABLED : hovered ? 0xFFFFFFFF : ACTION_COLOR);
            cursor += labelWidth + ACTION_GAP;
        }
    }

    protected static boolean inside(double mouseX, double mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        float y = actionsY();
        float cursor = contentLeft();
        for (CardAction action : actions) {
            float labelWidth = cardFont == null ? 0.0f : cardFont.stringWidth(action.label(), ACTION_SIZE);
            if (inside(event.x(), event.y(), cursor - 4.0f, y - 4.0f,
                    labelWidth + 8.0f, ACTION_SIZE + 8.0f)) {
                if (action.enabled().getAsBoolean()) {
                    action.action().run();
                }
                return true;
            }
            cursor += labelWidth + ACTION_GAP;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(previousScreen);
    }

    @Override
    protected void extractMenuBackground(GuiGraphicsExtractor extractor) {
    }
}
