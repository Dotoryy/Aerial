package cc.aerial.client.features.impl.visual;

import cc.aerial.client.utility.HudDrag;
import cc.aerial.client.AerialClient;
import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.impl.hud.DynamicIsland;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.render.BlurConsumer;
import cc.aerial.client.render.AerialBloomFilter;
import cc.aerial.client.render.AerialBlur;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.BitmapFont;
import cc.aerial.client.render.font.AerialFont;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.Minecraft;
import cc.aerial.client.theme.ColorUtil;
import cc.aerial.client.theme.Theme;
import cc.aerial.client.theme.ThemeManager;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ArraylistModule {
    public static final ArraylistModule INSTANCE = new ArraylistModule();

    private ArraylistModule() {
    }

    private final List<Module> entriesScratch = new ArrayList<>();

    private List<Module> entries() {
        entriesScratch.clear();
        for (Module module : AerialClient.getModuleRepository().getModules()) {
            if (module.isVisible()) {
                entriesScratch.add(module);
            }
        }
        return entriesScratch;
    }

    private static final float EDGE_OFFSET = 10.0f;
    private static final float WIDTH_OFFSET = 2.0f;

    private static final float TEXT_SIZE = 8.5f;

    private static final int BACKGROUND_COLOR = 0x6E000000;
    private static final int TAG_COLOR = 0xFFCCCCCC;

    private static final int FPS_SAVER_BACKGROUND = 0x40000000;
    private static final int FPS_SAVER_TAG_COLOR = 0xFFAAAAAA;

    private static AerialFont font;

    private static BitmapFont fpsSaverFont;

    private static void ensureAssetsLoaded() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
        }
        if (fpsSaverFont == null) {
            fpsSaverFont = BitmapFont.fromResource("novoline_vanilla.png");
        }
    }

    private static final class Row {
        double x = 5000, y = 0, targetX = 5000, targetY = 0;
        float animationTime;
        String displayName = "", displayTag = "";
        boolean hasTag;
        float nameWidth, tagWidth;

        float totalWidth;
        int color = ThemeManager.getTheme().getFirstColor().getRGB();

        String srcName, srcTag;
        boolean srcLowercase, srcRemoveSpaces;
        double srcGuiScale;

        Mode srcMode;

        float srcScale;
        FpsSaverFont srcFace;
    }

    private final Map<Module, Row> rows = new HashMap<>();

    private final List<Module> active = new ArrayList<>();

    private final Comparator<Module> byWidthDescending =
            (a, b) -> Float.compare(rows.get(b).totalWidth, rows.get(a).totalWidth);
    private long lastFrame = System.currentTimeMillis();
    private float totalHeight;

    public float getTotalHeight() {
        return totalHeight;
    }

    public void render(Render2DEvent event) {
        AerialBloomFilter.begin(BlurConsumer.ARRAYLIST);
        try {
            onRender2DBody(event);
        } finally {
            AerialBloomFilter.end();
        }
    }

    private void onRender2DBody(Render2DEvent event) {
        ensureAssetsLoaded();

        long now = System.currentTimeMillis();
        double dtMs = Math.max(0, now - lastFrame);
        lastFrame = now;

        boolean showTags = InterfaceModule.INSTANCE.isArraylistSuffix();
        Mode mode = InterfaceModule.INSTANCE.getArraylistMode();
        float fpsSaverScale = snapFpsSaverScale(InterfaceModule.INSTANCE.getArraylistFpsSaverScale());
        FpsSaverFont fpsSaverFace = InterfaceModule.INSTANCE.getArraylistFpsSaverFont();
        List<Module> entries = entries();

        for (Module entry : entries) {
            Row row = rows.computeIfAbsent(entry, e -> new Row());
            boolean visible = entry.isEnabled();
            if (visible) {
                row.animationTime = (float) Math.min(row.animationTime + dtMs / 100.0, 10);
            } else {
                row.animationTime = (float) Math.max(row.animationTime - dtMs / 100.0, 0);
            }
            if (row.animationTime == 0) {
                continue;
            }

            String rawName = entry.getName();
            String rawTag = showTags && entry.getSuffix() != null ? entry.getSuffix() : "";
            boolean lower = InterfaceModule.INSTANCE.isArraylistLowercase();
            boolean stripSpaces = InterfaceModule.INSTANCE.isArraylistRemoveSpaces();
            double guiScale = Minecraft.getInstance().getWindow().getGuiScale();
            if (row.srcName == null || !rawName.equals(row.srcName) || !rawTag.equals(row.srcTag)
                    || row.srcLowercase != lower || row.srcRemoveSpaces != stripSpaces
                    || row.srcGuiScale != guiScale || row.srcMode != mode
                    || row.srcScale != fpsSaverScale || row.srcFace != fpsSaverFace) {
                String name = rawName;
                String tag = rawTag;
                row.hasTag = !tag.isEmpty();
                if (lower) {
                    name = name.toLowerCase();
                    tag = tag.toLowerCase();
                }
                if (stripSpaces) {
                    name = name.replace(" ", "");
                    tag = tag.replace(" ", "");
                }
                row.displayName = name;
                row.displayTag = tag;
                if (mode == Mode.FPS_SAVER) {
                    if (fpsSaverFace == FpsSaverFont.VANILLA) {
                        Font vanilla = Minecraft.getInstance().font;
                        row.nameWidth = vanilla.width(name) * fpsSaverScale;
                        row.tagWidth = row.hasTag
                                ? (vanilla.width(tag) + 3.0f) * fpsSaverScale : 0.0f;
                    } else {
                        row.nameWidth = fpsSaverFont.width(name, fpsSaverScale);
                        row.tagWidth = row.hasTag
                                ? fpsSaverFont.width(tag, fpsSaverScale) + 3.0f * fpsSaverScale : 0.0f;
                    }
                } else {
                    row.nameWidth = font.stringWidth(name, TEXT_SIZE);
                    row.tagWidth = row.hasTag ? font.stringWidth(tag, TEXT_SIZE) + 3.0f : 0.0f;
                }
                row.totalWidth = row.nameWidth + row.tagWidth;

                row.srcMode = mode;
                row.srcScale = fpsSaverScale;
                row.srcFace = fpsSaverFace;
                row.srcName = rawName;
                row.srcTag = rawTag;
                row.srcLowercase = lower;
                row.srcRemoveSpaces = stripSpaces;
                row.srcGuiScale = guiScale;
            }
        }

        float screenWidth = event.width();

        float glyphCell = fpsSaverFace == FpsSaverFont.VANILLA
                ? (Minecraft.getInstance().font.lineHeight - 1) * fpsSaverScale
                : fpsSaverFont.lineHeight(fpsSaverScale);
        float boxHeight = mode == Mode.FPS_SAVER
                ? glyphCell + (InterfaceModule.INSTANCE.isArraylistDropShadow() ? fpsSaverScale : 0.0f)
                : Math.round(font.height(TEXT_SIZE));
        float rowStep = boxHeight;

        active.clear();
        for (Module entry : entries) {
            Row row = rows.get(entry);
            if (row != null && row.animationTime != 0) {
                active.add(entry);
            }
        }
        active.sort(byWidthDescending);

        float widest = 0.0f;
        int shown = 0;
        for (Module entry : active) {
            Row row = rows.get(entry);
            if (entry.isEnabled() || row.animationTime >= 10) {
                widest = Math.max(widest, (float) (row.nameWidth + row.tagWidth));
                shown++;
            }
        }
        handleDragging(screenWidth, widest, shown * rowStep, boxHeight);

        float offsetX = InterfaceModule.INSTANCE.getArraylistOffsetX();
        float offsetY = InterfaceModule.INSTANCE.getArraylistOffsetY();

        double posY = 0;
        for (Module entry : active) {
            Row row = rows.get(entry);

            row.targetX = screenWidth - row.nameWidth - row.tagWidth;
            row.targetY = posY;

            if (!entry.isEnabled() && row.animationTime < 10) {
                row.targetX = screenWidth + row.nameWidth + row.tagWidth;
            } else {
                posY += rowStep;
            }

            row.targetX -= EDGE_OFFSET - offsetX;
            row.targetY += EDGE_OFFSET + offsetY;

            if (Math.abs(row.x - row.targetX) > 0.5 || Math.abs(row.y - row.targetY) > 0.5
                    || (row.animationTime != 0 && row.animationTime != 10)) {
                double factor = Math.min(1.0, 1.5E-2 * dtMs);
                row.x += (row.targetX - row.x) * factor;
                row.y += (row.targetY - row.y) * factor;
            } else {
                row.x = row.targetX;
                row.y = row.targetY;
            }
        }

        if (mode != Mode.FPS_SAVER
                && InterfaceModule.INSTANCE.getArraylistBackground() == Background.NORMAL
                && InterfaceModule.INSTANCE.isArraylistBlur() && !active.isEmpty()) {
            List<float[]> boxes = new ArrayList<>(active.size());
            for (Module entry : active) {
                Row row = rows.get(entry);
                float boxWidth = row.nameWidth + row.tagWidth + 3.0f + WIDTH_OFFSET;
                boxes.add(new float[]{(float) row.x - WIDTH_OFFSET, (float) row.y, boxWidth, boxHeight});
            }
            AerialBlur.drawBlurredRects(event.extractor(), BlurConsumer.ARRAYLIST, boxes, 0.01f, 1.0f);
        }

        int index = 0;
        for (Module entry : active) {
            Row row = rows.get(entry);
            updateColor(row);
            if (mode == Mode.FPS_SAVER) {
                drawRowFpsSaver(event, row, boxHeight, fpsSaverScale, fpsSaverFace, index == 0);
            } else {
                drawRow(event, row, boxHeight);
            }
            index++;
        }

        this.totalHeight = active.isEmpty() ? 0f : (float) posY + EDGE_OFFSET;
    }

    private void updateColor(Row row) {
        Theme theme = ThemeManager.getTheme();
        Color color = theme.getFirstColor();
        switch (InterfaceModule.INSTANCE.getArraylistColorMode()) {
            case FADE -> color = theme.getAccentColor(0, row.y);
            case BREATHE -> {
                double factor = theme.getBlendFactor(0, 0);
                color = ColorUtil.mixColors(color, theme.getSecondColor(), factor);
            }
            default -> {
            }
        }
        row.color = color.getRGB();
    }

    private void drawRow(Render2DEvent event, Row row, float boxHeight) {
        float x = (float) row.x;
        float y = (float) row.y;
        int color = row.color;

        if (InterfaceModule.INSTANCE.getArraylistBackground() == Background.NORMAL) {
            float boxWidth = row.nameWidth + row.tagWidth + 3.0f + WIDTH_OFFSET;
            RenderUtil.flatRect(event.extractor(), x - WIDTH_OFFSET, y, boxWidth, boxHeight, BACKGROUND_COLOR);
        }

        TextRenderUtil.drawString(event.extractor(), font, row.displayName, x, y, TEXT_SIZE, color);
        if (row.hasTag) {
            TextRenderUtil.drawString(event.extractor(), font, row.displayTag,
                    x + row.nameWidth + 3.0f, y, TEXT_SIZE, TAG_COLOR);
        }

        if (InterfaceModule.INSTANCE.isArraylistSidebar()) {
            drawSidebar(event, row, x + row.nameWidth + row.tagWidth + 2.25f, y, boxHeight);
        }
    }

    private void drawRowFpsSaver(Render2DEvent event, Row row, float boxHeight,
                                 float scale, FpsSaverFont face, boolean first) {
        GuiGraphicsExtractor extractor = event.extractor();
        float x = (float) row.x;
        float y = (float) row.y;
        int color = row.color | 0xFF000000;
        boolean shadow = InterfaceModule.INSTANCE.isArraylistDropShadow();

        float totalWidth = row.nameWidth + row.tagWidth - (shadow ? 0.0f : scale);
        float capY = first ? y - scale : y;
        float capHeight = first ? boxHeight + scale : boxHeight;

        if (InterfaceModule.INSTANCE.getArraylistBackground() == Background.NORMAL) {
            RenderUtil.flatRect(extractor, x - scale, capY,
                    totalWidth + scale * 2.0f, capHeight, FPS_SAVER_BACKGROUND);
        }

        if (InterfaceModule.INSTANCE.isArraylistSidebar()) {
            drawFpsSaverBar(event, row, x + totalWidth + scale, capY, capHeight, scale, shadow);
        }

        if (face == FpsSaverFont.VANILLA) {
            Font vanilla = Minecraft.getInstance().font;
            extractor.pose().pushMatrix();
            extractor.pose().translate(x, y);
            extractor.pose().scale(scale, scale);
            extractor.text(vanilla, row.displayName, 0, 0, color, shadow);
            if (row.hasTag) {
                extractor.text(vanilla, row.displayTag,
                        vanilla.width(row.displayName) + 3, 0, FPS_SAVER_TAG_COLOR, shadow);
            }
            extractor.pose().popMatrix();
            return;
        }

        if (shadow) {
            fpsSaverFont.drawWithShadow(extractor, row.displayName, x, y, scale, color);
        } else {
            fpsSaverFont.draw(extractor, row.displayName, x, y, scale, color);
        }
        if (row.hasTag) {
            float tagX = x + fpsSaverFont.width(row.displayName, scale) + 3.0f * scale;
            if (shadow) {
                fpsSaverFont.drawWithShadow(extractor, row.displayTag, tagX, y, scale, FPS_SAVER_TAG_COLOR);
            } else {
                fpsSaverFont.draw(extractor, row.displayTag, tagX, y, scale, FPS_SAVER_TAG_COLOR);
            }
        }
    }

    private static float snapFpsSaverScale(float wanted) {
        double guiScale = Math.max(1.0, Minecraft.getInstance().getWindow().getGuiScale());
        int physical = Math.max(1, (int) Math.round(guiScale * wanted));
        return (float) (physical / guiScale);
    }

    private void drawFpsSaverBar(Render2DEvent event, Row row, float x, float y,
                                 float boxHeight, float scale, boolean shadow) {
        GuiGraphicsExtractor extractor = event.extractor();
        if (InterfaceModule.INSTANCE.getArraylistColorMode() != ColorMode.FADE) {
            int color = row.color | 0xFF000000;
            RenderUtil.flatRect(extractor, x, y, scale, boxHeight, color);
            if (shadow) {
                RenderUtil.flatRect(extractor, x + scale, y, scale, boxHeight, shadowOf(color));
            }
            return;
        }
        Theme theme = ThemeManager.getTheme();
        int steps = Math.max(1, (int) boxHeight);
        for (int i = 0; i < steps; i++) {
            int color = theme.getAccentRgb(0, y + i) | 0xFF000000;
            RenderUtil.flatRect(extractor, x, y + i, scale, 1.0f, color);
            if (shadow) {
                RenderUtil.flatRect(extractor, x + scale, y + i, scale, 1.0f, shadowOf(color));
            }
        }
    }

    private static int shadowOf(int color) {
        return ((color & 0xFCFCFC) >> 2) | (color & 0xFF000000);
    }

    private void drawSidebar(Render2DEvent event, Row row, float x, float y, float boxHeight) {
        if (InterfaceModule.INSTANCE.getArraylistColorMode() != ColorMode.FADE) {
            RenderUtil.flatRect(event.extractor(), x, y, 1.4f, boxHeight, row.color);
            return;
        }
        Theme theme = ThemeManager.getTheme();
        int steps = Math.max(1, (int) boxHeight);
        for (int i = 0; i < steps; i++) {
            RenderUtil.flatRect(event.extractor(), x, y + i, 1.4f, 1.0f,
                    theme.getAccentRgb(0, row.y + i));
        }
    }

    private boolean dragging;
    private float dragOffsetX;
    private float dragOffsetY;

    private void handleDragging(float screenWidth, float widest, float totalHeight, float rowHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.gui.screen() instanceof ChatScreen)) {
            dragging = false;
            return;
        }
        if (widest <= 0.0f || totalHeight <= 0.0f) {
            return;
        }

        float offsetX = InterfaceModule.INSTANCE.getArraylistOffsetX();
        float offsetY = InterfaceModule.INSTANCE.getArraylistOffsetY();
        float boxX = screenWidth - widest - EDGE_OFFSET + offsetX;
        float boxY = EDGE_OFFSET + offsetY;

        float mouseX = (float) minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
        float mouseY = (float) minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
        boolean pressed = org.lwjgl.glfw.GLFW.glfwGetMouseButton(minecraft.getWindow().handle(),
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

        if (!pressed) {
            dragging = false;
            return;
        }
        boolean hovered = mouseX >= boxX && mouseY >= boxY
                && mouseX < boxX + widest && mouseY < boxY + totalHeight;
        if (!dragging && hovered) {
            dragging = true;
            dragOffsetX = mouseX - offsetX;
            dragOffsetY = mouseY - offsetY;
        }
        if (dragging) {
            float screenH = minecraft.getWindow().getGuiScaledHeight();
            float wantedX = screenWidth - widest - EDGE_OFFSET + (mouseX - dragOffsetX);
            float wantedY = EDGE_OFFSET + (mouseY - dragOffsetY);
            float clampedX = HudDrag.clamp(wantedX, widest, screenWidth);
            float clampedY = HudDrag.clamp(wantedY, totalHeight, screenH);
            InterfaceModule.INSTANCE.setArraylistOffset(
                    clampedX - (screenWidth - widest - EDGE_OFFSET),
                    clampedY - EDGE_OFFSET);
        }
    }

    public enum Mode {
        AERIAL("Aerial"), FPS_SAVER("Fps Saver");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum FpsSaverFont {
        VANILLA("Vanilla"),

        CLIENT("Client");

        private final String label;

        FpsSaverFont(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum Background {
        OFF("Off"), NORMAL("Normal");

        private final String label;

        Background(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum ColorMode {
        STATIC("Static"), FADE("Fade"), BREATHE("Breathe");

        private final String label;

        ColorMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }
}
