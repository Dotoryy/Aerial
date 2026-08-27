package cc.aerial.client.screen.title;

import cc.aerial.client.accountmanager.gui.AltScreen;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.screen.server.AerialServerScreen;
import cc.aerial.client.theme.Theme;
import cc.aerial.client.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.stream.Stream;

public final class AerialTitleScreen extends Screen {
    private static final float WORDMARK_SIZE = 44.0f;
    private static final float FOOTER_SIZE = 8.0f;
    private static final float ACCOUNT_SIZE = 8.5f;

    private static final float STACK_WIDTH = 176.0f;
    private static final float ROW_GAP = 5.0f;
    private static final float COMPACT_GAP = 5.0f;

    private static final float MENU_TOP_GAP = 13.0f;
    private static final float ACCOUNT_TOP_GAP = 12.0f;

    private static final String NOTICE = "Copyright Mojang AB. Do not distribute!";
    private static final float NOTICE_SIZE = 8.0f;
    private static final float NOTICE_MARGIN = 8.0f;
    private static final int NOTICE_COLOR = 0xFF8A8C96;

    private static final float BAND_PADDING = 34.0f;
    private static final int BAND_CORE = 0xA605050A;
    private static final int BAND_EDGE = 0x0005050A;

    private static final int FOOTER_COLOR = 0xFF74767F;
    private static final int ACCOUNT_COLOR = 0xFFC8CAD4;

    private final List<MenuButton> primary;
    private final List<MenuButton> compact;
    private final List<MenuButton> buttons;

    private AerialFont font;
    private AerialFont boldFont;

    public AerialTitleScreen() {
        super(Component.literal("Aerial"));
        Minecraft mc = Minecraft.getInstance();
        this.primary = List.of(
                new MenuButton("Singleplayer", MenuButton.Weight.PRIMARY,
                        () -> mc.setScreenAndShow(new SelectWorldScreen(this))),
                new MenuButton("Multiplayer", MenuButton.Weight.PRIMARY,
                        () -> mc.setScreenAndShow(new AerialServerScreen(this))));
        this.compact = List.of(
                new MenuButton("Alt", MenuButton.Weight.COMPACT,
                        () -> mc.setScreenAndShow(new AltScreen(this))),
                new MenuButton("Options", MenuButton.Weight.COMPACT,
                        () -> mc.setScreenAndShow(new OptionsScreen(this, mc.options, false))),
                new MenuButton("Quit", MenuButton.Weight.COMPACT, mc::stop));

        this.buttons = Stream.concat(primary.stream(), compact.stream()).toList();
    }

    private void ensureFonts() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
            boldFont = AerialFont.createFromResource("OpalProductSansBold.ttf");
        }
    }

    private float stackHeight() {
        float menu = 0.0f;
        for (MenuButton button : primary) {
            menu += button.getHeight() + ROW_GAP;
        }
        menu += compact.get(0).getHeight();
        return WORDMARK_SIZE + MENU_TOP_GAP + menu + ACCOUNT_TOP_GAP + ACCOUNT_SIZE;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick) {
        ensureFonts();
        TitleBackground.draw(extractor, width, height);

        Theme theme = ThemeManager.getTheme();
        int accentLeft = theme.getAccentColor(0, 50).getRGB() | 0xFF000000;
        int accentRight = theme.getAccentColor(0, 0).getRGB() | 0xFF000000;

        float total = stackHeight();
        float top = (height - total) * 0.5f;
        float centerX = width * 0.5f;
        float stackLeft = centerX - STACK_WIDTH * 0.5f;

        drawBand(extractor, top - BAND_PADDING, total + BAND_PADDING * 2.0f);

        float wordmarkWidth = boldFont.stringWidth("aerial", WORDMARK_SIZE);
        TextRenderUtil.drawGradientString(extractor, boldFont, "aerial",
                centerX - wordmarkWidth * 0.5f, top, WORDMARK_SIZE, accentLeft, accentRight);

        float y = top + WORDMARK_SIZE + MENU_TOP_GAP;
        for (MenuButton button : primary) {
            button.setBounds(stackLeft, y, STACK_WIDTH);
            button.draw(extractor, font, mouseX, mouseY);
            y += button.getHeight() + ROW_GAP;
        }

        float compactWidth = (STACK_WIDTH - COMPACT_GAP * (compact.size() - 1)) / compact.size();
        float compactX = stackLeft;
        for (MenuButton button : compact) {
            button.setBounds(compactX, y, compactWidth);
            button.draw(extractor, font, mouseX, mouseY);
            compactX += compactWidth + COMPACT_GAP;
        }
        y += compact.get(0).getHeight();

        drawAccount(extractor, centerX, y + ACCOUNT_TOP_GAP, accentRight);
        drawFooter(extractor, centerX);
        TextRenderUtil.drawString(extractor, font, NOTICE,
                width - NOTICE_MARGIN - font.stringWidth(NOTICE, NOTICE_SIZE), NOTICE_MARGIN,
                NOTICE_SIZE, NOTICE_COLOR);

        super.extractRenderState(extractor, mouseX, mouseY, partialTick);
    }

    private void drawBand(GuiGraphicsExtractor extractor, float y, float bandHeight) {
        float half = bandHeight * 0.5f;
        RenderUtil.roundedRectGradient(extractor, 0.0f, y, width, half, 0.5f,
                BAND_EDGE, BAND_CORE, true, null);
        RenderUtil.roundedRectGradient(extractor, 0.0f, y + half, width, half, 0.5f,
                BAND_CORE, BAND_EDGE, true, null);
    }

    private void drawAccount(GuiGraphicsExtractor extractor, float centerX, float y, int accent) {
        String name = Minecraft.getInstance().getUser().getName();
        float dot = 4.0f;
        float gap = 5.0f;
        float nameWidth = font.stringWidth(name, ACCOUNT_SIZE);

        float left = centerX - (dot + gap + nameWidth) * 0.5f;
        RenderUtil.roundedRect(extractor, left, y + (ACCOUNT_SIZE - dot) * 0.5f,
                dot, dot, dot * 0.5f, accent);
        TextRenderUtil.drawString(extractor, font, name,
                left + dot + gap, y, ACCOUNT_SIZE, ACCOUNT_COLOR);
    }

    private void drawFooter(GuiGraphicsExtractor extractor, float centerX) {
        String text = "aerialclient.cc   -   " + Minecraft.getInstance().getFps() + " fps";
        TextRenderUtil.drawString(extractor, font, text,
                centerX - font.stringWidth(text, FOOTER_SIZE) * 0.5f, height - 14.0f,
                FOOTER_SIZE, FOOTER_COLOR);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubled) {
        if (event.button() == 0) {
            for (MenuButton button : buttons) {
                if (button.isInside(event.x(), event.y())) {
                    Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                    net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    button.click();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubled);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    protected void extractMenuBackground(GuiGraphicsExtractor extractor) {
    }
}
