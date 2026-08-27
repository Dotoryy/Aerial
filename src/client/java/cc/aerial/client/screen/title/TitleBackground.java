package cc.aerial.client.screen.title;

import cc.aerial.client.render.AerialImage;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.theme.ColorUtil;
import cc.aerial.client.theme.Theme;
import cc.aerial.client.theme.ThemeManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class TitleBackground {
    private static final int CURTAIN_COLUMNS = 240;

    private static final int CURTAIN_SLICES = 5;

    private static final int GLOW_RINGS = 20;
    private static final int STARS = 90;
    private static final int PARTICLES = 46;

    private static final int BASE_TOP = 0xFF090B12;
    private static final int BASE_BOTTOM = 0xFF05060A;
    private static final int VIGNETTE = 0x99000000;
    private static final float VIGNETTE_SPAN = 0.22f;

    private static List<Frame> frames;
    private static boolean fileChecked;
    private static long frameStart;
    private static int frameIndex;

    private TitleBackground() {
    }

    public static void draw(GuiGraphicsExtractor extractor, int width, int height) {
        ensureFileLoaded();
        if (frames != null && !frames.isEmpty()) {
            drawImage(extractor, width, height);
        } else {
            drawAurora(extractor, width, height);
        }
        drawVignette(extractor, width, height);
    }

    private static void drawAurora(GuiGraphicsExtractor extractor, int width, int height) {
        RenderUtil.flatRectGradient(extractor, 0.0f, 0.0f, width, height, BASE_TOP, BASE_BOTTOM, null);

        Theme theme = ThemeManager.getTheme();
        java.awt.Color first = theme.getAccentColor(0, 0);
        java.awt.Color second = theme.getAccentColor(0, 60);
        double time = System.currentTimeMillis() / 1000.0;

        drawGlows(extractor, width, height, time, first, second);
        drawStars(extractor, width, height, time);

        drawCurtain(extractor, width, height, time, 2.4, first, second, 0.62f, 0.05f, 0.78f);
        drawCurtain(extractor, width, height, time, 0.0, first, second, 1.0f, 0.0f, 1.0f);
        drawParticles(extractor, width, height, first);
    }

    private static void drawGlows(GuiGraphicsExtractor extractor, int width, int height, double time,
                                  java.awt.Color first, java.awt.Color second) {
        float shortSide = Math.min(width, height);
        drawGlow(extractor,
                width * (0.22f + 0.06f * (float) Math.sin(time * 0.13)),
                height * (0.30f + 0.05f * (float) Math.cos(time * 0.17)),
                shortSide * 0.85f, first, 74);

        drawGlow(extractor,
                width * (0.55f + 0.10f * (float) Math.sin(time * 0.07 + 4.2)),
                height * (1.02f + 0.04f * (float) Math.cos(time * 0.15 + 0.6)),
                shortSide * 1.25f, second, 52);
    }

    private static void drawGlow(GuiGraphicsExtractor extractor, float centerX, float centerY,
                                 float radius, java.awt.Color color, int peakAlpha) {
        int rgb = color.getRGB() & 0xFFFFFF;
        for (int i = GLOW_RINGS; i >= 1; i--) {
            float t = i / (float) GLOW_RINGS;
            float size = radius * t;

            int alpha = Math.max(1, Math.round(peakAlpha / (float) GLOW_RINGS * (1.0f - t) * 2.2f));
            RenderUtil.roundedRect(extractor, centerX - size * 0.5f, centerY - size * 0.5f,
                    size, size, size * 0.5f, (alpha << 24) | rgb);
        }
    }

    private static void drawCurtain(GuiGraphicsExtractor extractor, int width, int height, double time,
                                    double phase, java.awt.Color low, java.awt.Color high,
                                    float intensity, float drop, float scale) {
        for (int column = 0; column < CURTAIN_COLUMNS; column++) {
            int x0 = edge(column, CURTAIN_COLUMNS, width);
            int x1 = edge(column + 1, CURTAIN_COLUMNS, width);
            if (x1 <= x0) {
                continue;
            }

            float u = (column + 0.5f) / CURTAIN_COLUMNS;

            double edge = 0.74 + drop
                    + 0.055 * Math.sin(u * 5.1 + time * 0.33 + phase)
                    + 0.028 * Math.sin(u * 11.3 - time * 0.21 + phase * 1.7);
            double span = (0.34 + 0.10 * Math.sin(u * 3.7 - time * 0.27 + phase)) * scale;
            float bottom = (float) (edge * height);
            float top = (float) ((edge - span) * height);

            double fine = Math.sin(u * 47.0 + time * 0.70 + phase * 3.1);
            double cluster = Math.sin(u * 13.0 - time * 0.23 + phase);
            float ray = (float) Math.max(0.0, 0.35 + 0.65 * (fine * 0.5 + 0.5) * (cluster * 0.5 + 0.5));

            for (int slice = 0; slice < CURTAIN_SLICES; slice++) {
                float p0 = slice / (float) CURTAIN_SLICES;
                float p1 = (slice + 1) / (float) CURTAIN_SLICES;
                int y0 = Math.round(lerp(top, bottom, p0));
                int y1 = Math.round(lerp(top, bottom, p1));
                if (y1 <= y0) {
                    continue;
                }
                extractor.fillGradient(x0, y0, x1, y1,
                        curtainColor(p0, ray, low, high, intensity),
                        curtainColor(p1, ray, low, high, intensity));
            }
        }
    }

    private static int curtainColor(float p, float ray, java.awt.Color low, java.awt.Color high,
                                    float intensity) {
        float profile = (float) Math.pow(p, 2.0);
        if (p > 0.90f) {
            float fade = (1.0f - p) / 0.10f;
            profile *= fade * fade;
        }
        int rgb = ColorUtil.mixColors(low, high, 1.0f - p).getRGB() & 0xFFFFFF;
        int alpha = Math.min(255, Math.round(190 * profile * ray * intensity));
        return (alpha << 24) | rgb;
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static int edge(int index, int count, int total) {
        return Math.round(index * total / (float) count);
    }

    private static void drawStars(GuiGraphicsExtractor extractor, int width, int height, double time) {
        for (int i = 0; i < STARS; i++) {
            float seedX = fract(i * 0.7548776662f);
            float seedY = fract(i * 0.5698402909f);
            float seedPhase = fract(i * 0.3819660113f);

            float y = seedY * seedY * height * 0.72f;
            float twinkle = 0.35f + 0.65f * (float) Math.pow(
                    Math.sin(time * 0.9 + seedPhase * 6.283) * 0.5 + 0.5, 2.0);
            int alpha = Math.round(140 * twinkle * (0.4f + 0.6f * (1.0f - seedY)));
            float size = 0.6f + seedPhase * 0.9f;

            RenderUtil.roundedRect(extractor, seedX * width, y, size, size, size * 0.5f,
                    (alpha << 24) | 0xFFFFFF);
        }
    }

    private static void drawParticles(GuiGraphicsExtractor extractor, int width, int height,
                                      java.awt.Color accent) {
        double time = System.currentTimeMillis() / 1000.0;
        for (int i = 0; i < PARTICLES; i++) {
            float seedX = fract(i * 0.7548776662f);
            float seedY = fract(i * 0.5698402909f);
            float speed = 0.12f + seedX * 0.5f;
            float size = 0.7f + seedY * 1.6f;

            float x = seedX * width;
            float y = height - fract((float) (seedY + time * speed * 0.06)) * height;
            int alpha = Math.round(60 + 110 * seedY);

            RenderUtil.roundedRect(extractor, x, y, size, size, size * 0.5f,
                    (alpha << 24) | (accent.getRGB() & 0xFFFFFF));
        }
    }

    private static float fract(float value) {
        return value - (float) Math.floor(value);
    }

    private static void drawVignette(GuiGraphicsExtractor extractor, int width, int height) {
        float spanX = width * VIGNETTE_SPAN;
        float spanY = height * VIGNETTE_SPAN;
        RenderUtil.flatRectGradient(extractor, 0.0f, 0.0f, spanX, height, VIGNETTE, 0x00000000, null);
        RenderUtil.flatRectGradient(extractor, width - spanX, 0.0f, spanX, height, 0x00000000, VIGNETTE, null);

        int steps = 16;
        int span = Math.round(spanY);
        for (int i = 0; i < steps; i++) {
            float fade = 1.0f - i / (float) steps;
            int color = Math.round(0x99 * fade * fade) << 24;
            int y0 = edge(i, steps, span);
            int y1 = edge(i + 1, steps, span);
            RenderUtil.flatRect(extractor, 0.0f, y0, width, y1 - y0, color);
            RenderUtil.flatRect(extractor, 0.0f, height - y1, width, y1 - y0, color);
        }
    }

    private record Frame(AerialImage image, int delayMs, int width, int height) {
    }

    private static void ensureFileLoaded() {
        if (fileChecked) {
            return;
        }
        fileChecked = true;
        Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("aerial");
        for (String name : new String[]{"background.gif", "background.png", "background.jpg"}) {
            File file = dir.resolve(name).toFile();
            if (file.isFile()) {
                frames = name.endsWith(".gif") ? readGif(file) : readStill(file);
                if (frames != null && !frames.isEmpty()) {
                    frameStart = System.currentTimeMillis();
                    return;
                }
            }
        }
    }

    private static List<Frame> readStill(File file) {
        try {
            BufferedImage image = ImageIO.read(file);
            if (image == null) {
                return null;
            }
            return List.of(new Frame(AerialImage.fromImage(image), Integer.MAX_VALUE,
                    image.getWidth(), image.getHeight()));
        } catch (Exception exception) {
            return null;
        }
    }

    private static List<Frame> readGif(File file) {
        try (ImageInputStream stream = ImageIO.createImageInputStream(file)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            reader.setInput(stream);
            int count = reader.getNumImages(true);
            if (count <= 0) {
                return null;
            }

            List<Frame> out = new ArrayList<>(count);
            BufferedImage canvas = null;
            java.awt.Graphics2D graphics = null;
            for (int i = 0; i < count; i++) {
                BufferedImage frame = reader.read(i);
                if (canvas == null) {
                    canvas = new BufferedImage(reader.getWidth(0), reader.getHeight(0),
                            BufferedImage.TYPE_INT_ARGB);
                    graphics = canvas.createGraphics();
                }
                graphics.drawImage(frame, 0, 0, null);
                BufferedImage snapshot = new BufferedImage(canvas.getWidth(), canvas.getHeight(),
                        BufferedImage.TYPE_INT_ARGB);
                snapshot.createGraphics().drawImage(canvas, 0, 0, null);

                out.add(new Frame(AerialImage.fromImage(snapshot), delayOf(reader, i),
                        snapshot.getWidth(), snapshot.getHeight()));
            }
            reader.dispose();
            return out;
        } catch (Exception exception) {
            return null;
        }
    }

    private static int delayOf(ImageReader reader, int index) {
        try {
            IIOMetadata metadata = reader.getImageMetadata(index);
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(metadata.getNativeMetadataFormatName());
            IIOMetadataNode control = (IIOMetadataNode) root
                    .getElementsByTagName("GraphicControlExtension").item(0);
            int hundredths = Integer.parseInt(control.getAttribute("delayTime"));
            return hundredths <= 1 ? 100 : hundredths * 10;
        } catch (Exception exception) {
            return 100;
        }
    }

    private static void drawImage(GuiGraphicsExtractor extractor, int width, int height) {
        Frame frame = advance();
        float scale = Math.max(width / (float) frame.width(), height / (float) frame.height());
        float drawWidth = frame.width() * scale;
        float drawHeight = frame.height() * scale;
        RenderUtil.image(extractor, frame.image(),
                (width - drawWidth) * 0.5f, (height - drawHeight) * 0.5f, drawWidth, drawHeight);
    }

    private static Frame advance() {
        if (frames.size() == 1) {
            return frames.getFirst();
        }
        long now = System.currentTimeMillis();

        while (now - frameStart >= frames.get(frameIndex).delayMs()) {
            frameStart += frames.get(frameIndex).delayMs();
            frameIndex = (frameIndex + 1) % frames.size();
        }
        return frames.get(frameIndex);
    }
}
