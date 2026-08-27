package cc.aerial.client.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.resources.Identifier;

public final class AerialPipelines {
    private static volatile boolean shadersReady;

    public static void markReady() {
        shadersReady = true;
    }

    public static boolean ready() {
        return shadersReady;
    }

    public static final Identifier ROUNDED_RECT_SHADER =
            Identifier.fromNamespaceAndPath("aerial", "core/rounded_rect");

    public static final RenderPipeline ROUNDED_RECT = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("aerial", "pipeline/rounded_rect"))
            .withVertexShader(ROUNDED_RECT_SHADER)
            .withFragmentShader(ROUNDED_RECT_SHADER)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)

            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)

            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    public static final Identifier ROUNDED_RECT_ASYM_SHADER =
            Identifier.fromNamespaceAndPath("aerial", "core/rounded_rect_asym");

    public static final RenderPipeline ROUNDED_RECT_ASYM = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("aerial", "pipeline/rounded_rect_asym"))
            .withVertexShader(ROUNDED_RECT_ASYM_SHADER)
            .withFragmentShader(ROUNDED_RECT_ASYM_SHADER)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)

            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    public static final Identifier ROUNDED_OUTLINE_SHADER =
            Identifier.fromNamespaceAndPath("aerial", "core/rounded_outline");

    public static final RenderPipeline ROUNDED_OUTLINE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("aerial", "pipeline/rounded_outline"))
            .withVertexShader(ROUNDED_OUTLINE_SHADER)
            .withFragmentShader(ROUNDED_OUTLINE_SHADER)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)

            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    public static final Identifier ROUNDED_TEXTURE_SHADER =
            Identifier.fromNamespaceAndPath("aerial", "core/rounded_texture");

    public static final RenderPipeline ROUNDED_TEXTURE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("aerial", "pipeline/rounded_texture"))
            .withVertexShader(ROUNDED_TEXTURE_SHADER)
            .withFragmentShader(ROUNDED_TEXTURE_SHADER)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)

            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    public static final Identifier BLUR_RECT_SHADER =
            Identifier.fromNamespaceAndPath("aerial", "core/blur_rect");

    public static final RenderPipeline BLUR_RECT = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("aerial", "pipeline/blur_rect"))
            .withVertexShader(BLUR_RECT_SHADER)
            .withFragmentShader(BLUR_RECT_SHADER)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)

            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER1)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    public static final Identifier BLUR_RECT_FLAT_SHADER =
            Identifier.fromNamespaceAndPath("aerial", "core/blur_rect_flat");

    public static final RenderPipeline BLUR_RECT_FLAT = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("aerial", "pipeline/blur_rect_flat"))
            .withVertexShader(BLUR_RECT_FLAT_SHADER)
            .withFragmentShader(BLUR_RECT_FLAT_SHADER)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    public static final Identifier BLOOM_COMPOSITE_SHADER =
            Identifier.fromNamespaceAndPath("aerial", "core/bloom_composite");

    public static final RenderPipeline BLOOM_COMPOSITE = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("aerial", "pipeline/bloom_composite"))
            .withVertexShader(BLOOM_COMPOSITE_SHADER)
            .withFragmentShader(BLOOM_COMPOSITE_SHADER)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA))
            .withCull(false)
            .build();

    public static final Identifier TEXT_SHADER =
            Identifier.fromNamespaceAndPath("aerial", "core/text");

    public static final RenderPipeline TEXT = RenderPipeline.builder()
            .withLocation(Identifier.fromNamespaceAndPath("aerial", "pipeline/text"))
            .withVertexShader(TEXT_SHADER)
            .withFragmentShader(TEXT_SHADER)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withCull(false)
            .build();

    private AerialPipelines() {
    }
}
