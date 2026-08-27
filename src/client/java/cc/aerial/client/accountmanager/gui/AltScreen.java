package cc.aerial.client.accountmanager.gui;

import cc.aerial.client.accountmanager.util.AuthExecutors;
import cc.aerial.client.accountmanager.Account;
import cc.aerial.client.accountmanager.AccountLogin;
import cc.aerial.client.accountmanager.AccountManager;
import cc.aerial.client.accountmanager.SessionManager;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.screen.title.TitleBackground;
import cc.aerial.client.theme.Theme;
import cc.aerial.client.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class AltScreen extends Screen {
    private static final float CARD_WIDTH = 320.0f;
    private static final float CARD_RADIUS = 8.0f;
    private static final float PADDING = 14.0f;
    private static final float ROW_HEIGHT = 30.0f;
    private static final float ROW_GAP = 4.0f;
    private static final float ROW_RADIUS = 5.0f;
    private static final int VISIBLE_ROWS = 7;
    private static final float TITLE_SIZE = 15.0f;
    private static final float NAME_SIZE = 10.0f;
    private static final float META_SIZE = 7.5f;
    private static final float ACTION_SIZE = 8.5f;
    private static final float SEARCH_HEIGHT = 18.0f;

    private static final int CARD_COLOR = 0xE60D0D14;
    private static final int ROW_COLOR = 0x33141420;
    private static final int ROW_HOVER = 0x59202030;
    private static final int NAME_COLOR = 0xFFEDEDF2;
    private static final int META_COLOR = 0xFF7C7E8A;
    private static final int ACTION_COLOR = 0xFFB9BAC4;
    private static final int DANGER_COLOR = 0xFFE05A5A;

    private static final float SCROLL_RATE = 16.0f;

    private final @Nullable Screen previousScreen;
    private final List<Account> filtered = new ArrayList<>();

    private AerialFont font;
    private AerialFont boldFont;

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(AuthExecutors.daemonFactory("Aerial Alt Login"));

    private String search = "";
    private boolean searchFocused;
    private int selected = -1;
    private float scrollTarget;
    private float scroll;
    private long lastScrollFrame;
    private String status = "";

    public AltScreen(@Nullable Screen previousScreen) {
        super(Component.literal("Alt"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        AccountManager.load();
        SessionManager.captureLaunchSession();
        refilter();
    }

    private void ensureFonts() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
            boldFont = AerialFont.createFromResource("OpalProductSansBold.ttf");
        }
    }

    private void refilter() {
        filtered.clear();
        String needle = search.toLowerCase(Locale.ROOT);
        for (Account account : AccountManager.accounts) {
            if (needle.isEmpty() || account.getUsername().toLowerCase(Locale.ROOT).contains(needle)) {
                filtered.add(account);
            }
        }
        selected = -1;
    }

    private float cardHeight() {
        return PADDING * 2 + TITLE_SIZE + 10.0f + SEARCH_HEIGHT + 8.0f
                + VISIBLE_ROWS * ROW_HEIGHT + (VISIBLE_ROWS - 1) * ROW_GAP + 10.0f + ACTION_SIZE + 6.0f;
    }

    private float cardLeft() {
        return (width - CARD_WIDTH) * 0.5f;
    }

    private float cardTop() {
        return (height - cardHeight()) * 0.5f;
    }

    private float listTop() {
        return cardTop() + PADDING + TITLE_SIZE + 10.0f + SEARCH_HEIGHT + 8.0f;
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
        float content = filtered.size() * ROW_HEIGHT + Math.max(0, filtered.size() - 1) * ROW_GAP;
        return Math.max(0.0f, content - listHeight());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        ensureFonts();

        TitleBackground.draw(extractor, width, height);

        Theme theme = ThemeManager.getTheme();
        int accent = theme.getAccentColor(0, 0).getRGB() | 0xFF000000;
        int accentLeft = theme.getAccentColor(0, 50).getRGB() | 0xFF000000;

        float left = cardLeft();
        float top = cardTop();
        RenderUtil.roundedRect(extractor, left, top, CARD_WIDTH, cardHeight(), CARD_RADIUS, CARD_COLOR);

        TextRenderUtil.drawGradientString(extractor, boldFont, "alt manager",
                left + PADDING, top + PADDING, TITLE_SIZE, accentLeft, accent);

        String count = filtered.size() + (filtered.size() == 1 ? " account" : " accounts");
        TextRenderUtil.drawString(extractor, font, count,
                left + CARD_WIDTH - PADDING - font.stringWidth(count, META_SIZE),
                top + PADDING + TITLE_SIZE - META_SIZE, META_SIZE, META_COLOR);

        drawSearch(extractor, left, top + PADDING + TITLE_SIZE + 10.0f, accent);
        drawList(extractor, mouseX, mouseY, accent);
        drawActions(extractor, mouseX, mouseY);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void drawSearch(GuiGraphicsExtractor extractor, float left, float y, int accent) {
        float boxLeft = left + PADDING;
        float boxWidth = CARD_WIDTH - PADDING * 2;
        if (searchFocused) {
            RenderUtil.roundedRect(extractor, boxLeft - 1.0f, y - 1.0f, boxWidth + 2.0f,
                    SEARCH_HEIGHT + 2.0f, 4.0f, accent);
        }
        RenderUtil.roundedRect(extractor, boxLeft, y, boxWidth, SEARCH_HEIGHT, 3.5f, 0x66101018);

        boolean empty = search.isEmpty() && !searchFocused;
        TextRenderUtil.drawString(extractor, font, empty ? "Search" : search,
                boxLeft + 7.0f, y + (SEARCH_HEIGHT - META_SIZE - 1.0f) * 0.5f, META_SIZE + 1.0f,
                empty ? META_COLOR : NAME_COLOR);

        if (searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float caretX = boxLeft + 7.0f + font.stringWidth(search, META_SIZE + 1.0f) + 1.0f;
            RenderUtil.flatRect(extractor, caretX, y + 4.0f, 0.75f, SEARCH_HEIGHT - 8.0f, NAME_COLOR);
        }
    }

    private void drawList(GuiGraphicsExtractor extractor, int mouseX, int mouseY, int accent) {
        advanceScroll();
        float left = cardLeft() + PADDING;
        float rowWidth = CARD_WIDTH - PADDING * 2;
        float top = listTop();

        ScreenRectangle clip = new ScreenRectangle(Math.round(left), Math.round(top),
                Math.round(rowWidth), Math.round(listHeight()));

        String current = Minecraft.getInstance().getUser().getName();
        for (int i = 0; i < filtered.size(); i++) {
            float rowY = top + i * (ROW_HEIGHT + ROW_GAP) - scroll;
            if (rowY + ROW_HEIGHT < top || rowY > top + listHeight()) {
                continue;
            }
            Account account = filtered.get(i);
            boolean hovered = mouseX >= left && mouseX <= left + rowWidth
                    && mouseY >= Math.max(rowY, top) && mouseY <= Math.min(rowY + ROW_HEIGHT, top + listHeight());
            boolean isSelected = i == selected;
            boolean isCurrent = account.getUsername().equals(current);

            RenderUtil.roundedRect(extractor, left, rowY, rowWidth, ROW_HEIGHT, ROW_RADIUS,
                    hovered || isSelected ? ROW_HOVER : ROW_COLOR, clip);
            if (isSelected) {
                RenderUtil.roundedRect(extractor, left + 4.0f, rowY + 6.0f, 2.5f, ROW_HEIGHT - 12.0f,
                        1.25f, accent, clip);
            }

            TextRenderUtil.drawString(extractor, font, account.getUsername(),
                    left + 12.0f, rowY + 6.0f, NAME_SIZE, NAME_COLOR, clip);
            TextRenderUtil.drawString(extractor, font, account.getType().toString().toLowerCase(Locale.ROOT),
                    left + 12.0f, rowY + 6.0f + NAME_SIZE + 2.0f, META_SIZE, META_COLOR, clip);

            if (isCurrent) {
                String tag = "in use";
                float tagWidth = font.stringWidth(tag, META_SIZE) + 10.0f;
                RenderUtil.roundedRect(extractor, left + rowWidth - tagWidth - 8.0f,
                        rowY + (ROW_HEIGHT - 11.0f) * 0.5f, tagWidth, 11.0f, 5.5f, 0x33FFFFFF, clip);
                TextRenderUtil.drawString(extractor, font, tag,
                        left + rowWidth - tagWidth - 3.0f,
                        rowY + (ROW_HEIGHT - META_SIZE) * 0.5f, META_SIZE, accent, clip);
            }
        }

        if (filtered.isEmpty()) {
            String empty = AccountManager.accounts.isEmpty() ? "No accounts yet" : "No matches";
            TextRenderUtil.drawString(extractor, font, empty,
                    left + (rowWidth - font.stringWidth(empty, NAME_SIZE)) * 0.5f,
                    top + listHeight() * 0.5f - NAME_SIZE * 0.5f, NAME_SIZE, META_COLOR);
        }
    }

    private void drawActions(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        float y = actionsY();
        float cursor = cardLeft() + PADDING;
        for (Action action : ACTIONS) {
            float labelWidth = font.stringWidth(action.label(), ACTION_SIZE);
            boolean hovered = mouseX >= cursor - 4.0f && mouseX <= cursor + labelWidth + 4.0f
                    && mouseY >= y - 4.0f && mouseY <= y + ACTION_SIZE + 4.0f;
            int color = action.danger() ? DANGER_COLOR : ACTION_COLOR;
            TextRenderUtil.drawString(extractor, font, action.label(), cursor, y, ACTION_SIZE,
                    hovered ? brighten(color) : color);
            cursor += labelWidth + 16.0f;
        }
        if (!status.isEmpty()) {
            TextRenderUtil.drawString(extractor, font, status,
                    cardLeft() + CARD_WIDTH - PADDING - font.stringWidth(status, META_SIZE),
                    y + 0.5f, META_SIZE, META_COLOR);
        }
    }

    private float actionsY() {
        return listTop() + listHeight() + 10.0f;
    }

    private static int brighten(int color) {
        int r = Math.min(255, ((color >> 16) & 0xFF) + 50);
        int g = Math.min(255, ((color >> 8) & 0xFF) + 50);
        int b = Math.min(255, (color & 0xFF) + 50);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private record Action(String label, boolean danger) {
    }

    private static final Action[] ACTIONS = {
            new Action("Login", false),
            new Action("Add", false),
            new Action("Delete", true),
            new Action("Restore", false),
            new Action("Store", false),
            new Action("Back", false)
    };

    private void runAction(int index) {
        switch (index) {
            case 0 -> login();
            case 1 -> Minecraft.getInstance().setScreenAndShow(new AltAddScreen(previousScreen));
            case 2 -> delete();
            case 3 -> restore();
            case 4 -> Minecraft.getInstance().setScreenAndShow(new LocaltsScreen(previousScreen));
            default -> onClose();
        }
    }

    private void login() {
        if (selected < 0 || selected >= filtered.size()) {
            status = "Pick an account first";
            return;
        }
        Account account = filtered.get(selected);
        status = "Logging in as " + account.getUsername() + "...";
        AccountLogin.login(account, EXECUTOR).thenRun(() ->
                Minecraft.getInstance().execute(() -> status = "Logged in as "
                        + Minecraft.getInstance().getUser().getName()));
    }

    private void delete() {
        if (selected < 0 || selected >= filtered.size()) {
            status = "Pick an account first";
            return;
        }
        AccountManager.accounts.remove(filtered.get(selected));
        AccountManager.save();
        refilter();
        status = "Deleted";
    }

    private void restore() {
        if (SessionManager.getLaunchSession() == null) {
            status = "No launch session";
            return;
        }
        SessionManager.restoreLaunchSession();
        status = "Restored " + Minecraft.getInstance().getUser().getName();
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x();
        double mouseY = event.y();
        float left = cardLeft() + PADDING;
        float rowWidth = CARD_WIDTH - PADDING * 2;

        searchFocused = mouseX >= left && mouseX <= left + rowWidth
                && mouseY >= cardTop() + PADDING + TITLE_SIZE + 10.0f
                && mouseY <= cardTop() + PADDING + TITLE_SIZE + 10.0f + SEARCH_HEIGHT;
        if (searchFocused) {
            return true;
        }

        float actionsY = actionsY();
        if (mouseY >= actionsY - 4.0f && mouseY <= actionsY + ACTION_SIZE + 4.0f) {
            float cursor = cardLeft() + PADDING;
            for (int i = 0; i < ACTIONS.length; i++) {
                float labelWidth = font.stringWidth(ACTIONS[i].label(), ACTION_SIZE);
                if (mouseX >= cursor - 4.0f && mouseX <= cursor + labelWidth + 4.0f) {
                    runAction(i);
                    return true;
                }
                cursor += labelWidth + 16.0f;
            }
        }

        int row = rowAt(mouseY);
        if (row >= 0 && mouseX >= left && mouseX <= left + rowWidth) {
            selected = row;
            if (doubled) {
                login();
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
        float local = (float) mouseY - top + scroll;
        int index = (int) (local / (ROW_HEIGHT + ROW_GAP));
        return index >= 0 && index < filtered.size() ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        scrollTarget = (float) Math.max(0.0, Math.min(maxScroll(),
                scrollTarget - vertical * (ROW_HEIGHT + ROW_GAP) * 0.5));
        return true;
    }

    @Override
    public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
        if (searchFocused && event.isAllowedChatCharacter()) {
            search += (char) event.codepoint();
            refilter();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (searchFocused) {
            if (keyEvent.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!search.isEmpty()) {
                    search = search.substring(0, search.length() - 1);
                    refilter();
                }
                return true;
            }
            if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE || keyEvent.key() == GLFW.GLFW_KEY_ENTER) {
                searchFocused = false;
                return true;
            }
            if (keyEvent.isPaste()) {
                search += Minecraft.getInstance().keyboardHandler.getClipboard();
                refilter();
                return true;
            }
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(previousScreen);
    }

    @Override
    protected void extractMenuBackground(GuiGraphicsExtractor extractor) {
    }
}
