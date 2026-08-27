package cc.aerial.client.overlay;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.event.subscriber.IEventSubscriber;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.impl.utility.OverlayModule;
import cc.aerial.client.render.BlurConsumer;
import cc.aerial.client.render.AerialBloomFilter;
import cc.aerial.client.render.AerialBlur;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

public final class OverlayRenderer implements IEventSubscriber {
    public static final OverlayRenderer INSTANCE = new OverlayRenderer();

    private static final float MARGIN = 6.0f;
    private static final float PADDING_X = 6.0f;
    private static final float PADDING_Y = 5.0f;
    private static final float ROW_HEIGHT = 11.0f;
    private static final float HEADER_GAP = 3.0f;
    private static final float TEXT_SIZE = 8.0f;
    private static final float HEADER_SIZE = 7.5f;
    private static final float RADIUS = 4.0f;

    private static final float CELL_GAP = 3.0f;

    private static final int BACKGROUND_COLOR = 0x8C0F0F0F;
    private static final int HEADER_COLOR = 0xFF8A8A8A;
    private static final int TEXT_COLOR = 0xFFE8E8E8;
    private static final int DIVIDER_COLOR = 0x22FFFFFF;
    private static final int PARTY_COLOR = 0xFF55D6FF;
    private static final int NICK_COLOR = 0xFFFF5050;
    private static final int LOADING_COLOR = 0xFF6E6E6E;

    private static final float[] FKDR_STEPS = {1.0f, 3.0f, 6.0f, 10.0f};
    private static final int[] THREAT_COLORS = {
            0xFFAAAAAA, 0xFF6EE787, 0xFFF2D06B, 0xFFFF9F45, 0xFFFF5050
    };

    private AerialFont font;

    private OverlayRenderer() {
        EventDispatcher.subscribe(this);
    }

    @Override
    public boolean isHandlingEvents() {
        return true;
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        OverlayModule module = OverlayModule.INSTANCE;
        if (!module.isOverlayVisible()) {
            return;
        }

        AerialBloomFilter.begin(BlurConsumer.OVERLAY);
        try {
            draw(event, module);
        } finally {
            AerialBloomFilter.end();
        }
    }

    private void draw(Render2DEvent event, OverlayModule module) {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
        }
        List<BedwarsStats> entries = module.getEntries();
        List<OverlayColumn> columns = OverlayModule.activeColumns();
        if (entries.isEmpty() || columns.isEmpty()) {
            return;
        }

        float tableWidth = 0.0f;
        for (OverlayColumn column : columns) {
            tableWidth += column.getWidth();
        }
        float panelWidth = tableWidth + PADDING_X * 2;
        float panelHeight = PADDING_Y * 2 + HEADER_SIZE + HEADER_GAP + entries.size() * ROW_HEIGHT;
        float left = MARGIN;
        float top = (event.height() - panelHeight) * 0.5f;

        GuiGraphicsExtractor extractor = event.extractor();
        AerialBlur.drawGlass(extractor, BlurConsumer.OVERLAY, left, top, panelWidth, panelHeight,
                RADIUS, BACKGROUND_COLOR, 1.0f, null);

        float headerY = top + PADDING_Y;
        float cursorX = left + PADDING_X;
        for (OverlayColumn column : columns) {
            TextRenderUtil.drawString(extractor, font, column.getHeader(),
                    cursorX, headerY, HEADER_SIZE, HEADER_COLOR);
            cursorX += column.getWidth();
        }

        float dividerY = headerY + HEADER_SIZE + HEADER_GAP * 0.5f;
        RenderUtil.sharpRect(extractor, left + PADDING_X, dividerY,
                left + panelWidth - PADDING_X, dividerY + 0.5f, DIVIDER_COLOR);

        float rowY = headerY + HEADER_SIZE + HEADER_GAP;
        for (BedwarsStats stats : entries) {
            drawRow(extractor, module, stats, columns, left + PADDING_X, rowY, tableWidth);
            rowY += ROW_HEIGHT;
        }
    }

    private void drawRow(GuiGraphicsExtractor extractor, OverlayModule module, BedwarsStats stats,
                         List<OverlayColumn> columns, float x, float y, float tableWidth) {
        float textY = y + (ROW_HEIGHT - TEXT_SIZE) * 0.5f;

        if (!stats.isLoaded()) {
            int color = stats.isNicked() ? NICK_COLOR : stats.isError() ? NICK_COLOR : LOADING_COLOR;
            String note = stats.isNicked() ? "NICK" : stats.isError() ? "ERR" : "...";

            float noteWidth = font.stringWidth(note, TEXT_SIZE);
            TextRenderUtil.drawString(extractor, font,
                    fit(stats.getName(), tableWidth - noteWidth - CELL_GAP * 2.0f),
                    x, textY, TEXT_SIZE, nameColor(module, stats));
            TextRenderUtil.drawString(extractor, font, note,
                    x + tableWidth - noteWidth, textY, TEXT_SIZE, color);
            return;
        }

        float cursorX = x;
        for (OverlayColumn column : columns) {
            String value = fit(column.valueOf(stats), column.getWidth() - CELL_GAP);
            int color = switch (column) {
                case NAME -> nameColor(module, stats);
                case STAR -> starColor(stats.getStar());
                case FKDR -> threatColor(stats.getFkdr(), FKDR_STEPS);
                case WLR -> threatColor(stats.getWlr(), FKDR_STEPS);

                case TAG -> OverlayColumn.tagOf(stats).color();
                case DAILY_FKDR -> threatColor(OverlayBordic.session(stats.getUuid()).fkdr(), FKDR_STEPS);
                case DAILY_WLR -> threatColor(OverlayBordic.session(stats.getUuid()).wlr(), FKDR_STEPS);
                case DAILY_STARS -> starGainColor(OverlayBordic.session(stats.getUuid()).starsGained());
                default -> TEXT_COLOR;
            };
            TextRenderUtil.drawString(extractor, font, value, cursorX, textY, TEXT_SIZE, color);
            cursorX += column.getWidth();
        }
    }

    private String fit(String value, float maxWidth) {
        if (font.stringWidth(value, TEXT_SIZE) <= maxWidth) {
            return value;
        }
        float ellipsis = font.stringWidth("..", TEXT_SIZE);
        int length = value.length();
        while (length > 0 && font.stringWidth(value.substring(0, length), TEXT_SIZE) + ellipsis > maxWidth) {
            length--;
        }
        return value.substring(0, length) + "..";
    }

    private static int nameColor(OverlayModule module, BedwarsStats stats) {
        return module.isPartyMember(stats.getUuid()) ? PARTY_COLOR : TEXT_COLOR;
    }

    private static int starColor(int star) {
        if (star >= 1000) {
            return THREAT_COLORS[4];
        }
        if (star >= 500) {
            return THREAT_COLORS[3];
        }
        if (star >= 300) {
            return THREAT_COLORS[2];
        }
        if (star >= 100) {
            return THREAT_COLORS[1];
        }
        return THREAT_COLORS[0];
    }

    private static int starGainColor(int gained) {
        if (gained >= 15) {
            return THREAT_COLORS[4];
        }
        if (gained >= 8) {
            return THREAT_COLORS[3];
        }
        if (gained >= 4) {
            return THREAT_COLORS[2];
        }
        if (gained >= 1) {
            return THREAT_COLORS[1];
        }
        return THREAT_COLORS[0];
    }

    private static int threatColor(float value, float[] steps) {
        int index = 0;
        while (index < steps.length && value >= steps[index]) {
            index++;
        }
        return THREAT_COLORS[index];
    }
}
