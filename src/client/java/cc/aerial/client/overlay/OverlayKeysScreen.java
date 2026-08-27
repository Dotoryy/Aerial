package cc.aerial.client.overlay;

import cc.aerial.client.property.StringProperty;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

public final class OverlayKeysScreen extends Screen {
    private static final float PANEL_WIDTH = 240.0f;
    private static final float ROW_HEIGHT = 30.0f;
    private static final float PADDING = 12.0f;
    private static final float TITLE_SIZE = 12.0f;
    private static final float LABEL_SIZE = 8.0f;
    private static final float VALUE_SIZE = 8.0f;
    private static final float HINT_SIZE = 7.5f;
    private static final float BOX_HEIGHT = 12.0f;
    private static final float RADIUS = 6.0f;

    private static final int PANEL_COLOR = 0xE6141414;
    private static final int BOX_COLOR = 0xFF191919;
    private static final int FOCUS_COLOR = 0xFF6EE787;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int LABEL_COLOR = 0xFFBFBFBF;
    private static final int HINT_COLOR = 0xFF8A8A8A;

    private final @Nullable Screen previousScreen;
    private final StringProperty[] keys;
    private final String[] hints;

    private AerialFont font;
    private StringProperty focused;

    public OverlayKeysScreen(@Nullable Screen previousScreen, StringProperty[] keys, String[] hints) {
        super(Component.literal("API Keys"));
        this.previousScreen = previousScreen;
        this.keys = keys;
        this.hints = hints;
    }

    private void ensureFont() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
        }
    }

    private float panelHeight() {
        return PADDING * 2 + TITLE_SIZE + 8.0f + keys.length * ROW_HEIGHT + 4.0f + HINT_SIZE;
    }

    private float panelLeft() {
        return (width - PANEL_WIDTH) * 0.5f;
    }

    private float panelTop() {
        return (height - panelHeight()) * 0.5f;
    }

    private float rowTop(int index) {
        return panelTop() + PADDING + TITLE_SIZE + 8.0f + index * ROW_HEIGHT;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        ensureFont();
        float left = panelLeft();
        float top = panelTop();

        RenderUtil.roundedRect(extractor, left, top, PANEL_WIDTH, panelHeight(), RADIUS, PANEL_COLOR);
        TextRenderUtil.drawString(extractor, font, "API Keys",
                left + PADDING, top + PADDING, TITLE_SIZE, TEXT_COLOR);

        for (int i = 0; i < keys.length; i++) {
            StringProperty key = keys[i];
            float rowY = rowTop(i);
            boolean isFocused = focused == key;

            TextRenderUtil.drawString(extractor, font, key.getName(),
                    left + PADDING, rowY, LABEL_SIZE, LABEL_COLOR);

            float boxX = left + PADDING;
            float boxY = rowY + LABEL_SIZE + 3.0f;
            float boxWidth = PANEL_WIDTH - PADDING * 2;
            if (isFocused) {
                RenderUtil.roundedRect(extractor, boxX - 1.0f, boxY - 1.0f,
                        boxWidth + 2.0f, BOX_HEIGHT + 2.0f, 3.0f, FOCUS_COLOR);
            }
            RenderUtil.roundedRect(extractor, boxX, boxY, boxWidth, BOX_HEIGHT, 2.5f, BOX_COLOR);

            String shown = display(key, isFocused);
            float textX = boxX + 4.0f;
            float textY = boxY + (BOX_HEIGHT - VALUE_SIZE) * 0.5f;
            if (shown.isEmpty()) {
                TextRenderUtil.drawString(extractor, font, hints[i], textX, textY, VALUE_SIZE, HINT_COLOR);
            } else {
                TextRenderUtil.drawString(extractor, font, shown, textX, textY, VALUE_SIZE, TEXT_COLOR);
            }

            if (isFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                int cursor = Math.max(0, Math.min(key.getCursor(), key.getValue().length()));
                float cursorX = textX + font.stringWidth(key.getValue().substring(0, cursor), VALUE_SIZE);
                RenderUtil.flatRect(extractor, cursorX, boxY + 1.5f, 0.75f, BOX_HEIGHT - 3.0f, TEXT_COLOR);
            }
        }

        TextRenderUtil.drawString(extractor, font, "Click a field to edit  -  Ctrl+V pastes  -  Empty = source off",
                left + PADDING, top + panelHeight() - PADDING - HINT_SIZE, HINT_SIZE, HINT_COLOR);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private static String display(StringProperty key, boolean focused) {
        String value = key.getValue();
        if (focused || value.isEmpty()) {
            return value;
        }
        return "*".repeat(Math.min(value.length(), 40));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        focused = null;
        if (event.button() == 0) {
            for (int i = 0; i < keys.length; i++) {
                float boxY = rowTop(i) + LABEL_SIZE + 3.0f;
                if (event.x() >= panelLeft() + PADDING && event.x() <= panelLeft() + PANEL_WIDTH - PADDING
                        && event.y() >= boxY && event.y() <= boxY + BOX_HEIGHT) {
                    focused = keys[i];
                    focused.cursorToEnd();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (focused != null && event.isAllowedChatCharacter()) {
            focused.insertChar((char) event.codepoint());
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (focused == null) {
            return super.keyPressed(keyEvent);
        }
        if (keyEvent.isPaste()) {
            focused.insert(Minecraft.getInstance().keyboardHandler.getClipboard());
            return true;
        }
        if (keyEvent.isCopy()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(focused.getValue());
            return true;
        }
        if (keyEvent.isCut()) {
            Minecraft.getInstance().keyboardHandler.setClipboard(focused.getValue());
            focused.setValue("");
            focused.cursorToEnd();
            return true;
        }
        switch (keyEvent.key()) {
            case GLFW.GLFW_KEY_BACKSPACE -> focused.backspace();
            case GLFW.GLFW_KEY_DELETE -> focused.delete();
            case GLFW.GLFW_KEY_LEFT -> focused.moveCursor(-1);
            case GLFW.GLFW_KEY_RIGHT -> focused.moveCursor(1);
            case GLFW.GLFW_KEY_HOME -> focused.moveCursor(-focused.getValue().length());
            case GLFW.GLFW_KEY_END -> focused.cursorToEnd();
            case GLFW.GLFW_KEY_ESCAPE, GLFW.GLFW_KEY_ENTER -> focused = null;
            default -> {
            }
        }
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
