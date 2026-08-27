package cc.aerial.client.accountmanager.gui;

import cc.aerial.client.accountmanager.CrackedAuth;
import cc.aerial.client.accountmanager.util.UsernameGenerator;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.screen.animation.Animation;
import cc.aerial.client.screen.animation.Easing;
import cc.aerial.client.screen.title.TitleBackground;
import cc.aerial.client.theme.Theme;
import cc.aerial.client.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;

public final class AltAddScreen extends Screen {
    private static final float CARD_WIDTH = 300.0f;
    private static final float CARD_RADIUS = 8.0f;
    private static final float PADDING = 14.0f;
    private static final float ROW_HEIGHT = 34.0f;
    private static final float ROW_GAP = 4.0f;
    private static final float ROW_RADIUS = 5.0f;
    private static final float TITLE_SIZE = 15.0f;
    private static final float NAME_SIZE = 10.0f;
    private static final float META_SIZE = 7.5f;
    private static final float FIELD_HEIGHT = 20.0f;
    private static final float ACTION_SIZE = 8.5f;

    private static final int CARD_COLOR = 0xE60D0D14;
    private static final int ROW_COLOR = 0x33141420;
    private static final int ROW_HOVER = 0x59202030;
    private static final int NAME_COLOR = 0xFFEDEDF2;
    private static final int META_COLOR = 0xFF7C7E8A;
    private static final int ACTION_COLOR = 0xFFB9BAC4;

    private record Method(String name, String description) {
    }

    private static final Method[] METHODS = {
            new Method("Microsoft", "Sign in with a device code"),
            new Method("Cookie", "Import from a browser cookie export"),
            new Method("Access Token", "Paste an existing session token"),
            new Method("Refresh Token", "Paste a token that can renew itself")
    };

    private final @Nullable Screen previousScreen;
    private final Animation[] hovers = new Animation[METHODS.length];

    private AerialFont font;
    private AerialFont boldFont;
    private String username = "";
    private boolean fieldFocused;
    private String status = "";

    public AltAddScreen(@Nullable Screen previousScreen) {
        super(Component.literal("Add Account"));
        this.previousScreen = previousScreen;
        for (int i = 0; i < hovers.length; i++) {
            hovers[i] = new Animation(Easing.EASE_OUT_EXPO, 200);
        }
    }

    private void ensureFonts() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
            boldFont = AerialFont.createFromResource("OpalProductSansBold.ttf");
        }
    }

    private float cardHeight() {
        return PADDING * 2 + TITLE_SIZE + 12.0f
                + METHODS.length * ROW_HEIGHT + (METHODS.length - 1) * ROW_GAP
                + 14.0f + NAME_SIZE + 6.0f + FIELD_HEIGHT
                + 12.0f + ACTION_SIZE;
    }

    private float cardLeft() {
        return (width - CARD_WIDTH) * 0.5f;
    }

    private float cardTop() {
        return (height - cardHeight()) * 0.5f;
    }

    private float methodsTop() {
        return cardTop() + PADDING + TITLE_SIZE + 12.0f;
    }

    private float crackedLabelY() {
        return methodsTop() + METHODS.length * ROW_HEIGHT + (METHODS.length - 1) * ROW_GAP + 14.0f;
    }

    private float fieldY() {
        return crackedLabelY() + NAME_SIZE + 6.0f;
    }

    private float actionsY() {
        return fieldY() + FIELD_HEIGHT + 12.0f;
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
        TextRenderUtil.drawGradientString(extractor, boldFont, "add account",
                left + PADDING, cardTop() + PADDING, TITLE_SIZE, accentLeft, accent);

        drawMethods(extractor, mouseX, mouseY, accent);
        drawCracked(extractor, accent);
        drawActions(extractor, mouseX, mouseY);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void drawMethods(GuiGraphicsExtractor extractor, int mouseX, int mouseY, int accent) {
        float left = cardLeft() + PADDING;
        float rowWidth = CARD_WIDTH - PADDING * 2;
        for (int i = 0; i < METHODS.length; i++) {
            float y = methodsTop() + i * (ROW_HEIGHT + ROW_GAP);
            boolean hovered = inside(mouseX, mouseY, left, y, rowWidth, ROW_HEIGHT);
            hovers[i].run(hovered ? 1.0f : 0.0f);
            float progress = hovers[i].getValue();

            RenderUtil.roundedRect(extractor, left, y, rowWidth, ROW_HEIGHT, ROW_RADIUS,
                    hovered ? ROW_HOVER : ROW_COLOR);
            float barHeight = (ROW_HEIGHT - 12.0f) * progress;
            if (barHeight > 0.2f) {
                RenderUtil.roundedRect(extractor, left + 5.0f, y + (ROW_HEIGHT - barHeight) * 0.5f,
                        2.5f, barHeight, 1.25f, accent);
            }

            TextRenderUtil.drawString(extractor, font, METHODS[i].name(),
                    left + 15.0f, y + 8.0f, NAME_SIZE, NAME_COLOR);
            TextRenderUtil.drawString(extractor, font, METHODS[i].description(),
                    left + 15.0f, y + 8.0f + NAME_SIZE + 2.5f, META_SIZE, META_COLOR);
        }
    }

    private void drawCracked(GuiGraphicsExtractor extractor, int accent) {
        float left = cardLeft() + PADDING;
        float fieldWidth = CARD_WIDTH - PADDING * 2;

        TextRenderUtil.drawString(extractor, font, "Cracked", left, crackedLabelY(), NAME_SIZE, NAME_COLOR);
        String hint = "offline mode";
        TextRenderUtil.drawString(extractor, font, hint,
                left + fieldWidth - font.stringWidth(hint, META_SIZE),
                crackedLabelY() + 2.0f, META_SIZE, META_COLOR);

        float y = fieldY();
        if (fieldFocused) {
            RenderUtil.roundedRect(extractor, left - 1.0f, y - 1.0f, fieldWidth + 2.0f,
                    FIELD_HEIGHT + 2.0f, 4.5f, accent);
        }
        RenderUtil.roundedRect(extractor, left, y, fieldWidth, FIELD_HEIGHT, 4.0f, 0x66101018);

        boolean empty = username.isEmpty() && !fieldFocused;
        TextRenderUtil.drawString(extractor, font, empty ? "Username" : username,
                left + 8.0f, y + (FIELD_HEIGHT - NAME_SIZE) * 0.5f, NAME_SIZE,
                empty ? META_COLOR : NAME_COLOR);

        if (fieldFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) {
            float caretX = left + 8.0f + font.stringWidth(username, NAME_SIZE) + 1.0f;
            RenderUtil.flatRect(extractor, caretX, y + 5.0f, 0.75f, FIELD_HEIGHT - 10.0f, NAME_COLOR);
        }
    }

    private static final String[] ACTIONS = {"Add", "Random", "Back"};

    private void drawActions(GuiGraphicsExtractor extractor, int mouseX, int mouseY) {
        float y = actionsY();
        float cursor = cardLeft() + PADDING;
        for (String action : ACTIONS) {
            float labelWidth = font.stringWidth(action, ACTION_SIZE);
            boolean hovered = inside(mouseX, mouseY, cursor - 4.0f, y - 4.0f,
                    labelWidth + 8.0f, ACTION_SIZE + 8.0f);
            TextRenderUtil.drawString(extractor, font, action, cursor, y, ACTION_SIZE,
                    hovered ? 0xFFFFFFFF : ACTION_COLOR);
            cursor += labelWidth + 16.0f;
        }
        if (!status.isEmpty()) {
            TextRenderUtil.drawString(extractor, font, status,
                    cardLeft() + CARD_WIDTH - PADDING - font.stringWidth(status, META_SIZE),
                    y + 0.5f, META_SIZE, META_COLOR);
        }
    }

    private static boolean inside(double mouseX, double mouseY, float x, float y, float w, float h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }

    private void openMethod(int index) {
        Minecraft mc = Minecraft.getInstance();

        switch (index) {
            case 0 -> mc.setScreenAndShow(new MicrosoftAuthScreen(previousScreen));
            case 1 -> mc.setScreenAndShow(new CookieAuthScreen(previousScreen));
            case 2 -> mc.setScreenAndShow(new TokenLoginScreen(previousScreen));
            default -> mc.setScreenAndShow(new RefreshTokenLoginScreen(previousScreen));
        }
    }

    private void addCracked() {
        String name = username.trim();
        if (name.isEmpty()) {
            status = "Enter a username";
            return;
        }
        if (CrackedAuth.login(name)) {
            Minecraft.getInstance().setScreenAndShow(new AltScreen(previousScreen));
        } else {
            status = "Could not add " + name;
        }
    }

    private void randomUsername() {
        status = "Generating...";
        CompletableFuture.supplyAsync(UsernameGenerator::generate).thenAccept(name ->
                Minecraft.getInstance().execute(() -> {
                    if (name == null) {
                        status = "Generator unavailable";
                        return;
                    }
                    username = name;
                    fieldFocused = true;
                    status = "";
                }));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        double mouseX = event.x();
        double mouseY = event.y();
        float left = cardLeft() + PADDING;
        float rowWidth = CARD_WIDTH - PADDING * 2;

        fieldFocused = inside(mouseX, mouseY, left, fieldY(), rowWidth, FIELD_HEIGHT);
        if (fieldFocused) {
            return true;
        }

        for (int i = 0; i < METHODS.length; i++) {
            float y = methodsTop() + i * (ROW_HEIGHT + ROW_GAP);
            if (inside(mouseX, mouseY, left, y, rowWidth, ROW_HEIGHT)) {
                openMethod(i);
                return true;
            }
        }

        float actionsY = actionsY();
        float cursor = cardLeft() + PADDING;
        for (int i = 0; i < ACTIONS.length; i++) {
            float labelWidth = font.stringWidth(ACTIONS[i], ACTION_SIZE);
            if (inside(mouseX, mouseY, cursor - 4.0f, actionsY - 4.0f,
                    labelWidth + 8.0f, ACTION_SIZE + 8.0f)) {
                switch (i) {
                    case 0 -> addCracked();
                    case 1 -> randomUsername();
                    default -> onClose();
                }
                return true;
            }
            cursor += labelWidth + 16.0f;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        char typed = (char) event.codepoint();
        if (fieldFocused && username.length() < 16
                && (Character.isLetterOrDigit(typed) || typed == '_')) {
            username += typed;
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        if (fieldFocused) {
            switch (keyEvent.key()) {
                case GLFW.GLFW_KEY_BACKSPACE -> {
                    if (!username.isEmpty()) {
                        username = username.substring(0, username.length() - 1);
                    }
                }
                case GLFW.GLFW_KEY_ENTER -> addCracked();
                case GLFW.GLFW_KEY_ESCAPE -> fieldFocused = false;
                default -> {
                }
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
