package cc.aerial.client.accountmanager.gui;

import cc.aerial.client.accountmanager.Account;
import cc.aerial.client.accountmanager.LocaltsService;
import cc.aerial.client.accountmanager.util.AuthExecutors;
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
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class LocaltsScreen extends Screen {
    private static final float CARD_WIDTH = 340.0f;
    private static final float CARD_RADIUS = 8.0f;
    private static final float PADDING = 14.0f;
    private static final float ROW_HEIGHT = 34.0f;
    private static final float ROW_GAP = 4.0f;
    private static final float ROW_RADIUS = 5.0f;
    private static final int VISIBLE_ROWS = 6;
    private static final float TITLE_SIZE = 15.0f;
    private static final float NAME_SIZE = 10.0f;
    private static final float META_SIZE = 7.5f;
    private static final float BALANCE_SIZE = 10.5f;
    private static final float ACTION_SIZE = 8.5f;
    private static final float FIELD_HEIGHT = 20.0f;

    private static final int CARD_COLOR = 0xE60D0D14;
    private static final int ROW_COLOR = 0x33141420;
    private static final int ROW_HOVER = 0x59202030;
    private static final int ROW_DISABLED = 0x22101018;
    private static final int NAME_COLOR = 0xFFEDEDF2;
    private static final int META_COLOR = 0xFF7C7E8A;
    private static final int PRICE_COLOR = 0xFFF5D374;
    private static final int STOCK_COLOR = 0xFF9CE39A;
    private static final int OUT_OF_STOCK_COLOR = 0xFFE05A5A;
    private static final int ACTION_COLOR = 0xFFB9BAC4;

    private static final float SCROLL_RATE = 16.0f;

    private static final ExecutorService EXECUTOR =
            Executors.newSingleThreadExecutor(AuthExecutors.daemonFactory("Aerial Localts"));

    private final @Nullable Screen previousScreen;

    private AerialFont font;
    private AerialFont boldFont;

    private AerialFont dynamicFont;

    private @Nullable LocaltsService.Account me;
    private List<LocaltsService.Product> products = List.of();
    private @Nullable CompletableFuture<?> inFlight;

    private int selected = -1;
    private float scrollTarget;
    private float scroll;
    private long lastScrollFrame;
    private String status = "";
    private boolean statusIsError;

    private boolean editingKey;
    private String keyDraft = "";
    private boolean keyFocused;

    public LocaltsScreen(@Nullable Screen previousScreen) {
        super(Component.literal("Localts"));
        this.previousScreen = previousScreen;
    }

    @Override
    protected void init() {
        if (!LocaltsService.hasApiKey()) {
            editingKey = true;
            keyFocused = true;
        } else {
            refresh();
        }
    }

    private void ensureFonts() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
            boldFont = AerialFont.createFromResource("OpalProductSansBold.ttf");
            dynamicFont = AerialFont.createDynamicFromResource("OpalProductSansMedium.ttf");
        }
    }

    private float cardHeight() {
        return PADDING * 2 + TITLE_SIZE + 14.0f + VISIBLE_ROWS * ROW_HEIGHT
                + (VISIBLE_ROWS - 1) * ROW_GAP + 12.0f + ACTION_SIZE;
    }

    private float cardLeft() {
        return (width - CARD_WIDTH) * 0.5f;
    }

    private float cardTop() {
        return (height - cardHeight()) * 0.5f;
    }

    private float listTop() {
        return cardTop() + PADDING + TITLE_SIZE + 14.0f;
    }

    private float listHeight() {
        return VISIBLE_ROWS * ROW_HEIGHT + (VISIBLE_ROWS - 1) * ROW_GAP;
    }

    private float fieldY() {
        return cardTop() + PADDING + TITLE_SIZE + 14.0f + META_SIZE + 6.0f;
    }

    private float actionsY() {
        return editingKey ? fieldY() + FIELD_HEIGHT + 14.0f
                : listTop() + listHeight() + 12.0f;
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

        TextRenderUtil.drawGradientString(extractor, boldFont, "localts",
                left + PADDING, top + PADDING, TITLE_SIZE, accentLeft, accent);
        drawBalance(extractor, left, top, accent);

        if (editingKey) {
            drawApiKeyField(extractor, accent);
        } else {
            drawProducts(extractor, mouseX, mouseY, accent);
        }

        drawActions(extractor, mouseX, mouseY);
        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void drawBalance(GuiGraphicsExtractor extractor, float left, float top, int accent) {
        String primary;
        int primaryColor;
        if (me != null) {
            primary = me.balance() + " cr";
            primaryColor = accent;
        } else if (editingKey) {
            primary = "no key";
            primaryColor = META_COLOR;
        } else {
            primary = "-- cr";
            primaryColor = META_COLOR;
        }
        float primaryWidth = boldFont.stringWidth(primary, BALANCE_SIZE);
        TextRenderUtil.drawString(extractor, boldFont, primary,
                left + CARD_WIDTH - PADDING - primaryWidth,
                top + PADDING + (TITLE_SIZE - BALANCE_SIZE) * 0.5f, BALANCE_SIZE, primaryColor);
        if (me != null) {
            String name = me.username();
            dynamicFont.ensureGlyphs(name);
            float nameWidth = dynamicFont.stringWidth(name, META_SIZE);
            TextRenderUtil.drawString(extractor, dynamicFont, name,
                    left + CARD_WIDTH - PADDING - Math.max(primaryWidth, nameWidth),
                    top + PADDING + TITLE_SIZE, META_SIZE, META_COLOR);
        }
    }

    private void drawApiKeyField(GuiGraphicsExtractor extractor, int accent) {
        float left = cardLeft() + PADDING;
        float fieldWidth = CARD_WIDTH - PADDING * 2;
        TextRenderUtil.drawString(extractor, font,
                "Paste your API key -- generated on the Localts Settings page",
                left, cardTop() + PADDING + TITLE_SIZE + 14.0f, META_SIZE, META_COLOR);

        float y = fieldY();
        if (keyFocused) {
            RenderUtil.roundedRect(extractor, left - 1.0f, y - 1.0f, fieldWidth + 2.0f,
                    FIELD_HEIGHT + 2.0f, 4.5f, accent);
        }
        RenderUtil.roundedRect(extractor, left, y, fieldWidth, FIELD_HEIGHT, 4.0f, 0x66101018);

        boolean empty = keyDraft.isEmpty() && !keyFocused;
        String display = empty ? "API key"
                : "*".repeat(Math.min(keyDraft.length(), (int) (fieldWidth / 4)));
        TextRenderUtil.drawString(extractor, font, display,
                left + 8.0f, y + (FIELD_HEIGHT - NAME_SIZE) * 0.5f, NAME_SIZE,
                empty ? META_COLOR : NAME_COLOR);

        if (keyFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float caretX = left + 8.0f + font.stringWidth(display, NAME_SIZE) + 1.0f;
            RenderUtil.flatRect(extractor, caretX, y + 5.0f, 0.75f, FIELD_HEIGHT - 10.0f, NAME_COLOR);
        }
    }

    private void drawProducts(GuiGraphicsExtractor extractor, int mouseX, int mouseY, int accent) {
        advanceScroll();
        float left = cardLeft() + PADDING;
        float rowWidth = CARD_WIDTH - PADDING * 2;
        float top = listTop();

        ScreenRectangle clip = new ScreenRectangle(Math.round(left), Math.round(top),
                Math.round(rowWidth), Math.round(listHeight()));

        for (int i = 0; i < products.size(); i++) {
            float rowY = top + i * (ROW_HEIGHT + ROW_GAP) - scroll;
            if (rowY + ROW_HEIGHT < top || rowY > top + listHeight()) {
                continue;
            }
            LocaltsService.Product product = products.get(i);
            boolean available = product.stock() > 0 && (me == null || me.balance() >= product.priceInCredits());
            boolean hovered = mouseX >= left && mouseX <= left + rowWidth
                    && mouseY >= Math.max(rowY, top) && mouseY <= Math.min(rowY + ROW_HEIGHT, top + listHeight());
            boolean isSelected = i == selected;

            int background = !available ? ROW_DISABLED : (hovered || isSelected ? ROW_HOVER : ROW_COLOR);
            RenderUtil.roundedRect(extractor, left, rowY, rowWidth, ROW_HEIGHT, ROW_RADIUS, background, clip);
            if (isSelected && available) {
                RenderUtil.roundedRect(extractor, left + 4.0f, rowY + 6.0f, 2.5f, ROW_HEIGHT - 12.0f,
                        1.25f, accent, clip);
            }

            String productName = product.name();
            String subtitle = product.category().isEmpty() ? product.type() : product.category();
            dynamicFont.ensureGlyphs(productName);
            dynamicFont.ensureGlyphs(subtitle);
            TextRenderUtil.drawString(extractor, dynamicFont, productName,
                    left + 12.0f, rowY + 7.0f, NAME_SIZE, available ? NAME_COLOR : META_COLOR, clip);
            TextRenderUtil.drawString(extractor, dynamicFont, subtitle,
                    left + 12.0f, rowY + 7.0f + NAME_SIZE + 2.5f, META_SIZE, META_COLOR, clip);

            String price = product.priceInCredits() + " cr";
            String stockText = product.stock() > 0 ? "x" + product.stock() : "out of stock";
            int stockColor = product.stock() > 0 ? STOCK_COLOR : OUT_OF_STOCK_COLOR;

            float priceWidth = boldFont.stringWidth(price, NAME_SIZE);
            float stockWidth = font.stringWidth(stockText, META_SIZE);
            TextRenderUtil.drawString(extractor, boldFont, price,
                    left + rowWidth - 12.0f - priceWidth, rowY + 7.0f, NAME_SIZE, PRICE_COLOR, clip);
            TextRenderUtil.drawString(extractor, font, stockText,
                    left + rowWidth - 12.0f - stockWidth, rowY + 7.0f + NAME_SIZE + 2.5f,
                    META_SIZE, stockColor, clip);
        }

        if (products.isEmpty()) {
            String empty = inFlight != null ? "Loading..." : "No products";
            TextRenderUtil.drawString(extractor, font, empty,
                    left + (rowWidth - font.stringWidth(empty, NAME_SIZE)) * 0.5f,
                    top + listHeight() * 0.5f - NAME_SIZE * 0.5f, NAME_SIZE, META_COLOR);
        }
    }

    private void drawActions(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        float y = actionsY();
        float cursor = cardLeft() + PADDING;
        String[] labels = actionLabels();
        for (String label : labels) {
            float labelWidth = font.stringWidth(label, ACTION_SIZE);
            boolean hovered = mouseX >= cursor - 4.0f && mouseX <= cursor + labelWidth + 4.0f
                    && mouseY >= y - 4.0f && mouseY <= y + ACTION_SIZE + 4.0f;
            TextRenderUtil.drawString(extractor, font, label, cursor, y, ACTION_SIZE,
                    hovered ? 0xFFFFFFFF : ACTION_COLOR);
            cursor += labelWidth + 16.0f;
        }
        if (!status.isEmpty()) {
            int color = statusIsError ? OUT_OF_STOCK_COLOR : META_COLOR;
            TextRenderUtil.drawString(extractor, font, status,
                    cardLeft() + CARD_WIDTH - PADDING - font.stringWidth(status, META_SIZE),
                    y + 0.5f, META_SIZE, color);
        }
    }

    private String[] actionLabels() {
        return editingKey
                ? new String[] {"Save", "Clear", "Back"}
                : new String[] {"Buy", "Refresh", "API key", "Back"};
    }

    private void refresh() {
        if (inFlight != null && !inFlight.isDone()) {
            return;
        }
        setStatus("Refreshing...", false);
        Minecraft mc = Minecraft.getInstance();

        CompletableFuture<LocaltsService.Account> meCall = LocaltsService.me(EXECUTOR)
                .whenComplete((account, ex) -> mc.execute(() -> {
                    if (ex != null) {
                        setStatus(rootMessage(ex), true);
                        return;
                    }
                    me = account;
                    if (status.equals("Refreshing...")) {
                        setStatus("", false);
                    }
                }));
        CompletableFuture<List<LocaltsService.Product>> productsCall = LocaltsService.products(EXECUTOR)
                .whenComplete((list, ex) -> mc.execute(() -> {
                    if (ex != null) {
                        setStatus(rootMessage(ex), true);
                        return;
                    }
                    products = list;
                    selected = -1;
                    scroll = 0.0f;
                    scrollTarget = 0.0f;
                }));
        inFlight = CompletableFuture.allOf(meCall, productsCall);
    }

    private void buy() {
        if (selected < 0 || selected >= products.size()) {
            setStatus("Pick a product first", true);
            return;
        }
        if (inFlight != null && !inFlight.isDone()) {
            return;
        }
        LocaltsService.Product product = products.get(selected);
        if (product.stock() <= 0) {
            setStatus("Out of stock", true);
            return;
        }
        if (me != null && me.balance() < product.priceInCredits()) {
            setStatus("Not enough credits", true);
            return;
        }
        setStatus("Buying " + product.name() + "...", false);
        Minecraft mc = Minecraft.getInstance();

        inFlight = LocaltsService.purchaseAndImport(product.id(), EXECUTOR,
                phase -> mc.execute(() -> setStatus(phase, false)))
                .whenComplete((account, ex) -> mc.execute(() -> {
                    if (ex != null) {
                        setStatus(rootMessage(ex), true);
                        return;
                    }
                    setStatus("Added " + account.getUsername(), false);
                    refresh();
                }));
    }

    private void saveKey() {
        LocaltsService.setApiKey(keyDraft);
        keyDraft = "";
        keyFocused = false;
        editingKey = false;
        me = null;
        products = List.of();
        refresh();
    }

    private void clearKey() {
        LocaltsService.setApiKey("");
        keyDraft = "";
        me = null;
        setStatus("Cleared", false);
    }

    private void openKeyEditor() {
        editingKey = true;
        keyFocused = true;
        keyDraft = "";
        setStatus("", false);
    }

    private void setStatus(String text, boolean error) {
        this.status = text;
        this.statusIsError = error;
    }

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isEmpty() ? current.getClass().getSimpleName() : message;
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
        float content = products.size() * ROW_HEIGHT + Math.max(0, products.size() - 1) * ROW_GAP;
        return Math.max(0.0f, content - listHeight());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x();
        double mouseY = event.y();

        if (editingKey) {
            float left = cardLeft() + PADDING;
            float fieldWidth = CARD_WIDTH - PADDING * 2;
            keyFocused = mouseX >= left && mouseX <= left + fieldWidth
                    && mouseY >= fieldY() && mouseY <= fieldY() + FIELD_HEIGHT;
            if (keyFocused) {
                return true;
            }
        }

        String[] labels = actionLabels();
        float y = actionsY();
        if (mouseY >= y - 4.0f && mouseY <= y + ACTION_SIZE + 4.0f) {
            float cursor = cardLeft() + PADDING;
            for (int i = 0; i < labels.length; i++) {
                float labelWidth = font.stringWidth(labels[i], ACTION_SIZE);
                if (mouseX >= cursor - 4.0f && mouseX <= cursor + labelWidth + 4.0f) {
                    runAction(i);
                    return true;
                }
                cursor += labelWidth + 16.0f;
            }
        }

        if (!editingKey) {
            int row = rowAt(mouseY);
            float left = cardLeft() + PADDING;
            float rowWidth = CARD_WIDTH - PADDING * 2;
            if (row >= 0 && mouseX >= left && mouseX <= left + rowWidth) {
                selected = row;
                if (doubled) {
                    buy();
                }
                return true;
            }
        }
        return super.mouseClicked(event, doubled);
    }

    private void runAction(int index) {
        if (editingKey) {
            switch (index) {
                case 0 -> saveKey();
                case 1 -> clearKey();
                default -> onClose();
            }
        } else {
            switch (index) {
                case 0 -> buy();
                case 1 -> refresh();
                case 2 -> openKeyEditor();
                default -> onClose();
            }
        }
    }

    private int rowAt(double mouseY) {
        float top = listTop();
        if (mouseY < top || mouseY > top + listHeight()) {
            return -1;
        }
        float local = (float) mouseY - top + scroll;
        int index = (int) (local / (ROW_HEIGHT + ROW_GAP));
        return index >= 0 && index < products.size() ? index : -1;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        if (editingKey) {
            return true;
        }
        scrollTarget = (float) Math.max(0.0, Math.min(maxScroll(),
                scrollTarget - vertical * (ROW_HEIGHT + ROW_GAP) * 0.5));
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (editingKey && keyFocused && event.isAllowedChatCharacter()) {
            keyDraft += (char) event.codepoint();
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (editingKey && keyFocused) {
            if (keyEvent.key() == GLFW.GLFW_KEY_BACKSPACE) {
                if (!keyDraft.isEmpty()) {
                    keyDraft = keyDraft.substring(0, keyDraft.length() - 1);
                }
                return true;
            }
            if (keyEvent.isPaste()) {
                keyDraft += Minecraft.getInstance().keyboardHandler.getClipboard();
                return true;
            }
            if (keyEvent.key() == GLFW.GLFW_KEY_ENTER) {
                saveKey();
                return true;
            }
            if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
                keyFocused = false;
                return true;
            }
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(new AltScreen(previousScreen));
    }

    @Override
    protected void extractMenuBackground(GuiGraphicsExtractor extractor) {
    }
}
