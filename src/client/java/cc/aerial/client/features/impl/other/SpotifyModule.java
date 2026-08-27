package cc.aerial.client.features.impl.other;

import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.other.spotify.LyricLine;
import cc.aerial.client.features.impl.other.spotify.SpotifyService;
import cc.aerial.client.features.impl.visual.InterfaceModule;
import cc.aerial.client.property.ActionProperty;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.GroupProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.property.Property;
import cc.aerial.client.property.StringProperty;
import cc.aerial.client.render.BlurConsumer;
import cc.aerial.client.render.AerialBloomFilter;
import cc.aerial.client.render.AerialBlur;
import cc.aerial.client.render.AerialImage;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.screen.SpotifyApiScreen;
import cc.aerial.client.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.ChatScreen;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class SpotifyModule extends Module {
    public static final SpotifyModule INSTANCE = new SpotifyModule();

    enum MusicService {
        CIDER("Cider"), SPOTIFY("Spotify");

        private final String label;

        MusicService(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    enum LyricsProvider {
        LRCLIB("LRCLIB"), TTML("TTML (Apple-style)"), ENHANCED_LRC("Enhanced LRC (word-timed)"), CUSTOM("Custom");

        private final String label;

        LyricsProvider(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final ModeProperty<MusicService> musicService = new ModeProperty<>("Music Service", MusicService.CIDER);

    private final StringProperty clientId = new StringProperty("Client ID", "");
    private final StringProperty clientSecret = new StringProperty("Client Secret", "");
    private final GroupProperty credentialsGroup = new GroupProperty("Spotify Credentials", clientId, clientSecret)
            .hideIf(() -> true);
    private final ActionProperty apiSettings = new ActionProperty("API Settings",
            () -> Minecraft.getInstance().setScreenAndShow(
                    new SpotifyApiScreen(Minecraft.getInstance().gui.screen(), clientId, clientSecret)));

    private final NumberProperty refreshTicks = new NumberProperty("Refresh Ticks", 10, 1, 20, 1);
    private final BooleanProperty showLyrics = new BooleanProperty("Show Lyrics", true);
    private final NumberProperty lyricLines = new NumberProperty("Lyric Lines", 3, 1, 8, 1)
            .hideIf(() -> !showLyrics.getValue());
    private final ModeProperty<LyricsProvider> lyricsProvider = new ModeProperty<>("Lyrics Provider", LyricsProvider.LRCLIB)
            .hideIf(() -> !showLyrics.getValue());
    private final StringProperty lyricsEndpointUrl = new StringProperty("Lyrics Endpoint URL", "")
            .hideIf(() -> !showLyrics.getValue() || lyricsProvider.getValue() == LyricsProvider.LRCLIB);
    private final StringProperty lyricsEndpointHeader = new StringProperty("Lyrics Endpoint Header", "")
            .hideIf(() -> !showLyrics.getValue() || lyricsProvider.getValue() == LyricsProvider.LRCLIB);
    private final BooleanProperty karaokeFill = new BooleanProperty("Karaoke Fill", true)
            .hideIf(() -> !showLyrics.getValue());
    private final NumberProperty karaokeSpeed = new NumberProperty("Karaoke Speed", 1.25, 0.25, 3.0, 0.05)
            .hideIf(() -> !showLyrics.getValue() || !karaokeFill.getValue());
    private final BooleanProperty debug = new BooleanProperty("Debug", false);

    private final NumberProperty xPos = new NumberProperty("X", 20.0, -10000.0, 10000.0, 0.01).hideIf(() -> true);
    private final NumberProperty yPos = new NumberProperty("Y", 20.0, -10000.0, 10000.0, 0.01).hideIf(() -> true);

    private SpotifyModule() {
        super("Spotify", "Shows the currently playing track", ModuleCategory.UTILITY);
        addProperties(musicService, apiSettings, credentialsGroup, refreshTicks,
                showLyrics, lyricLines, lyricsProvider, lyricsEndpointUrl, lyricsEndpointHeader,
                karaokeFill, karaokeSpeed, debug, xPos, yPos);
    }

    @Override
    protected void onEnable() {
        SpotifyService.INSTANCE.ensureAuthorized(clientId.getValue(), clientSecret.getValue());
        SpotifyService.INSTANCE.startPoller(() -> new SpotifyService.PollConfig(
                musicService.getValue().toString(),
                clientId.getValue(), clientSecret.getValue(),
                lyricsProvider.getValue().toString(), lyricsEndpointUrl.getValue(), lyricsEndpointHeader.getValue(),
                (int) refreshTicks.getValue().doubleValue(), debug.getValue()));
    }

    @Override
    protected void onDisable() {
        SpotifyService.INSTANCE.stopPoller();
    }

    private static final float PANEL_WIDTH = 168.0f;
    private static final float PADDING = 8.0f;
    private static final float CORNER_RADIUS = 8.0f;
    private static final float ART_SIZE = 32.0f;
    private static final float ART_GAP = 8.0f;
    private static final float LYRICS_GAP = 8.0f;
    private static final float LYRIC_LINE_HEIGHT = 11.0f;

    private static final float TITLE_ARTIST_GAP = 2.0f;
    private static final float ARTIST_BAR_GAP = 4.0f;
    private static final float BAR_TIME_GAP = 2.0f;
    private static final float BAR_HEIGHT = 2.0f;

    private static final int BACKGROUND_COLOR = 0x80090909;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFF808080;
    private static final int TRACK_COLOR = 0x1CFFFFFF;
    private static final int DIM_LYRIC_COLOR = 0x80B4B4B4;

    private static final float TITLE_SIZE = 9.0f;
    private static final float ARTIST_SIZE = 7.0f;
    private static final float TIME_SIZE = 5.5f;
    private static final float ACTIVE_LYRIC_SIZE = 8.5f;
    private static final float LYRIC_SIZE = 7.5f;

    private static float textColumnHeight() {
        return TITLE_SIZE + TITLE_ARTIST_GAP + ARTIST_SIZE + ARTIST_BAR_GAP + BAR_HEIGHT + BAR_TIME_GAP + TIME_SIZE;
    }

    private static AerialFont font;

    private static void ensureFontLoaded() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
        }
    }

    private enum Script { LATIN, HAN, KANA, HANGUL, OTHER }

    private static Script scriptOf(char c) {
        if (c >= 0x20 && c <= 0x7E) {
            return Script.LATIN;
        }
        Character.UnicodeScript script = Character.UnicodeScript.of(c);
        if (script == Character.UnicodeScript.HANGUL) {
            return Script.HANGUL;
        }
        if (script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA) {
            return Script.KANA;
        }
        if (script == Character.UnicodeScript.HAN) {
            return Script.HAN;
        }
        return Script.OTHER;
    }

    private static final java.util.EnumMap<Script, AerialFont> scriptFonts = new java.util.EnumMap<>(Script.class);

    private static AerialFont scriptFont(Script script, String run) {
        String resource = switch (script) {
            case HAN -> "HarmonyOSSansSCMedium.ttf";
            case KANA -> "LINESeedJPRegular.ttf";
            case HANGUL -> "LINESeedKRRegular.ttf";
            default -> null;
        };
        if (resource == null) {
            return null;
        }
        AerialFont scriptFont = scriptFonts.get(script);
        if (scriptFont == null) {
            scriptFont = AerialFont.createDynamicFromResource(resource);
            scriptFonts.put(script, scriptFont);
        }

        scriptFont.ensureGlyphs(run);
        return scriptFont;
    }

    private record Run(String text, @Nullable AerialFont font, Script script) {
    }

    private static String lastSplitText;
    private static List<Run> lastSplitRuns;

    private static List<Run> splitRuns(String text) {
        if (text.equals(lastSplitText)) {
            return lastSplitRuns;
        }
        List<Run> runs = buildRuns(text);
        lastSplitText = text;
        lastSplitRuns = runs;
        return runs;
    }

    private static List<Run> buildRuns(String text) {
        List<Run> runs = new ArrayList<>();
        int start = 0;
        Script currentScript = null;
        for (int i = 0; i <= text.length(); i++) {
            Script script = i < text.length() ? scriptOf(text.charAt(i)) : null;
            if (script != currentScript) {
                if (currentScript != null && i > start) {
                    String slice = text.substring(start, i);
                    AerialFont runFont = switch (currentScript) {
                        case LATIN -> font;
                        case HAN, KANA, HANGUL -> {
                            AerialFont found = scriptFont(currentScript, slice);
                            yield found != null ? found : font;
                        }
                        case OTHER -> null;
                    };
                    runs.add(new Run(slice, runFont, currentScript));
                }
                start = i;
                currentScript = script;
            }
        }
        return runs;
    }

    private static final java.util.Map<Script, Float> scriptSizeCorrection = new java.util.HashMap<>();
    private static final float SIZE_CORRECTION_REFERENCE = 32.0f;

    private static float correctedSize(Run run, float size) {
        if (run.font() == null || run.font() == font) {
            return size;
        }
        Float ratio = scriptSizeCorrection.get(run.script());
        if (ratio == null) {
            float baseHeight = font.height(SIZE_CORRECTION_REFERENCE);
            float scriptHeight = run.font().height(SIZE_CORRECTION_REFERENCE);
            ratio = scriptHeight > 0.0f ? baseHeight / scriptHeight : 1.0f;
            scriptSizeCorrection.put(run.script(), ratio);
        }
        return size * ratio;
    }

    private static float vanillaScale(float size) {
        return size / Minecraft.getInstance().font.lineHeight;
    }

    private static float textWidth(String text, float size) {
        float width = 0.0f;
        for (Run run : splitRuns(text)) {
            width += run.font() != null ? run.font().stringWidth(run.text(), correctedSize(run, size))
                    : Minecraft.getInstance().font.width(run.text()) * vanillaScale(size);
        }
        return width;
    }

    private static float drawText(GuiGraphicsExtractor extractor, String text, float x, float y, float size, int color) {
        return drawText(extractor, text, x, y, size, color, null);
    }

    private static float drawText(GuiGraphicsExtractor extractor, String text, float x, float y, float size, int color,
                                  @Nullable ScreenRectangle clip) {
        float cursor = x;
        for (Run run : splitRuns(text)) {
            if (run.font() != null) {
                cursor += TextRenderUtil.drawString(extractor, run.font(), run.text(), cursor, y, correctedSize(run, size), color, clip);
            } else {
                float width = Minecraft.getInstance().font.width(run.text()) * vanillaScale(size);
                drawVanillaText(extractor, run.text(), cursor, y, size, color, clip);
                cursor += width;
            }
        }
        return cursor - x;
    }

    private static void drawTextFill(GuiGraphicsExtractor extractor, String text, float x, float y, float size,
                                     int colorLeft, int colorRight, @Nullable ScreenRectangle clip) {
        float cursor = x;
        for (Run run : splitRuns(text)) {
            if (run.font() != null) {
                cursor += TextRenderUtil.drawGradientString(extractor, run.font(), run.text(), cursor, y, correctedSize(run, size), colorLeft, colorRight, clip);
            } else {
                float width = Minecraft.getInstance().font.width(run.text()) * vanillaScale(size);
                drawVanillaText(extractor, run.text(), cursor, y, size, colorLeft, clip);
                cursor += width;
            }
        }
    }

    private static ScreenRectangle clampClip(GuiGraphicsExtractor extractor, int rawX, int rawY, int rawWidth, int rawHeight) {
        int x0 = Math.max(0, rawX);
        int y0 = Math.max(0, rawY);
        int x1 = Math.max(x0, Math.min(extractor.guiWidth(), rawX + rawWidth));
        int y1 = Math.max(y0, Math.min(extractor.guiHeight(), rawY + rawHeight));
        return new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);
    }

    private static void drawVanillaText(GuiGraphicsExtractor extractor, String text, float x, float y, float size,
                                        int color, @Nullable ScreenRectangle clip) {
        boolean clipping = clip != null;
        if (clipping) {
            int x0 = Math.max(0, Math.min(extractor.guiWidth(), clip.left()));
            int y0 = Math.max(0, Math.min(extractor.guiHeight(), clip.top()));
            int x1 = Math.max(x0, Math.min(extractor.guiWidth(), clip.right()));
            int y1 = Math.max(y0, Math.min(extractor.guiHeight(), clip.bottom()));
            extractor.enableScissor(x0, y0, x1, y1);
        }
        float scale = vanillaScale(size);
        extractor.pose().pushMatrix();
        extractor.pose().translate(x, y);
        extractor.pose().scale(scale, scale);
        extractor.text(Minecraft.getInstance().font, text, 0, 0, color);
        extractor.pose().popMatrix();
        if (clipping) {
            extractor.disableScissor();
        }
    }

    private float x = 20f, y = 20f;
    private boolean dragging;
    private float dragOffsetX, dragOffsetY;
    private float currentHeight = 90f;

    private void handleDragging(float boxHeight) {
        if (!(Minecraft.getInstance().gui.screen() instanceof ChatScreen)) {
            dragging = false;
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float mouseX = (float) mc.mouseHandler.getScaledXPos(mc.getWindow());
        float mouseY = (float) mc.mouseHandler.getScaledYPos(mc.getWindow());
        boolean leftPressed = org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().handle(),
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

        if (leftPressed) {
            if (!dragging && mouseX >= x && mouseY >= y && mouseX < x + PANEL_WIDTH && mouseY < y + boxHeight) {
                dragging = true;
                dragOffsetX = mouseX - x;
                dragOffsetY = mouseY - y;
            }
            if (dragging) {
                x = mouseX - dragOffsetX;
                y = mouseY - dragOffsetY;
                xPos.setValue((double) x);
                yPos.setValue((double) y);
            }
        } else {
            dragging = false;
        }
    }

    private float targetHeight;
    private float animatedHeight;
    private float heightVelocity;
    private boolean firstFrame = true;
    private long lastFrameTimeMs = System.currentTimeMillis();
    private static final float SPRING_STIFFNESS = 25.0f;
    private static final float SPRING_DAMPING = 6.5f;

    private float marqueeOffset;

    private int fillLineIndex = -1;
    private float fillWidth;
    private long lastFillFrameTimeMs = System.currentTimeMillis();
    private int lastFillTimeMs;
    private long fillBoostUntilMs;
    private float lastFillTarget;
    private static final int FILL_SEEK_BACK_MS = 600;
    private static final int FILL_BOOST_MS = 260;
    private static final float FILL_BOOST_MULTIPLIER = 8.0f;

    private int currentLineIndex = -1;
    private long lastLineSwitchTimeMs;

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        AerialBloomFilter.begin(BlurConsumer.SPOTIFY);
        try {
            onRender2DBody(event);
        } finally {
            AerialBloomFilter.end();
        }
    }

    private void onRender2DBody(Render2DEvent event) {
        ensureFontLoaded();
        if (!dragging) {
            x = xPos.getValue().floatValue();
            y = yPos.getValue().floatValue();
        }

        float maxX = Math.max(0.0f, event.extractor().guiWidth() - PANEL_WIDTH);
        float maxY = Math.max(0.0f, event.extractor().guiHeight() - animatedHeight);
        float clampedX = Math.max(0.0f, Math.min(maxX, x));
        float clampedY = Math.max(0.0f, Math.min(maxY, y));
        if (clampedX != x || clampedY != y) {
            x = clampedX;
            y = clampedY;
            xPos.setValue((double) x);
            yPos.setValue((double) y);
        }

        SpotifyService service = SpotifyService.INSTANCE;
        int progressMs = service.estimatedProgressMs();
        updateCurrentLine(service, progressMs);

        boolean lyricsOn = showLyrics.getValue() && service.lyricsAvailable;
        int lineWindow = Math.max(1, (int) lyricLines.getValue().doubleValue());
        int lyricRows = lyricsOn ? Math.min(Math.max(1, lineCount(service)), lineWindow) : 0;

        float artRowHeight = Math.max(ART_SIZE, textColumnHeight());
        float contentHeight = PADDING * 2.0f + artRowHeight
                + (lyricsOn ? LYRICS_GAP + lyricRows * LYRIC_LINE_HEIGHT : 0.0f);
        stepSpringHeight(contentHeight);
        handleDragging(animatedHeight);

        GuiGraphicsExtractor extractor = event.extractor();
        AerialBlur.drawGlass(extractor, BlurConsumer.SPOTIFY, x, y, PANEL_WIDTH, animatedHeight,
                CORNER_RADIUS, BACKGROUND_COLOR, 1.0f, null);

        Theme theme = InterfaceModule.INSTANCE.getTheme();
        int accentLeft = theme.getAccentColor(0, 0).getRGB() | 0xFF000000;
        int accentRight = theme.getAccentColor(0, 50).getRGB() | 0xFF000000;

        float artX = x + PADDING;
        float artY = y + PADDING;
        AerialImage artwork = service.artwork;
        if (artwork != null) {
            RenderUtil.image(extractor, artwork, artX, artY, ART_SIZE, ART_SIZE);
        } else {
            RenderUtil.roundedRect(extractor, artX, artY, ART_SIZE, ART_SIZE, 6.0f, 0x33FFFFFF);
        }

        float textX = artX + ART_SIZE + ART_GAP;
        float textColumnWidth = PANEL_WIDTH - PADDING - (textX - x);
        float columnTop = artY + (artRowHeight - textColumnHeight()) * 0.5f;

        float titleY = columnTop;
        float artistY = titleY + TITLE_SIZE + TITLE_ARTIST_GAP;
        float barY = artistY + ARTIST_SIZE + ARTIST_BAR_GAP;
        float timeY = barY + BAR_HEIGHT + BAR_TIME_GAP;

        drawMarqueeText(extractor, service.song, textX, titleY, textColumnWidth, TITLE_SIZE, WHITE);
        drawMarqueeText(extractor, service.artist, textX, artistY, textColumnWidth, ARTIST_SIZE, MUTED_COLOR);

        RenderUtil.roundedRect(extractor, textX, barY, textColumnWidth, BAR_HEIGHT, BAR_HEIGHT / 2.0f, TRACK_COLOR);
        float progressFrac = service.durationMs > 0 ? Math.min(1.0f, progressMs / (float) service.durationMs) : 0.0f;
        float fillW = textColumnWidth * progressFrac;
        if (fillW > 0.5f) {
            RenderUtil.roundedRectGradient(extractor, textX, barY, Math.max(BAR_HEIGHT, fillW), BAR_HEIGHT,
                    BAR_HEIGHT / 2.0f, accentLeft, accentRight, false, null);
        }

        String currentTime = formatTime(progressMs);
        String totalTime = formatTime(service.durationMs);
        float totalWidth = textWidth(totalTime, TIME_SIZE);
        drawText(extractor, currentTime, textX, timeY, TIME_SIZE, MUTED_COLOR);
        drawText(extractor, totalTime, textX + textColumnWidth - totalWidth, timeY, TIME_SIZE, MUTED_COLOR);

        if (lyricsOn) {
            float lyricsWidth = PANEL_WIDTH - PADDING * 2.0f;
            drawLyrics(extractor, service, progressMs, x + PADDING, artY + artRowHeight + LYRICS_GAP,
                    lyricsWidth, lyricRows, accentLeft, accentRight);
        }

        if (dragging) {
            drawDragOutline(extractor);
        }
    }

    private void drawDragOutline(GuiGraphicsExtractor extractor) {
        Theme theme = InterfaceModule.INSTANCE.getTheme();
        int color = theme.getAccentColor(0, 0).getRGB() | 0xCC000000;
        RenderUtil.flatRect(extractor, x - 0.5f, y - 0.5f, PANEL_WIDTH + 1.0f, 1.0f, color);
        RenderUtil.flatRect(extractor, x - 0.5f, y + animatedHeight - 0.5f, PANEL_WIDTH + 1.0f, 1.0f, color);
        RenderUtil.flatRect(extractor, x - 0.5f, y - 0.5f, 1.0f, animatedHeight + 1.0f, color);
        RenderUtil.flatRect(extractor, x + PANEL_WIDTH - 0.5f, y - 0.5f, 1.0f, animatedHeight + 1.0f, color);
    }

    private void stepSpringHeight(float wanted) {
        long now = System.currentTimeMillis();
        if (firstFrame) {
            targetHeight = animatedHeight = wanted;
            currentHeight = wanted;
            firstFrame = false;
            lastFrameTimeMs = now;
            return;
        }
        if (Math.abs(wanted - targetHeight) > 0.5f) {
            targetHeight = wanted;
        }
        float deltaSeconds = Math.min(0.1f, (now - lastFrameTimeMs) / 1000.0f);
        lastFrameTimeMs = now;
        float acceleration = SPRING_STIFFNESS * (targetHeight - animatedHeight) - SPRING_DAMPING * heightVelocity;
        heightVelocity += acceleration * deltaSeconds;
        animatedHeight += heightVelocity * deltaSeconds;
        currentHeight = animatedHeight;
    }

    private void drawMarqueeText(GuiGraphicsExtractor extractor, String text, float textX, float textY, float maxWidth, float size, int color) {
        float width = textWidth(text, size);
        if (width <= maxWidth) {
            drawText(extractor, text, textX, textY, size, color);
            return;
        }
        ScreenRectangle clip = clampClip(extractor, Math.round(textX), Math.round(textY - 1.0f),
                Math.round(maxWidth), Math.round(size + 4.0f));
        drawText(extractor, text, textX - marqueeOffset, textY, size, color, clip);

        marqueeOffset += 0.4f;
        if (marqueeOffset > width + 20.0f) {
            marqueeOffset = -20.0f;
        }
    }

    private static String formatTime(int ms) {
        int totalSeconds = Math.max(0, ms) / 1000;
        return String.format("%d:%02d", totalSeconds / 60, totalSeconds % 60);
    }

    private int lineCount(SpotifyService service) {
        return service.syncedLyrics ? service.syncedLines.size() : service.plainLines.size();
    }

    private String lineText(SpotifyService service, int index) {
        if (service.syncedLyrics) {
            return index >= 0 && index < service.syncedLines.size() ? service.syncedLines.get(index).text : "";
        }
        return index >= 0 && index < service.plainLines.size() ? service.plainLines.get(index) : "";
    }

    private int lineIndexFor(SpotifyService service, int positionMs) {
        if (!service.lyricsAvailable) {
            return -1;
        }
        if (service.syncedLyrics && !service.syncedLines.isEmpty()) {
            List<LyricLine> lines = service.syncedLines;
            if (positionMs < lines.get(0).startTime) {
                return -1;
            }
            int lo = 0, hi = lines.size() - 1, best = -1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (lines.get(mid).startTime <= positionMs) {
                    best = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }
            return best;
        }
        if (!service.syncedLyrics && !service.plainLines.isEmpty() && service.durationMs > 0) {
            int index = (int) Math.floor((double) positionMs / service.durationMs * service.plainLines.size());
            return Math.max(0, Math.min(service.plainLines.size() - 1, index));
        }
        return -1;
    }

    private void updateCurrentLine(SpotifyService service, int positionMs) {
        if (!service.lyricsAvailable) {
            currentLineIndex = -1;
            return;
        }
        int index = lineIndexFor(service, positionMs);
        if (index < 0 && currentLineIndex >= 0 && service.syncedLyrics && !service.syncedLines.isEmpty()) {
            if (positionMs >= service.syncedLines.get(0).startTime) {
                return;
            }
        }
        boolean settleGuard = currentLineIndex < 0 || index < 0 || index >= currentLineIndex
                || System.currentTimeMillis() - lastLineSwitchTimeMs >= 120L;
        if (settleGuard && index != currentLineIndex) {
            long now = System.currentTimeMillis();
            currentLineIndex = index;
            lastLineSwitchTimeMs = now;
            fillLineIndex = -1;
            fillWidth = 0.0f;
            fillBoostUntilMs = now + FILL_BOOST_MS;
        }
    }

    private void drawLyrics(GuiGraphicsExtractor extractor, SpotifyService service, int progressMs,
                            float lyricsX, float lyricsY, float width, int rows, int accentLeft, int accentRight) {
        int total = lineCount(service);
        int center = Math.max(0, (rows - 1) / 2);
        int start = currentLineIndex >= 0 ? clampInt(currentLineIndex - center, 0, Math.max(0, total - rows)) : 0;

        ScreenRectangle clip = clampClip(extractor, Math.round(lyricsX), Math.round(lyricsY),
                Math.round(width), Math.round(rows * LYRIC_LINE_HEIGHT + 2.0f));

        float rowY = lyricsY;
        for (int i = 0; i < rows && start + i < total; i++) {
            int lineIndex = start + i;
            boolean active = lineIndex == currentLineIndex;
            String text = lineText(service, lineIndex);
            if (active) {
                drawActiveLyricLine(extractor, service, text, lineIndex, progressMs, lyricsX, rowY, width, clip, accentLeft, accentRight);
            } else {
                drawText(extractor, text, lyricsX, rowY, LYRIC_SIZE, DIM_LYRIC_COLOR, clip);
            }
            rowY += LYRIC_LINE_HEIGHT;
        }
    }

    private void drawActiveLyricLine(GuiGraphicsExtractor extractor, SpotifyService service, String text, int lineIndex,
                                     int progressMs, float rowX, float rowY, float width, ScreenRectangle rowsClip,
                                     int accentLeft, int accentRight) {
        String shown = elideToWidth(text, width);
        float lineWidth = textWidth(shown, ACTIVE_LYRIC_SIZE);

        if (!karaokeFill.getValue()) {
            drawText(extractor, shown, rowX, rowY, ACTIVE_LYRIC_SIZE, WHITE, rowsClip);
            return;
        }

        LyricLine line = service.syncedLyrics && lineIndex >= 0 && lineIndex < service.syncedLines.size()
                ? service.syncedLines.get(lineIndex) : null;
        float filledOfFull = filledWidth(line, lineIndex, progressMs, lineWidth);
        float animated = animateFill(lineIndex, filledOfFull, lineWidth, progressMs);

        drawText(extractor, shown, rowX, rowY, ACTIVE_LYRIC_SIZE, DIM_LYRIC_COLOR, rowsClip);
        if (animated > 0.5f) {
            ScreenRectangle fillClip = clampClip(extractor, Math.round(rowX), Math.round(rowY - 1.0f),
                    Math.round(Math.min(animated, width)), Math.round(ACTIVE_LYRIC_SIZE + 4.0f));
            drawTextFill(extractor, shown, rowX, rowY, ACTIVE_LYRIC_SIZE, accentLeft, accentRight, fillClip);
        }
    }

    private String elideToWidth(String text, float maxWidth) {
        if (textWidth(text, ACTIVE_LYRIC_SIZE) <= maxWidth) {
            return text;
        }
        String ellipsis = "...";
        float ellipsisWidth = textWidth(ellipsis, ACTIVE_LYRIC_SIZE);
        int low = 0, high = text.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (textWidth(text.substring(0, mid), ACTIVE_LYRIC_SIZE) + ellipsisWidth <= maxWidth) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return text.substring(0, low).stripTrailing() + ellipsis;
    }

    private float filledWidth(LyricLine line, int lineIndex, int progressMs, float lineWidth) {
        if (line != null && !line.words.isEmpty()) {
            float filled = 0.0f;
            for (var word : line.words) {
                float wordWidth = lineWidth * wordShare(line, word);
                if (progressMs < word.endTime) {
                    if (progressMs > word.startTime) {
                        int span = Math.max(1, word.endTime - word.startTime);
                        filled += wordWidth * clamp01((float) (progressMs - word.startTime) / span);
                    }
                    break;
                }
                filled += wordWidth;
            }
            return Math.max(0.0f, Math.min(lineWidth, filled));
        }
        return lineWidth * lineProgress(lineIndex, progressMs);
    }

    private float wordShare(LyricLine line, cc.aerial.client.features.impl.other.spotify.LyricWord word) {
        int totalChars = 0;
        for (var w : line.words) {
            totalChars += Math.max(1, w.text.length());
        }
        return totalChars == 0 ? 0.0f : Math.max(1, word.text.length()) / (float) totalChars;
    }

    private float lineProgress(int lineIndex, int progressMs) {
        SpotifyService service = SpotifyService.INSTANCE;
        if (service.syncedLyrics && lineIndex >= 0 && lineIndex < service.syncedLines.size()) {
            int start = service.syncedLines.get(lineIndex).startTime;
            int end = lineIndex + 1 < service.syncedLines.size() ? service.syncedLines.get(lineIndex + 1).startTime : service.durationMs;
            if (end <= start) {
                return 1.0f;
            }
            return clamp01((float) (progressMs - start) / (end - start));
        }
        return -1.0f;
    }

    private float animateFill(int lineIndex, float target, float lineWidth, int progressMs) {
        long now = System.currentTimeMillis();
        if (lineIndex != fillLineIndex) {
            fillLineIndex = lineIndex;
            fillWidth = 0.0f;
            lastFillFrameTimeMs = now;
            lastFillTimeMs = progressMs;
            fillBoostUntilMs = now + FILL_BOOST_MS;
            lastFillTarget = target;
            return clampRange(target, 0.0f, lineWidth);
        }
        if (progressMs + FILL_SEEK_BACK_MS < lastFillTimeMs) {
            fillWidth = clampRange(target, 0.0f, lineWidth);
            lastFillFrameTimeMs = now;
            lastFillTimeMs = progressMs;
            fillBoostUntilMs = now + FILL_BOOST_MS;
            lastFillTarget = target;
            return fillWidth;
        }

        float deltaSeconds = Math.max(0.0f, (now - lastFillFrameTimeMs) / 1000.0f);
        lastFillFrameTimeMs = now;
        if (target - lastFillTarget > 6.0f) {
            fillBoostUntilMs = now + FILL_BOOST_MS;
        }
        lastFillTarget = target;

        float speed = fillSpeed(lineIndex, lineWidth) * (float) karaokeSpeed.getValue().doubleValue();
        if (now < fillBoostUntilMs) {
            speed *= FILL_BOOST_MULTIPLIER;
        }
        if (target > fillWidth) {
            float step = speed * deltaSeconds;
            if (target - fillWidth >= lineWidth * 0.25f || target - fillWidth <= 2.0f) {
                fillWidth = target;
            } else {
                fillWidth = Math.min(target, fillWidth + Math.max(0.75f, step));
            }
        }
        fillWidth = clampRange(fillWidth, 0.0f, lineWidth);
        lastFillTimeMs = progressMs;
        return fillWidth;
    }

    private float fillSpeed(int lineIndex, float lineWidth) {
        SpotifyService service = SpotifyService.INSTANCE;
        int start;
        int end;
        if (service.syncedLyrics && lineIndex >= 0 && lineIndex < service.syncedLines.size()) {
            start = service.syncedLines.get(lineIndex).startTime;
            end = lineIndex + 1 < service.syncedLines.size() ? service.syncedLines.get(lineIndex + 1).startTime : service.durationMs;
        } else {
            int total = Math.max(1, lineCount(service));
            start = (int) Math.round(service.durationMs * ((double) lineIndex / total));
            end = (int) Math.round(service.durationMs * ((double) (lineIndex + 1) / total));
        }
        int span = Math.max(60, end - start);
        return lineWidth * 1000.0f / span;
    }

    private static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    private static float clampRange(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
