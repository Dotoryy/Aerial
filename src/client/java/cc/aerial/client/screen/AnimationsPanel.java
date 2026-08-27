package cc.aerial.client.screen;

import cc.aerial.client.features.impl.visual.AnimationsModule;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.GroupProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.property.Property;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.screen.animation.Animation;
import cc.aerial.client.screen.animation.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import org.joml.Matrix3x2fStack;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

final class AnimationsPanel implements RowContent {
    private static final float BOOLEAN_HEIGHT = 17.0f;

    private static final float NUMBER_HEIGHT = 24.0f;

    private static final float MODE_BASE_HEIGHT = 32.0f;
    private static final float MODE_OPTION_HEIGHT = 13.0f;

    private static final float GROUP_HEIGHT = 22.0f;

    private final boolean flat;

    AnimationsPanel() {
        this(false);
    }

    AnimationsPanel(boolean flat) {
        this.flat = flat;
    }

    private static final float GROUP_CONTENT_ALPHA = 0.1f;

    private static final int GROUP_CARD_COLOR = 0xFF000000;
    private static final float GROUP_CARD_ALPHA = 0.2f;

    private final AnimationsModule module = AnimationsModule.INSTANCE;

    private final Map<BooleanProperty, Animation> boolAnims = new IdentityHashMap<>();
    private final Map<NumberProperty, DragState> numberStates = new IdentityHashMap<>();
    private final Map<ModeProperty<?>, ExpandState> modeStates = new IdentityHashMap<>();
    private final Map<GroupProperty, ExpandState> groupStates = new IdentityHashMap<>();

    private final Map<Property<?>, Float> heights = new IdentityHashMap<>();

    private static final class DragState {
        Animation anim;
        boolean dragging;
    }

    private static final class ExpandState {
        final Animation anim = new Animation(Easing.DECELERATE, 125);
        boolean expanded;
    }

    @Override
    public float measure(float x, float y, float width, double mouseX, double mouseY) {
        float total = 0.0f;
        float cursorY = y;
        for (GroupProperty group : module.getGroups()) {
            float h = measureGroup(group, x, cursorY, width, mouseX, mouseY);
            total += h;
            cursorY += h;
        }
        return total;
    }

    private float measureGroup(GroupProperty group, float x, float y, float width, double mouseX, double mouseY) {
        ExpandState state = groupStates.computeIfAbsent(group, g -> new ExpandState());
        state.anim.run(state.expanded ? 1.0f : 0.0f);
        float expand = state.anim.getValue();

        float childrenHeight = 0.0f;
        float childY = y + GROUP_HEIGHT;
        for (Property<?> child : group.getPropertyList()) {
            if (child.isHidden()) {
                continue;
            }
            float h = measureChild(child, x, childY, width, mouseX, mouseY);
            childrenHeight += h;
            childY += h;
        }

        float height = GROUP_HEIGHT + childrenHeight * expand;
        heights.put(group, height);
        return height;
    }

    private float measureChild(Property<?> child, float x, float y, float width, double mouseX, double mouseY) {
        float height;
        if (child instanceof BooleanProperty bool) {
            boolAnims.computeIfAbsent(bool, b -> {
                Animation a = new Animation(Easing.DECELERATE, 150);
                a.setValue(b.getValue() ? 1.0f : 0.0f);
                return a;
            }).run(bool.getValue() ? 1.0f : 0.0f);
            height = BOOLEAN_HEIGHT;
        } else if (child instanceof NumberProperty number) {
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
        } else if (child instanceof ModeProperty<?> mode) {
            ExpandState state = modeStates.computeIfAbsent(mode, m -> new ExpandState());
            state.anim.run(state.expanded ? 1.0f : 0.0f);
            int optionCount = mode.getValues().length - 1;
            height = MODE_BASE_HEIGHT + optionCount * MODE_OPTION_HEIGHT * state.anim.getValue();
        } else {
            height = 0.0f;
        }
        heights.put(child, height);
        return height;
    }

    private float cachedHeight(Property<?> property) {
        Float h = heights.get(property);
        return h == null ? 0.0f : h;
    }

    @Override
    public void draw(GuiGraphicsExtractor extractor, float x, float y, float width, float rowAlpha,
              int mouseX, int mouseY, ScreenRectangle scissor, boolean isLastRow) {
        float cursorY = y;
        List<GroupProperty> groups = List.of(module.getGroups());
        for (int i = 0; i < groups.size(); i++) {
            GroupProperty group = groups.get(i);
            boolean isLastGroup = isLastRow && i == groups.size() - 1;
            float groupHeight = cachedHeight(group);
            drawGroup(extractor, group, x, cursorY, width, groupHeight, rowAlpha, mouseX, mouseY, scissor, isLastGroup);
            cursorY += groupHeight;
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
            if (contentHeight > 0) {
                ScreenRectangle contentScissor = new ScreenRectangle(
                        Math.round(x), contentTop, Math.round(width), contentHeight).intersection(scissor);

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
                    drawChild(extractor, child, x, childY, width, childHeight, rowAlpha, mouseX, mouseY, contentScissor);
                    childY += childHeight;
                }
            }
        }
    }

    private void drawChild(GuiGraphicsExtractor extractor, Property<?> child, float x, float y, float width,
                            float height, float rowAlpha, int mouseX, int mouseY, ScreenRectangle scissor) {
        if (!flat) {
            RenderUtil.flatRect(extractor, x, y, width, height,
                    AerialClickGui.withAlpha(AerialClickGui.PROPERTY_BG_COLOR, AerialClickGui.PROPERTY_BG_ALPHA * rowAlpha), scissor);
        }

        if (child instanceof BooleanProperty bool) {
            drawBoolean(extractor, bool, x, y, width, rowAlpha, scissor);
        } else if (child instanceof NumberProperty number) {
            drawNumber(extractor, number, x, y, width, rowAlpha, scissor);
        } else if (child instanceof ModeProperty<?> mode) {
            drawMode(extractor, mode, x, y, width, height, rowAlpha, scissor);
        }
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

        float switchX = x + 88.0f;
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

        String valueString = SimplePropertyPanel.formatNumber(property.getValue());
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
                rectX + 4.0f, rectY + 4.0f, AerialClickGui.PROPERTY_TEXT_SIZE,
                AerialClickGui.withAlpha(AerialClickGui.ENABLED_TEXT_COLOR, rowAlpha), scissor);

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
                added += 13.0f;
            }
        }
    }

    private void drawExpandChevron(GuiGraphicsExtractor extractor, float iconX, float iconY, float iconSize,
                                    float expandValue, int color, float rowAlpha, ScreenRectangle scissor) {
        AerialFont iconFont = AerialClickGui.regularIconFont();
        float angle = (float) Math.toRadians(expandValue * 180.0);

        Matrix3x2fStack pose = extractor.pose();
        pose.pushMatrix();
        pose.rotateAbout(angle, iconX + iconSize * 0.5f, iconY + iconSize * 0.5f);
        TextRenderUtil.drawString(extractor, iconFont, String.valueOf(AerialClickGui.EXPAND_ICON),
                iconX, iconY, iconSize, AerialClickGui.withAlpha(color, rowAlpha), scissor);
        pose.popMatrix();
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
        for (GroupProperty group : module.getGroups()) {
            float groupHeight = cachedHeight(group);
            if (groupClicked(group, x, cursorY, width, mouseX, mouseY, button)) {
                return true;
            }
            cursorY += groupHeight;
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
            if (childClicked(child, x, childY, width, childHeight, mouseX, mouseY, button)) {
                return true;
            }
            childY += childHeight;
        }
        return false;
    }

    private boolean childClicked(Property<?> child, float x, float y, float width, float height,
                                  double mouseX, double mouseY, int button) {
        if (child instanceof BooleanProperty bool) {
            if (button == 0 && isHovering(x, y, width, height, mouseX, mouseY)) {
                bool.toggle();
                return true;
            }
        } else if (child instanceof NumberProperty number) {
            if (button == 0 && isHovering(x, y, width, height, mouseX, mouseY)) {
                numberStates.computeIfAbsent(number, n -> new DragState()).dragging = true;
                return true;
            }
        } else if (child instanceof ModeProperty<?> mode) {
            return modeClicked(mode, x, y, width, height, mouseX, mouseY, button);
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

    @Override
    public void mouseReleased(int button) {
        if (button != 0) {
            return;
        }
        for (DragState state : numberStates.values()) {
            state.dragging = false;
        }
    }

    private static boolean isHovering(float x, float y, float width, float height, double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
}
