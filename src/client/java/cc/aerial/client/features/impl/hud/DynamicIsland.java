package cc.aerial.client.features.impl.hud;

import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.features.impl.visual.InterfaceModule;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.render.BlurConsumer;
import cc.aerial.client.render.AerialBloomFilter;
import cc.aerial.client.render.AerialBlur;
import cc.aerial.client.render.AerialImage;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.screen.animation.Animation;
import cc.aerial.client.screen.animation.Easing;
import cc.aerial.client.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.ServerData;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class DynamicIsland implements IslandTrigger {
    public static final DynamicIsland INSTANCE = new DynamicIsland();

    private DynamicIsland() {
    }

    private static final List<IslandTrigger> TRIGGERS = new ArrayList<>(List.of(INSTANCE));
    private static boolean sortingDirty;

    public static void addTrigger(IslandTrigger trigger) {
        if (trigger != INSTANCE && !TRIGGERS.contains(trigger)) {
            TRIGGERS.add(trigger);
            sortingDirty = true;
        }
    }

    public static void removeTrigger(IslandTrigger trigger) {
        if (trigger != INSTANCE && TRIGGERS.remove(trigger)) {
            sortingDirty = true;
        }
    }

    private static IslandTrigger decidingTrigger() {
        if (sortingDirty) {
            Collections.sort(TRIGGERS);
            sortingDirty = false;
        }
        return TRIGGERS.getFirst();
    }

    private final Animation xAnimation = new Animation(Easing.EASE_OUT_EXPO, 250);
    private final Animation yAnimation = new Animation(Easing.EASE_OUT_EXPO, 250);
    private final Animation widthAnimation = new Animation(Easing.EASE_OUT_EXPO, 250);
    private final Animation heightAnimation = new Animation(Easing.EASE_OUT_EXPO, 250);
    private boolean positioned;

    private static final int CAPSULE_COLOR = 0x80090909;

    private static final int MUTED_COLOR = 0xFF808080;
    private static final int WHITE = 0xFFFFFFFF;

    private static final float PADDING = 6.0f;
    private static final float ICON_SIZE = 18.0f;
    private static final float HEIGHT = 26.0f;
    private static final float GAP = 3.0f;
    private static final float DIVIDER_HEIGHT = 9.0f;
    private static final float DIVIDER_WIDTH = 0.75f;
    private static final float TOP_MARGIN = 6.0f;

    private static final float ICON_X_OFFSET = 0.5f;
    private static final float ICON_Y_OFFSET = 0.0f;

    private static final float CONTENT_Y_OFFSET = 1.0f;

    private static final float TITLE_SIZE = 12.0f;
    private static final float LABEL_SIZE = 7.3f;
    private static final float STAT_SIZE = 6.0f;

    private static final float ROW_GAP = -1.0f;

    private static final float TITLE_Y_OFFSET = -1.5f;

    private static final float LABEL_Y_OFFSET = -1.5f;

    private static final String TITLE_TEXT = "puls";
    private static final String CHANNEL_TEXT = "beta";
    private static final String VERSION_TEXT = "mc26.2-beta.1";

    private static final String SINGLEPLAYER_TEXT = "singleplayer";

    private static final int MAX_SERVER_TEXT = 20;

    private static AerialFont boldFont;
    private static AerialFont mediumFont;

    private static AerialImage logo;

    private static void ensureAssetsLoaded() {
        if (boldFont == null) {
            boldFont = AerialFont.createFromResource("OpalProductSansBold.ttf");
            mediumFont = AerialFont.createFromResource("OpalProductSansMedium.ttf");
            logo = AerialImage.fromResource("aerial_logo.png");
        }
    }

    public void render(Render2DEvent event) {
        AerialBloomFilter.begin(BlurConsumer.DYNAMIC_ISLAND);
        try {
            onRender2DBody(event);
        } finally {
            AerialBloomFilter.end();
        }
    }

    private void onRender2DBody(Render2DEvent event) {
        ensureAssetsLoaded();

        IslandTrigger trigger = decidingTrigger();
        float width = trigger.getIslandWidth();
        float height = trigger.getIslandHeight();
        float x = (event.width() - width) * 0.5f;
        float y = TOP_MARGIN;

        if (!positioned) {
            xAnimation.setValue(x);
            yAnimation.setValue(y);
            widthAnimation.setValue(width);
            heightAnimation.setValue(height);
            positioned = true;
        } else {
            xAnimation.run(x);
            yAnimation.run(y);
            widthAnimation.run(width);
            heightAnimation.run(height);
        }

        float animatedX = xAnimation.getValue();
        float animatedY = yAnimation.getValue();
        float animatedWidth = widthAnimation.getValue();
        float animatedHeight = heightAnimation.getValue();
        float progress = Math.min(1.0f, heightAnimation.getProgress());

        AerialBlur.drawGlass(event.extractor(), BlurConsumer.DYNAMIC_ISLAND, animatedX, animatedY,
                animatedWidth, animatedHeight, animatedHeight * 0.5f, CAPSULE_COLOR, 1.0f, null);
        trigger.renderIsland(event.extractor(), animatedX, animatedY, animatedWidth, animatedHeight, progress);
    }

    @Override
    public float getIslandWidth() {
        ensureAssetsLoaded();
        float titleWidth = boldFont.stringWidth(TITLE_TEXT, TITLE_SIZE);
        float channelColWidth = Math.max(
                boldFont.stringWidth(CHANNEL_TEXT, LABEL_SIZE),
                mediumFont.stringWidth(VERSION_TEXT, STAT_SIZE));
        float serverColWidth = Math.max(
                boldFont.stringWidth(serverText(), LABEL_SIZE),
                mediumFont.stringWidth(pingText(), STAT_SIZE));
        return PADDING + ICON_SIZE + GAP + titleWidth + GAP + DIVIDER_WIDTH + GAP
                + channelColWidth + GAP + DIVIDER_WIDTH + GAP + serverColWidth + PADDING;
    }

    @Override
    public float getIslandHeight() {
        return HEIGHT;
    }

    @Override
    public void renderIsland(GuiGraphicsExtractor extractor, float x, float y, float width, float height, float progress) {
        float centerY = y + height * 0.5f;
        float contentY = centerY + CONTENT_Y_OFFSET;

        float cursorX = x + PADDING;

        Theme theme = InterfaceModule.INSTANCE.getTheme();

        RenderUtil.image(extractor, logo,
                cursorX + ICON_X_OFFSET, centerY - ICON_SIZE * 0.5f + ICON_Y_OFFSET,
                ICON_SIZE, ICON_SIZE,
                theme.getAccentColor(0, 50).getRGB(), theme.getAccentColor(0, 0).getRGB());
        cursorX += ICON_SIZE + GAP;

        float titleWidth = boldFont.stringWidth(TITLE_TEXT, TITLE_SIZE);
        TextRenderUtil.drawGradientString(extractor, boldFont, TITLE_TEXT,
                cursorX, contentY - TITLE_SIZE * 0.5f + TITLE_Y_OFFSET, TITLE_SIZE,
                theme.getAccentColor(0, 50).getRGB(), theme.getAccentColor(0, 0).getRGB());
        cursorX += titleWidth + GAP;

        cursorX = drawDivider(extractor, cursorX, contentY);

        cursorX = drawColumn(extractor, cursorX, contentY, CHANNEL_TEXT, VERSION_TEXT);
        cursorX += GAP;

        cursorX = drawDivider(extractor, cursorX, contentY);

        drawColumn(extractor, cursorX, contentY, serverText(), pingText());
    }

    private static final String[][] SERVER_ALIASES = {
            {"overlag", "Overlag"},
            {"liquidproxy", "liquidproxy"},
    };

    private static String serverText() {
        ServerData server = Minecraft.getInstance().getCurrentServer();
        if (server == null) {
            return SINGLEPLAYER_TEXT;
        }
        String ip = server.ip;

        for (String[] alias : SERVER_ALIASES) {
            if (StringUtils.containsIgnoreCase(ip, alias[0])) {
                return alias[1];
            }
        }
        return ip.length() > MAX_SERVER_TEXT ? ip.substring(0, MAX_SERVER_TEXT - 3) + "..." : ip;
    }

    private static String pingText() {
        Minecraft mc = Minecraft.getInstance();
        ClientPacketListener connection = mc.getConnection();
        if (mc.getCurrentServer() == null || connection == null || mc.player == null) {
            return "0 ms";
        }
        PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
        return (info == null ? 0 : info.getLatency()) + " ms";
    }

    private float drawDivider(GuiGraphicsExtractor extractor, float cursorX, float centerY) {
        RenderUtil.flatRect(extractor,
                cursorX, centerY - DIVIDER_HEIGHT * 0.5f, DIVIDER_WIDTH, DIVIDER_HEIGHT, MUTED_COLOR);
        return cursorX + DIVIDER_WIDTH + GAP;
    }

    private float drawColumn(GuiGraphicsExtractor extractor, float cursorX, float centerY, String label, String stat) {
        float stackHeight = LABEL_SIZE + ROW_GAP + STAT_SIZE;
        float stackTop = centerY - stackHeight * 0.5f;

        TextRenderUtil.drawString(extractor, boldFont, label,
                cursorX, stackTop + LABEL_Y_OFFSET, LABEL_SIZE, WHITE);
        TextRenderUtil.drawString(extractor, mediumFont, stat,
                cursorX, stackTop + LABEL_SIZE + ROW_GAP, STAT_SIZE, MUTED_COLOR);

        return cursorX + Math.max(boldFont.stringWidth(label, LABEL_SIZE), mediumFont.stringWidth(stat, STAT_SIZE));
    }
}
