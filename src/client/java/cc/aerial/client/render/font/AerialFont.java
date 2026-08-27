package cc.aerial.client.render.font;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class AerialFont {
    private static final char FALLBACK_CHAR = '?';

    private static final int PAD = 1;

    private static final int SUBPIXEL_STEPS = 4;
    private static final int ATLAS_WIDTH = 1024;

    private static final FontRenderContext FRC = new FontRenderContext(null, true, true);

    private final Font awtFont;

    private char[] chars;
    private final Map<Integer, Atlas> atlases = new HashMap<>();

    private final boolean dynamic;

    private AerialFont(Font awtFont, char[] chars, boolean dynamic) {
        this.awtFont = awtFont;
        this.chars = chars;
        this.dynamic = dynamic;
    }

    public static AerialFont create(Font awtFont) {
        return new AerialFont(awtFont, asciiChars(), false);
    }

    private MsdfAtlas msdf;

    public static AerialFont createFromResource(String fileName) {
        AerialFont font = new AerialFont(loadResourceFont(fileName), asciiChars(), false);

        String name = fileName.endsWith(".ttf") || fileName.endsWith(".otf")
                ? fileName.substring(0, fileName.length() - 4)
                : fileName;
        font.msdf = MsdfAtlas.load(name);
        return font;
    }

    public static AerialFont createIconFromResource(String fileName, char... glyphs) {
        return new AerialFont(loadResourceFont(fileName), glyphs.clone(), false);
    }

    public static AerialFont createDynamicFromResource(String fileName) {
        return new AerialFont(loadResourceFont(fileName), new char[0], true);
    }

    public boolean ensureGlyphs(CharSequence text) {
        if (msdf != null) {
            return false;
        }
        if (!dynamic || text.length() == 0) {
            return false;
        }

        char[] additions = null;
        int additionCount = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Arrays.binarySearch(chars, c) >= 0) {
                continue;
            }
            if (additions == null) {
                additions = new char[text.length()];
            }

            boolean alreadyQueued = false;
            for (int j = 0; j < additionCount; j++) {
                if (additions[j] == c) {
                    alreadyQueued = true;
                    break;
                }
            }
            if (!alreadyQueued) {
                additions[additionCount++] = c;
            }
        }
        if (additionCount == 0) {
            return false;
        }

        char[] grown = Arrays.copyOf(chars, chars.length + additionCount);
        System.arraycopy(additions, 0, grown, chars.length, additionCount);

        Arrays.sort(grown);
        this.chars = grown;

        RETIRED.addAll(atlases.values());
        atlases.clear();
        lastPhysical = -1;
        lastAtlas = null;
        return true;
    }

    public static void endFrame() {
        if (RETIRED.isEmpty()) {
            return;
        }
        for (Atlas atlas : RETIRED) {
            atlas.close();
        }
        RETIRED.clear();
    }

    private static final java.util.List<Atlas> RETIRED = new java.util.ArrayList<>();

    private static Font loadResourceFont(String fileName) {
        String path = "/assets/aerial/fonts/" + fileName;
        try (InputStream in = AerialFont.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing font resource: " + path);
            }
            return Font.createFont(Font.TRUETYPE_FONT, in);
        } catch (IOException | FontFormatException e) {
            throw new IllegalStateException("Failed to load font resource: " + path, e);
        }
    }

    private static char[] asciiChars() {
        char[] chars = new char[126 - 32 + 1];
        for (int i = 0; i < chars.length; i++) {
            chars[i] = (char) (32 + i);
        }
        return chars;
    }

    private static float pixelRatio() {
        return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
    }

    private int lastPhysical = -1;
    private Atlas lastAtlas;

    private Atlas atlasFor(float size, float ratio) {
        int physical = Math.max(1, Math.round(size * ratio));
        if (physical == lastPhysical) {
            return lastAtlas;
        }
        Atlas atlas = atlases.get(physical);
        if (atlas == null) {
            atlas = buildAtlas(physical);
            atlases.put(physical, atlas);
        }
        lastPhysical = physical;
        lastAtlas = atlas;
        return atlas;
    }

    public float stringWidth(CharSequence text, float size) {
        if (msdf != null) {
            float width = 0.0f;
            for (int i = 0; i < text.length(); i++) {
                width += msdf.lookup(text.charAt(i)).advance();
            }
            return width * size;
        }
        float ratio = pixelRatio();
        Atlas atlas = atlasFor(size, ratio);
        float width = 0.0f;
        for (int i = 0; i < text.length(); i++) {
            width += atlas.glyph(text.charAt(i), 0).advance;
        }
        return width / ratio;
    }

    public float height(float size) {
        if (msdf != null) {
            return msdf.lineHeight * size;
        }
        float ratio = pixelRatio();
        return atlasFor(size, ratio).lineHeight / ratio;
    }

    public GlyphQuad[] layout(CharSequence text, float x, float y, float size) {
        if (msdf != null) {
            return layoutMsdf(text, x, y, size);
        }
        float ratio = pixelRatio();
        Atlas atlas = atlasFor(size, ratio);
        float inv = 1.0f / ratio;

        float penX = x * ratio;
        int baseline = Math.round(y * ratio) + atlas.ascent;

        GlyphQuad[] quads = new GlyphQuad[text.length()];
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            float base = (float) Math.floor(penX);
            int step = Math.round((penX - base) * SUBPIXEL_STEPS);
            if (step >= SUBPIXEL_STEPS) {
                step -= SUBPIXEL_STEPS;
                base += 1.0f;
            }

            Glyph glyph = atlas.glyph(text.charAt(i), step);
            if (glyph.hasInk) {
                float gx = base + glyph.bearingX;
                float gy = baseline + glyph.bearingY;
                quads[count++] = new GlyphQuad(
                        gx * inv, gy * inv,
                        (gx + glyph.width) * inv, (gy + glyph.height) * inv,
                        glyph.u0, glyph.v0, glyph.u1, glyph.v1);
            }
            penX += glyph.advance;
        }
        return count == quads.length ? quads : Arrays.copyOf(quads, count);
    }

    private GlyphQuad[] layoutMsdf(CharSequence text, float x, float y, float size) {
        float penX = x;
        float baseline = y + msdf.ascender * size;

        GlyphQuad[] quads = new GlyphQuad[text.length()];
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            MsdfAtlas.Entry glyph = msdf.lookup(text.charAt(i));
            if (glyph.hasInk()) {
                quads[count++] = new GlyphQuad(
                        penX + glyph.planeLeft() * size,
                        baseline - glyph.planeTop() * size,
                        penX + glyph.planeRight() * size,
                        baseline - glyph.planeBottom() * size,
                        glyph.u0(), glyph.v0(), glyph.u1(), glyph.v1());
            }
            penX += glyph.advance() * size;
        }
        return count == quads.length ? quads : Arrays.copyOf(quads, count);
    }

    public GpuTextureView textureView(float size) {
        return msdf != null ? msdf.view() : atlasFor(size, pixelRatio()).view;
    }

    public GpuSampler sampler(float size) {
        return msdf != null ? msdf.sampler() : atlasFor(size, pixelRatio()).sampler;
    }

    private Atlas buildAtlas(int px) {
        Font raster = awtFont.deriveFont((float) px);

        if (chars.length == 0) {
            chars = new char[]{FALLBACK_CHAR};
        }
        int count = chars.length * SUBPIXEL_STEPS;

        GlyphVector[] vectors = new GlyphVector[count];
        float[] offset = new float[count];
        float[] advance = new float[count];

        int[] boxX = new int[count];
        int[] boxY = new int[count];
        int[] boxW = new int[count];
        int[] boxH = new int[count];

        for (int c = 0; c < chars.length; c++) {
            GlyphVector gv = raster.createGlyphVector(FRC, String.valueOf(chars[c]));
            float adv = gv.getGlyphMetrics(0).getAdvanceX();

            for (int s = 0; s < SUBPIXEL_STEPS; s++) {
                int i = c * SUBPIXEL_STEPS + s;
                float frac = s / (float) SUBPIXEL_STEPS;
                vectors[i] = gv;
                offset[i] = frac;
                advance[i] = adv;

                Rectangle2D b = gv.getOutline(frac, 0.0f).getBounds2D();
                if (b.getWidth() <= 0 || b.getHeight() <= 0) {
                    boxW[i] = 0;
                    boxH[i] = 0;
                    continue;
                }
                boxX[i] = (int) Math.floor(b.getMinX()) - PAD;
                boxY[i] = (int) Math.floor(b.getMinY()) - PAD;
                boxW[i] = (int) Math.ceil(b.getMaxX()) + PAD - boxX[i];
                boxH[i] = (int) Math.ceil(b.getMaxY()) + PAD - boxY[i];
            }
        }

        int[] cellX = new int[count];
        int[] cellY = new int[count];
        int penX = 0, penY = 0, rowHeight = 0;
        for (int i = 0; i < count; i++) {
            if (penX + boxW[i] > ATLAS_WIDTH) {
                penX = 0;
                penY += rowHeight;
                rowHeight = 0;
            }
            cellX[i] = penX;
            cellY[i] = penY;
            penX += boxW[i];
            rowHeight = Math.max(rowHeight, boxH[i]);
        }
        int atlasHeight = Math.max(1, penY + rowHeight);

        BufferedImage image = new BufferedImage(ATLAS_WIDTH, atlasHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setColor(Color.WHITE);

        Glyph[] glyphs = new Glyph[count];
        for (int i = 0; i < count; i++) {
            boolean hasInk = boxW[i] > 0 && boxH[i] > 0;
            if (hasInk) {
                g.fill(vectors[i].getOutline(
                        offset[i] + (cellX[i] - boxX[i]),
                        cellY[i] - boxY[i]));
            }
            glyphs[i] = new Glyph(
                    cellX[i] / (float) ATLAS_WIDTH,
                    cellY[i] / (float) atlasHeight,
                    (cellX[i] + boxW[i]) / (float) ATLAS_WIDTH,
                    (cellY[i] + boxH[i]) / (float) atlasHeight,
                    boxW[i], boxH[i],
                    boxX[i], boxY[i],
                    advance[i], hasInk);
        }
        g.dispose();

        NativeImage native_ = new NativeImage(ATLAS_WIDTH, atlasHeight, false);
        for (int y = 0; y < atlasHeight; y++) {
            for (int x = 0; x < ATLAS_WIDTH; x++) {
                int coverage = image.getRGB(x, y) >>> 24;
                native_.setPixel(x, y, (coverage << 24) | (coverage << 16) | (coverage << 8) | coverage);
            }
        }

        DynamicTexture texture = new DynamicTexture(() -> "aerial_font_" + px, native_);
        float ascent = raster.getLineMetrics("Hg", FRC).getAscent();
        float descent = raster.getLineMetrics("Hg", FRC).getDescent();

        int minChar = Character.MAX_VALUE;
        int maxChar = Character.MIN_VALUE;
        for (char c : chars) {
            minChar = Math.min(minChar, c);
            maxChar = Math.max(maxChar, c);
        }
        int[] indexOf = new int[maxChar - minChar + 1];
        Arrays.fill(indexOf, -1);
        for (int c = 0; c < chars.length; c++) {
            indexOf[chars[c] - minChar] = c;
        }

        return new Atlas(glyphs, indexOf, minChar, texture, Math.round(ascent), ascent + descent);
    }

    private static final class Atlas {
        private final Glyph[] glyphs;

        private final int[] indexOf;
        private final int minChar;

        private final int fallbackIndex;
        private final GpuTextureView view;
        private final GpuSampler sampler;

        private final DynamicTexture texture;

        private final int ascent;
        private final float lineHeight;

        private static final Glyph BLANK = new Glyph(0, 0, 0, 0, 0, 0, 0, 0, 0, false);

        private Atlas(Glyph[] glyphs, int[] indexOf, int minChar, DynamicTexture texture,
                      int ascent, float lineHeight) {
            this.glyphs = glyphs;
            this.indexOf = indexOf;
            this.minChar = minChar;
            this.fallbackIndex = lookup(indexOf, minChar, FALLBACK_CHAR);
            this.texture = texture;
            this.view = texture.getTextureView();

            this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            this.ascent = ascent;
            this.lineHeight = lineHeight;
        }

        private void close() {
            texture.close();
        }

        private static int lookup(int[] table, int minChar, char c) {
            int offset = c - minChar;
            return offset < 0 || offset >= table.length ? -1 : table[offset];
        }

        private Glyph glyph(char c, int subpixelStep) {
            int index = lookup(indexOf, minChar, c);
            if (index < 0) {
                index = fallbackIndex;
            }
            if (index < 0) {
                return BLANK;
            }
            return glyphs[index * SUBPIXEL_STEPS + subpixelStep];
        }
    }
}
