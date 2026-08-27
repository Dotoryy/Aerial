package cc.aerial.client.theme;

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

public final class ThemeScreen extends Screen {
    private static final int COLUMNS = 3;
    private static final float CELL_WIDTH = 96.0f;
    private static final float CELL_HEIGHT = 26.0f;
    private static final float CELL_GAP = 5.0f;
    private static final float PADDING = 12.0f;
    private static final float TITLE_SIZE = 12.0f;
    private static final float NAME_SIZE = 8.0f;
    private static final float HINT_SIZE = 7.5f;
    private static final float RADIUS = 6.0f;
    private static final float CELL_RADIUS = 3.5f;

    private static final float BAND_WIDTH = 3.0f;

    private static final int VISIBLE_ROWS = 9;

    private static final int PANEL_COLOR = 0xE6141414;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int HINT_COLOR = 0xFF8A8A8A;
    private static final int SELECTED_BORDER = 0xFFFFFFFF;
    private static final int HOVER_BORDER = 0x66FFFFFF;

    private static final Theme[] THEMES = Theme.values();

    private final @Nullable Screen previousScreen;
    private AerialFont font;
    private float scroll;

    public ThemeScreen(@Nullable Screen previousScreen) {
        super(Component.literal("Themes"));
        this.previousScreen = previousScreen;
    }

    private void ensureFont() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
        }
    }

    private static int rowCount() {
        return (THEMES.length + COLUMNS - 1) / COLUMNS;
    }

    private static float gridWidth() {
        return COLUMNS * CELL_WIDTH + (COLUMNS - 1) * CELL_GAP;
    }

    private static float gridHeight() {
        return VISIBLE_ROWS * CELL_HEIGHT + (VISIBLE_ROWS - 1) * CELL_GAP;
    }

    private static float panelWidth() {
        return gridWidth() + PADDING * 2;
    }

    private static float panelHeight() {
        return PADDING * 2 + TITLE_SIZE + 8.0f + gridHeight() + 6.0f + HINT_SIZE;
    }

    private float panelLeft() {
        return (width - panelWidth()) * 0.5f;
    }

    private float panelTop() {
        return (height - panelHeight()) * 0.5f;
    }

    private float gridTop() {
        return panelTop() + PADDING + TITLE_SIZE + 8.0f;
    }

    private static float maxScroll() {
        float content = rowCount() * CELL_HEIGHT + (rowCount() - 1) * CELL_GAP;
        return Math.max(0.0f, content - gridHeight());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        ensureFont();
        float left = panelLeft();
        float top = panelTop();

        RenderUtil.roundedRect(extractor, left, top, panelWidth(), panelHeight(), RADIUS, PANEL_COLOR);
        TextRenderUtil.drawString(extractor, font, "Themes",
                left + PADDING, top + PADDING, TITLE_SIZE, TEXT_COLOR);

        ScreenRectangle clip = new ScreenRectangle(
                Math.round(left + PADDING), Math.round(gridTop()),
                Math.round(gridWidth()), Math.round(gridHeight()));

        Theme current = ThemeManager.getTheme();
        for (int i = 0; i < THEMES.length; i++) {
            float cellX = left + PADDING + (i % COLUMNS) * (CELL_WIDTH + CELL_GAP);
            float cellY = gridTop() + (i / COLUMNS) * (CELL_HEIGHT + CELL_GAP) - scroll;

            if (cellY + CELL_HEIGHT < gridTop() || cellY > gridTop() + gridHeight()) {
                continue;
            }
            boolean hovered = isInside(mouseX, mouseY, cellX, cellY, CELL_WIDTH, CELL_HEIGHT)
                    && mouseY >= gridTop() && mouseY <= gridTop() + gridHeight();
            drawCell(extractor, THEMES[i], cellX, cellY, THEMES[i] == current, hovered, clip);
        }

        TextRenderUtil.drawString(extractor, font, "Click to apply  -  Scroll for more",
                left + PADDING, top + panelHeight() - PADDING - HINT_SIZE, HINT_SIZE, HINT_COLOR);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void drawCell(GuiGraphicsExtractor extractor, Theme theme, float x, float y,
                          boolean selected, boolean hovered, ScreenRectangle clip) {
        if (selected || hovered) {
            RenderUtil.roundedRect(extractor, x - 1.0f, y - 1.0f, CELL_WIDTH + 2.0f, CELL_HEIGHT + 2.0f,
                    CELL_RADIUS + 1.0f, selected ? SELECTED_BORDER : HOVER_BORDER, clip);
        }
        RenderUtil.roundedRect(extractor, x, y, CELL_WIDTH, CELL_HEIGHT, CELL_RADIUS, 0xFF1A1A1A, clip);

        float stripX = x + 4.0f;
        float stripY = y + 4.0f;
        float stripWidth = CELL_WIDTH - 8.0f;
        float stripHeight = 8.0f;
        for (float offset = 0.0f; offset < stripWidth; offset += BAND_WIDTH) {
            float bandWidth = Math.min(BAND_WIDTH, stripWidth - offset);
            int color = theme.getAccentColor(offset, 0.0).getRGB();
            RenderUtil.flatRect(extractor, stripX + offset, stripY, bandWidth, stripHeight, color, clip);
        }

        TextRenderUtil.drawString(extractor, font, theme.getThemeName(),
                x + 4.0f, y + stripHeight + 8.0f, NAME_SIZE,
                selected ? TEXT_COLOR : 0xFFCFCFCF, clip);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0 && event.y() >= gridTop() && event.y() <= gridTop() + gridHeight()) {
            float left = panelLeft();
            for (int i = 0; i < THEMES.length; i++) {
                float cellX = left + PADDING + (i % COLUMNS) * (CELL_WIDTH + CELL_GAP);
                float cellY = gridTop() + (i / COLUMNS) * (CELL_HEIGHT + CELL_GAP) - scroll;
                if (isInside(event.x(), event.y(), cellX, cellY, CELL_WIDTH, CELL_HEIGHT)) {
                    ThemeManager.setTheme(THEMES[i]);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = (float) Math.max(0.0, Math.min(maxScroll(), scroll - verticalAmount * (CELL_HEIGHT + CELL_GAP)));
        return true;
    }

    private static boolean isInside(double mouseX, double mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
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
