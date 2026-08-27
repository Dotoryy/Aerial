package cc.aerial.client.features.impl.visual;

import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.render.BlurConsumer;
import cc.aerial.client.render.AerialBloomFilter;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class PotionEffectsModule extends Module {
    public static final PotionEffectsModule INSTANCE = new PotionEffectsModule();

    private static final float FONT_SIZE = 10.0f;

    private static final float ICON_SIZE = 11.0f;
    private static final float ICON_GAP = 3.0f;

    private static final float ROW_GAP = 0.6f;
    private static final float LEFT_MARGIN = 4.0f;

    private static final int GREY = 0xFF9A9A9A;
    private static final int ICON_TINT = 0xFFFFFFFF;

    private final BooleanProperty showIcons = new BooleanProperty("Icons", true);
    private final BooleanProperty lowercase = new BooleanProperty("Lowercase", true);
    private final BooleanProperty showDuration = new BooleanProperty("Duration", true);

    private final BooleanProperty removeVanillaUi = new BooleanProperty("Remove Vanilla UI", true);

    private final NumberProperty xPos = new NumberProperty("X", -1.0, -10000.0, 10000.0, 0.01).hideIf(() -> true);
    private final NumberProperty yPos = new NumberProperty("Y", -1.0, -10000.0, 10000.0, 0.01).hideIf(() -> true);

    private static AerialFont font;

    private float x, y;
    private float currentWidth, currentHeight;
    private boolean dragging;
    private float dragOffsetX, dragOffsetY;

    private boolean positioned;

    private PotionEffectsModule() {
        super("Potion Effects", "Lists your active effects", ModuleCategory.VISUAL);
        addProperties(showIcons, showDuration, lowercase, removeVanillaUi, xPos, yPos);
    }

    public boolean isRemovingVanillaUi() {
        return isEnabled() && removeVanillaUi.getValue();
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        AerialBloomFilter.begin(BlurConsumer.POTION_EFFECTS);
        try {
            draw(event);
        } finally {
            AerialBloomFilter.end();
        }
    }

    private void draw(Render2DEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        boolean inChat = mc.gui.screen() instanceof ChatScreen;
        if (mc.gui.screen() != null && !inChat) {
            return;
        }
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
        }

        List<Row> rows = collectRows(mc);

        layout(rows, event);
        if (inChat) {
            handleDragging(mc);
        }
        if (rows.isEmpty()) {
            if (dragging) {
                drawDragOutline(event.extractor());
            }
            return;
        }

        GuiGraphicsExtractor extractor = event.extractor();
        float rowHeight = Math.max(FONT_SIZE, showIcons.getValue() ? ICON_SIZE : FONT_SIZE);
        float cursorY = y;
        for (Row row : rows) {
            float textX = x;
            if (showIcons.getValue()) {
                extractor.blitSprite(RenderPipelines.GUI_TEXTURED, row.sprite,
                        Math.round(textX), Math.round(cursorY + (rowHeight - ICON_SIZE) * 0.5f),
                        Math.round(ICON_SIZE), Math.round(ICON_SIZE), ICON_TINT);
                textX += ICON_SIZE + ICON_GAP;
            }
            float textY = cursorY + (rowHeight - FONT_SIZE) * 0.5f;
            float used = TextRenderUtil.drawString(extractor, font, row.name, textX, textY, FONT_SIZE, row.color);
            if (!row.duration.isEmpty()) {
                TextRenderUtil.drawString(extractor, font, row.duration,
                        textX + used + 3.0f, textY, FONT_SIZE, GREY);
            }
            cursorY += rowHeight + ROW_GAP;
        }

        if (dragging) {
            drawDragOutline(extractor);
        }
    }

    private record Row(Identifier sprite, String name, String duration, int color, float width) {
    }

    private List<Row> collectRows(Minecraft mc) {
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<Holder<MobEffect>, MobEffectInstance> entry
                : mc.player.getActiveEffectsMap().entrySet()) {
            MobEffectInstance instance = entry.getValue();
            String name = convertCase(instance.getEffect().value().getDisplayName().getString())
                    + (instance.getAmplifier() > 0 ? " " + (instance.getAmplifier() + 1) : "");
            String duration = showDuration.getValue()
                    ? (instance.isInfiniteDuration() ? "**:**" : formatTicks(instance.getDuration()))
                    : "";

            float width = font.stringWidth(name, FONT_SIZE)
                    + (duration.isEmpty() ? 0.0f : 3.0f + font.stringWidth(duration, FONT_SIZE));
            rows.add(new Row(Hud.getMobEffectSprite(entry.getKey()), name, duration,
                    0xFF000000 | entry.getKey().value().getColor(), width));
        }

        rows.sort(Comparator.comparingDouble(Row::width).reversed());
        return rows;
    }

    private String convertCase(String text) {
        return lowercase.getValue() ? text.toLowerCase(Locale.ROOT) : text;
    }

    private static String formatTicks(int ticks) {
        int seconds = Mth.floor(ticks / 20.0f);
        int minutes = seconds / 60;
        seconds %= 60;
        int hours = minutes / 60;
        minutes %= 60;
        return hours > 0
                ? String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
                : String.format(Locale.ROOT, "%d:%02d", minutes, seconds);
    }

    private void layout(List<Row> rows, Render2DEvent event) {
        float rowHeight = Math.max(FONT_SIZE, showIcons.getValue() ? ICON_SIZE : FONT_SIZE);
        float widest = 0.0f;
        for (Row row : rows) {
            widest = Math.max(widest, row.width());
        }
        currentWidth = Math.max(60.0f, (showIcons.getValue() ? ICON_SIZE + ICON_GAP : 0.0f) + widest);
        currentHeight = Math.max(rowHeight, rows.size() * rowHeight + Math.max(0, rows.size() - 1) * ROW_GAP);
        resolvePosition(event);
    }

    private void resolvePosition(Render2DEvent event) {
        if (dragging) {
            return;
        }
        boolean saved = xPos.getValue() >= 0.0 && yPos.getValue() >= 0.0;
        if (saved) {
            x = xPos.getValue().floatValue();
            y = yPos.getValue().floatValue();
            positioned = true;
            return;
        }
        x = LEFT_MARGIN;

        y = (event.height() - currentHeight) * 0.5f;
        positioned = false;
    }

    private void handleDragging(Minecraft mc) {
        float mouseX = (float) mc.mouseHandler.getScaledXPos(mc.getWindow());
        float mouseY = (float) mc.mouseHandler.getScaledYPos(mc.getWindow());

        boolean pressed = org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().handle(),
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

        if (!pressed) {
            dragging = false;
            return;
        }
        if (!dragging && isInside(mouseX, mouseY)) {
            dragging = true;
            dragOffsetX = mouseX - x;
            dragOffsetY = mouseY - y;
        }
        if (dragging) {
            x = mouseX - dragOffsetX;
            y = mouseY - dragOffsetY;
            xPos.setValue((double) x);
            yPos.setValue((double) y);
            positioned = true;
        }
    }

    private boolean isInside(float mouseX, float mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + currentWidth && mouseY < y + currentHeight;
    }

    private void drawDragOutline(GuiGraphicsExtractor extractor) {
        int color = InterfaceModule.INSTANCE.getTheme().getAccentColor(0, 0).getRGB() | 0xFF000000;
        RenderUtil.flatRect(extractor, x - 0.5f, y - 0.5f, currentWidth + 1.0f, 1.0f, color);
        RenderUtil.flatRect(extractor, x - 0.5f, y + currentHeight - 0.5f, currentWidth + 1.0f, 1.0f, color);
        RenderUtil.flatRect(extractor, x - 0.5f, y - 0.5f, 1.0f, currentHeight + 1.0f, color);
        RenderUtil.flatRect(extractor, x + currentWidth - 0.5f, y - 0.5f, 1.0f, currentHeight + 1.0f, color);
    }

    public boolean isPositioned() {
        return positioned;
    }
}
