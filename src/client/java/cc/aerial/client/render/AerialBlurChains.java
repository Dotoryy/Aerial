package cc.aerial.client.render;

import cc.aerial.client.features.impl.visual.PostProcessingModule;
import cc.aerial.client.mixin.PostChainAccessor;
import cc.aerial.client.mixin.ShaderManagerAccessor;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelTargetBundle;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostChainConfig;
import net.minecraft.client.renderer.ShaderManager;
import net.minecraft.client.renderer.UniformValue;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AerialBlurChains {
    public enum Slot {
        BACKDROP_GAUSSIAN,
        BACKDROP_KAWASE,
        BACKDROP_RISE,
        GLOW;

        public static final Slot[] VALUES = values();

        public static Slot backdrop(PostProcessingModule.BlurMode mode) {
            return switch (mode) {
                case KAWASE -> BACKDROP_KAWASE;
                case RISE -> BACKDROP_RISE;
                default -> BACKDROP_GAUSSIAN;
            };
        }

        public boolean isBackdrop() {
            return this != GLOW;
        }
    }

    private static final float[] GAUSSIAN_SCALES = {0.5f, 0.5f, 1.0f, 1.0f};

    private static final float[] KAWASE_SCALES = {0.7f, 1.0f, 1.0f, 0.7f};

    private static final int DOWNSCALE = 2;

    private static final Identifier BACKDROP_OUT = Identifier.fromNamespaceAndPath("aerial", "backdrop_out");

    private static final Identifier SMALL_A = Identifier.fromNamespaceAndPath("aerial", "small_a");
    private static final Identifier SMALL_B = Identifier.fromNamespaceAndPath("aerial", "small_b");
    private static final Identifier SCREENQUAD = Identifier.withDefaultNamespace("core/screenquad");
    private static final Identifier GAUSSIAN_FSH = Identifier.fromNamespaceAndPath("aerial", "post/aerial_gaussian");
    private static final Identifier KAWASE_DOWN_FSH = Identifier.fromNamespaceAndPath("aerial", "post/aerial_kawase_down");
    private static final Identifier KAWASE_UP_FSH = Identifier.fromNamespaceAndPath("aerial", "post/aerial_kawase_up");
    private static final Identifier RISE_FSH = Identifier.fromNamespaceAndPath("aerial", "post/aerial_rise");

    private static final float RISE_COMPRESSION = 3.0f;

    private static final Cached[] CACHE = buildCache();

    private static Cached[] buildCache() {
        Cached[] cache = new Cached[Slot.VALUES.length];
        for (int i = 0; i < cache.length; i++) {
            cache[i] = new Cached();
        }
        return cache;
    }

    private static final List<PostChain> PENDING_CLOSE = new ArrayList<>();

    public static void closePending() {
        if (PENDING_CLOSE.isEmpty()) {
            return;
        }
        for (PostChain chain : PENDING_CLOSE) {
            chain.close();
        }
        PENDING_CLOSE.clear();
    }

    private static final class Cached {
        PostChain chain;
        PostProcessingModule.BlurMode mode;
        int radius = -1;
        int width = -1;
        int height = -1;
    }

    private AerialBlurChains() {
    }

    public static void invalidate() {
        for (Cached cached : CACHE) {
            if (cached.chain != null) {
                PENDING_CLOSE.add(cached.chain);
            }
            cached.chain = null;
            cached.mode = null;
            cached.radius = -1;
            cached.width = -1;
            cached.height = -1;
        }
    }

    @Nullable
    public static PostChain get(Slot slot, PostProcessingModule.BlurMode mode, int radius, int width, int height) {
        int smallWidth = Math.max(1, width / DOWNSCALE);
        int smallHeight = Math.max(1, height / DOWNSCALE);

        Identifier output = slot.isBackdrop() ? BACKDROP_OUT : PostChain.MAIN_TARGET_ID;

        Cached cached = CACHE[slot.ordinal()];
        if (cached.chain != null && cached.mode == mode && cached.radius == radius
                && cached.width == width && cached.height == height) {
            return cached.chain;
        }

        if (cached.chain != null) {
            PENDING_CLOSE.add(cached.chain);
            cached.chain = null;
        }

        Minecraft mc = Minecraft.getInstance();
        ShaderManagerAccessor accessor = (ShaderManagerAccessor) mc.getShaderManager();

        PostChainConfig config = switch (mode) {
            case KAWASE -> kawaseConfig(radius, smallWidth, smallHeight, output);
            case RISE -> riseConfig(radius, smallWidth, smallHeight, output);
            default -> gaussianConfig(radius, smallWidth, smallHeight, output);
        };
        try {
            cached.chain = PostChain.load(config, mc.getTextureManager(), LevelTargetBundle.MAIN_TARGETS,
                    Identifier.fromNamespaceAndPath("aerial", "runtime_blur_" + slot.ordinal()),
                    accessor.aerial$postChainProjection(),
                    accessor.aerial$postChainProjectionMatrixBuffer());
        } catch (ShaderManager.CompilationException e) {
            cached.chain = null;
        }
        cached.mode = mode;
        cached.radius = radius;
        cached.width = width;
        cached.height = height;
        return cached.chain;
    }

    private static float innerRadius(float radius) {
        return radius / DOWNSCALE;
    }

    @Nullable
    public static RenderTarget outputTarget(Slot slot) {
        Cached cached = CACHE[slot.ordinal()];
        if (cached.chain == null) {
            return null;
        }
        return ((PostChainAccessor) cached.chain).aerial$persistentTargets().get(BACKDROP_OUT);
    }

    private static PostChainConfig gaussianConfig(int radius, int smallWidth, int smallHeight, Identifier output) {
        return new PostChainConfig(internalTargets(smallWidth, smallHeight, output, true), List.of(

                gaussianPass(PostChain.MAIN_TARGET_ID, SMALL_A, 1.0f, 0.0f, radius * GAUSSIAN_SCALES[0]),
                gaussianPass(SMALL_A, SMALL_B, 0.0f, 1.0f, innerRadius(radius * GAUSSIAN_SCALES[1])),
                gaussianPass(SMALL_B, SMALL_A, 1.0f, 0.0f, innerRadius(radius * GAUSSIAN_SCALES[2])),

                gaussianPass(SMALL_A, output, 0.0f, 1.0f, innerRadius(radius * GAUSSIAN_SCALES[3]))));
    }

    private static PostChainConfig riseConfig(int radius, int smallWidth, int smallHeight, Identifier output) {
        return new PostChainConfig(internalTargets(smallWidth, smallHeight, output, false), List.of(
                risePass(PostChain.MAIN_TARGET_ID, SMALL_A, RISE_COMPRESSION, 0.0f, radius),
                risePass(SMALL_A, output, 0.0f, innerRadius(RISE_COMPRESSION), radius)));
    }

    private static PostChainConfig kawaseConfig(int radius, int smallWidth, int smallHeight, Identifier output) {
        return new PostChainConfig(internalTargets(smallWidth, smallHeight, output, true), List.of(
                kawasePass(KAWASE_DOWN_FSH, PostChain.MAIN_TARGET_ID, SMALL_A, radius * KAWASE_SCALES[0]),
                kawasePass(KAWASE_DOWN_FSH, SMALL_A, SMALL_B, innerRadius(radius * KAWASE_SCALES[1])),
                kawasePass(KAWASE_UP_FSH, SMALL_B, SMALL_A, innerRadius(radius * KAWASE_SCALES[2])),
                kawasePass(KAWASE_UP_FSH, SMALL_A, output, innerRadius(radius * KAWASE_SCALES[3]))));
    }

    private static Map<Identifier, PostChainConfig.InternalTarget> internalTargets(
            int smallWidth, int smallHeight, Identifier output, boolean needsPingPong) {
        PostChainConfig.InternalTarget small = new PostChainConfig.InternalTarget(
                Optional.of(smallWidth), Optional.of(smallHeight), false, 0);
        boolean persistentOut = output.equals(BACKDROP_OUT);
        if (!needsPingPong) {
            return persistentOut
                    ? Map.of(SMALL_A, small, BACKDROP_OUT, persistentOut(smallWidth, smallHeight))
                    : Map.of(SMALL_A, small);
        }
        return persistentOut
                ? Map.of(SMALL_A, small, SMALL_B, small,
                        BACKDROP_OUT, persistentOut(smallWidth, smallHeight))
                : Map.of(SMALL_A, small, SMALL_B, small);
    }

    private static PostChainConfig.InternalTarget persistentOut(int smallWidth, int smallHeight) {
        return new PostChainConfig.InternalTarget(
                Optional.of(smallWidth), Optional.of(smallHeight), true, 0);
    }

    private static PostChainConfig.Pass gaussianPass(Identifier in, Identifier out, float dirX, float dirY, float radius) {
        return pass(GAUSSIAN_FSH, in, out, dirX, dirY, radius);
    }

    private static PostChainConfig.Pass kawasePass(Identifier shader, Identifier in, Identifier out, float radius) {
        return pass(shader, in, out, 1.0f, 1.0f, radius);
    }

    private static PostChainConfig.Pass risePass(Identifier in, Identifier out, float dirX, float dirY, float radius) {
        return pass(RISE_FSH, in, out, dirX, dirY, radius);
    }

    private static PostChainConfig.Pass pass(Identifier shader, Identifier in, Identifier out,
                                             float dirX, float dirY, float radius) {
        return new PostChainConfig.Pass(SCREENQUAD, shader,
                List.of(new PostChainConfig.TargetInput("In", in, false, true)),
                out,
                Map.of("AerialBlurConfig", List.of(
                        new UniformValue.Vec2Uniform(new Vector2f(dirX, dirY)),
                        new UniformValue.FloatUniform(radius))));
    }
}
