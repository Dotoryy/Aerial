package cc.aerial.client.screen;

import cc.aerial.client.features.impl.other.spotify.SpotifyService;
import cc.aerial.client.property.ActionProperty;
import cc.aerial.client.property.Property;
import cc.aerial.client.property.StringProperty;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
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

public final class SpotifyApiScreen extends Screen {
    private static final float PADDING = 10.0f;
    private static final float RADIUS = 6.0f;
    private static final float TITLE_SIZE = 11.0f;
    private static final float PANEL_WIDTH = 240.0f;
    private static final float MAX_PANEL_HEIGHT = 220.0f;
    private static final int PANEL_COLOR = 0xF01A1A1E;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFF808080;

    private final @Nullable Screen previousScreen;
    private final SimplePropertyPanel panel;
    private final Property<?>[] properties;
    private AerialFont font;
    private float scroll;
    private float contentHeight;

    public SpotifyApiScreen(@Nullable Screen previousScreen, StringProperty clientId, StringProperty clientSecret) {
        super(Component.literal("Spotify API"));
        this.previousScreen = previousScreen;
        ActionProperty authorize = new ActionProperty("Authorize with Spotify",
                () -> SpotifyService.INSTANCE.startAuthFlow(clientId.getValue(), clientSecret.getValue()));
        ActionProperty logOut = new ActionProperty("Log Out", SpotifyService.INSTANCE::clearAuthorization);
        this.properties = new Property<?>[]{clientId, clientSecret, authorize, logOut};
        this.panel = new SimplePropertyPanel(properties);
    }

    private void ensureFont() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
        }
    }

    private float panelHeight() {
        return Math.min(MAX_PANEL_HEIGHT, PADDING * 2.0f + TITLE_SIZE + PADDING + contentHeight + PADDING + 10.0f);
    }

    private float panelLeft() {
        return (width - PANEL_WIDTH) / 2.0f;
    }

    private float panelTop() {
        return (height - panelHeight()) / 2.0f;
    }

    private float listTop() {
        return panelTop() + PADDING + TITLE_SIZE + PADDING;
    }

    private float listHeight() {
        return panelHeight() - (PADDING * 2.0f + TITLE_SIZE + PADDING) - PADDING - 10.0f;
    }

    private float listWidth() {
        return PANEL_WIDTH - PADDING * 2.0f;
    }

    private float maxScroll() {
        return Math.max(0.0f, contentHeight - listHeight());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        ensureFont();

        float listX = panelLeft() + PADDING;
        this.contentHeight = panel.measure(listX, listTop() - scroll, listWidth(), mouseX, mouseY);
        this.scroll = Math.max(0.0f, Math.min(maxScroll(), scroll));

        float left = panelLeft();
        float top = panelTop();
        float height = panelHeight();
        RenderUtil.roundedRect(extractor, left, top, PANEL_WIDTH, height, RADIUS, PANEL_COLOR);
        TextRenderUtil.drawString(extractor, font, "Spotify API",
                left + PADDING, top + PADDING, TITLE_SIZE, TEXT_COLOR);

        ScreenRectangle clip = new ScreenRectangle(
                Math.round(listX), Math.round(listTop()),
                Math.round(listWidth()), Math.round(listHeight()));
        panel.draw(extractor, listX, listTop() - scroll, listWidth(), 1.0f, mouseX, mouseY, clip, true);

        String status = SpotifyService.INSTANCE.isAuthorized() ? "Authorized" : "Not authorized";
        TextRenderUtil.drawString(extractor, font, status,
                left + PADDING, top + height - PADDING - 8.0f, 8.0f, MUTED_COLOR);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        AerialClickGui.focusTextProperty(null);
        float listX = panelLeft() + PADDING;
        if (panel.mouseClicked(listX, listTop() - scroll, listWidth(), event.x(), event.y(), event.button())) {
            return true;
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        panel.mouseReleased(event.button());
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0.0f, Math.min(maxScroll(), scroll - (float) verticalAmount * 12.0f));
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        StringProperty focused = AerialClickGui.getFocusedTextProperty();
        if (focused != null && event.isAllowedChatCharacter()) {
            focused.insertChar((char) event.codepoint());
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent keyEvent) {
        StringProperty focused = AerialClickGui.getFocusedTextProperty();
        if (focused != null) {
            if (keyEvent.isPaste()) {
                focused.insert(Minecraft.getInstance().keyboardHandler.getClipboard());
                return true;
            }
            if (keyEvent.isCopy()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(focused.getValue());
                return true;
            }
            if (keyEvent.isCut()) {
                Minecraft.getInstance().keyboardHandler.setClipboard(focused.getValue());
                focused.setValue("");
                focused.cursorToEnd();
                return true;
            }
            int key = keyEvent.key();
            if (key == GLFW.GLFW_KEY_DELETE) {
                focused.delete();
                return true;
            }
            if (key == GLFW.GLFW_KEY_HOME) {
                focused.moveCursor(-focused.getValue().length());
                return true;
            }
            if (key == GLFW.GLFW_KEY_END) {
                focused.cursorToEnd();
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                focused.backspace();
                return true;
            } else if (key == GLFW.GLFW_KEY_LEFT) {
                focused.moveCursor(-1);
                return true;
            } else if (key == GLFW.GLFW_KEY_RIGHT) {
                focused.moveCursor(1);
                return true;
            } else if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER) {
                AerialClickGui.focusTextProperty(null);
                return true;
            }
            return true;
        }
        if (keyEvent.key() == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public void onClose() {
        AerialClickGui.focusTextProperty(null);
        Minecraft.getInstance().setScreenAndShow(previousScreen);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
