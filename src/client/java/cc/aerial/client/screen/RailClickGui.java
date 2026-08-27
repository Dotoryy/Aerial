package cc.aerial.client.screen;

import cc.aerial.client.AerialClient;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.visual.AnimationsModule;
import cc.aerial.client.property.Property;
import cc.aerial.client.render.BlurConsumer;
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
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RailClickGui extends Screen {
    private static final float PANEL_WIDTH = 470.0f;

    private static final float PANEL_HEIGHT = 322.0f;

    private static final float PADDING = 8.0f;
    private static final float GAP = 6.0f;
    private static final float CARD_RADIUS = 10.0f;

    private static final float RAIL_WIDTH = 40.0f;
    private static final float LIST_WIDTH = 156.0f;

    private static final float RAIL_SLOT = 30.0f;
    private static final float LIST_ROW = 24.0f;
    private static final float LIST_HEADER = 24.0f;
    private static final float DETAIL_HEADER = 38.0f;

    private static final int CARD_TINT = 0xA8121319;
    private static final int TEXT = 0xFFF2F3F5;
    private static final int TEXT_DIM = 0xFF9CA0AA;
    private static final int TEXT_FAINT = 0xFF6E727C;

    private static final int FAVOURITE_GOLD = 0xFFFFC93C;

    private static final int FIELD_TINT = 0x66343A46;
    private static final float FIELD_HEIGHT = 16.0f;

    private static final long FIELD_FADE_MS = 200L;

    private static final float TITLE_SIZE = 9.0f;
    private static final float ROW_SIZE = 8.0f;
    private static final float SMALL_SIZE = 7.0f;

    static final char CLOSE_ICON = 0xE5CD;
    static final char SEARCH_ICON = 0xE8B6;
    static final char STAR_ICON = 0xE838;
    static final char STAR_OUTLINE_ICON = 0xE83A;

    private static final float STAR_HIT = 22.0f;

    private static final float STAR_INSET = 7.0f;

    private static final float ROW_INSET = 5.0f;
    private static final float ROW_RADIUS = 7.0f;

    private static final ModuleRepositoryView MODULES = new ModuleRepositoryView();
    private static final AnimationsPanel ANIMATIONS_PANEL = new AnimationsPanel(true);
    private static final Map<Module, RowContent> PANELS = new IdentityHashMap<>();
    private final Map<Module, Animation> toggleAnims = new IdentityHashMap<>();
    private final Map<ModuleCategory, Animation> railAnims = new IdentityHashMap<>();

    private static final Animation SEARCH_FIELD_ANIM = new Animation(Easing.EASE_OUT_EXPO, FIELD_FADE_MS);

    private final Animation searchAnim = new Animation(Easing.EASE_OUT_EXPO, 220L);
    private final Animation favouriteAnim = new Animation(Easing.EASE_OUT_EXPO, 220L);
    private final Animation openAnim = new Animation(Easing.EASE_OUT_EXPO, 320L);

    private static final Scroller LIST_SCROLL = new Scroller();
    private static final Scroller DETAIL_SCROLL = new Scroller();
    private static View sView = View.CATEGORY;
    private static ModuleCategory sCategory = ModuleCategory.COMBAT;
    private static Module sSelected;
    private static String sQuery = "";

    private static boolean sSearchFocused;
    private static float sPanelX = Float.NaN;
    private static float sPanelY;

    private enum View {
        SEARCH, FAVOURITES, CATEGORY
    }

    private static final Set<Module> FAVOURITES =
            java.util.Collections.newSetFromMap(new IdentityHashMap<>());

    private float detailContentHeight;

    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;
    private boolean closing;

    public RailClickGui() {
        super(Component.literal("Aerial"));
        for (ModuleCategory value : ModuleCategory.VALUES) {
            railAnims.put(value, new Animation(Easing.EASE_OUT_EXPO, 220L));
        }

        if (sSelected == null) {
            selectFirstVisible();
        }
    }

    public void requestClose() {
        closing = true;
    }

    private float driveOpenAnimation() {
        openAnim.run(closing ? 0.0f : 1.0f);
        return openAnim.getValue();
    }

    @Override
    public void tick() {
        if (closing && openAnim.getValue() <= 0.02f) {
            Minecraft.getInstance().setScreenAndShow(null);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        ensureCentred();
        if (Float.isNaN(sPanelX)) {
            return;
        }
        float progress = driveOpenAnimation();
        if (progress <= 0.005f) {
            return;
        }

        float x = sPanelX;
        float y = sPanelY;

        float lift = (1.0f - progress) * 14.0f;
        y += lift;

        float cardY = y + PADDING;
        float cardH = PANEL_HEIGHT - PADDING * 2.0f;
        float railX = x + PADDING;
        float listX = railX + RAIL_WIDTH + GAP;
        float detailX = listX + LIST_WIDTH + GAP;
        float detailW = PANEL_WIDTH - PADDING * 2.0f - RAIL_WIDTH - LIST_WIDTH - GAP * 2.0f;

        drawRail(extractor, railX, cardY, cardH, mouseX, mouseY, progress);
        drawList(extractor, listX, cardY, cardH, mouseX, mouseY, progress);
        drawDetail(extractor, detailX, cardY, cardH, detailW, mouseX, mouseY, progress);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void drawCardGlass(GuiGraphicsExtractor extractor, float x, float y,
                               float width, float height, float progress) {
        AerialBlur.drawGlass(extractor, BlurConsumer.CLICK_GUI, x, y, width, height,
                CARD_RADIUS, CARD_TINT, progress, null);
    }

    private void drawRail(GuiGraphicsExtractor extractor, float x, float y, float height,
                          int mouseX, int mouseY, float progress) {
        drawCardGlass(extractor, x, y, RAIL_WIDTH, height, progress);

        int accent = AerialClickGui.themeColor();

        float markSize = 12.0f;
        RenderUtil.roundedRect(extractor, x + (RAIL_WIDTH - markSize) / 2.0f, y + 12.0f,
                markSize, markSize, 4.0f, AerialClickGui.withAlpha(accent, progress), null);

        float slotY = y + 36.0f;
        slotY = drawRailSlot(extractor, x, slotY, SEARCH_ICON,
                sView == View.SEARCH, searchAnim, mouseX, mouseY, progress, accent);
        slotY = drawRailSlot(extractor, x, slotY, FAVOURITES.isEmpty() ? STAR_OUTLINE_ICON : STAR_ICON,
                sView == View.FAVOURITES, favouriteAnim, mouseX, mouseY, progress, accent);

        RenderUtil.roundedRect(extractor, x + 13.0f, slotY + 3.0f, RAIL_WIDTH - 26.0f, 1.0f, 0.5f,
                AerialClickGui.withAlpha(0x30FFFFFF, progress), null);
        slotY += 8.0f;

        for (ModuleCategory value : ModuleCategory.VALUES) {
            slotY = drawRailSlot(extractor, x, slotY, value.getIcon(),
                    sView == View.CATEGORY && value == sCategory, railAnims.get(value),
                    mouseX, mouseY, progress, accent);
        }

        float closeY = y + height - RAIL_SLOT - 4.0f;
        boolean closeHover = contains(mouseX, mouseY, x, closeY, RAIL_WIDTH, RAIL_SLOT);
        drawCentredIcon(extractor, CLOSE_ICON, x, closeY, RAIL_WIDTH, RAIL_SLOT, 12.0f,
                AerialClickGui.withAlpha(closeHover ? TEXT : TEXT_FAINT, progress));
    }

    private float drawRailSlot(GuiGraphicsExtractor extractor, float x, float slotY, char icon,
                               boolean active, Animation anim, int mouseX, int mouseY,
                               float progress, int accent) {
        anim.run(active ? 1.0f : 0.0f);
        float t = anim.getValue();

        boolean hovered = contains(mouseX, mouseY, x, slotY, RAIL_WIDTH, RAIL_SLOT);
        float surface = Math.max(t, hovered ? 0.35f : 0.0f);
        if (surface > 0.01f) {
            RenderUtil.roundedRect(extractor, x + 4.0f, slotY + 3.0f,
                    RAIL_WIDTH - 8.0f, RAIL_SLOT - 6.0f, 8.0f,
                    AerialClickGui.withAlpha(accent, 0.14f * surface * progress), null);
        }

        if (t > 0.01f) {
            float barH = (RAIL_SLOT - 14.0f) * t;
            RenderUtil.roundedRect(extractor, x + RAIL_WIDTH - 2.5f,
                    slotY + (RAIL_SLOT - barH) / 2.0f, 2.5f, barH, 1.25f,
                    AerialClickGui.withAlpha(accent, progress), null);
        }
        drawCentredIcon(extractor, icon, x, slotY, RAIL_WIDTH, RAIL_SLOT, 13.0f,
                AerialClickGui.withAlpha(blend(TEXT_FAINT, accent, t), progress));
        return slotY + RAIL_SLOT;
    }

    private void drawList(GuiGraphicsExtractor extractor, float x, float y, float height,
                          int mouseX, int mouseY, float progress) {
        drawCardGlass(extractor, x, y, LIST_WIDTH, height, progress);

        AerialFont bold = AerialClickGui.boldFont();
        AerialFont medium = AerialClickGui.mediumFont();
        int accent = AerialClickGui.themeColor();

        if (sView == View.SEARCH) {
            float fieldX = x + ROW_INSET;
            float fieldY = searchFieldY(y);
            float fieldW = searchFieldWidth();

            float fieldRadius = FIELD_HEIGHT / 2.0f;

            boolean searching = sSearchFocused;
            SEARCH_FIELD_ANIM.run(searching ? 1.0f : 0.0f);
            float active = SEARCH_FIELD_ANIM.getValue();

            if (active > 0.01f) {
                float grownH = FIELD_HEIGHT * (0.55f + 0.45f * active);
                float grownY = fieldY + (FIELD_HEIGHT - grownH) / 2.0f;
                float grownRadius = grownH / 2.0f;
                AerialBlur.drawGlass(extractor, BlurConsumer.CLICK_GUI, fieldX, grownY,
                        fieldW, grownH, grownRadius, FIELD_TINT, progress * active, null);
            }

            drawCentredIcon(extractor, SEARCH_ICON, fieldX + 2.0f, fieldY, 14.0f, FIELD_HEIGHT,
                    9.0f, AerialClickGui.withAlpha(TEXT_FAINT, progress));

            boolean hasQuery = !sQuery.isEmpty();
            String shown = hasQuery ? sQuery : "Search";
            int colour = hasQuery ? (searching ? TEXT : TEXT_DIM) : TEXT_FAINT;
            float textX = fieldX + 17.0f;
            float textY = fieldY + (FIELD_HEIGHT - ROW_SIZE) / 2.0f - 0.5f;

            ScreenRectangle fieldClip = rect(fieldX, fieldY, fieldW, FIELD_HEIGHT);
            TextRenderUtil.drawString(extractor, medium, shown, textX, textY,
                    ROW_SIZE, AerialClickGui.withAlpha(colour, progress), fieldClip);
        } else {
            TextRenderUtil.drawString(extractor, bold, headerLabel(), x + 12.0f, y + 9.0f,
                    TITLE_SIZE, AerialClickGui.withAlpha(TEXT, progress));
        }

        List<Module> list = visibleModules();
        float bodyY = y + LIST_HEADER;
        float bodyH = height - LIST_HEADER - 6.0f;
        ScreenRectangle clip = rect(x, bodyY, LIST_WIDTH, bodyH);
        float content = list.size() * LIST_ROW;
        LIST_SCROLL.onScroll(Math.max(0.0f, content - bodyH));

        if (list.isEmpty()) {
            String empty = sView == View.FAVOURITES
                    ? "Star a module to pin it here"
                    : (sView == View.SEARCH && sQuery.isEmpty() ? "Type to search" : "Nothing found");
            float w = medium.stringWidth(empty, SMALL_SIZE);
            TextRenderUtil.drawString(extractor, medium, empty, x + (LIST_WIDTH - w) / 2.0f,
                    bodyY + 18.0f, SMALL_SIZE, AerialClickGui.withAlpha(TEXT_FAINT, progress));
            return;
        }

        float rowY = bodyY + LIST_SCROLL.getAnimation().getValue();
        for (Module module : list) {
            if (rowY + LIST_ROW >= bodyY && rowY <= bodyY + bodyH) {
                drawListRow(extractor, module, x, rowY, mouseX, mouseY, progress, clip, accent, medium);
            }
            rowY += LIST_ROW;
        }
    }

    private void drawListRow(GuiGraphicsExtractor extractor, Module module, float x, float rowY,
                             int mouseX, int mouseY, float progress, ScreenRectangle clip,
                             int accent, AerialFont medium) {
        boolean isSelected = module == sSelected;
        boolean hovered = contains(mouseX, mouseY, x, rowY, LIST_WIDTH, LIST_ROW);

        Animation anim = toggleAnims.computeIfAbsent(module,
                key -> new Animation(Easing.EASE_OUT_EXPO, 200L));
        anim.run(module.isEnabled() ? 1.0f : 0.0f);
        float on = anim.getValue();

        float surface = isSelected ? 0.55f : (hovered ? 0.22f : 0.0f);
        if (surface > 0.01f) {
            RenderUtil.roundedRect(extractor, x + ROW_INSET, rowY + 1.5f,
                    LIST_WIDTH - ROW_INSET * 2.0f, LIST_ROW - 3.0f, ROW_RADIUS,
                    AerialClickGui.withAlpha(0xFFFFFFFF, 0.07f * surface * progress), clip);
        }

        if (on > 0.01f) {
            float barH = (LIST_ROW - 11.0f) * on;
            RenderUtil.roundedRect(extractor, x + ROW_INSET + 2.0f, rowY + (LIST_ROW - barH) / 2.0f,
                    2.5f, barH, 1.25f, AerialClickGui.withAlpha(accent, progress), clip);
        }

        boolean starred = FAVOURITES.contains(module);
        float starX = starStripX(x);
        boolean starHover = contains(mouseX, mouseY, starX, rowY, STAR_HIT, LIST_ROW);
        int starColour = starred ? FAVOURITE_GOLD : (starHover ? TEXT_DIM : TEXT_FAINT);
        drawCentredIcon(extractor, starred ? STAR_ICON : STAR_OUTLINE_ICON,
                starX, rowY, STAR_HIT, LIST_ROW, 10.0f,
                AerialClickGui.withAlpha(starColour, progress));

        float rightEdge = starX - 1.0f;
        String suffix = module.getSuffix();
        if (suffix != null && !suffix.isEmpty()) {
            float width = medium.stringWidth(suffix, SMALL_SIZE);
            TextRenderUtil.drawString(extractor, medium, suffix, rightEdge - width,
                    rowY + (LIST_ROW - SMALL_SIZE) / 2.0f - 0.5f, SMALL_SIZE,
                    AerialClickGui.withAlpha(TEXT_FAINT, progress), clip);
            rightEdge -= width + 4.0f;
        }

        int nameColor = blend(TEXT_DIM, TEXT, Math.max(on, isSelected ? 1.0f : 0.0f));

        ScreenRectangle nameClip = new ScreenRectangle(clip.left(), clip.top(),
                Math.max(0, (int) Math.ceil(rightEdge) - clip.left()), clip.height());
        TextRenderUtil.drawString(extractor, medium, module.getName(), x + 15.0f,
                rowY + (LIST_ROW - ROW_SIZE) / 2.0f - 0.5f, ROW_SIZE,
                AerialClickGui.withAlpha(nameColor, progress), nameClip);
    }

    private void drawDetail(GuiGraphicsExtractor extractor, float x, float y, float height,
                            float width, int mouseX, int mouseY, float progress) {
        drawCardGlass(extractor, x, y, width, height, progress);

        AerialFont bold = AerialClickGui.boldFont();
        AerialFont medium = AerialClickGui.mediumFont();

        if (sSelected == null) {
            String empty = "Select a module";
            float w = medium.stringWidth(empty, ROW_SIZE);
            TextRenderUtil.drawString(extractor, medium, empty, x + (width - w) / 2.0f,
                    y + height / 2.0f - ROW_SIZE, ROW_SIZE,
                    AerialClickGui.withAlpha(TEXT_FAINT, progress));
            return;
        }

        TextRenderUtil.drawString(extractor, bold, sSelected.getName(), x + 14.0f, y + 11.0f,
                10.0f, AerialClickGui.withAlpha(TEXT, progress));
        String description = sSelected.getDescription();
        if (description != null && !description.isEmpty()) {
            TextRenderUtil.drawString(extractor, medium, description, x + 14.0f, y + 24.0f,
                    SMALL_SIZE, AerialClickGui.withAlpha(TEXT_FAINT, progress));
        }

        drawSwitch(extractor, x + width - 14.0f - 26.0f, y + 12.0f, mouseX, mouseY, progress);

        RowContent content = contentFor(sSelected);
        float bodyY = y + DETAIL_HEADER;
        float bodyH = height - DETAIL_HEADER - 8.0f;
        ScreenRectangle clip = rect(x, bodyY, width, bodyH);
        if (content == null) {
            String empty = "No settings";
            float w = medium.stringWidth(empty, SMALL_SIZE);
            TextRenderUtil.drawString(extractor, medium, empty, x + (width - w) / 2.0f,
                    bodyY + bodyH / 2.0f - SMALL_SIZE, SMALL_SIZE,
                    AerialClickGui.withAlpha(TEXT_FAINT, progress));
            return;
        }

        float inner = width - 24.0f;
        float measured = content.measure(x + 12.0f, bodyY, inner, mouseX, mouseY);
        detailContentHeight = measured;
        DETAIL_SCROLL.onScroll(Math.max(0.0f, measured - bodyH));
        content.draw(extractor, x + 12.0f, bodyY + DETAIL_SCROLL.getAnimation().getValue(),
                inner, progress, mouseX, mouseY, clip, true);
    }

    private void drawSwitch(GuiGraphicsExtractor extractor, float x, float y,
                            int mouseX, int mouseY, float progress) {
        float w = 26.0f;
        float h = 14.0f;
        Animation anim = toggleAnims.computeIfAbsent(sSelected,
                key -> new Animation(Easing.EASE_OUT_EXPO, 200L));
        anim.run(sSelected.isEnabled() ? 1.0f : 0.0f);
        float on = anim.getValue();

        int track = blend(0xFF3A3E48, AerialClickGui.themeColor(), on);
        RenderUtil.roundedRect(extractor, x, y, w, h, h / 2.0f,
                AerialClickGui.withAlpha(track, progress), null);
        float knob = h - 4.0f;
        RenderUtil.roundedRect(extractor, x + 2.0f + (w - knob - 4.0f) * on, y + 2.0f,
                knob, knob, knob / 2.0f, AerialClickGui.withAlpha(0xFFFFFFFF, progress), null);
    }

    private void drawCentredIcon(GuiGraphicsExtractor extractor, char icon, float x, float y,
                                 float width, float height, float size, int color) {
        AerialFont font = AerialClickGui.outlinedIconFont();
        String text = String.valueOf(icon);
        float w = font.stringWidth(text, size);
        TextRenderUtil.drawString(extractor, font, text, x + (width - w) / 2.0f,
                y + (height - size) / 2.0f, size, color);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        ensureCentred();
        if (Float.isNaN(sPanelX)) {
            return super.mouseClicked(event, doubled);
        }
        double mx = event.x();
        double my = event.y();
        int button = event.button();

        float x = sPanelX;
        float y = sPanelY;
        float cardY = y + PADDING;
        float cardH = PANEL_HEIGHT - PADDING * 2.0f;
        float railX = x + PADDING;
        float listX = railX + RAIL_WIDTH + GAP;
        float detailX = listX + LIST_WIDTH + GAP;
        float detailW = PANEL_WIDTH - PADDING * 2.0f - RAIL_WIDTH - LIST_WIDTH - GAP * 2.0f;

        if (!contains(mx, my, x, y, PANEL_WIDTH, PANEL_HEIGHT)) {
            return super.mouseClicked(event, doubled);
        }

        sSearchFocused = false;

        if (contains(mx, my, railX, cardY, RAIL_WIDTH, cardH)) {
            float slotY = cardY + 36.0f;
            if (contains(mx, my, railX, slotY, RAIL_WIDTH, RAIL_SLOT)) {
                switchTo(View.SEARCH, sCategory);

                sSearchFocused = true;
                return true;
            }
            slotY += RAIL_SLOT;
            if (contains(mx, my, railX, slotY, RAIL_WIDTH, RAIL_SLOT)) {
                switchTo(View.FAVOURITES, sCategory);
                return true;
            }
            slotY += RAIL_SLOT + 8.0f;

            for (ModuleCategory value : ModuleCategory.VALUES) {
                if (contains(mx, my, railX, slotY, RAIL_WIDTH, RAIL_SLOT)) {
                    switchTo(View.CATEGORY, value);
                    return true;
                }
                slotY += RAIL_SLOT;
            }
            float closeY = cardY + cardH - RAIL_SLOT - 4.0f;
            if (contains(mx, my, railX, closeY, RAIL_WIDTH, RAIL_SLOT)) {
                requestClose();
                return true;
            }
            return true;
        }

        if (sView == View.SEARCH
                && contains(mx, my, listX + ROW_INSET, searchFieldY(cardY),
                        searchFieldWidth(), FIELD_HEIGHT)) {
            sSearchFocused = true;
            return true;
        }

        if (contains(mx, my, listX, cardY, LIST_WIDTH, LIST_HEADER)) {
            dragging = true;
            dragOffsetX = (float) mx - sPanelX;
            dragOffsetY = (float) my - sPanelY;
            return true;
        }

        float bodyY = cardY + LIST_HEADER;
        float bodyH = cardH - LIST_HEADER - 6.0f;
        if (contains(mx, my, listX, bodyY, LIST_WIDTH, bodyH)) {
            float rowY = bodyY + LIST_SCROLL.getAnimation().getValue();
            for (Module module : visibleModules()) {
                if (contains(mx, my, listX, rowY, LIST_WIDTH, LIST_ROW)) {
                    if (contains(mx, my, starStripX(listX), rowY, STAR_HIT, LIST_ROW)) {
                        if (!FAVOURITES.remove(module)) {
                            FAVOURITES.add(module);
                        }
                    } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                        if (sSelected != module) {
                            sSelected = module;
                            DETAIL_SCROLL.getAnimation().setValue(0.0f);
                        }
                    } else {
                        module.toggle();
                    }
                    return true;
                }
                rowY += LIST_ROW;
            }
            return true;
        }

        if (contains(mx, my, detailX, cardY, detailW, cardH)) {
            if (sSelected != null
                    && contains(mx, my, detailX + detailW - 40.0f, cardY + 12.0f, 26.0f, 14.0f)) {
                sSelected.toggle();
                return true;
            }
            RowContent content = sSelected == null ? null : contentFor(sSelected);
            if (content != null) {
                float detailBodyY = cardY + DETAIL_HEADER + DETAIL_SCROLL.getAnimation().getValue();
                if (content.mouseClicked(detailX + 12.0f, detailBodyY, detailW - 24.0f, mx, my, button)) {
                    return true;
                }
            }
            return true;
        }

        dragging = true;
        dragOffsetX = (float) mx - sPanelX;
        dragOffsetY = (float) my - sPanelY;
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        dragging = false;
        if (sSelected != null) {
            RowContent content = contentFor(sSelected);
            if (content != null) {
                content.mouseReleased(event.button());
            }
        }
        return super.mouseReleased(event);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (dragging) {
            sPanelX = (float) mouseX - dragOffsetX;
            sPanelY = (float) mouseY - dragOffsetY;
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        ensureCentred();
        if (Float.isNaN(sPanelX)) {
            return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
        }
        float listX = sPanelX + PADDING + RAIL_WIDTH + GAP;
        float detailX = listX + LIST_WIDTH + GAP;
        float cardY = sPanelY + PADDING;
        float cardH = PANEL_HEIGHT - PADDING * 2.0f;
        float detailW = PANEL_WIDTH - PADDING * 2.0f - RAIL_WIDTH - LIST_WIDTH - GAP * 2.0f;

        if (contains(mouseX, mouseY, listX, cardY, LIST_WIDTH, cardH)) {
            float max = Math.max(0.0f,
                    visibleModules().size() * LIST_ROW - (cardH - LIST_HEADER - 6.0f));
            LIST_SCROLL.addScroll(vertical, max);
            return true;
        }
        if (contains(mouseX, mouseY, detailX, cardY, detailW, cardH)) {
            float max = Math.max(0.0f, detailContentHeight - (cardH - DETAIL_HEADER - 8.0f));
            DETAIL_SCROLL.addScroll(vertical, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (sView == View.SEARCH && sSearchFocused) {
            if (keyEvent.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!sQuery.isEmpty()) {
                    sQuery = sQuery.substring(0, sQuery.length() - 1);
                    LIST_SCROLL.getAnimation().setValue(0.0f);
                    selectFirstVisible();
                }
                return true;
            }

            if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
                sSearchFocused = false;
                return true;
            }
        }
        if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
            requestClose();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (sView == View.SEARCH && sSearchFocused && event.isAllowedChatCharacter()) {
            sQuery += (char) event.codepoint();
            LIST_SCROLL.getAnimation().setValue(0.0f);
            selectFirstVisible();
            return true;
        }
        return super.charTyped(event);
    }

    private void ensureCentred() {
        if (!Float.isNaN(sPanelX)) {
            return;
        }

        if (this.width <= 0 || this.height <= 0) {
            return;
        }
        sPanelX = (this.width - PANEL_WIDTH) / 2.0f;
        sPanelY = (this.height - PANEL_HEIGHT) / 2.0f;
    }

    private void switchTo(View next, ModuleCategory nextCategory) {
        if (sView == next && sCategory == nextCategory) {
            return;
        }
        sView = next;
        sCategory = nextCategory;
        sSearchFocused = false;
        LIST_SCROLL.getAnimation().setValue(0.0f);
        selectFirstVisible();
    }

    private void selectFirstVisible() {
        List<Module> list = visibleModules();
        sSelected = list.isEmpty() ? null : list.get(0);
        DETAIL_SCROLL.getAnimation().setValue(0.0f);
    }

    private String headerLabel() {
        return sView == View.FAVOURITES ? "Favourites" : sCategory.getName();
    }

    private List<Module> visibleModules() {
        switch (sView) {
            case FAVOURITES -> {
                List<Module> starred = new ArrayList<>();
                for (ModuleCategory value : ModuleCategory.VALUES) {
                    for (Module module : MODULES.inCategory(value)) {
                        if (FAVOURITES.contains(module)) {
                            starred.add(module);
                        }
                    }
                }
                return starred;
            }
            case SEARCH -> {
                if (sQuery.isEmpty()) {
                    return List.of();
                }
                String needle = sQuery.toLowerCase(java.util.Locale.ROOT);
                List<Module> prefix = new ArrayList<>();
                List<Module> contains = new ArrayList<>();
                for (ModuleCategory value : ModuleCategory.VALUES) {
                    for (Module module : MODULES.inCategory(value)) {
                        String name = module.getName().toLowerCase(java.util.Locale.ROOT);
                        if (name.startsWith(needle)) {
                            prefix.add(module);
                        } else if (name.contains(needle)) {
                            contains.add(module);
                        }
                    }
                }
                prefix.addAll(contains);
                return prefix;
            }
            default -> {
                return MODULES.inCategory(sCategory);
            }
        }
    }

    private RowContent contentFor(Module module) {
        if (module instanceof AnimationsModule) {
            return ANIMATIONS_PANEL;
        }
        return PANELS.computeIfAbsent(module, key -> {
            Property<?>[] properties = key.getProperties();

            return properties.length == 0 ? null : new SimplePropertyPanel(properties, true);
        });
    }

    private static float searchFieldY(float cardTop) {
        return cardTop + (LIST_HEADER - FIELD_HEIGHT) / 2.0f;
    }

    private static float searchFieldWidth() {
        return LIST_WIDTH - ROW_INSET * 2.0f;
    }

    private static float starStripX(float listX) {
        return listX + LIST_WIDTH - STAR_HIT - STAR_INSET;
    }

    private static boolean contains(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private static ScreenRectangle rect(float x, float y, float w, float h) {
        return new ScreenRectangle((int) Math.floor(x), (int) Math.floor(y),
                (int) Math.ceil(w) + 1, (int) Math.ceil(h) + 1);
    }

    private static int blend(int from, int to, float t) {
        float clamped = Math.max(0.0f, Math.min(1.0f, t));
        int a = (from >>> 24) & 0xFF;
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * clamped);
        int g = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * clamped);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * clamped);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static final class ModuleRepositoryView {
        private final Map<ModuleCategory, List<Module>> cache = new IdentityHashMap<>();

        List<Module> inCategory(ModuleCategory category) {
            return cache.computeIfAbsent(category, key -> {
                List<Module> list = new ArrayList<>(
                        AerialClient.getModuleRepository().getModulesInCategory(key));
                list.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                return list;
            });
        }
    }
}
