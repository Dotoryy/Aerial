package cc.aerial.client.features.impl.hud;

import cc.aerial.client.AerialClient;
import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.impl.visual.InterfaceModule;
import cc.aerial.client.render.BlurConsumer;
import cc.aerial.client.render.AerialBloomFilter;
import cc.aerial.client.render.AerialBlur;
import cc.aerial.client.render.AerialImage;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.GlyphQuad;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import org.apache.commons.lang3.StringUtils;

import cc.aerial.client.screen.animation.Animation;
import cc.aerial.client.screen.animation.Easing;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class RiseCapsuleModule implements CapsuleSection {
    public static final RiseCapsuleModule INSTANCE = new RiseCapsuleModule();

    private RiseCapsuleModule() {
    }

    private static final List<CapsuleSection> SECTIONS = new ArrayList<>(List.of(INSTANCE));
    private static boolean sortingDirty;

    public static void addSection(CapsuleSection section) {
        if (section != INSTANCE && !SECTIONS.contains(section)) {
            SECTIONS.add(section);
            sortingDirty = true;
        }
    }

    public static void removeSection(CapsuleSection section) {
        if (section != INSTANCE && SECTIONS.remove(section)) {
            sortingDirty = true;
        }
    }

    private static CapsuleSection activeSection() {
        if (sortingDirty) {
            Collections.sort(SECTIONS);
            sortingDirty = false;
        }
        return SECTIONS.getFirst();
    }

    private final Animation widthAnimation = new Animation(Easing.EASE_OUT_EXPO, 250);
    private boolean widthInitialised;

    private static final int BACKGROUND_COLOR = 0x80090909;

    private static final int MUTED_COLOR = 0xFF808080;
    private static final int WHITE = 0xFFFFFFFF;

    private static final float TOP_MARGIN = 6.0f;

    private static final float LEFT_MARGIN = 6.0f;
    private static final float HEIGHT = 18.0f;

    private static final float CORNER_RADIUS = 4.5f;
    private static final float PADDING = 5.0f;

    private static final float RIGHT_EXTRA = 3.5f;

    private static final float ICON_SIZE = 12.25f;

    private static final float DIVIDER_WIDTH = 0.5f;

    private static final float DIVIDER_HEIGHT = 11.0f;

    private static final int DIVIDER_COLOR = 0x40808080;

    private static final float DIVIDER_GAP = 5.0f;

    private static final float ADDRESS_GAP = 6.5f;

    private static final float FIELD_GAP = 3.5f;

    private static final float UNIT_GAP = 1.5f;

    private static final float VALUE_SIZE = 8.0f;

    private static final float STAT_SIZE = 6.5f;
    private static final float UNIT_SIZE = 5.5f;

    private static final String DOMAIN_TEXT = "aerialclient.cc";
    private static final String SINGLEPLAYER_TEXT = "singleplayer";

    private static final String[] MASKED_HOSTS = {"overlag", "liquidproxy"};
    private static final String MASK = "***";

    private static final int PING_DIGITS = 3;
    private static final int FPS_DIGITS = 4;
    private static final int MODS_DIGITS = 3;

    private static final DateTimeFormatter CLOCK_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static AerialFont font;
    private static AerialImage logo;

    private static float digitPitch = -1.0f;

    private static float valueInkCenter = -1.0f;
    private static float valueInkBottom;
    private static float unitInkBottom;
    private static float statInkBottom;

    private String frameServer = "", framePing = "0", frameFps = "0", frameMods = "0", frameClock = "";

    private void sampleFrame() {
        frameServer = serverText();
        framePing = pingValue();
        frameFps = String.valueOf(Minecraft.getInstance().getFps());
        frameMods = String.valueOf(modCount());
        frameClock = clockText();
    }

    private static void ensureAssetsLoaded() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
            logo = AerialImage.fromResource("aerial_logo.png");
        }
    }

    public void render(Render2DEvent event) {
        AerialBloomFilter.begin(BlurConsumer.RISE_CAPSULE);
        try {
            onRender2DBody(event);
        } finally {
            AerialBloomFilter.end();
        }
    }

    private void onRender2DBody(Render2DEvent event) {
        ensureAssetsLoaded();
        sampleFrame();

        GuiGraphicsExtractor extractor = event.extractor();
        CapsuleSection section = activeSection();
        float targetWidth = measureWidth(section);
        if (!widthInitialised) {
            widthAnimation.setValue(targetWidth);
            widthInitialised = true;
        } else {
            widthAnimation.run(targetWidth);
        }
        float width = widthAnimation.getValue();
        float sectionProgress = Math.min(1.0f, widthAnimation.getProgress());
        float x = LEFT_MARGIN;
        float y = TOP_MARGIN;

        AerialBlur.drawGlass(extractor, BlurConsumer.RISE_CAPSULE, x, y, width, HEIGHT,
                CORNER_RADIUS, BACKGROUND_COLOR, 1.0f, null);

        float centerY = y + HEIGHT * 0.5f;
        float cursorX = x + PADDING;

        Theme theme = InterfaceModule.INSTANCE.getTheme();
        int accentLeft = theme.getAccentColor(0, 50).getRGB();
        int accentRight = theme.getAccentColor(0, 0).getRGB();

        RenderUtil.image(extractor, logo,
                cursorX, centerY - ICON_SIZE * 0.5f, ICON_SIZE, ICON_SIZE, accentLeft, accentRight);
        cursorX += ICON_SIZE + DIVIDER_GAP;

        RenderUtil.flatRect(extractor, cursorX, centerY - DIVIDER_HEIGHT * 0.5f,
                DIVIDER_WIDTH, DIVIDER_HEIGHT, DIVIDER_COLOR);
        cursorX += DIVIDER_WIDTH + ADDRESS_GAP;

        cursorX += TextRenderUtil.drawGradientString(extractor, font, frameServer,
                cursorX, valueTop(centerY), VALUE_SIZE, accentLeft, accentRight);
        cursorX += FIELD_GAP;

        section.renderSection(extractor, cursorX, centerY, sectionProgress);
    }

    @Override
    public void renderSection(GuiGraphicsExtractor extractor, float x, float centerY, float progress) {
        float cursorX = x;
        cursorX = drawStat(extractor, cursorX, centerY, framePing, numberSlot(PING_DIGITS), "ms");
        cursorX = drawStat(extractor, cursorX, centerY, frameFps, numberSlot(FPS_DIGITS), "fps");
        cursorX = drawStat(extractor, cursorX, centerY, frameMods, numberSlot(MODS_DIGITS), "mods");
        cursorX = drawClock(extractor, cursorX, centerY);

        TextRenderUtil.drawString(extractor, font, DOMAIN_TEXT,
                cursorX + zeroSlackPad(), unitTop(rowBaseline(centerY)), UNIT_SIZE, MUTED_COLOR);
    }

    @Override
    public float getSectionWidth() {
        float width = measureStat(framePing, numberSlot(PING_DIGITS), "ms");
        width += measureStat(frameFps, numberSlot(FPS_DIGITS), "fps");
        width += measureStat(frameMods, numberSlot(MODS_DIGITS), "mods");
        width += Math.max(clockSlot(), font.stringWidth(frameClock, STAT_SIZE)) + FIELD_GAP;
        return width + zeroSlackPad() + font.stringWidth(DOMAIN_TEXT, UNIT_SIZE);
    }

    private static void measureInk() {
        if (valueInkCenter >= 0.0f) {
            return;
        }
        GlyphQuad[] value = font.layout("0", 0.0f, 0.0f, VALUE_SIZE);
        GlyphQuad[] unit = font.layout("0", 0.0f, 0.0f, UNIT_SIZE);
        GlyphQuad[] stat = font.layout("0", 0.0f, 0.0f, STAT_SIZE);
        valueInkCenter = value.length == 0 ? VALUE_SIZE * 0.5f : (value[0].y0 + value[0].y1) * 0.5f;
        valueInkBottom = value.length == 0 ? VALUE_SIZE : value[0].y1;
        unitInkBottom = unit.length == 0 ? UNIT_SIZE : unit[0].y1;
        statInkBottom = stat.length == 0 ? STAT_SIZE : stat[0].y1;
    }

    private static float valueTop(float centerY) {
        measureInk();
        return centerY - valueInkCenter;
    }

    private static float rowBaseline(float centerY) {
        measureInk();
        return centerY - valueInkCenter + valueInkBottom;
    }

    private static float statTop(float baseline) {
        measureInk();
        return baseline - statInkBottom;
    }

    private static float unitTop(float baseline) {
        measureInk();
        return baseline - unitInkBottom;
    }

    private static float digitPitch() {
        if (digitPitch < 0.0f) {
            float widest = 0.0f;
            for (char digit = '0'; digit <= '9'; digit++) {
                widest = Math.max(widest, font.stringWidth(String.valueOf(digit), STAT_SIZE));
            }
            digitPitch = widest;
        }
        return digitPitch;
    }

    private static float zeroSlackPad() {
        return digitPitch();
    }

    private static float numberSlot(int digits) {
        return digits * digitPitch();
    }

    private static float clockSlot() {
        return 6.0f * digitPitch() + 2.0f * font.stringWidth(":", STAT_SIZE);
    }

    private float drawStat(GuiGraphicsExtractor extractor, float cursorX, float centerY,
                           String value, float slot, String unit) {
        float baseline = rowBaseline(centerY);
        float natural = font.stringWidth(value, STAT_SIZE);
        float used = Math.max(slot, natural);

        TextRenderUtil.drawString(extractor, font, value,
                cursorX + used - natural, statTop(baseline), STAT_SIZE, WHITE);
        cursorX += used + UNIT_GAP;
        cursorX += TextRenderUtil.drawString(extractor, font, unit,
                cursorX, unitTop(baseline), UNIT_SIZE, MUTED_COLOR);
        return cursorX + FIELD_GAP;
    }

    private float drawClock(GuiGraphicsExtractor extractor, float cursorX, float centerY) {
        String text = frameClock;
        float natural = font.stringWidth(text, STAT_SIZE);
        float used = Math.max(clockSlot(), natural);

        TextRenderUtil.drawString(extractor, font, text,
                cursorX + used - natural, statTop(rowBaseline(centerY)), STAT_SIZE, WHITE);
        return cursorX + used + FIELD_GAP;
    }

    private float measureWidth(CapsuleSection section) {
        float width = PADDING + ICON_SIZE + DIVIDER_GAP + DIVIDER_WIDTH + ADDRESS_GAP;
        width += font.stringWidth(frameServer, VALUE_SIZE) + FIELD_GAP;
        return width + section.getSectionWidth() + PADDING + RIGHT_EXTRA;
    }

    static AerialFont font() {
        ensureAssetsLoaded();
        return font;
    }

    static float statSize() {
        return STAT_SIZE;
    }

    static float unitSize() {
        return UNIT_SIZE;
    }

    static float fieldGap() {
        return FIELD_GAP;
    }

    static float barHeight() {
        return HEIGHT;
    }

    static float sectionValueTop(float centerY) {
        return statTop(rowBaseline(centerY));
    }

    static float sectionUnitTop(float centerY) {
        return unitTop(rowBaseline(centerY));
    }

    private float measureStat(String value, float slot, String unit) {
        return Math.max(slot, font.stringWidth(value, STAT_SIZE))
                + UNIT_GAP + font.stringWidth(unit, UNIT_SIZE) + FIELD_GAP;
    }

    private static String serverText() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        return server == null ? SINGLEPLAYER_TEXT : maskProxy(server.ip);
    }

    private static String maskProxy(String ip) {
        for (String host : MASKED_HOSTS) {
            int match = StringUtils.indexOfIgnoreCase(ip, host);
            if (match < 0) {
                continue;
            }
            int cut = ip.lastIndexOf('.', match);
            if (cut <= 0) {
                return ip;
            }
            return MASK + ip.substring(cut);
        }
        return ip;
    }

    private static String pingValue() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (mc.getCurrentServer() == null || connection == null || mc.player == null) {
            return "0";
        }
        PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
        return String.valueOf(info == null ? 0 : info.getLatency());
    }

    private static int modCount() {
        int count = 0;
        for (Module module : AerialClient.getModuleRepository().getModules()) {
            if (module.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    private static String clockText() {
        return LocalTime.now().format(CLOCK_FORMAT);
    }
}
