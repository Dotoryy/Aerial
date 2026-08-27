package cc.aerial.client.screen.server;

import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.screen.widget.CardScreen;
import cc.aerial.client.screen.widget.AerialTextArea;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public abstract class ServerFormScreen extends CardScreen {
    protected static final float FIELD_HEIGHT = 20.0f;
    protected static final float FIELD_INSET = 7.0f;
    protected static final float LABEL_SIZE = 8.0f;
    protected static final float LABEL_GAP = 4.0f;
    protected static final float FIELD_GAP = 12.0f;
    protected static final float TEXT_SIZE = 9.0f;
    private static final int LABEL_COLOR = 0xFF9698A4;

    protected static final class Field {
        private final String label;
        private final String placeholder;
        private final int limit;
        private String initial;
        private AerialTextArea area;

        Field(String label, String placeholder, String initial, int limit) {
            this.label = label;
            this.placeholder = placeholder;
            this.initial = initial;
            this.limit = limit;
        }

        public String value() {
            return area == null ? initial : area.getValue().trim();
        }

        public void set(String value) {
            initial = value;
            if (area != null) {
                area.setValue(value);
            }
        }
    }

    private final List<Field> fields = new ArrayList<>();
    private int focusedField;

    protected ServerFormScreen(String title, @Nullable Screen previousScreen) {
        super(title, previousScreen);
    }

    protected final Field addField(String label, String placeholder, String initial, int limit) {
        Field field = new Field(label, placeholder, initial, limit);
        fields.add(field);
        return field;
    }

    @Override
    protected float contentHeight() {
        return fields.size() * (LABEL_SIZE + LABEL_GAP + FIELD_HEIGHT)
                + Math.max(0, fields.size() - 1) * FIELD_GAP;
    }

    @Override
    protected void init() {
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            if (field.area == null) {
                field.area = new AerialTextArea(cardFont(), TEXT_SIZE, field.placeholder);
                field.area.setSingleLine(true);
                field.area.setValue(field.initial);
            }
            field.area.setFocused(i == focusedField);
        }
        clearActions();
        addFormActions();
    }

    protected abstract void addFormActions();

    private float rowTop(int index) {
        return contentTop() + index * (LABEL_SIZE + LABEL_GAP + FIELD_HEIGHT + FIELD_GAP);
    }

    private float fieldTop(int index) {
        return rowTop(index) + LABEL_SIZE + LABEL_GAP;
    }

    private static float textTop(float wellTop) {
        return wellTop + (FIELD_HEIGHT - TEXT_SIZE) * 0.5f;
    }

    @Override
    protected void drawCardContent(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            TextRenderUtil.drawString(extractor, cardFont(), field.label,
                    contentLeft(), rowTop(i), LABEL_SIZE, LABEL_COLOR);
            float top = fieldTop(i);
            drawInputFrame(extractor, contentLeft(), top, contentWidth(), FIELD_HEIGHT,
                    field.area.isFocused());
            field.area.draw(extractor, contentLeft() + FIELD_INSET, textTop(top),
                    contentWidth() - FIELD_INSET * 2.0f, TEXT_SIZE);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        for (int i = 0; i < fields.size(); i++) {
            Field field = fields.get(i);
            float top = fieldTop(i);
            if (field.area.mouseClicked(event.x(), event.y(), contentLeft() + FIELD_INSET,
                    textTop(top), contentWidth() - FIELD_INSET * 2.0f, TEXT_SIZE)) {
                focus(i);
                return true;
            }

            if (inside(event.x(), event.y(), contentLeft(), top, contentWidth(), FIELD_HEIGHT)) {
                focus(i);
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    private void focus(int index) {
        focusedField = index;
        for (int i = 0; i < fields.size(); i++) {
            fields.get(i).area.setFocused(i == index);
        }
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        Field field = current();
        if (field != null && field.area.getValue().length() < field.limit) {
            return field.area.charTyped((char) event.codepoint());
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_TAB && !fields.isEmpty()) {
            focus((focusedField + 1) % fields.size());
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            onSubmit();
            return true;
        }
        Field field = current();
        if (field != null && field.area.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    private @Nullable Field current() {
        return focusedField >= 0 && focusedField < fields.size() ? fields.get(focusedField) : null;
    }

    protected abstract void onSubmit();
}
