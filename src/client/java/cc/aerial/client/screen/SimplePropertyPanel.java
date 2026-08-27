package cc.aerial.client.screen;

import cc.aerial.client.property.ActionProperty;
import org.joml.Matrix3x2fStack;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.BoundedNumberProperty;
import cc.aerial.client.property.GroupProperty;
import cc.aerial.client.property.KeyProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.MultipleBooleanProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.property.Property;
import cc.aerial.client.property.StringProperty;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.screen.animation.Animation;
import cc.aerial.client.screen.animation.Easing;
import cc.aerial.client.theme.Theme;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class SimplePropertyPanel implements RowContent {
    private static final float BOOLEAN_HEIGHT = 17.0f;
    private static final float NUMBER_HEIGHT = 24.0f;

    private static final float STRING_HEIGHT = 22.0f;
    private static final float GROUP_HEIGHT = 22.0f;

    private static final float GROUP_CONTENT_ALPHA = 0.1f;

    private static final int GROUP_CARD_COLOR = 0xFF000000;
    private static final float GROUP_CARD_ALPHA = 0.2f;

    private static final float CHIP_TEXT_SIZE = 6.0f;

    private static final float MODE_BASE_HEIGHT = 32.0f;
    private static final float MODE_OPTION_HEIGHT = 13.0f;

    private final Property<?>[] properties;

    private final Map<BooleanProperty, Animation> boolAnims = new IdentityHashMap<>();
    private final Map<NumberProperty, DragState> numberStates = new IdentityHashMap<>();
    private final Map<BoundedNumberProperty, BoundedDragState> boundedNumberStates = new IdentityHashMap<>();
    private final Map<ModeProperty<?>, ExpandState> modeStates = new IdentityHashMap<>();
    private final Map<GroupProperty, ExpandState> groupStates = new IdentityHashMap<>();
    private final Map<Property<?>, Float> heights = new IdentityHashMap<>();

    private static final class DragState {
        Animation anim;
        boolean dragging;
    }

    private static final class BoundedDragState {
        Animation lowAnim, highAnim;
        boolean draggingLow, draggingHigh;
    }

    private static final class ExpandState {
        final Animation anim = new Animation(Easing.DECELERATE, 125);
        boolean expanded;
    }

    private final boolean flat;

    SimplePropertyPanel(Property<?>[] properties) {
        this(properties, false);
    }

    SimplePropertyPanel(Property<?>[] properties, boolean flat) {
        this.properties = properties;
        this.flat = flat;
    }

    @Override
    public float measure(float x, float y, float width, double mouseX, double mouseY) {
        float total = 0.0f;
        float cursorY = y;
        for (Property<?> property : properties) {
            if (property.isHidden()) {
                continue;
            }
            float h = measureOne(property, x, cursorY, width, mouseX, mouseY);
            total += h;
            cursorY += h;
        }
        return total;
    }

    private float measureOne(Property<?> property, float x, float y, float width, double mouseX, double mouseY) {
        float height;
        if (property instanceof BooleanProperty bool) {
            boolAnims.computeIfAbsent(bool, b -> {
                Animation a = new Animation(Easing.DECELERATE, 150);
                a.setValue(b.getValue() ? 1.0f : 0.0f);
                return a;
            }).run(bool.getValue() ? 1.0f : 0.0f);
            height = BOOLEAN_HEIGHT;
        } else if (property instanceof NumberProperty number) {
            DragState drag = numberStates.computeIfAbsent(number, n -> new DragState());
            float sliderX = x + 6.0f;
            float sliderWidth = width - 12.0f;
            if (drag.dragging && mouseX != -1) {
                float percent = (float) Math.min(1.0, Math.max(0.0, (mouseX - sliderX) / sliderWidth));
                number.setValue(number.getMinValue() + (number.getMaxValue() - number.getMinValue()) * percent);
            }
            double widthPercent = (number.getValue() - number.getMinValue()) / (number.getMaxValue() - number.getMinValue());
            float destination = (float) (sliderWidth * widthPercent);
            if (drag.anim == null) {
                drag.anim = new Animation(Easing.LINEAR, 50);
                drag.anim.setValue(destination);
            } else {
                drag.anim.run(destination);
            }
            height = NUMBER_HEIGHT;
        } else if (property instanceof ActionProperty || property instanceof KeyProperty) {
            height = BOOLEAN_HEIGHT;
        } else if (property instanceof StringProperty) {
            height = STRING_HEIGHT;
        } else if (property instanceof BoundedNumberProperty bounded) {
            BoundedDragState drag = boundedNumberStates.computeIfAbsent(bounded, b -> new BoundedDragState());
            float sliderX = x + 6.0f;
            float sliderWidth = width - 12.0f;
            double range = bounded.getMaxValue() - bounded.getMinValue();
            if (drag.draggingLow && mouseX != -1) {
                float percent = (float) Math.min(1.0, Math.max(0.0, (mouseX - sliderX) / sliderWidth));
                bounded.setLow(bounded.getMinValue() + range * percent);
            } else if (drag.draggingHigh && mouseX != -1) {
                float percent = (float) Math.min(1.0, Math.max(0.0, (mouseX - sliderX) / sliderWidth));
                bounded.setHigh(bounded.getMinValue() + range * percent);
            }
            float lowDestination = (float) (sliderWidth * (bounded.getLow() - bounded.getMinValue()) / range);
            float highDestination = (float) (sliderWidth * (bounded.getHigh() - bounded.getMinValue()) / range);
            if (drag.lowAnim == null) {
                drag.lowAnim = new Animation(Easing.LINEAR, 50);
                drag.lowAnim.setValue(lowDestination);
                drag.highAnim = new Animation(Easing.LINEAR, 50);
                drag.highAnim.setValue(highDestination);
            } else {
                drag.lowAnim.run(lowDestination);
                drag.highAnim.run(highDestination);
            }
            height = NUMBER_HEIGHT;
        } else if (property instanceof MultipleBooleanProperty multi) {
            float[] boxHeightOut = new float[1];
            computeChipLayout(multi, width, boxHeightOut);

            height = BOOLEAN_HEIGHT + boxHeightOut[0];
        } else if (property instanceof ModeProperty<?> mode) {
            ExpandState state = modeStates.computeIfAbsent(mode, m -> new ExpandState());
            state.anim.run(state.expanded ? 1.0f : 0.0f);
            int optionCount = mode.getValues().length - 1;
            height = MODE_BASE_HEIGHT + optionCount * MODE_OPTION_HEIGHT * state.anim.getValue();
        } else if (property instanceof GroupProperty group) {
            ExpandState state = groupStates.computeIfAbsent(group, g -> new ExpandState());
            state.anim.run(state.expanded ? 1.0f : 0.0f);
            float expand = state.anim.getValue();
            float childrenHeight = 0.0f;
            for (Property<?> child : group.getPropertyList()) {
                if (child.isHidden()) {
                    continue;
                }
                childrenHeight += measureOne(child, x, y + GROUP_HEIGHT, width, mouseX, mouseY);
            }
            height = GROUP_HEIGHT + childrenHeight * expand;
        } else {
            height = 0.0f;
        }
        heights.put(property, height);
        return height;
    }

    private record ChipRect(BooleanProperty property, float x, float y, float width) {
    }

    private List<ChipRect> computeChipLayout(MultipleBooleanProperty property, float width, float[] outBoxHeight) {
        AerialFont font = AerialClickGui.mediumFont();
        float innerWidth = width - 10.0f;
        float chipHeight = 8.5f;
        float lineGap = 1.5f;
        float chipGapX = 2.5f;
        float padX = 1.5f;

        List<ChipRect> chips = new java.util.ArrayList<>();
        float cursorX = padX;
        float cursorY = lineGap;
        for (BooleanProperty bool : property.getValue()) {
            if (bool.isHidden()) {
                continue;
            }
            float elementWidth = font.stringWidth(bool.getName(), CHIP_TEXT_SIZE) + 8.75f;
            if (cursorX + elementWidth > innerWidth - padX && cursorX > padX) {
                cursorY += chipHeight + lineGap;
                cursorX = padX;
            }
            chips.add(new ChipRect(bool, cursorX, cursorY, elementWidth));
            cursorX += elementWidth + chipGapX;
        }
        outBoxHeight[0] = cursorY + chipHeight + lineGap;
        return chips;
    }

    private float cachedHeight(Property<?> property) {
        Float h = heights.get(property);
        return h == null ? 0.0f : h;
    }

    @Override
    public void draw(GuiGraphicsExtractor extractor, float x, float y, float width, float rowAlpha,
              int mouseX, int mouseY, ScreenRectangle scissor, boolean isLastRow) {
        int lastVisibleIndex = -1;
        for (int i = properties.length - 1; i >= 0; i--) {
            if (!properties[i].isHidden()) {
                lastVisibleIndex = i;
                break;
            }
        }

        float cursorY = y;
        for (int i = 0; i < properties.length; i++) {
            Property<?> property = properties[i];
            if (property.isHidden()) {
                continue;
            }
            float height = cachedHeight(property);
            boolean isLastProperty = isLastRow && i == lastVisibleIndex;
            drawOne(extractor, property, x, cursorY, width, height, rowAlpha, mouseX, mouseY, scissor, isLastProperty);
            cursorY += height;
        }
    }

    private void drawOne(GuiGraphicsExtractor extractor, Property<?> property, float x, float y, float width,
                          float height, float rowAlpha, int mouseX, int mouseY, ScreenRectangle scissor,
                          boolean isLastProperty) {
        if (property instanceof GroupProperty group) {
            drawGroup(extractor, group, x, y, width, height, rowAlpha, mouseX, mouseY, scissor, isLastProperty);
            return;
        }

        drawBackground(extractor, x, y, width, height, rowAlpha, scissor, isLastProperty);
        if (property instanceof BooleanProperty bool) {
            drawBoolean(extractor, bool, x, y, width, rowAlpha, scissor);
        } else if (property instanceof NumberProperty number) {
            drawNumber(extractor, number, x, y, width, rowAlpha, scissor);
        } else if (property instanceof ActionProperty action) {
            drawAction(extractor, action, x, y, width, rowAlpha, scissor);
        } else if (property instanceof KeyProperty key) {
            drawKey(extractor, key, x, y, width, rowAlpha, scissor);
        } else if (property instanceof StringProperty string) {
            drawString(extractor, string, x, y, width, rowAlpha, scissor);
        } else if (property instanceof BoundedNumberProperty bounded) {
            drawBoundedNumber(extractor, bounded, x, y, width, rowAlpha, scissor);
        } else if (property instanceof MultipleBooleanProperty multi) {
            drawMultipleBoolean(extractor, multi, x, y, width, rowAlpha, scissor);
        } else if (property instanceof ModeProperty<?> mode) {
            drawMode(extractor, mode, x, y, width, height, rowAlpha, scissor);
        }
    }

    private void drawMode(GuiGraphicsExtractor extractor, ModeProperty<?> property, float x, float y, float width,
                           float height, float rowAlpha, ScreenRectangle scissor) {
        AerialFont font = AerialClickGui.mediumFont();
        AerialFont boldFont = AerialClickGui.boldFont();

        TextRenderUtil.drawString(extractor, font, property.getName(),
                x + 5.0f, y + 5.0f, AerialClickGui.PROPERTY_TEXT_SIZE,
                AerialClickGui.withAlpha(AerialClickGui.ENABLED_TEXT_COLOR, rowAlpha), scissor);

        float rectX = x + 3.0f;
        float rectY = y + 15.0f;
        float rectWidth = width - 7.0f;
        float rectHeight = height - 19.0f;
        RenderUtil.roundedRect(extractor, rectX, rectY, rectWidth, rectHeight, 4.0f,
                AerialClickGui.withAlpha(AerialClickGui.PROPERTY_BG_COLOR, AerialClickGui.PROPERTY_BG_ALPHA * rowAlpha), scissor);

        String valueText = property.getValue().toString();
        TextRenderUtil.drawString(extractor, boldFont, valueText,
                rectX + 4.0f, rectY + 3.0f, AerialClickGui.PROPERTY_TEXT_SIZE,
                AerialClickGui.withAlpha(AerialClickGui.ENABLED_TEXT_COLOR, rowAlpha), scissor);

        if (property.getValue() instanceof Theme selectedTheme) {
            float valueWidth = boldFont.stringWidth(valueText, AerialClickGui.PROPERTY_TEXT_SIZE);
            float swatchY = rectY + 4.0f;
            RenderUtil.roundedRect(extractor, rectX + 4.0f + valueWidth + 4.0f, swatchY, 7.0f, 7.0f, 2.0f,
                    AerialClickGui.withAlpha(selectedTheme.getFirstColor().getRGB(), rowAlpha), scissor);
            RenderUtil.roundedRect(extractor, rectX + 4.0f + valueWidth + 13.0f, swatchY, 7.0f, 7.0f, 2.0f,
                    AerialClickGui.withAlpha(selectedTheme.getSecondColor().getRGB(), rowAlpha), scissor);
        }

        ExpandState state = modeStates.get(property);
        float expand = state == null ? 0.0f : state.anim.getValue();
        drawExpandChevron(extractor, rectX + rectWidth - 12.0f, rectY + 2.0f, 9.0f, expand,
                AerialClickGui.ENABLED_TEXT_COLOR, rowAlpha, scissor);

        if (expand > 0.0f) {
            ScreenRectangle boxScissor = new ScreenRectangle(
                    Math.round(rectX), Math.round(rectY),
                    Math.round(rectWidth), Math.max(0, Math.round(rectHeight)));
            if (scissor != null) {
                boxScissor = boxScissor.intersection(scissor);
            }

            Enum<?>[] values = property.getValues();
            float added = 0.0f;
            for (Enum<?> option : values) {
                if (option == property.getValue()) {
                    continue;
                }
                TextRenderUtil.drawString(extractor, font, option.toString(),
                        rectX + 4.0f, rectY + 16.0f + added, AerialClickGui.PROPERTY_TEXT_SIZE,
                        AerialClickGui.withAlpha(AerialClickGui.ENABLED_TEXT_COLOR, rowAlpha), boxScissor);
                if (option instanceof Theme optionTheme) {
                    float swatchY = rectY + 16.0f + added - 1.5f;
                    RenderUtil.roundedRect(extractor, rectX + rectWidth - 20.0f, swatchY, 7.0f, 7.0f, 2.0f,
                            AerialClickGui.withAlpha(optionTheme.getFirstColor().getRGB(), rowAlpha), boxScissor);
                    RenderUtil.roundedRect(extractor, rectX + rectWidth - 11.0f, swatchY, 7.0f, 7.0f, 2.0f,
                            AerialClickGui.withAlpha(optionTheme.getSecondColor().getRGB(), rowAlpha), boxScissor);
                }
                added += 13.0f;
            }
        }
    }

    private void drawBackground(GuiGraphicsExtractor extractor, float x, float y, float width, float height,
                                 float rowAlpha, ScreenRectangle scissor, boolean isLastProperty) {
        if (flat) {
            return;
        }
        if (isLastProperty) {
            AerialClickGui.drawBottomRoundedRect(extractor, x, y, width, height, 5.0f,
                    AerialClickGui.withAlpha(AerialClickGui.PROPERTY_BG_COLOR, AerialClickGui.PROPERTY_BG_ALPHA * rowAlpha), scissor);
        } else {
            RenderUtil.flatRect(extractor, x, y, width, height,
                    AerialClickGui.withAlpha(AerialClickGui.PROPERTY_BG_COLOR, AerialClickGui.PROPERTY_BG_ALPHA * rowAlpha), scissor);
        }
    }

    private void drawAction(GuiGraphicsExtractor extractor, ActionProperty property, float x, float y,
                            float width, float rowAlpha, ScreenRectangle scissor) {
        AerialFont font = AerialClickGui.mediumFont();
        float nameY = y + (BOOLEAN_HEIGHT - AerialClickGui.PROPERTY_TEXT_SIZE) * 0.5f;
        TextRenderUtil.drawString(extractor, font, property.getName(),
                x + 5.0f, nameY, AerialClickGui.PROPERTY_TEXT_SIZE,
                AerialClickGui.withAlpha(AerialClickGui.ENABLED_TEXT_COLOR, rowAlpha), scissor);

        AerialFont iconFont = AerialClickGui.regularIconFont();
        float iconSize = 11.0f;
        float iconX = x + width - iconSize - 5.0f;
        float iconY = y + (BOOLEAN_HEIGHT - iconSize) * 0.5f;
        Matrix3x2fStack pose = extractor.pose();
        pose.pushMatrix();
        pose.rotateAbout((float) (-Math.PI * 0.5), iconX + iconSize * 0.5f, iconY + iconSize * 0.5f);
        TextRenderUtil.drawString(extractor, iconFont, String.valueOf(AerialClickGui.EXPAND_ICON),
                iconX, iconY, iconSize,
                AerialClickGui.withAlpha(AerialClickGui.ENABLED_TEXT_COLOR, rowAlpha), scissor);
        pose.popMatrix();
    }

    private void drawKey(GuiGraphicsExtractor extractor, KeyProperty property, float x, float y,
                         float width, float rowAlpha, ScreenRectangle scissor) {
        AerialFont font = AerialClickGui.mediumFont();
        float nameY = y + (BOOLEAN_HEIGHT - AerialClickGui.PROPERTY_TEXT_SIZE) * 0.5f;
        TextRenderUtil.drawString(extractor, font, property.getName(),
                x + 5.0f, nameY, AerialClickGui.PROPERTY_TEXT_SIZE,
                AerialClickGui.withAlpha(AerialClickGui.ENABLED_TEXT_COLOR, rowAlpha), scissor);

        boolean listening = AerialClickGui.getListeningKeyProperty() == property;
        String label = listening ? "..." : property.getDisplayName();
        float labelWidth = font.stringWidth(label, CHIP_TEXT_SIZE);
        float chipWidth = Math.max(labelWidth + 8.0f, 20.0f);
        float chipHeight = 9.0f;
        float chipX = x + width - chipWidth - 5.0f;
        float chipY = y + (BOOLEAN_HEIGHT - chipHeight) * 0.5f;

        RenderUtil.roundedRect(extractor, chipX, chipY, chipWidth, chipHeight, 2.0f,
                AerialClickGui.withAlpha(0xFF191919, rowAlpha), scissor);
        TextRenderUtil.drawString(extractor, font, label,
                chipX + (chipWidth - labelWidth) * 0.5f, chipY + (chipHeight - CHIP_TEXT_SIZE) * 0.5f,
                CHIP_TEXT_SIZE,
                AerialClickGui.withAlpha(listening ? AerialClickGui.themeColor() : 0xFFFFFFFF, rowAlpha),
                scissor);
    }

    private void drawBoolean(GuiGraphicsExtractor extractor, BooleanProperty property, float x, float y, float width,
                              float rowAlpha, ScreenRectangle scissor) {
        AerialFont font = AerialClickGui.mediumFont();
        float nameY = y + (BOOLEAN_HEIGHT - AerialClickGui.PROPERTY_TEXT_SIZE) * 0.5f;
        TextRenderUtil.drawString(extractor, font, property.getName(),
                x + 5.0f, nameY, AerialClickGui.PROPERTY_TEXT_SIZE,
                AerialClickGui.withAlpha(AerialClickGui.ENABLED_TEXT_COLOR, rowAlpha), scissor);

        Animation anim = boolAnims.get(property);
        float value = anim == null ? (property.getValue() ? 1.0f : 0.0f) : anim.getValue();

        float switchX = x + width - AerialClickGui.SWITCH_WIDTH - AerialClickGui.TEXT_INSET;
        float switchY = y + (BOOLEAN_HEIGHT - AerialClickGui.SWITCH_HEIGHT) * 0.5f;
        int trackColor = blendOpaque(AerialClickGui.PROPERTY_FALSE_COLOR, AerialClickGui.themeColor(), value);
        RenderUtil.roundedRect(extractor, switchX, switchY, AerialClickGui.SWITCH_WIDTH, AerialClickGui.SWITCH_HEIGHT,
                AerialClickGui.SWITCH_HEIGHT * 0.5f, AerialClickGui.withAlpha(trackColor, rowAlpha), scissor);

        float knobSize = AerialClickGui.SWITCH_HEIGHT - AerialClickGui.SWITCH_KNOB_MARGIN * 2.0f;
        float knobX = switchX + AerialClickGui.SWITCH_KNOB_MARGIN + value * AerialClickGui.SWITCH_KNOB_TRAVEL;
        float knobY = switchY + AerialClickGui.SWITCH_KNOB_MARGIN;
        RenderUtil.roundedRect(extractor, knobX, knobY, knobSize, knobSize, knobSize * 0.5f,
                AerialClickGui.withAlpha(0xFFFFFFFF, rowAlpha), scissor);
    }

    private void drawNumber(GuiGraphicsExtractor extractor, NumberProperty property, float x, float y, float width,
                             float rowAlpha, ScreenRectangle scissor) {
        AerialFont font = AerialClickGui.mediumFont();
        float nameY = y + 5.0f;
        TextRenderUtil.drawString(extractor, font, property.getName(),
                x + 5.0f, nameY, AerialClickGui.PROPERTY_TEXT_SIZE,
                AerialClickGui.withAlpha(AerialClickGui.ENABLED_TEXT_COLOR, rowAlpha), scissor);

        String valueString = formatNumber(property.getValue());
        float valueTextWidth = font.stringWidth(valueString, AerialClickGui.PROPERTY_TEXT_SIZE);
        TextRenderUtil.drawString(extractor, font, valueString,
                x + width - 5.0f - valueTextWidth, nameY, AerialClickGui.PROPERTY_TEXT_SIZE,
                AerialClickGui.withAlpha(0xFFFFFFFF, rowAlpha * 0.8f), scissor);

        float sliderWidth = width - 12.0f;
        float sliderHeight = 2.5f;
        float sliderX = x + 6.0f;
        float sliderY = y + 16.0f;

        RenderUtil.roundedRect(extractor, sliderX, sliderY, sliderWidth, sliderHeight, sliderHeight * 0.5f,
                AerialClickGui.withAlpha(0xFF373737, rowAlpha), scissor);

        DragState drag = numberStates.get(property);
        float dragValue = drag == null || drag.anim == null ? 0.0f : drag.anim.getValue();
        if (dragValue > 1.0f) {
            RenderUtil.roundedRect(extractor, sliderX, sliderY, dragValue, sliderHeight, sliderHeight * 0.5f,
                    AerialClickGui.withAlpha(AerialClickGui.themeColor(), rowAlpha), scissor);
        }

        RenderUtil.roundedRect(extractor, sliderX + dragValue - 1.0f, sliderY - 1.3f, 2.0f, 5.0f, 1.0f,
                AerialClickGui.withAlpha(0xFFFFFFFF, rowAlpha), scissor);
    }

    private void drawString(GuiGraphicsExtractor extractor, StringProperty property, float x, float y, float width,
                             float rowAlpha, ScreenRectangle scissor) {
        AerialFont font = AerialClickGui.mediumFont();
        float nameY = y + 0.5f;
        TextRenderUtil.drawString(extractor, font, property.getName(),
                x + 5.0f, nameY, AerialClickGui.PROPERTY_TEXT_SIZE,
                AerialClickGui.withAlpha(AerialClickGui.ENABLED_TEXT_COLOR, rowAlpha), scissor);

        boolean focused = AerialClickGui.getFocusedTextProperty() == property;

        float boxX = x + 6.0f;
        float boxY = y + 9.5f;
        float boxWidth = width - 12.0f;
        float boxHeight = 9.0f;

        if (focused) {
            RenderUtil.roundedRect(extractor, boxX - 1.0f, boxY - 1.0f, boxWidth + 2.0f, boxHeight + 2.0f, 2.5f,
                    AerialClickGui.withAlpha(AerialClickGui.themeColor(), rowAlpha), scissor);
        }
        RenderUtil.roundedRect(extractor, boxX, boxY, boxWidth, boxHeight, 2.0f,
                AerialClickGui.withAlpha(0xFF191919, rowAlpha), scissor);

        String value = property.getValue();
        float textX = boxX + 2.5f;
        float textY = boxY + (boxHeight - CHIP_TEXT_SIZE) * 0.5f;
        TextRenderUtil.drawString(extractor, font, value, textX, textY, CHIP_TEXT_SIZE,
                AerialClickGui.withAlpha(0xFFFFFFFF, rowAlpha), scissor);

        if (focused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            int cursor = Math.max(0, Math.min(property.getCursor(), value.length()));
            float cursorX = textX + font.stringWidth(value.substring(0, cursor), CHIP_TEXT_SIZE);
            RenderUtil.flatRect(extractor, cursorX, boxY + 1.0f, 0.75f, boxHeight - 2.0f,
                    AerialClickGui.withAlpha(0xFFFFFFFF, rowAlpha), scissor);
        }
    }

    static String formatNumber(double value) {
        if (value == (long) value) {
            return String.valueOf((long) value);
        }
        String formatted = String.format("%.3f", value);
        int end = formatted.length();
        while (end > 0 && formatted.charAt(end - 1) == '0') {
            end--;
        }
        if (end > 0 && formatted.charAt(end - 1) == '.') {
            end--;
        }
        return formatted.substring(0, end);
    }

    private void drawBoundedNumber(GuiGraphicsExtractor extractor, BoundedNumberProperty property, float x, float y, float width,
                                    float rowAlpha, ScreenRectangle scissor) {
        AerialFont font = AerialClickGui.mediumFont();
        float nameY = y + 5.0f;
        TextRenderUtil.drawString(extractor, font, property.getName(),
                x + 5.0f, nameY, AerialClickGui.PROPERTY_TEXT_SIZE,
                AerialClickGui.withAlpha(AerialClickGui.ENABLED_TEXT_COLOR, rowAlpha), scissor);

        String valueString = formatNumber(property.getLow()) + " - " + formatNumber(property.getHigh());
        float valueTextWidth = font.stringWidth(valueString, AerialClickGui.PROPERTY_TEXT_SIZE);
        TextRenderUtil.drawString(extractor, font, valueString,
                x + width - 5.0f - valueTextWidth, nameY, AerialClickGui.PROPERTY_TEXT_SIZE,
                AerialClickGui.withAlpha(0xFFFFFFFF, rowAlpha * 0.8f), scissor);

        float sliderWidth = width - 12.0f;
        float sliderHeight = 2.5f;
        float sliderX = x + 6.0f;
        float sliderY = y + 16.0f;

        RenderUtil.roundedRect(extractor, sliderX, sliderY, sliderWidth, sliderHeight, sliderHeight * 0.5f,
                AerialClickGui.withAlpha(0xFF373737, rowAlpha), scissor);

        BoundedDragState drag = boundedNumberStates.get(property);
        float lowValue = drag == null || drag.lowAnim == null ? 0.0f : drag.lowAnim.getValue();
        float highValue = drag == null || drag.highAnim == null ? sliderWidth : drag.highAnim.getValue();

        if (highValue > lowValue) {
            RenderUtil.roundedRect(extractor, sliderX + lowValue, sliderY, highValue - lowValue, sliderHeight, sliderHeight * 0.5f,
                    AerialClickGui.withAlpha(AerialClickGui.themeColor(), rowAlpha), scissor);
        }

        RenderUtil.roundedRect(extractor, sliderX + lowValue - 1.0f, sliderY - 1.3f, 2.0f, 5.0f, 1.0f,
                AerialClickGui.withAlpha(0xFFFFFFFF, rowAlpha), scissor);
        RenderUtil.roundedRect(extractor, sliderX + highValue - 1.0f, sliderY - 1.3f, 2.0f, 5.0f, 1.0f,
                AerialClickGui.withAlpha(0xFFFFFFFF, rowAlpha), scissor);
    }

    private void drawMultipleBoolean(GuiGraphicsExtractor extractor, MultipleBooleanProperty property, float x, float y,
                                      float width, float rowAlpha, ScreenRectangle scissor) {
        AerialFont font = AerialClickGui.mediumFont();

        TextRenderUtil.drawString(extractor, font, property.getName(),
                x + 5.0f, y + 3.0f, AerialClickGui.PROPERTY_TEXT_SIZE,
                AerialClickGui.withAlpha(AerialClickGui.ENABLED_TEXT_COLOR, rowAlpha), scissor);

        float boxX = x + 5.0f;
        float boxY = y + 13.0f;
        float boxWidth = width - 10.0f;
        float[] boxHeightOut = new float[1];
        List<ChipRect> chips = computeChipLayout(property, width, boxHeightOut);
        float boxHeight = boxHeightOut[0];

        RenderUtil.roundedRect(extractor, boxX - 1.0f, boxY - 1.0f, boxWidth + 2.0f, boxHeight + 2.0f, 3.0f,
                AerialClickGui.withAlpha(0xFF505050, rowAlpha), scissor);
        RenderUtil.roundedRect(extractor, boxX, boxY, boxWidth, boxHeight, 2.5f,
                AerialClickGui.withAlpha(0xFF191919, rowAlpha), scissor);

        for (ChipRect chip : chips) {
            boolean on = chip.property().getValue();
            int chipColor = on ? AerialClickGui.withAlpha(AerialClickGui.themeColor(), 0.4f) : darker(AerialClickGui.MUTED_COLOR, 0.6f);
            RenderUtil.roundedRect(extractor, boxX + chip.x(), boxY + chip.y(), chip.width(), 8.5f, 2.5f,
                    AerialClickGui.withAlpha(chipColor, rowAlpha), scissor);

            int textColor = on ? AerialClickGui.withAlpha(0xFFFFFFFF, 0.9f) : 0xFFAAAAAA;
            float textWidth = font.stringWidth(chip.property().getName(), CHIP_TEXT_SIZE);
            TextRenderUtil.drawString(extractor, font, chip.property().getName(),
                    boxX + chip.x() + (chip.width() - textWidth) * 0.5f, boxY + chip.y() + 0.5f, CHIP_TEXT_SIZE,
                    AerialClickGui.withAlpha(textColor, rowAlpha), scissor);
        }
    }

    private void drawGroup(GuiGraphicsExtractor extractor, GroupProperty group, float x, float y, float width,
                            float height, float rowAlpha, int mouseX, int mouseY, ScreenRectangle scissor,
                            boolean isLastProperty) {
        ExpandState state = groupStates.get(group);
        float expand = state == null ? 0.0f : state.anim.getValue();

        boolean roundBottom = isLastProperty && expand <= 0.0f;
        if (flat) {
        } else if (roundBottom) {
            AerialClickGui.drawBottomRoundedRect(extractor, x, y, width, height, 5.0f,
                    AerialClickGui.withAlpha(AerialClickGui.PROPERTY_BG_COLOR, AerialClickGui.PROPERTY_BG_ALPHA * rowAlpha), scissor);
        } else {
            RenderUtil.flatRect(extractor, x, y, width, height,
                    AerialClickGui.withAlpha(AerialClickGui.PROPERTY_BG_COLOR, AerialClickGui.PROPERTY_BG_ALPHA * rowAlpha), scissor);
        }

        float padding = 3.0f * (1.0f - expand);
        float cardX = x + padding;
        float cardY = y + padding;
        float cardWidth = width - padding * 2.0f;

        float cardHeight = GROUP_HEIGHT - padding * (flat ? 1.0f : 2.0f);
        float cardRadius = 4.0f * (1.0f - expand);
        RenderUtil.roundedRect(extractor, cardX, cardY, cardWidth, cardHeight, cardRadius,
                AerialClickGui.withAlpha(GROUP_CARD_COLOR, GROUP_CARD_ALPHA * rowAlpha), scissor);

        AerialFont boldFont = AerialClickGui.boldFont();
        String name = group.getName();
        float nameWidth = boldFont.stringWidth(name, AerialClickGui.PROPERTY_TEXT_SIZE);
        float nameY = cardY + (cardHeight - AerialClickGui.PROPERTY_TEXT_SIZE) * 0.5f;
        TextRenderUtil.drawString(extractor, boldFont, name,
                cardX + (cardWidth - nameWidth) * 0.5f, nameY, AerialClickGui.PROPERTY_TEXT_SIZE,
                AerialClickGui.withAlpha(AerialClickGui.ENABLED_TEXT_COLOR, rowAlpha), scissor);

        drawExpandChevron(extractor, x + 3.0f + width - 20.0f, y + 5.0f, 12.0f, expand,
                AerialClickGui.ENABLED_TEXT_COLOR, rowAlpha, scissor);

        if (expand > 0.0f) {
            int contentTop = Math.round(y + GROUP_HEIGHT);
            int contentHeight = Math.max(0, Math.round(height - GROUP_HEIGHT));
            ScreenRectangle contentScissor = contentHeight > 0
                    ? new ScreenRectangle(Math.round(x), contentTop, Math.round(width), contentHeight).intersection(scissor)
                    : null;

            if (flat && contentHeight > 0) {
                RenderUtil.roundedRectAsym(extractor, cardX, y + GROUP_HEIGHT, cardWidth,
                        height - GROUP_HEIGHT, cardRadius + 1.0f, true,
                        AerialClickGui.withAlpha(GROUP_CARD_COLOR, GROUP_CONTENT_ALPHA * rowAlpha),
                        scissor);
            }

            float childY = y + GROUP_HEIGHT;
            for (Property<?> child : group.getPropertyList()) {
                if (child.isHidden()) {
                    continue;
                }
                float childHeight = cachedHeight(child);
                if (contentScissor != null) {
                    drawOne(extractor, child, x, childY, width, childHeight, rowAlpha, mouseX, mouseY, contentScissor, false);
                }
                childY += childHeight;
            }
        }
    }

    private void drawExpandChevron(GuiGraphicsExtractor extractor, float iconX, float iconY, float iconSize,
                                    float expandValue, int color, float rowAlpha, ScreenRectangle scissor) {
        AerialFont iconFont = AerialClickGui.regularIconFont();
        float angle = (float) Math.toRadians(expandValue * 180.0);

        org.joml.Matrix3x2fStack pose = extractor.pose();
        pose.pushMatrix();
        pose.rotateAbout(angle, iconX + iconSize * 0.5f, iconY + iconSize * 0.5f);
        TextRenderUtil.drawString(extractor, iconFont, String.valueOf(AerialClickGui.EXPAND_ICON),
                iconX, iconY, iconSize, AerialClickGui.withAlpha(color, rowAlpha), scissor);
        pose.popMatrix();
    }

    private static int darker(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.round(((argb >> 16) & 0xFF) * factor);
        int g = Math.round(((argb >> 8) & 0xFF) * factor);
        int b = Math.round((argb & 0xFF) * factor);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int blendOpaque(int baseArgb, int overlayArgb, float overlayAlpha) {
        overlayAlpha = Math.max(0.0f, Math.min(1.0f, overlayAlpha));
        int baseR = (baseArgb >> 16) & 0xFF, baseG = (baseArgb >> 8) & 0xFF, baseB = baseArgb & 0xFF;
        int ovR = (overlayArgb >> 16) & 0xFF, ovG = (overlayArgb >> 8) & 0xFF, ovB = overlayArgb & 0xFF;
        int r = Math.round(baseR + (ovR - baseR) * overlayAlpha);
        int g = Math.round(baseG + (ovG - baseG) * overlayAlpha);
        int b = Math.round(baseB + (ovB - baseB) * overlayAlpha);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    @Override
    public boolean mouseClicked(float x, float y, float width, double mouseX, double mouseY, int button) {
        measure(x, y, width, -1, -1);
        float cursorY = y;
        for (Property<?> property : properties) {
            if (property.isHidden()) {
                continue;
            }
            float height = cachedHeight(property);
            if (clickOne(property, x, cursorY, width, height, mouseX, mouseY, button)) {
                return true;
            }
            cursorY += height;
        }
        return false;
    }

    private boolean clickOne(Property<?> property, float x, float y, float width, float height,
                              double mouseX, double mouseY, int button) {
        if (property instanceof BooleanProperty bool) {
            if (button == 0 && isHovering(x, y, width, height, mouseX, mouseY)) {
                bool.toggle();
                return true;
            }
        } else if (property instanceof NumberProperty number) {
            if (button == 0 && isHovering(x, y, width, height, mouseX, mouseY)) {
                numberStates.computeIfAbsent(number, n -> new DragState()).dragging = true;
                return true;
            }
        } else if (property instanceof ActionProperty action) {
            if (button == 0 && isHovering(x, y, width, height, mouseX, mouseY)) {
                action.run();
                return true;
            }
        } else if (property instanceof KeyProperty key) {
            if (isHovering(x, y, width, height, mouseX, mouseY)) {
                if (button == 1) {
                    key.setValue(KeyProperty.UNBOUND);
                    AerialClickGui.listenForKey(null);
                } else if (button == 0) {
                    AerialClickGui.listenForKey(key);
                }
                return true;
            }
        } else if (property instanceof StringProperty string) {
            if (button == 0 && isHovering(x, y, width, height, mouseX, mouseY)) {
                AerialClickGui.focusTextProperty(string);
                string.cursorToEnd();
                return true;
            }
        } else if (property instanceof BoundedNumberProperty bounded) {
            if (button == 0 && isHovering(x, y, width, height, mouseX, mouseY)) {
                BoundedDragState drag = boundedNumberStates.computeIfAbsent(bounded, b -> new BoundedDragState());
                float sliderX = x + 6.0f;
                float lowValue = drag.lowAnim == null ? 0.0f : drag.lowAnim.getValue();
                float highValue = drag.highAnim == null ? (width - 12.0f) : drag.highAnim.getValue();
                double distLow = Math.abs(mouseX - (sliderX + lowValue));
                double distHigh = Math.abs(mouseX - (sliderX + highValue));
                if (distLow <= distHigh) {
                    drag.draggingLow = true;
                } else {
                    drag.draggingHigh = true;
                }
                return true;
            }
        } else if (property instanceof MultipleBooleanProperty multi) {
            return multipleBooleanClicked(multi, x, y, width, mouseX, mouseY);
        } else if (property instanceof ModeProperty<?> mode) {
            return modeClicked(mode, x, y, width, height, mouseX, mouseY, button);
        } else if (property instanceof GroupProperty group) {
            return groupClicked(group, x, y, width, mouseX, mouseY, button);
        }
        return false;
    }

    private boolean modeClicked(ModeProperty<?> mode, float x, float y, float width, float height,
                                 double mouseX, double mouseY, int button) {
        if (button == 1 && isHovering(x, y, width, 32.0f, mouseX, mouseY)) {
            ExpandState state = modeStates.computeIfAbsent(mode, m -> new ExpandState());
            state.expanded = !state.expanded;
            return true;
        }

        ExpandState state = modeStates.get(mode);
        if (state == null || !state.expanded) {
            return false;
        }

        float rectX = x + 3.0f;
        float rectY = y + 15.0f;
        float rectWidth = width - 13.0f;
        Enum<?>[] values = mode.getValues();
        float added = 0.0f;
        for (Enum<?> option : values) {
            if (option.ordinal() == mode.getValue().ordinal()) {
                continue;
            }
            if (isHovering(rectX, rectY + 16.0f + added, rectWidth, 13.0f, mouseX, mouseY)) {
                mode.setValueOrdinal(option.ordinal());
                state.expanded = false;
                return true;
            }
            added += 13.0f;
        }
        return false;
    }

    private boolean multipleBooleanClicked(MultipleBooleanProperty property, float x, float y, float width,
                                            double mouseX, double mouseY) {
        float boxX = x + 5.0f;
        float boxY = y + 13.0f;
        float[] boxHeightOut = new float[1];
        for (ChipRect chip : computeChipLayout(property, width, boxHeightOut)) {
            if (isHovering(boxX + chip.x(), boxY + chip.y(), chip.width(), 8.5f, mouseX, mouseY)) {
                chip.property().toggle();
                return true;
            }
        }
        return false;
    }

    private boolean groupClicked(GroupProperty group, float x, float y, float width, double mouseX, double mouseY, int button) {
        if (isHovering(x, y, width, 17.0f, mouseX, mouseY)) {
            ExpandState state = groupStates.computeIfAbsent(group, g -> new ExpandState());
            state.expanded = !state.expanded;
            return true;
        }

        ExpandState state = groupStates.get(group);
        if (state == null || state.anim.getValue() <= 0.0f) {
            return false;
        }

        float childY = y + GROUP_HEIGHT;
        for (Property<?> child : group.getPropertyList()) {
            if (child.isHidden()) {
                continue;
            }
            float childHeight = cachedHeight(child);
            if (clickOne(child, x, childY, width, childHeight, mouseX, mouseY, button)) {
                return true;
            }
            childY += childHeight;
        }
        return false;
    }

    @Override
    public void mouseReleased(int button) {
        if (button != 0) {
            return;
        }
        for (DragState state : numberStates.values()) {
            state.dragging = false;
        }
        for (BoundedDragState state : boundedNumberStates.values()) {
            state.draggingLow = false;
            state.draggingHigh = false;
        }
    }

    private static boolean isHovering(float x, float y, float width, float height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
