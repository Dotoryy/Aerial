package cc.aerial.client.screen.server;

import cc.aerial.client.render.AerialImage;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.screen.title.TitleBackground;
import cc.aerial.client.theme.Theme;
import cc.aerial.client.theme.ThemeManager;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ConnectScreen;
import java.util.List;
import java.util.ArrayList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.EventLoopGroupHolder;
import org.jetbrains.annotations.Nullable;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

public final class AerialServerScreen extends Screen {
    private static final float CARD_WIDTH = 380.0f;
    private static final float CARD_RADIUS = 8.0f;
    private static final float PADDING = 14.0f;
    private static final float ROW_HEIGHT = 36.0f;
    private static final float ROW_GAP = 4.0f;
    private static final float ROW_RADIUS = 5.0f;
    private static final int VISIBLE_ROWS = 6;
    private static final float TITLE_SIZE = 15.0f;
    private static final float NAME_SIZE = 10.0f;
    private static final float META_SIZE = 7.5f;
    private static final float ACTION_SIZE = 8.5f;
    private static final float ICON_SIZE = 24.0f;

    private static final int CARD_COLOR = 0xE60D0D14;
    private static final int ROW_COLOR = 0x33141420;
    private static final int ROW_HOVER = 0x59202030;
    private static final int NAME_COLOR = 0xFFEDEDF2;
    private static final int META_COLOR = 0xFF7C7E8A;
    private static final int ACTION_COLOR = 0xFFB9BAC4;
    private static final int DANGER_COLOR = 0xFFE05A5A;
    private static final int ICON_PLACEHOLDER = 0x22FFFFFF;

    private static final long[] PING_STEPS = {80L, 150L, 300L};
    private static final int[] PING_COLORS = {0xFF6EE787, 0xFFF2D06B, 0xFFFF9F45, 0xFFFF5050};

    private final @Nullable Screen previousScreen;
    private final ServerStatusPinger pinger = new ServerStatusPinger();

    private static final float SCROLL_RATE = 16.0f;

    private final Map<String, AerialImage> icons = new HashMap<>();

    private final java.util.ArrayDeque<ServerData> pending = new java.util.ArrayDeque<>();
    private volatile int refreshTotal;
    private volatile boolean running = true;
    private Thread pingThread;

    private ServerList servers;
    private AerialFont font;
    private AerialFont boldFont;
    private int selected = -1;
    private float scrollTarget;
    private float scroll;
    private long lastScrollFrame;

    public AerialServerScreen(@Nullable Screen previousScreen) {
        super(Component.literal("Multiplayer"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        if (servers == null) {
            servers = new ServerList(minecraft);
            servers.load();
            pingAll();
        }
    }

    private void ensureFonts() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
            boldFont = AerialFont.createFromResource("OpalProductSansBold.ttf");
        }
    }

    private void pingAll() {
        icons.clear();
        synchronized (pending) {
            pending.clear();
            for (int i = 0; i < servers.size(); i++) {
                ServerData data = servers.get(i);

                data.ping = 0L;
                pending.add(data);
            }
            refreshTotal = pending.size();
        }
        startPingThread();
    }

    private void startPingThread() {
        if (pingThread != null && pingThread.isAlive()) {
            return;
        }
        boolean nativeTransport = minecraft.options.useNativeTransport();
        pingThread = new Thread(() -> {
            while (running) {
                ServerData next;
                synchronized (pending) {
                    next = pending.poll();
                }
                if (next != null) {
                    try {
                        pinger.pingServer(next, () -> {
                        }, () -> {
                        }, EventLoopGroupHolder.remote(nativeTransport));
                    } catch (Exception exception) {
                        next.ping = -1L;
                    }
                }
                pinger.tick();
                try {
                    Thread.sleep(next == null ? 50L : 5L);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }

            pinger.removeAll();
        }, "Aerial Server Pinger");
        pingThread.setDaemon(true);
        pingThread.start();
    }

    private int pendingCount() {
        synchronized (pending) {
            return pending.size();
        }
    }

    @Override
    public void removed() {
        running = false;
        if (pingThread != null) {
            pingThread.interrupt();
        }
    }

    private void drawRefreshing(GuiGraphicsExtractor extractor, int accent) {
        int remaining = pendingCount();
        if (remaining <= 0 || refreshTotal <= 0) {
            return;
        }

        float padding = 12.0f;
        float labelGap = 5.0f;
        float barGap = 7.0f;
        float barHeight = 2.5f;

        float panelWidth = 130.0f;
        float panelHeight = padding * 2 + NAME_SIZE + labelGap + META_SIZE + barGap + barHeight;
        float left = (width - panelWidth) * 0.5f;
        float top = (height - panelHeight) * 0.5f;

        RenderUtil.roundedRect(extractor, left, top, panelWidth, panelHeight, 6.0f, 0xF2101018);

        float cursorY = top + padding;
        String label = "Refreshing";
        TextRenderUtil.drawString(extractor, font, label,
                left + (panelWidth - font.stringWidth(label, NAME_SIZE)) * 0.5f,
                cursorY, NAME_SIZE, NAME_COLOR);
        cursorY += NAME_SIZE + labelGap;

        int done = refreshTotal - remaining;
        String progress = done + " / " + refreshTotal;
        TextRenderUtil.drawString(extractor, font, progress,
                left + (panelWidth - font.stringWidth(progress, META_SIZE)) * 0.5f,
                cursorY, META_SIZE, META_COLOR);
        cursorY += META_SIZE + barGap;

        float barLeft = left + 14.0f;
        float barWidth = panelWidth - 28.0f;
        RenderUtil.roundedRect(extractor, barLeft, cursorY, barWidth, barHeight, barHeight * 0.5f,
                0x33FFFFFF);
        RenderUtil.roundedRect(extractor, barLeft, cursorY,
                barWidth * (done / (float) refreshTotal), barHeight, barHeight * 0.5f, accent);
    }

    private float cardHeight() {
        return PADDING * 2 + TITLE_SIZE + 12.0f
                + VISIBLE_ROWS * ROW_HEIGHT + (VISIBLE_ROWS - 1) * ROW_GAP
                + 12.0f + ACTION_SIZE;
    }

    private float cardLeft() {
        return (width - CARD_WIDTH) * 0.5f;
    }

    private float cardTop() {
        return (height - cardHeight()) * 0.5f;
    }

    private float listTop() {
        return cardTop() + PADDING + TITLE_SIZE + 12.0f;
    }

    private float listHeight() {
        return VISIBLE_ROWS * ROW_HEIGHT + (VISIBLE_ROWS - 1) * ROW_GAP;
    }

    private void advanceScroll() {
        long now = System.nanoTime();
        float delta = lastScrollFrame == 0L ? 0.0f
                : Math.min(0.1f, (now - lastScrollFrame) / 1.0E9f);
        lastScrollFrame = now;

        scrollTarget = Math.max(0.0f, Math.min(maxScroll(), scrollTarget));
        scroll += (scrollTarget - scroll) * (1.0f - (float) Math.exp(-SCROLL_RATE * delta));

        if (Math.abs(scrollTarget - scroll) < 0.05f) {
            scroll = scrollTarget;
        }
    }

    private float maxScroll() {
        int count = servers == null ? 0 : servers.size();
        return Math.max(0.0f, count * ROW_HEIGHT + Math.max(0, count - 1) * ROW_GAP - listHeight());
    }

    private float actionsY() {
        return listTop() + listHeight() + 12.0f;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        ensureFonts();
        TitleBackground.draw(extractor, width, height);

        Theme theme = ThemeManager.getTheme();
        int accent = theme.getAccentColor(0, 0).getRGB() | 0xFF000000;
        int accentLeft = theme.getAccentColor(0, 50).getRGB() | 0xFF000000;

        float left = cardLeft();
        RenderUtil.roundedRect(extractor, left, cardTop(), CARD_WIDTH, cardHeight(), CARD_RADIUS, CARD_COLOR);
        TextRenderUtil.drawGradientString(extractor, boldFont, "multiplayer",
                left + PADDING, cardTop() + PADDING, TITLE_SIZE, accentLeft, accent);

        String count = servers.size() + (servers.size() == 1 ? " server" : " servers");
        TextRenderUtil.drawString(extractor, font, count,
                left + CARD_WIDTH - PADDING - font.stringWidth(count, META_SIZE),
                cardTop() + PADDING + TITLE_SIZE - META_SIZE, META_SIZE, META_COLOR);

        drawList(extractor, mouseX, mouseY, accent);
        drawActions(extractor, mouseX, mouseY);
        drawRefreshing(extractor, accent);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void drawList(GuiGraphicsExtractor extractor, int mouseX, int mouseY, int accent) {
        advanceScroll();
        float left = cardLeft() + PADDING;
        float rowWidth = CARD_WIDTH - PADDING * 2;
        float top = listTop();

        ScreenRectangle clip = new ScreenRectangle(Math.round(left), Math.round(top),
                Math.round(rowWidth), Math.round(listHeight()));

        if (servers.size() == 0) {
            String empty = "No servers yet";
            TextRenderUtil.drawString(extractor, font, empty,
                    left + (rowWidth - font.stringWidth(empty, NAME_SIZE)) * 0.5f,
                    top + listHeight() * 0.5f - NAME_SIZE * 0.5f, NAME_SIZE, META_COLOR);
            return;
        }

        for (int i = 0; i < servers.size(); i++) {
            float rowY = top + i * (ROW_HEIGHT + ROW_GAP) - scroll;
            if (rowY + ROW_HEIGHT < top || rowY > top + listHeight()) {
                continue;
            }
            drawRow(extractor, servers.get(i), i, left, rowY, rowWidth, mouseX, mouseY, top, accent, clip);
        }
    }

    private void drawRow(GuiGraphicsExtractor extractor, ServerData data, int index,
                         float left, float rowY, float rowWidth, int mouseX, int mouseY,
                         float listTop, int accent, ScreenRectangle clip) {
        boolean hovered = mouseX >= left && mouseX <= left + rowWidth
                && mouseY >= Math.max(rowY, listTop)
                && mouseY <= Math.min(rowY + ROW_HEIGHT, listTop + listHeight());
        boolean isSelected = index == selected;

        RenderUtil.roundedRect(extractor, left, rowY, rowWidth, ROW_HEIGHT, ROW_RADIUS,
                hovered || isSelected ? ROW_HOVER : ROW_COLOR, clip);
        if (isSelected) {
            RenderUtil.roundedRect(extractor, left + 4.0f, rowY + 7.0f, 2.5f, ROW_HEIGHT - 14.0f,
                    1.25f, accent, clip);
        }

        float iconX = left + 10.0f;
        float iconY = rowY + (ROW_HEIGHT - ICON_SIZE) * 0.5f;
        AerialImage icon = iconFor(data);
        if (icon != null) {
            RenderUtil.image(extractor, icon, iconX, iconY, ICON_SIZE, ICON_SIZE, clip);
        } else {
            RenderUtil.roundedRect(extractor, iconX, iconY, ICON_SIZE, ICON_SIZE, 3.0f,
                    ICON_PLACEHOLDER, clip);
        }

        float textX = iconX + ICON_SIZE + 9.0f;
        TextRenderUtil.drawString(extractor, font, data.name, textX, rowY + 8.0f, NAME_SIZE,
                NAME_COLOR, clip);
        TextRenderUtil.drawString(extractor, font, data.ip, textX, rowY + 8.0f + NAME_SIZE + 2.5f,
                META_SIZE, META_COLOR, clip);

        String ping = pingText(data);
        float pingWidth = font.stringWidth(ping, META_SIZE);
        TextRenderUtil.drawString(extractor, font, ping,
                left + rowWidth - 10.0f - pingWidth, rowY + 8.0f + NAME_SIZE + 2.5f, META_SIZE,
                pingColor(data), clip);

        String players = playerText(data);
        if (!players.isEmpty()) {
            TextRenderUtil.drawString(extractor, font, players,
                    left + rowWidth - 10.0f - font.stringWidth(players, META_SIZE),
                    rowY + 8.0f, META_SIZE, META_COLOR, clip);
        }
    }

    private @Nullable AerialImage iconFor(ServerData data) {
        if (icons.containsKey(data.ip)) {
            return icons.get(data.ip);
        }
        byte[] bytes = data.getIconBytes();
        if (bytes == null) {
            return null;
        }
        AerialImage image = null;
        try {
            image = AerialImage.fromImage(ImageIO.read(new ByteArrayInputStream(bytes)));
        } catch (Exception exception) {
        }
        icons.put(data.ip, image);
        return image;
    }

    private static String pingText(ServerData data) {
        if (data.ping < 0L) {
            return "unreachable";
        }
        if (data.ping == 0L) {
            return "pinging";
        }
        return data.ping + " ms";
    }

    private static int pingColor(ServerData data) {
        if (data.ping <= 0L) {
            return META_COLOR;
        }
        for (int i = 0; i < PING_STEPS.length; i++) {
            if (data.ping < PING_STEPS[i]) {
                return PING_COLORS[i];
            }
        }
        return PING_COLORS[PING_COLORS.length - 1];
    }

    private static String playerText(ServerData data) {
        if (data.players == null) {
            return "";
        }
        String players = data.players.online() + "/" + data.players.max();
        if (data.protocol != SharedConstants.getCurrentVersion().protocolVersion() && data.version != null) {
            return data.version.getString() + "  " + players;
        }
        return players;
    }

    private record Action(String label, boolean danger) {
    }

    private static final Action[] ACTIONS = {
            new Action("Join", false),
            new Action("Add", false),
            new Action("Edit", false),
            new Action("Delete", true),
            new Action("Direct", false),
            new Action("Refresh", false),
            new Action("Back", false)
    };

    private void runAction(int index) {
        switch (index) {
            case 0 -> join();
            case 1 -> addServer();
            case 2 -> editServer();
            case 3 -> deleteServer();
            case 4 -> directJoin();
            case 5 -> pingAll();
            default -> onClose();
        }
    }

    private void drawActions(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        float y = actionsY();
        float cursor = cardLeft() + PADDING;
        for (Action action : ACTIONS) {
            float labelWidth = font.stringWidth(action.label(), ACTION_SIZE);
            boolean hovered = inside(mouseX, mouseY, cursor - 4.0f, y - 4.0f,
                    labelWidth + 8.0f, ACTION_SIZE + 8.0f);
            int color = action.danger() ? DANGER_COLOR : ACTION_COLOR;
            TextRenderUtil.drawString(extractor, font, action.label(), cursor, y, ACTION_SIZE,
                    hovered ? brighten(color) : color);
            cursor += labelWidth + 14.0f;
        }
    }

    private static int brighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 50);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 50);
        int b = Math.min(255, (color & 0xFF) + 50);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private @Nullable ServerData selectedServer() {
        return selected >= 0 && selected < servers.size() ? servers.get(selected) : null;
    }

    private void join() {
        ServerData data = selectedServer();
        if (data == null) {
            return;
        }
        ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(data.ip), data,
                false, null);
    }

    private void addServer() {
        ServerData draft = new ServerData("Minecraft Server", "", ServerData.Type.OTHER);
        minecraft.setScreenAndShow(new ServerEditScreen(this, "Add Server", draft, saved -> {
            servers.add(saved, false);
            servers.save();
            pingAll();
            minecraft.setScreenAndShow(this);
        }));
    }

    private void editServer() {
        ServerData data = selectedServer();
        if (data == null) {
            return;
        }
        minecraft.setScreenAndShow(new ServerEditScreen(this, "Edit Server", data, saved -> {
            servers.save();
            pingAll();
            minecraft.setScreenAndShow(this);
        }));
    }

    private void deleteServer() {
        ServerData data = selectedServer();
        if (data == null) {
            return;
        }
        servers.remove(data);
        servers.save();
        selected = -1;
    }

    private void directJoin() {
        minecraft.setScreenAndShow(new ServerDirectScreen(this, host -> {
            ServerData data = new ServerData("Minecraft Server", host, ServerData.Type.OTHER);
            ConnectScreen.startConnecting(this, minecraft, ServerAddress.parseString(host), data,
                    false, null);
        }));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x();
        double mouseY = event.y();

        float y = actionsY();
        if (mouseY >= y - 4.0f && mouseY <= y + ACTION_SIZE + 4.0f) {
            float cursor = cardLeft() + PADDING;
            for (int i = 0; i < ACTIONS.length; i++) {
                float labelWidth = font.stringWidth(ACTIONS[i].label(), ACTION_SIZE);
                if (mouseX >= cursor - 4.0f && mouseX <= cursor + labelWidth + 4.0f) {
                    runAction(i);
                    return true;
                }
                cursor += labelWidth + 14.0f;
            }
        }

        int row = rowAt(mouseY);
        float left = cardLeft() + PADDING;
        if (row >= 0 && mouseX >= left && mouseX <= left + CARD_WIDTH - PADDING * 2) {
            selected = row;
            if (doubled) {
                join();
            }
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    private int rowAt(double mouseY) {
        float top = listTop();
        if (mouseY < top || mouseY > top + listHeight()) {
            return -1;
        }
        int index = (int) (((float) mouseY - top + scroll) / (ROW_HEIGHT + ROW_GAP));
        return index >= 0 && index < servers.size() ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollTarget = (float) Math.max(0.0, Math.min(maxScroll(),
                scrollTarget - vertical * (ROW_HEIGHT + ROW_GAP) * 0.5));
        return true;
    }

    private static boolean inside(double mouseX, double mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(previousScreen);
    }

    @Override
    protected void extractMenuBackground(GuiGraphicsExtractor extractor) {
    }
}
