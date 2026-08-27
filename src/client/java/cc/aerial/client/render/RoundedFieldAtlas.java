package cc.aerial.client.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.renderer.texture.DynamicTexture;

public final class RoundedFieldAtlas {
    public static final float RANGE = 0.25f;

    public static final float DOMAIN = 1.5f;

    private static final int SIZE = 256;

    private static DynamicTexture texture;
    private static GpuTextureView view;
    private static GpuSampler sampler;

    private RoundedFieldAtlas() {
    }

    private static void ensureBuilt() {
        if (view != null) {
            return;
        }
        NativeImage image = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, false);
        for (int y = 0; y < SIZE; y++) {
            float qy = (y + 0.5f) / SIZE * DOMAIN;
            for (int x = 0; x < SIZE; x++) {
                float qx = (x + 0.5f) / SIZE * DOMAIN;
                float distance = (float) Math.sqrt(qx * qx + qy * qy) - 1.0f;
                float encoded = distance / (2.0f * RANGE) + 0.5f;
                int v = Math.round(Math.max(0.0f, Math.min(1.0f, encoded)) * 255.0f);
                image.setPixelABGR(x, y, 0xFF000000 | (v << 16) | (v << 8) | v);
            }
        }
        texture = new DynamicTexture(() -> "aerial_rounded_field", image);
        view = texture.getTextureView();

        sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
    }

    public static GpuTextureView view() {
        ensureBuilt();
        return view;
    }

    public static GpuSampler sampler() {
        ensureBuilt();
        return sampler;
    }
}
