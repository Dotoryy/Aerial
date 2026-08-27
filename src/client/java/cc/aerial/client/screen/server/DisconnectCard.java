package cc.aerial.client.screen.server;

import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.screen.title.TitleBackground;
import cc.aerial.client.theme.Theme;
import cc.aerial.client.theme.ThemeManager;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

public final class DisconnectCard {
    private static final float CARD_WIDTH = 300.0f;
    private static final float PADDING = 16.0f;
    private static final float TITLE_SIZE = 14.0f;
    private static final float REASON_SIZE = 8.5f;
    private static final float REASON_LEADING = 3.0f;
    private static final float ACTION_SIZE = 8.5f;
    private static final float TITLE_GAP = 10.0f;
    private static final float ACTION_GAP = 14.0f;
    private static final float ACTION_SPACING = 18.0f;
    private static final int MAX_LINES = 8;

    private static final int CARD_COLOR = 0xE60D0D14;
    private static final int REASON_COLOR = 0xFFC3C5CE;
    private static final int ACTION_COLOR = 0xFFB9BAC4;

    private static final int TITLE_COLOR = 0xFFE05A5A;

    private static AerialFont font;
    private static AerialFont boldFont;

    private DisconnectCard() {
    }

    private static void ensureFonts() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
            boldFont = AerialFont.createFromResource("OpalProductSansBold.ttf");
        }
    }

    private static List<String> wrap(String text) {
        ensureFonts();
        float limit = CARD_WIDTH - PADDING * 2;
        List<String> lines = new ArrayList<>();
        for (String paragraph : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String candidate = line.isEmpty() ? word : line + " " + word;
                if (font.stringWidth(candidate, REASON_SIZE) <= limit) {
                    line = new StringBuilder(candidate);
                    continue;
                }
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder();
                }
                while (font.stringWidth(word, REASON_SIZE) > limit) {
                    int fit = 1;
                    while (fit < word.length()
                            && font.stringWidth(word.substring(0, fit + 1), REASON_SIZE) <= limit) {
                        fit++;
                    }
                    lines.add(word.substring(0, fit));
                    word = word.substring(fit);
                }
                line = new StringBuilder(word);
            }
            lines.add(line.toString());
        }
        return lines.size() > MAX_LINES ? lines.subList(0, MAX_LINES) : lines;
    }

    private static float cardHeight(int lineCount) {
        return PADDING * 2 + TITLE_SIZE + TITLE_GAP
                + lineCount * REASON_SIZE + Math.max(0, lineCount - 1) * REASON_LEADING
                + ACTION_GAP + ACTION_SIZE;
    }

    public static void draw(GuiGraphicsExtractor extractor, int width, int height,
                            String title, String reason, String[] actions, int mouseX, int mouseY) {
        ensureFonts();
        TitleBackground.draw(extractor, width, height);

        List<String> lines = wrap(reason);
        float left = (width - CARD_WIDTH) * 0.5f;
        float top = (height - cardHeight(lines.size())) * 0.5f;

        RenderUtil.roundedRect(extractor, left, top, CARD_WIDTH, cardHeight(lines.size()), 8.0f,
                CARD_COLOR);

        TextRenderUtil.drawString(extractor, boldFont, title,
                left + (CARD_WIDTH - boldFont.stringWidth(title, TITLE_SIZE)) * 0.5f,
                top + PADDING, TITLE_SIZE, TITLE_COLOR);

        float cursorY = top + PADDING + TITLE_SIZE + TITLE_GAP;
        for (String line : lines) {
            TextRenderUtil.drawString(extractor, font, line,
                    left + (CARD_WIDTH - font.stringWidth(line, REASON_SIZE)) * 0.5f,
                    cursorY, REASON_SIZE, REASON_COLOR);
            cursorY += REASON_SIZE + REASON_LEADING;
        }

        float actionY = actionY(height, lines.size());
        float cursorX = actionsLeft(width, actions);
        for (String action : actions) {
            float actionWidth = font.stringWidth(action, ACTION_SIZE);
            boolean hovered = mouseX >= cursorX - 6.0f && mouseX <= cursorX + actionWidth + 6.0f
                    && mouseY >= actionY - 5.0f && mouseY <= actionY + ACTION_SIZE + 5.0f;
            TextRenderUtil.drawString(extractor, font, action, cursorX, actionY, ACTION_SIZE,
                    hovered ? 0xFFFFFFFF : ACTION_COLOR);
            cursorX += actionWidth + ACTION_SPACING;
        }
    }

    private static float actionY(int height, int lineCount) {
        return (height - cardHeight(lineCount)) * 0.5f + PADDING + TITLE_SIZE + TITLE_GAP
                + lineCount * REASON_SIZE + Math.max(0, lineCount - 1) * REASON_LEADING + ACTION_GAP;
    }

    private static float actionsLeft(int width, String[] actions) {
        ensureFonts();
        float total = 0.0f;
        for (int i = 0; i < actions.length; i++) {
            total += font.stringWidth(actions[i], ACTION_SIZE);
            if (i < actions.length - 1) {
                total += ACTION_SPACING;
            }
        }
        return (width - total) * 0.5f;
    }

    public static int actionAt(int width, int height, double mouseX, double mouseY,
                               String reason, String[] actions) {
        ensureFonts();
        int lineCount = wrap(reason).size();
        float actionY = actionY(height, lineCount);
        if (mouseY < actionY - 5.0f || mouseY > actionY + ACTION_SIZE + 5.0f) {
            return -1;
        }
        float cursorX = actionsLeft(width, actions);
        for (int i = 0; i < actions.length; i++) {
            float actionWidth = font.stringWidth(actions[i], ACTION_SIZE);
            if (mouseX >= cursorX - 6.0f && mouseX <= cursorX + actionWidth + 6.0f) {
                return i;
            }
            cursorX += actionWidth + ACTION_SPACING;
        }
        return -1;
    }
}
