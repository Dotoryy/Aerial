package cc.aerial.client.render.font;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.DynamicTexture;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

final class MsdfAtlas {
    record Entry(float advance,
                 float planeLeft, float planeBottom, float planeRight, float planeTop,
                 float u0, float v0, float u1, float v1,
                 boolean hasInk) {
    }

    private final Entry[] entries;
    private final int minChar;
    private final Entry fallback;
    private final String name;

    private NativeImage pending;
    private DynamicTexture texture;
    private GpuTextureView view;
    private GpuSampler sampler;

    final float lineHeight;
    final float ascender;

    private static final float SHADER_DISTANCE_RANGE = 10.0f;

    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    private static final Entry BLANK =
            new Entry(0.0f, 0, 0, 0, 0, 0, 0, 0, 0, false);

    private MsdfAtlas(Entry[] entries, int minChar, String name, NativeImage pending,
                      float lineHeight, float ascender) {
        this.entries = entries;
        this.minChar = minChar;
        this.name = name;
        this.pending = pending;
        this.lineHeight = lineHeight;
        this.ascender = ascender;
        this.fallback = lookup('?');
    }

    private void ensureUploaded() {
        if (texture != null) {
            return;
        }
        texture = new DynamicTexture(() -> "aerial_msdf_" + name, pending);

        pending = null;
        view = texture.getTextureView();

        sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    static MsdfAtlas load(String name) {
        String base = "/assets/aerial/msdf/" + name;
        JsonObject root;
        try (InputStream in = MsdfAtlas.class.getResourceAsStream(base + ".json")) {
            if (in == null) {
                return null;
            }
            root = JsonParser.parseReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (IOException e) {
            return null;
        }

        NativeImage image;
        try (InputStream in = MsdfAtlas.class.getResourceAsStream(base + ".png")) {
            if (in == null) {
                return null;
            }
            image = NativeImage.read(in);
        } catch (IOException e) {
            return null;
        }

        JsonObject atlas = root.getAsJsonObject("atlas");

        float range = atlas.get("distanceRange").getAsFloat();
        if (Math.abs(range - SHADER_DISTANCE_RANGE) > 1.0E-3f) {
            LOGGER.error("MSDF atlas {} has distanceRange {} but core/text.fsh is built for {}."
                            + " Regenerate with -pxrange {} or update the shader constant.",
                    name, range, SHADER_DISTANCE_RANGE, (int) SHADER_DISTANCE_RANGE);
            image.close();
            return null;
        }
        float atlasWidth = atlas.get("width").getAsFloat();
        float atlasHeight = atlas.get("height").getAsFloat();
        boolean yOriginBottom = !"top".equals(atlas.get("yOrigin").getAsString());

        JsonObject metrics = root.getAsJsonObject("metrics");
        float lineHeight = metrics.get("lineHeight").getAsFloat();
        float ascender = metrics.get("ascender").getAsFloat();

        JsonArray glyphs = root.getAsJsonArray("glyphs");
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (JsonElement element : glyphs) {
            int code = element.getAsJsonObject().get("unicode").getAsInt();
            min = Math.min(min, code);
            max = Math.max(max, code);
        }
        if (min > max) {
            image.close();
            return null;
        }

        Entry[] entries = new Entry[max - min + 1];
        for (JsonElement element : glyphs) {
            JsonObject glyph = element.getAsJsonObject();
            int code = glyph.get("unicode").getAsInt();
            float advance = glyph.get("advance").getAsFloat();
            JsonObject plane = glyph.getAsJsonObject("planeBounds");
            JsonObject bounds = glyph.getAsJsonObject("atlasBounds");
            if (plane == null || bounds == null) {
                entries[code - min] = new Entry(advance, 0, 0, 0, 0, 0, 0, 0, 0, false);
                continue;
            }
            float left = bounds.get("left").getAsFloat();
            float right = bounds.get("right").getAsFloat();
            float bottom = bounds.get("bottom").getAsFloat();
            float top = bounds.get("top").getAsFloat();

            float v0 = yOriginBottom ? 1.0f - top / atlasHeight : top / atlasHeight;
            float v1 = yOriginBottom ? 1.0f - bottom / atlasHeight : bottom / atlasHeight;
            entries[code - min] = new Entry(advance,
                    plane.get("left").getAsFloat(), plane.get("bottom").getAsFloat(),
                    plane.get("right").getAsFloat(), plane.get("top").getAsFloat(),
                    left / atlasWidth, v0, right / atlasWidth, v1,
                    true);
        }

        return new MsdfAtlas(entries, min, name, image, lineHeight, ascender);
    }

    Entry lookup(char c) {
        int offset = c - minChar;
        if (offset < 0 || offset >= entries.length || entries[offset] == null) {
            return fallback == null ? BLANK : fallback;
        }
        return entries[offset];
    }

    GpuTextureView view() {
        ensureUploaded();
        return view;
    }

    GpuSampler sampler() {
        ensureUploaded();
        return sampler;
    }

    void close() {
        if (texture != null) {
            texture.close();
        } else if (pending != null) {
            pending.close();
        }
    }
}
