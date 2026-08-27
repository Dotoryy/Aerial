package cc.aerial.client.screen;

import cc.aerial.client.AerialClient;
import cc.aerial.client.binding.BindRepository;
import cc.aerial.client.binding.BindingService;
import cc.aerial.client.binding.InputType;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.visual.AnimationsModule;
import cc.aerial.client.features.impl.visual.InterfaceModule;
import cc.aerial.client.features.repository.ModuleRepository;
import cc.aerial.client.property.KeyProperty;
import cc.aerial.client.property.Property;
import cc.aerial.client.property.StringProperty;
import cc.aerial.client.render.BlurConsumer;
import cc.aerial.client.render.AerialBloomFilter;
import cc.aerial.client.render.AerialBlur;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.screen.animation.Animation;
import cc.aerial.client.screen.animation.Easing;
import cc.aerial.client.screen.animation.Scroller;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2fStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.BooleanSupplier;

public final class AerialClickGui extends Screen {
    private static final int HEADER_COLOR = 0xFF0F0F0F;
    private static final float HEADER_ALPHA = 0.85f;

    private static final int ROW_BASE_COLOR = 0xFF1E1E2D;
    private static final float ROW_BASE_ALPHA = 0.7f;

    static final int MUTED_COLOR = 0xFF808080;
    static final int ENABLED_TEXT_COLOR = 0xFFFFFFFF;
    static final int DISABLED_TEXT_COLOR = 0xFFCCCCCC;
    static final int PROPERTY_FALSE_COLOR = 0xFF3C3C3C;

    static final int PROPERTY_BG_COLOR = 0xFF000000;
    static final float PROPERTY_BG_ALPHA = 0.25f;

    static final float PANEL_WIDTH = 110.0f;

    private static final float PANEL_SPACING = 10.0f;
    private static final float ROW_HEIGHT = 20.0f;

    private static final float PROPERTY_HEIGHT = 17.0f;
    private static final float PANEL_TOP = 25.0f;
    private static final float RADIUS = 5.0f;
    private static final float HEADER_TEXT_SIZE = 9.0f;
    private static final float ROW_TEXT_SIZE = 8.0f;
    static final float PROPERTY_TEXT_SIZE = 7.0f;
    private static final float BIND_TEXT_SIZE = 7.0f;
    static final float TEXT_INSET = 6.0f;

    private static final float SWITCH_SCALE = 0.85f;
    static final float SWITCH_WIDTH = 20.0f * SWITCH_SCALE;
    static final float SWITCH_HEIGHT = 10.0f * SWITCH_SCALE;
    static final float SWITCH_KNOB_MARGIN = 1.0f * SWITCH_SCALE;
    static final float SWITCH_KNOB_TRAVEL = 9.5f * SWITCH_SCALE;

    private static final char ICON_COMBAT = 0xE9E0;
    private static final char ICON_MOVEMENT = 0xE566;
    private static final char ICON_VISUAL = 0xE8F4;
    private static final char ICON_WORLD = 0xE80B;
    private static final char ICON_UTILITY = 0xEA3C;
    private static final float HEADER_ICON_SIZE = 10.0f;

    static final char EXPAND_ICON = 0xE5CF;
    private static final float EXPAND_ICON_SIZE = 12.0f;

    private static AerialFont boldFont;
    private static AerialFont mediumFont;
    private static AerialFont outlinedIconFont;
    private static AerialFont regularIconFont;

    static AerialFont boldFont() {
        ensureFontsLoaded();
        return boldFont;
    }

    static AerialFont mediumFont() {
        ensureFontsLoaded();
        return mediumFont;
    }

    static AerialFont regularIconFont() {
        ensureFontsLoaded();
        return regularIconFont;
    }

    static AerialFont outlinedIconFont() {
        ensureFontsLoaded();
        return outlinedIconFont;
    }

    private final ModuleRepository moduleRepository = AerialClient.getModuleRepository();
    private final AnimationsPanel animationsPanel = new AnimationsPanel();

    private final CategoryPanelState combatPanel = buildPanel(ModuleCategory.COMBAT, ICON_COMBAT, 0);
    private final CategoryPanelState movementPanel = buildPanel(ModuleCategory.MOVEMENT, ICON_MOVEMENT, 1);
    private final CategoryPanelState visualPanel = buildPanel(ModuleCategory.VISUAL, ICON_VISUAL, 2);
    private final CategoryPanelState worldPanel = buildPanel(ModuleCategory.WORLD, ICON_WORLD, 3);
    private final CategoryPanelState utilityPanel = buildPanel(ModuleCategory.UTILITY, ICON_UTILITY, 4);
    private final List<CategoryPanelState> panels = List.of(combatPanel, movementPanel, visualPanel, worldPanel, utilityPanel);

    private boolean closing;

    private Animation allowMovementSwitchAnim;

    private Row selectingBindRow;

    private static StringProperty focusedTextProperty;

    static void focusTextProperty(StringProperty property) {
        focusedTextProperty = property;
    }

    static StringProperty getFocusedTextProperty() {
        return focusedTextProperty;
    }

    private static KeyProperty listeningKeyProperty;

    static void listenForKey(KeyProperty property) {
        listeningKeyProperty = property;
    }

    static KeyProperty getListeningKeyProperty() {
        return listeningKeyProperty;
    }

    public AerialClickGui() {
        super(Component.empty());
    }

    private CategoryPanelState buildPanel(ModuleCategory category, char icon, int index, Row... extraRows) {
        List<Row> rows = new ArrayList<>();
        for (Module module : moduleRepository.getModulesInCategory(category)) {
            rows.add(moduleRow(module));
        }
        for (Row extra : extraRows) {
            rows.add(extra);
        }
        rows.sort(Comparator.comparing(row -> row.name));
        return new CategoryPanelState(category.getName(), icon, List.copyOf(rows), index);
    }

    private Row moduleRow(Module module) {
        Property<?>[] properties = module.getProperties();
        RowContent content;
        if (module instanceof AnimationsModule) {
            content = animationsPanel;
        } else if (properties.length == 0) {
            content = null;
        } else {
            content = new SimplePropertyPanel(properties);
        }
        return new Row(module.getName(), module::isEnabled, module::toggle, null, content, module);
    }

    private static void ensureFontsLoaded() {
        if (boldFont == null) {
            boldFont = AerialFont.createFromResource("OpalProductSansBold.ttf");
            mediumFont = AerialFont.createFromResource("OpalProductSansMedium.ttf");

            outlinedIconFont = AerialFont.createIconFromResource("OpalMaterialIconsOutlined.ttf",
                    ICON_COMBAT, ICON_MOVEMENT, ICON_VISUAL, ICON_WORLD, ICON_UTILITY,
                    ModuleCategory.SCRIPTS.getIcon(),
                    RailClickGui.CLOSE_ICON, RailClickGui.SEARCH_ICON,
                    RailClickGui.STAR_ICON, RailClickGui.STAR_OUTLINE_ICON);
            regularIconFont = AerialFont.createIconFromResource("OpalMaterialIconsRegular.ttf", EXPAND_ICON);
        }
    }

    private float panelX(CategoryPanelState panel) {
        int count = panels.size();
        float totalWidth = count * PANEL_WIDTH + (count - 1) * PANEL_SPACING;
        float startX = (width - totalWidth) * 0.5f;
        return startX + panel.index * (PANEL_WIDTH + PANEL_SPACING);
    }

    public void requestClose() {
        closing = true;
    }

    @Override
    public void tick() {
        CategoryPanelState lastPanel = panels.get(panels.size() - 1);
        if (closing && lastPanel.openAnimation.isFinished() && lastPanel.openAnimation.getValue() <= 0.0f) {
            Minecraft.getInstance().setScreenAndShow(null);
        }
    }

    private float[] rowHeights(CategoryPanelState panel) {
        return rowHeights(panel, -1, -1);
    }

    private float[] rowHeights(CategoryPanelState panel, double mouseX, double mouseY) {
        List<Row> rows = panel.rows;
        float x = panelX(panel);
        float[] heights = new float[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            row.expandAnim.run(row.expanded ? 1.0f : 0.0f);
            float contentHeight = row.content != null
                    ? row.content.measure(x, 0.0f, PANEL_WIDTH, mouseX, mouseY)
                    : PROPERTY_HEIGHT;
            heights[i] = ROW_HEIGHT + (row.hasProperties ? row.expandAnim.getValue() * contentHeight : 0.0f);
        }
        return heights;
    }

    private static float sum(float[] values) {
        float total = 0.0f;
        for (float v : values) {
            total += v;
        }
        return total;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        ensureFontsLoaded();
        for (CategoryPanelState panel : panels) {
            renderPanel(extractor, panel, mouseX, mouseY);
        }
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void renderPanel(GuiGraphicsExtractor extractor, CategoryPanelState panel, int mouseX, int mouseY) {
        AerialBloomFilter.begin(BlurConsumer.CLICK_GUI);
        try {
            renderPanelBody(extractor, panel, mouseX, mouseY);
        } finally {
            AerialBloomFilter.end();
        }
    }

    private void renderPanelBody(GuiGraphicsExtractor extractor, CategoryPanelState panel, int mouseX, int mouseY) {
        panel.openAnimation.run(closing ? 0.0f : 1.0f);
        float openValue = panel.openAnimation.getValue();

        float x = panelX(panel);
        float y = PANEL_TOP;
        float[] rowHeights = rowHeights(panel, mouseX, mouseY);
        float contentHeight = sum(rowHeights);
        float totalHeight = ROW_HEIGHT + contentHeight;

        float availableHeight = Math.max(0.0f, height - y);
        float scissorHeight = Math.min(availableHeight, totalHeight * openValue);

        int roundedScissorHeight = Math.round(scissorHeight);

        float visibleContentHeight = Math.max(0.0f, Math.min(availableHeight - ROW_HEIGHT, contentHeight));
        float maxScrollOffset = Math.max(0, contentHeight - visibleContentHeight);
        panel.scroller.onScroll(maxScrollOffset);
        float scrollOffset = panel.scroller.getAnimation().getValue();

        if (roundedScissorHeight <= 0) {
            return;
        }

        ScreenRectangle panelScissor = new ScreenRectangle(
                Math.round(x), Math.round(y), Math.round(PANEL_WIDTH), roundedScissorHeight);

        AerialBlur.drawBlurredRound(extractor, BlurConsumer.CLICK_GUI, x, y, PANEL_WIDTH,
                Math.min(totalHeight, roundedScissorHeight), RADIUS, openValue, panelScissor);

        drawTopRoundedRect(extractor, x, y, PANEL_WIDTH, ROW_HEIGHT, RADIUS,
                withAlpha(HEADER_COLOR, HEADER_ALPHA * openValue), panelScissor);

        TextRenderUtil.drawString(extractor, boldFont, panel.headerText,
                x + TEXT_INSET, y + (ROW_HEIGHT - HEADER_TEXT_SIZE) * 0.5f, HEADER_TEXT_SIZE,
                withAlpha(ENABLED_TEXT_COLOR, openValue), panelScissor);

        TextRenderUtil.drawString(extractor, outlinedIconFont, String.valueOf(panel.headerIcon),
                x + PANEL_WIDTH - HEADER_ICON_SIZE - TEXT_INSET * 0.5f,
                y + (ROW_HEIGHT - HEADER_ICON_SIZE) * 0.5f, HEADER_ICON_SIZE,
                withAlpha(ENABLED_TEXT_COLOR, openValue), panelScissor);

        int contentTop = Math.round(y + ROW_HEIGHT);
        int contentScissorHeight = Math.round(Math.max(0.0f, scissorHeight - ROW_HEIGHT));
        if (contentScissorHeight > 0) {
            int contentBottom = contentTop + contentScissorHeight;
            ScreenRectangle contentScissor = new ScreenRectangle(
                    Math.round(x), contentTop, Math.round(PANEL_WIDTH), contentScissorHeight);

            drawBottomRoundedRect(extractor, x, y + ROW_HEIGHT, PANEL_WIDTH, contentHeight, RADIUS,
                    withAlpha(ROW_BASE_COLOR, ROW_BASE_ALPHA * openValue), contentScissor);

            float cursorY = y + ROW_HEIGHT;
            List<Row> rows = panel.rows;
            for (int i = 0; i < rows.size(); i++) {
                Row row = rows.get(i);
                boolean isLastRow = i == rows.size() - 1;
                float rowY = cursorY + scrollOffset;

                int rowTop = Math.max(contentTop, Math.round(rowY));
                int rowBottom = Math.min(contentBottom, Math.round(rowY + rowHeights[i]));
                if (rowBottom > rowTop) {
                    ScreenRectangle rowScissor = new ScreenRectangle(
                            Math.round(x), rowTop, Math.round(PANEL_WIDTH), rowBottom - rowTop);
                    drawRow(extractor, row, x, rowY, openValue, mouseX, mouseY, isLastRow, rowScissor);
                }

                cursorY += rowHeights[i];
            }
        }

        drawBinds(extractor, panel, x, y, scrollOffset, openValue, scissorHeight, rowHeights);
    }

    private void drawRow(GuiGraphicsExtractor extractor, Row row, float x, float rowY, float rowAlpha,
                         int mouseX, int mouseY, boolean isLastRow, ScreenRectangle scissor) {
        boolean hovering = mouseX >= x && mouseX <= x + PANEL_WIDTH && mouseY >= rowY && mouseY <= rowY + ROW_HEIGHT;

        row.hoverAnim.run(hovering ? 1.0f : 0.0f);

        row.toggleAnim.run(row.enabled.getAsBoolean() ? 0.4f : 0.0f);
        float toggleValue = row.toggleAnim.getValue();

        boolean roundBottom = isLastRow && !(row.hasProperties && row.expandAnim.getValue() > 0.0f);

        if (toggleValue > 0.0f) {
            int left = withAlpha(themeColor(), toggleValue * rowAlpha);
            int right = withAlpha(themeColorSecondary(), toggleValue * rowAlpha);
            if (roundBottom) {
                drawBottomRoundedRectGradient(extractor, x, rowY, PANEL_WIDTH, ROW_HEIGHT, RADIUS, left, right, scissor);
            } else {
                RenderUtil.flatRectGradient(extractor, x, rowY, PANEL_WIDTH, ROW_HEIGHT, left, right, scissor);
            }
        }

        boolean enabled = row.enabled.getAsBoolean();
        AerialFont nameFont = enabled ? boldFont : mediumFont;
        int rowTextColor = enabled ? ENABLED_TEXT_COLOR : DISABLED_TEXT_COLOR;
        TextRenderUtil.drawString(extractor, nameFont, row.name,
                x + TEXT_INSET, rowY + (ROW_HEIGHT - ROW_TEXT_SIZE) * 0.5f, ROW_TEXT_SIZE,
                withAlpha(rowTextColor, rowAlpha), scissor);

        boolean showingBindLabel = row == selectingBindRow || (isTabHeld() && (row.module != null || row.bindName != null));
        if (row.hasProperties && !showingBindLabel) {
            float iconX = x + PANEL_WIDTH - EXPAND_ICON_SIZE - TEXT_INSET * 0.5f;
            float iconY = rowY + (ROW_HEIGHT - EXPAND_ICON_SIZE) * 0.5f;
            float angle = (float) Math.toRadians(row.expandAnim.getValue() * 180.0);

            Matrix3x2fStack pose = extractor.pose();
            pose.pushMatrix();
            pose.rotateAbout(angle, iconX + EXPAND_ICON_SIZE * 0.5f, iconY + EXPAND_ICON_SIZE * 0.5f);
            TextRenderUtil.drawString(extractor, regularIconFont, String.valueOf(EXPAND_ICON),
                    iconX, iconY, EXPAND_ICON_SIZE,
                    withAlpha(rowTextColor, rowAlpha), scissor);
            pose.popMatrix();
        }

        if (row.hasProperties && row.expandAnim.getValue() > 0.0f) {
            float propertyY = rowY + ROW_HEIGHT;
            if (row.content != null) {
                row.content.draw(extractor, x, propertyY, PANEL_WIDTH, rowAlpha, mouseX, mouseY, scissor, isLastRow);
            } else {
                drawAllowMovementProperty(extractor, x, propertyY, rowAlpha, isLastRow, scissor);
            }
        }
    }

    private void drawAllowMovementProperty(GuiGraphicsExtractor extractor, float x, float y, float rowAlpha,
                                           boolean isLastRow, ScreenRectangle scissor) {
        if (isLastRow) {
            drawBottomRoundedRect(extractor, x, y, PANEL_WIDTH, PROPERTY_HEIGHT, RADIUS,
                    withAlpha(PROPERTY_BG_COLOR, PROPERTY_BG_ALPHA * rowAlpha), scissor);
        } else {
            RenderUtil.flatRect(extractor, x, y, PANEL_WIDTH, PROPERTY_HEIGHT,
                    withAlpha(PROPERTY_BG_COLOR, PROPERTY_BG_ALPHA * rowAlpha), scissor);
        }

        TextRenderUtil.drawString(extractor, mediumFont, "Allow movement",
                x + TEXT_INSET, y + (PROPERTY_HEIGHT - PROPERTY_TEXT_SIZE) * 0.5f, PROPERTY_TEXT_SIZE,
                withAlpha(ENABLED_TEXT_COLOR, rowAlpha), scissor);

        float destination = ClickGuiState.isAllowMovement() ? 1.0f : 0.0f;
        if (allowMovementSwitchAnim == null) {
            allowMovementSwitchAnim = new Animation(Easing.DECELERATE, 150);
            allowMovementSwitchAnim.setValue(destination);
        } else {
            allowMovementSwitchAnim.run(destination);
        }
        float switchValue = allowMovementSwitchAnim.getValue();

        float switchX = x + PANEL_WIDTH - SWITCH_WIDTH - TEXT_INSET;
        float switchY = y + (PROPERTY_HEIGHT - SWITCH_HEIGHT) * 0.5f;
        int trackColor = blendOpaque(PROPERTY_FALSE_COLOR, themeColor(), switchValue);
        RenderUtil.roundedRect(extractor, switchX, switchY, SWITCH_WIDTH, SWITCH_HEIGHT, SWITCH_HEIGHT * 0.5f,
                withAlpha(trackColor, rowAlpha), scissor);

        float knobSize = SWITCH_HEIGHT - SWITCH_KNOB_MARGIN * 2.0f;
        float knobX = switchX + SWITCH_KNOB_MARGIN + switchValue * SWITCH_KNOB_TRAVEL;
        float knobY = switchY + SWITCH_KNOB_MARGIN;
        RenderUtil.roundedRect(extractor, knobX, knobY, knobSize, knobSize, knobSize * 0.5f,
                withAlpha(0xFFFFFFFF, rowAlpha), scissor);
    }

    private void drawBinds(GuiGraphicsExtractor extractor, CategoryPanelState panel, float x, float y, float scrollOffset,
                           float rowAlpha, float scissorHeight, float[] rowHeights) {
        float cursorY = y + ROW_HEIGHT;
        List<Row> rows = panel.rows;
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            float rowY = cursorY + scrollOffset;

            String label;
            if (row == selectingBindRow) {
                label = "...";
            } else if (!isTabHeld()) {
                label = null;
            } else if (row.module != null) {
                BindingService.BindKey key = BindRepository.INSTANCE.getBindingService().getKeyFromBindable(row.module);
                label = key == null ? null : BindRepository.INSTANCE.getNameFromInteger(key.code(), key.type());
            } else {
                label = row.bindName;
            }

            if (label != null) {
                String keyText = "[" + label + "]";
                float keyWidth = mediumFont.stringWidth(keyText, BIND_TEXT_SIZE);
                int scissorH = Math.round(Math.max(0.0f, Math.min(scissorHeight - (cursorY - y), ROW_HEIGHT)));
                if (scissorH > 0) {
                    ScreenRectangle scissor = new ScreenRectangle(
                            Math.round(x), Math.round(rowY), Math.round(PANEL_WIDTH), scissorH);
                    TextRenderUtil.drawString(extractor, mediumFont, keyText,
                            x + PANEL_WIDTH - keyWidth - TEXT_INSET,
                            rowY + (ROW_HEIGHT - BIND_TEXT_SIZE) * 0.5f, BIND_TEXT_SIZE,
                            withAlpha(MUTED_COLOR, rowAlpha), scissor);
                }
            }
            cursorY += rowHeights[i];
        }
    }

    private static boolean isTabHeld() {
        long handle = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_TAB) == GLFW.GLFW_PRESS;
    }

    static void drawBottomRoundedRect(GuiGraphicsExtractor extractor, float x, float y,
                                              float width, float height, float radius, int color,
                                              ScreenRectangle scissor) {
        RenderUtil.roundedRectAsym(extractor, x, y, width, height, radius, true, color, scissor);
    }

    static void drawBottomRoundedRectGradient(GuiGraphicsExtractor extractor, float x, float y,
                                              float width, float height, float radius, int colorLeft, int colorRight,
                                              ScreenRectangle scissor) {
        RenderUtil.roundedRectAsymGradient(extractor, x, y, width, height, radius, true, colorLeft, colorRight, scissor);
    }

    private static void drawTopRoundedRect(GuiGraphicsExtractor extractor, float x, float y,
                                           float width, float height, float radius, int color,
                                           ScreenRectangle scissor) {
        RenderUtil.roundedRectAsym(extractor, x, y, width, height, radius, false, color, scissor);
    }

    static int withAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Math.max(0.0f, Math.min(1.0f, alpha)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    static int themeColor() {
        return InterfaceModule.INSTANCE.getTheme().getFirstColor().getRGB();
    }

    static int themeColorSecondary() {
        return InterfaceModule.INSTANCE.getTheme().getSecondColor().getRGB();
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (selectingBindRow != null) {
            BindingService bindingService = BindRepository.INSTANCE.getBindingService();
            bindingService.clearBindings(selectingBindRow.module);
            bindingService.register(event.button(), selectingBindRow.module, InputType.MOUSE);
            selectingBindRow = null;
            return true;
        }

        focusedTextProperty = null;
        listeningKeyProperty = null;

        for (CategoryPanelState panel : panels) {
            if (panelMouseClicked(panel, event)) {
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    private boolean panelMouseClicked(CategoryPanelState panel, MouseButtonEvent event) {
        float x = panelX(panel);
        float cursorY = PANEL_TOP + ROW_HEIGHT + panel.scroller.getAnimation().getValue();
        float[] heights = rowHeights(panel);

        for (int i = 0; i < panel.rows.size(); i++) {
            Row row = panel.rows.get(i);
            float thisRowHeight = heights[i];

            if (inBounds(x, cursorY, event.x(), event.y(), PANEL_WIDTH, ROW_HEIGHT)) {
                if (event.button() == 0) {
                    row.toggle.run();
                } else if (event.button() == 1 && row.hasProperties) {
                    row.expanded = !row.expanded;
                } else if (event.button() == 2 && row.module != null) {
                    selectingBindRow = row;
                }
                return true;
            }

            if (row.hasProperties && row.expandAnim.getValue() > 0.0f) {
                float propertyY = cursorY + ROW_HEIGHT;
                if (row.content != null) {
                    if (row.content.mouseClicked(x, propertyY, PANEL_WIDTH, event.x(), event.y(), event.button())) {
                        return true;
                    }
                }
            }

            cursorY += thisRowHeight;
        }

        return false;
    }

    private static boolean inBounds(float x, float y, double px, double py, float w, float h) {
        return px >= x && px <= x + w && py >= y && py <= y + h;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == 0) {
            for (CategoryPanelState panel : panels) {
                for (Row row : panel.rows) {
                    if (row.content != null) {
                        row.content.mouseReleased(event.button());
                    }
                }
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        for (CategoryPanelState panel : panels) {
            float x = panelX(panel);
            float contentTop = PANEL_TOP + ROW_HEIGHT;
            if (mouseX >= x && mouseX <= x + PANEL_WIDTH && mouseY >= contentTop) {
                float maxOffset = Math.max(0, sum(rowHeights(panel)) - (height - contentTop));
                panel.scroller.addScroll(verticalAmount, maxOffset);
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (focusedTextProperty != null && event.isAllowedChatCharacter()) {
            focusedTextProperty.insertChar((char) event.codepoint());
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (focusedTextProperty != null) {
            if (keyEvent.isPaste()) {
                focusedTextProperty.insert(Minecraft.getInstance().keyboardHandler.getClipboard());
                return true;
            }
            if (keyEvent.isCopy()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(focusedTextProperty.getValue());
                return true;
            }
            if (keyEvent.isCut()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(focusedTextProperty.getValue());
                focusedTextProperty.setValue("");
                focusedTextProperty.cursorToEnd();
                return true;
            }
            int key = keyEvent.key();
            if (key == GLFW.GLFW_KEY_DELETE) {
                focusedTextProperty.delete();
                return true;
            }
            if (key == GLFW.GLFW_KEY_HOME) {
                focusedTextProperty.moveCursor(-focusedTextProperty.getValue().length());
                return true;
            }
            if (key == GLFW.GLFW_KEY_END) {
                focusedTextProperty.cursorToEnd();
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                focusedTextProperty.backspace();
                return true;
            } else if (key == GLFW.GLFW_KEY_LEFT) {
                focusedTextProperty.moveCursor(-1);
                return true;
            } else if (key == GLFW.GLFW_KEY_RIGHT) {
                focusedTextProperty.moveCursor(1);
                return true;
            } else if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                focusedTextProperty = null;
                return true;
            }
            return true;
        }

        if (listeningKeyProperty != null) {
            listeningKeyProperty.setValue(keyEvent.key() == GLFW.GLFW_KEY_ESCAPE
                    ? KeyProperty.UNBOUND : keyEvent.key());
            listeningKeyProperty = null;
            return true;
        }

        if (selectingBindRow != null) {
            BindingService bindingService = BindRepository.INSTANCE.getBindingService();
            bindingService.clearBindings(selectingBindRow.module);
            if (keyEvent.key() != GLFW.GLFW_KEY_ESCAPE) {
                bindingService.register(keyEvent.key(), selectingBindRow.module, InputType.KEYBOARD);
            }
            selectingBindRow = null;
            return true;
        }

        if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE && shouldCloseOnEsc()) {
            requestClose();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class CategoryPanelState {
        final String headerText;
        final char headerIcon;
        final List<Row> rows;
        final int index;
        final Animation openAnimation;
        final Scroller scroller = new Scroller();

        CategoryPanelState(String headerText, char headerIcon, List<Row> rows, int index) {
            this.headerText = headerText;
            this.headerIcon = headerIcon;
            this.rows = rows;
            this.index = index;

            this.openAnimation = new Animation(Easing.EASE_OUT_SINE, 100 + index * 80L);
        }
    }

    private static final class Row {
        final String name;
        final BooleanSupplier enabled;
        final Runnable toggle;
        final String bindName;
        final boolean hasProperties;
        final RowContent content;

        final Module module;
        final Animation hoverAnim = new Animation(Easing.DECELERATE, 150);
        final Animation toggleAnim = new Animation(Easing.DECELERATE, 150);
        final Animation expandAnim = new Animation(Easing.DECELERATE, 125);
        boolean expanded;

        Row(String name, BooleanSupplier enabled, Runnable toggle, String bindName, boolean hasProperties) {
            this(name, enabled, toggle, bindName, hasProperties, null, null);
        }

        Row(String name, BooleanSupplier enabled, Runnable toggle, String bindName, RowContent content, Module module) {
            this(name, enabled, toggle, bindName, content != null, content, module);
        }

        private Row(String name, BooleanSupplier enabled, Runnable toggle, String bindName, boolean hasProperties, RowContent content, Module module) {
            this.name = name;
            this.enabled = enabled;
            this.toggle = toggle;
            this.bindName = bindName;
            this.hasProperties = hasProperties;
            this.content = content;
            this.module = module;
        }
    }
}
