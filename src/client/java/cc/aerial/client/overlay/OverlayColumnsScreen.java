package cc.aerial.client.overlay;

import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class OverlayColumnsScreen extends Screen {
    private static final float PANEL_WIDTH = 190.0f;
    private static final float ROW_HEIGHT = 20.0f;
    private static final float PADDING = 10.0f;
    private static final float TITLE_SIZE = 12.0f;
    private static final float ROW_SIZE = 9.0f;
    private static final float HINT_SIZE = 7.5f;
    private static final float RADIUS = 6.0f;

    private static final int PANEL_COLOR = 0xE6141414;
    private static final int ROW_COLOR = 0x1AFFFFFF;
    private static final int ROW_HOVER_COLOR = 0x33FFFFFF;
    private static final int ON_COLOR = 0xFF6EE787;
    private static final int OFF_COLOR = 0xFF6E6E6E;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int HINT_COLOR = 0xFF8A8A8A;

    private final @Nullable Screen previousScreen;
    private AerialFont font;

    private OverlayColumn dragging;

    public OverlayColumnsScreen(@Nullable Screen previousScreen) {
        super(Component.literal("Columns"));
        this.previousScreen = previousScreen;
    }

    private void ensureFont() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
        }
    }

    private float panelHeight() {
        return PADDING * 2 + TITLE_SIZE + 6.0f + OverlayColumn.VALUES.length * ROW_HEIGHT + 4.0f + HINT_SIZE;
    }

    private float panelLeft() {
        return (width - PANEL_WIDTH) * 0.5f;
    }

    private float panelTop() {
        return (height - panelHeight()) * 0.5f;
    }

    private float rowTop(int index) {
        return panelTop() + PADDING + TITLE_SIZE + 6.0f + index * ROW_HEIGHT;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        ensureFont();
        float left = panelLeft();
        float top = panelTop();

        RenderUtil.roundedRect(extractor, left, top, PANEL_WIDTH, panelHeight(), RADIUS, PANEL_COLOR);
        TextRenderUtil.drawString(extractor, font, "Overlay Columns",
                left + PADDING, top + PADDING, TITLE_SIZE, TEXT_COLOR);

        OverlayColumn[] order = OverlayColumn.VALUES;
        for (int i = 0; i < order.length; i++) {
            OverlayColumn column = order[i];
            float rowY = rowTop(i);
            boolean hovered = isInside(mouseX, mouseY, left + PADDING, rowY,
                    PANEL_WIDTH - PADDING * 2, ROW_HEIGHT - 2.0f);

            RenderUtil.roundedRect(extractor, left + PADDING, rowY,
                    PANEL_WIDTH - PADDING * 2, ROW_HEIGHT - 2.0f, 3.0f,
                    hovered || column == dragging ? ROW_HOVER_COLOR : ROW_COLOR);

            float dotSize = 5.0f;
            RenderUtil.roundedRect(extractor, left + PADDING + 7.0f,
                    rowY + (ROW_HEIGHT - 2.0f - dotSize) * 0.5f, dotSize, dotSize, dotSize * 0.5f,
                    column.isEnabled() ? ON_COLOR : OFF_COLOR);

            float textY = rowY + (ROW_HEIGHT - 2.0f - ROW_SIZE) * 0.5f;
            TextRenderUtil.drawString(extractor, font, column.getLabel(),
                    left + PADDING + 18.0f, textY, ROW_SIZE,
                    column.isEnabled() ? TEXT_COLOR : HINT_COLOR);
            TextRenderUtil.drawString(extractor, font, column.getHeader(),
                    left + PANEL_WIDTH - PADDING - 8.0f - font.stringWidth(column.getHeader(), ROW_SIZE),
                    textY, ROW_SIZE, HINT_COLOR);
        }

        TextRenderUtil.drawString(extractor, font, "Click to toggle  -  Drag to reorder",
                left + PADDING, top + panelHeight() - PADDING - HINT_SIZE, HINT_SIZE, HINT_COLOR);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            int index = rowAt(event.y());
            if (index >= 0 && isInside(event.x(), event.y(), panelLeft() + PADDING, rowTop(index),
                    PANEL_WIDTH - PADDING * 2, ROW_HEIGHT - 2.0f)) {
                dragging = OverlayColumn.VALUES[index];
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (dragging == null) {
            return super.mouseReleased(event);
        }
        OverlayColumn held = dragging;
        dragging = null;

        int from = indexOf(held);
        int to = rowAt(event.y());
        if (to < 0) {
            to = event.y() < rowTop(0) ? 0 : OverlayColumn.VALUES.length - 1;
        }
        if (to == from) {
            held.setEnabled(!held.isEnabled());
        } else {
            move(from, to);
        }
        return true;
    }

    private static void move(int from, int to) {
        OverlayColumn[] order = OverlayColumn.VALUES;
        OverlayColumn moved = order[from];
        if (from < to) {
            System.arraycopy(order, from + 1, order, from, to - from);
        } else {
            System.arraycopy(order, to, order, to + 1, from - to);
        }
        order[to] = moved;
    }

    private static int indexOf(OverlayColumn column) {
        for (int i = 0; i < OverlayColumn.VALUES.length; i++) {
            if (OverlayColumn.VALUES[i] == column) {
                return i;
            }
        }
        return 0;
    }

    private int rowAt(double mouseY) {
        for (int i = 0; i < OverlayColumn.VALUES.length; i++) {
            float rowY = rowTop(i);
            if (mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT - 2.0f) {
                return i;
            }
        }
        return -1;
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
