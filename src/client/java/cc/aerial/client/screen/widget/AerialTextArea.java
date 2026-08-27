package cc.aerial.client.screen.widget;

import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public final class AerialTextArea {
    private static final float LINE_GAP = 2.0f;
    private static final int TEXT_COLOR = 0xFFEDEDF2;
    private static final int PLACEHOLDER_COLOR = 0xFF6E7079;

    private static final int SELECTION_COLOR = 0x593C6EFF;

    private final AerialFont font;
    private final float textSize;
    private final String placeholder;

    private String value = "";
    private int cursor;

    private int selectionAnchor = -1;
    private float scroll;
    private boolean focused;

    private boolean singleLine;

    private final List<Line> lines = new ArrayList<>();
    private String laidOutValue;
    private float laidOutWidth = -1.0f;

    private record Line(int start, String text) {
    }

    public AerialTextArea(AerialFont font, float textSize, String placeholder) {
        this.font = font;
        this.textSize = textSize;
        this.placeholder = placeholder;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
        this.cursor = value.length();
        this.selectionAnchor = -1;
    }

    public boolean isFocused() {
        return focused;
    }

    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    public void setSingleLine(boolean singleLine) {
        this.singleLine = singleLine;
    }

    private float lineHeight() {
        return textSize + LINE_GAP;
    }

    private void layout(float width) {
        if (value.equals(laidOutValue) && width == laidOutWidth) {
            return;
        }
        laidOutValue = value;
        laidOutWidth = width;
        lines.clear();

        int index = 0;
        for (String paragraph : value.split("\n", -1)) {
            int start = 0;
            while (true) {
                int fit = fitCount(paragraph, start, width);

                fit = Math.max(1, fit);
                int end = Math.min(paragraph.length(), start + fit);
                lines.add(new Line(index + start, paragraph.substring(start, end)));
                if (end >= paragraph.length()) {
                    break;
                }
                start = end;
            }
            index += paragraph.length() + 1;
        }
    }

    private int fitCount(String text, int from, float width) {
        float used = 0.0f;
        int count = 0;
        for (int i = from; i < text.length(); i++) {
            float advance = font.stringWidth(String.valueOf(text.charAt(i)), textSize);
            if (used + advance > width) {
                break;
            }
            used += advance;
            count++;
        }
        return count;
    }

    public void draw(GuiGraphicsExtractor extractor, float x, float y, float width, float height) {
        layout(width);

        if (value.isEmpty() && !focused) {
            TextRenderUtil.drawString(extractor, font, placeholder, x, y, textSize, PLACEHOLDER_COLOR);
            return;
        }

        keepCursorVisible(height);
        ScreenRectangle clip = new ScreenRectangle(Math.round(x), Math.round(y),
                Math.round(width), Math.round(height));

        for (int i = 0; i < lines.size(); i++) {
            float lineY = y + i * lineHeight() - scroll;
            if (lineY + lineHeight() < y || lineY > y + height) {
                continue;
            }

            drawSelection(extractor, lines.get(i), x, lineY, clip);
            TextRenderUtil.drawString(extractor, font, lines.get(i).text(), x, lineY, textSize,
                    TEXT_COLOR, clip);
        }

        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int line = lineOf(cursor);
            float caretY = y + line * lineHeight() - scroll;
            if (caretY >= y - lineHeight() && caretY <= y + height) {
                Line current = lines.get(line);

                int offset = Math.max(0, Math.min(current.text().length(), cursor - current.start()));
                float caretX = x + font.stringWidth(current.text().substring(0, offset), textSize);
                RenderUtil.roundedRect(extractor, caretX, caretY, 0.9f, textSize, 0.45f,
                        TEXT_COLOR, clip);
            }
        }
    }

    private void drawSelection(GuiGraphicsExtractor extractor, Line line, float x, float lineY,
                               ScreenRectangle clip) {
        if (!hasSelection()) {
            return;
        }
        int from = Math.max(selectionStart() - line.start(), 0);
        int to = Math.min(selectionEnd() - line.start(), line.text().length());
        if (to <= from) {
            return;
        }
        float left = x + font.stringWidth(line.text().substring(0, from), textSize);
        float right = x + font.stringWidth(line.text().substring(0, to), textSize);
        RenderUtil.roundedRect(extractor, left, lineY - 1.0f, right - left, textSize + 2.0f,
                1.5f, SELECTION_COLOR, clip);
    }

    private void keepCursorVisible(float height) {
        if (singleLine) {
            scroll = 0.0f;
            return;
        }
        float caretTop = lineOf(cursor) * lineHeight();
        if (caretTop < scroll) {
            scroll = caretTop;
        } else if (caretTop + lineHeight() > scroll + height) {
            scroll = caretTop + lineHeight() - height;
        }
        scroll = Math.max(0.0f, Math.min(maxScroll(height), scroll));
    }

    private float maxScroll(float height) {
        return Math.max(0.0f, lines.size() * lineHeight() - height);
    }

    private int lineOf(int index) {
        for (int i = lines.size() - 1; i >= 0; i--) {
            if (index >= lines.get(i).start()) {
                return i;
            }
        }
        return 0;
    }

    public boolean mouseClicked(double mouseX, double mouseY, float x, float y, float width, float height) {
        boolean inside = mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
        focused = inside;
        if (!inside) {
            return false;
        }
        layout(width);
        int line = Math.max(0, Math.min(lines.size() - 1,
                (int) ((mouseY - y + scroll) / lineHeight())));
        Line target = lines.get(line);

        float cursorX = x;
        int offset = 0;
        while (offset < target.text().length()) {
            float advance = font.stringWidth(String.valueOf(target.text().charAt(offset)), textSize);
            if (cursorX + advance * 0.5f > mouseX) {
                break;
            }
            cursorX += advance;
            offset++;
        }
        cursor = target.start() + offset;
        selectionAnchor = -1;
        return true;
    }

    public boolean mouseScrolled(double vertical, float height) {
        scroll = (float) Math.max(0.0, Math.min(maxScroll(height), scroll - vertical * lineHeight() * 3.0));
        return true;
    }

    public boolean charTyped(char typed) {
        if (!focused) {
            return false;
        }
        insert(String.valueOf(typed));
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (!focused) {
            return false;
        }
        if (isSelectAll(event)) {
            selectionAnchor = 0;
            cursor = value.length();
            return true;
        }
        if (event.isPaste()) {
            insert(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        if (event.isCopy()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
            return true;
        }
        if (event.isCut()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(selectedText());
            if (hasSelection()) {
                deleteSelection();
            } else {
                value = "";
                cursor = 0;
            }
            return true;
        }

        boolean extend = hasShift();
        switch (event.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (deleteSelection()) {
                    break;
                }
                if (cursor > 0) {
                    value = value.substring(0, cursor - 1) + value.substring(cursor);
                    cursor--;
                }
            }
            case GLFW.GLFW_KEY_DELETE -> {
                if (deleteSelection()) {
                    break;
                }
                if (cursor < value.length()) {
                    value = value.substring(0, cursor) + value.substring(cursor + 1);
                }
            }
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                if (singleLine) {
                    return false;
                }
                insert("\n");
            }
            case GLFW.GLFW_KEY_LEFT -> moveTo(Math.max(0, cursor - 1), extend);
            case GLFW.GLFW_KEY_RIGHT -> moveTo(Math.min(value.length(), cursor + 1), extend);
            case GLFW.GLFW_KEY_UP -> moveLine(-1, extend);
            case GLFW.GLFW_KEY_DOWN -> moveLine(1, extend);
            case GLFW.GLFW_KEY_HOME ->
                    moveTo(lines.isEmpty() ? 0 : lines.get(lineOf(cursor)).start(), extend);
            case GLFW.GLFW_KEY_END -> {
                if (!lines.isEmpty()) {
                    Line line = lines.get(lineOf(cursor));
                    moveTo(line.start() + line.text().length(), extend);
                }
            }
            case GLFW.GLFW_KEY_ESCAPE -> {
                if (hasSelection()) {
                    selectionAnchor = -1;
                } else {
                    focused = false;
                }
            }
            default -> {
            }
        }
        return true;
    }

    private static boolean isSelectAll(KeyEvent event) {
        if (event.key() != GLFW.GLFW_KEY_A) {
            return false;
        }
        long window = Minecraft.getInstance().getWindow().handle();
        return isDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || isDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL)
                || isDown(window, GLFW.GLFW_KEY_LEFT_SUPER) || isDown(window, GLFW.GLFW_KEY_RIGHT_SUPER);
    }

    private static boolean hasShift() {
        long window = Minecraft.getInstance().getWindow().handle();
        return isDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || isDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    private static boolean isDown(long window, int key) {
        return GLFW.glfwGetKey(window, key) == GLFW.GLFW_PRESS;
    }

    private boolean hasSelection() {
        return selectionAnchor >= 0 && selectionAnchor != cursor;
    }

    private int selectionStart() {
        return Math.min(selectionAnchor, cursor);
    }

    private int selectionEnd() {
        return Math.max(selectionAnchor, cursor);
    }

    private String selectedText() {
        return hasSelection() ? value.substring(selectionStart(), selectionEnd()) : value;
    }

    private boolean deleteSelection() {
        if (!hasSelection()) {
            selectionAnchor = -1;
            return false;
        }
        int start = selectionStart();
        value = value.substring(0, start) + value.substring(selectionEnd());
        cursor = start;
        selectionAnchor = -1;
        return true;
    }

    private void moveTo(int position, boolean extend) {
        if (extend) {
            if (selectionAnchor < 0) {
                selectionAnchor = cursor;
            }
        } else {
            selectionAnchor = -1;
        }
        cursor = position;
    }

    private void moveLine(int direction, boolean extend) {
        if (lines.isEmpty()) {
            return;
        }
        int line = lineOf(cursor);
        int column = cursor - lines.get(line).start();
        int target = Math.max(0, Math.min(lines.size() - 1, line + direction));
        Line destination = lines.get(target);
        moveTo(destination.start() + Math.min(column, destination.text().length()), extend);
    }

    private void insert(String text) {
        String cleaned = text.replace("\r", "").replace("\t", " ");
        if (singleLine) {
            cleaned = cleaned.replace("\n", " ").trim();
        }
        value = value.substring(0, cursor) + cleaned + value.substring(cursor);
        cursor += cleaned.length();
    }
}
