package cc.aerial.client.render.font;

import cc.aerial.client.render.ImageRenderState;
import cc.aerial.client.render.AerialBloomFilter;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public final class BitmapFont {
    private static final int COLUMNS = 16;
    private static final int SPACE_ADVANCE = 4;
    private static final int GLYPHS = 256;

    private final BufferedImage source;
    private final int cell;
    private final int atlasSize;

    private final int[] advance = new int[GLYPHS];

    private final int[] inkWidth = new int[GLYPHS];

    private final int[] inkTop = new int[GLYPHS];
    private final int[] inkBottom = new int[GLYPHS];

    private GpuTextureView view;
    private GpuSampler sampler;

    private BitmapFont(BufferedImage source) {
        this.source = source;
        this.atlasSize = source.getWidth();
        this.cell = atlasSize / COLUMNS;
        measure();
    }

    private static final Map<String, BitmapFont> CACHE = new HashMap<>();

    public static BitmapFont fromResource(String fileName) {
        BitmapFont cached = CACHE.get(fileName);
        if (cached != null) {
            return cached;
        }
        BitmapFont loaded = load(fileName);
        CACHE.put(fileName, loaded);
        return loaded;
    }

    private static BitmapFont load(String fileName) {
        String path = "/assets/aerial/textures/" + fileName;
        try (InputStream in = BitmapFont.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing bitmap font resource: " + path);
            }
            return new BitmapFont(ImageIO.read(in));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load bitmap font resource: " + path, e);
        }
    }

    private void measure() {
        for (int index = 0; index < GLYPHS; index++) {
            int originX = (index % COLUMNS) * cell;
            int originY = (index / COLUMNS) * cell;
            int right = -1;
            int top = -1;
            int bottom = -1;
            for (int row = 0; row < cell; row++) {
                for (int col = 0; col < cell; col++) {
                    if ((source.getRGB(originX + col, originY + row) >>> 24) == 0) {
                        continue;
                    }
                    if (col > right) {
                        right = col;
                    }
                    if (top < 0) {
                        top = row;
                    }
                    bottom = row;
                }
            }
            inkTop[index] = top;
            inkBottom[index] = bottom;
            inkWidth[index] = right + 1;
            advance[index] = right < 0 ? (index == ' ' ? SPACE_ADVANCE : 0) : right + 2;
        }
    }

    private void ensureUploaded() {
        if (view != null) {
            return;
        }
        NativeImage image = new NativeImage(atlasSize, atlasSize, false);
        for (int y = 0; y < atlasSize; y++) {
            for (int x = 0; x < atlasSize; x++) {
                image.setPixel(x, y, source.getRGB(x, y));
            }
        }
        DynamicTexture texture = new DynamicTexture(() -> "aerial_bitmap_font", image);
        this.view = texture.getTextureView();
        this.sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
    }

    public float lineHeight(float scale) {
        return cell * scale;
    }

    public float width(String text, float scale) {
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            total += advanceOf(text.charAt(i));
        }
        return total * scale;
    }

    private int advanceOf(char c) {
        return c < GLYPHS ? advance[c] : advance[' '];
    }

    public float inkCenter(String text, float scale) {
        int top = Integer.MAX_VALUE;
        int bottom = -1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int index = c < GLYPHS ? c : ' ';
            if (inkTop[index] < 0) {
                continue;
            }
            top = Math.min(top, inkTop[index]);
            bottom = Math.max(bottom, inkBottom[index]);
        }
        if (bottom < 0) {
            return cell * 0.5f * scale;
        }
        return (top + bottom + 1) * 0.5f * scale;
    }

    public float draw(GuiGraphicsExtractor extractor, String text, float x, float y, float scale, int color) {
        ensureUploaded();
        float penX = x;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int index = c < GLYPHS ? c : ' ';
            int ink = inkWidth[index];
            if (ink > 0) {
                int originX = (index % COLUMNS) * cell;
                int originY = (index / COLUMNS) * cell;
                float u0 = originX / (float) atlasSize;
                float v0 = originY / (float) atlasSize;
                float u1 = (originX + ink) / (float) atlasSize;
                float v1 = (originY + cell) / (float) atlasSize;
                AerialBloomFilter.submit(extractor,
                        ImageRenderState.of(extractor.pose(),
                                penX, y, penX + ink * scale, y + cell * scale,
                                u0, v0, u1, v1,
                                color, color, view, sampler, null));
            }
            penX += advance[index] * scale;
        }
        return penX;
    }

    public float drawWithShadow(GuiGraphicsExtractor extractor, String text, float x, float y, float scale, int color) {
        int shadow = ((color & 0xFCFCFC) >> 2) | (color & 0xFF000000);
        draw(extractor, text, x + scale, y + scale, scale, shadow);
        return draw(extractor, text, x, y, scale, color);
    }
}
