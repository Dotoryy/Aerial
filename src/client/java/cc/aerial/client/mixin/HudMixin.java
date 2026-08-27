package cc.aerial.client.mixin;

import cc.aerial.client.event.EventDispatcher;
import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.features.impl.visual.AnimationsModule;
import cc.aerial.client.features.impl.visual.ArraylistModule;
import cc.aerial.client.features.impl.visual.ChatModule;
import cc.aerial.client.features.impl.visual.PotionEffectsModule;
import cc.aerial.client.features.impl.visual.ScoreboardModule;
import cc.aerial.client.features.impl.visual.StreamerModule;
import cc.aerial.client.render.BlurConsumer;
import cc.aerial.client.render.ChatDecoration;
import cc.aerial.client.render.AerialBlur;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.theme.ThemeManager;
import net.minecraft.client.DeltaTracker;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.Mth;
import org.joml.Matrix3x2fStack;

import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudMixin {
    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void aerial$compositeBloom(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        AerialBlur.compositeBloom(extractor);
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void aerial$onExtractRenderState(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        EventDispatcher.dispatch(new Render2DEvent(extractor, deltaTracker.getGameTimeDeltaPartialTick(false)));
    }

    @Inject(method = "extractChat", at = @At(value = "INVOKE", shift = At.Shift.BEFORE,
            target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"))
    private void aerial$beforeChat(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        ChatDecoration.before(extractor);
    }

    @Inject(method = "extractChat", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
            target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V"))
    private void aerial$afterChat(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        ChatDecoration.after(extractor);
    }

    @Redirect(method = "extractItemHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getOffhandItem()Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack aerial$hideShieldInHotbar(Player player) {
        ItemStack real = player.getOffhandItem();
        AnimationsModule module = AnimationsModule.INSTANCE;
        if (module.isEnabled() && module.isHideShieldSlotInHotbar() && module.isHideShield()
                && real.getItem() instanceof ShieldItem) {
            return ItemStack.EMPTY;
        }
        return real;
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void aerial$cancelScoreboard(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!ScoreboardModule.INSTANCE.isScoreboardEnabled()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void aerial$cancelEffects(GuiGraphicsExtractor extractor, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (PotionEffectsModule.INSTANCE.isRemovingVanillaUi()) {
            ci.cancel();
        }
    }

    @Redirect(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"))
    private void aerial$scoreboardText(GuiGraphicsExtractor extractor, Font font, Component text,
                                        int x, int y, int color, boolean shadow) {
        Component adjusted = aerial$processedLine(text);
        boolean useShadow = ScoreboardModule.INSTANCE.isTextShadow();
        if (!ScoreboardModule.INSTANCE.isCustomFont()) {
            extractor.text(font, adjusted, x, y, color, useShadow);
            return;
        }
        aerial$drawWithClientFont(extractor, adjusted, x, y, color, useShadow);
    }

    @Unique
    private static final java.util.Map<Component, Component> aerial$LINE_CACHE = new java.util.HashMap<>();
    @Unique
    private static final int aerial$LINE_CACHE_MAX = 64;

    @Unique
    private static boolean aerial$lineCacheHidingIds;

    @Unique
    private static Component aerial$processedLine(Component text) {
        boolean hiding = StreamerModule.INSTANCE.isHidingServerId();
        if (hiding != aerial$lineCacheHidingIds) {
            aerial$LINE_CACHE.clear();
            aerial$lineCacheHidingIds = hiding;
        }
        Component cached = aerial$LINE_CACHE.get(text);
        if (cached != null) {
            return cached;
        }
        if (aerial$LINE_CACHE.size() >= aerial$LINE_CACHE_MAX) {
            aerial$LINE_CACHE.clear();
        }

        Component processed = aerial$reparseLegacyFormatting(text);
        if (hiding) {
            processed = aerial$replaceServerIp(processed, aerial$DOMAIN_PATTERN);
            if (aerial$hasDarkGray(processed) && aerial$isIdText(aerial$darkGrayText(processed))) {
                processed = aerial$obfuscateDarkGrayRuns(processed);
            }
        }
        aerial$LINE_CACHE.put(text, processed);
        return processed;
    }

    private static void aerial$drawWithClientFont(GuiGraphicsExtractor extractor, Component text,
                                                   int x, int y, int color, boolean shadow) {
        if (aerial$scoreboardFont == null) {
            aerial$scoreboardFont = AerialFont.createFromResource("OpalProductSansMedium.ttf");
        }

        Component runs = text;

        float top = y + aerial$SCOREBOARD_FONT_NUDGE_Y;
        float[] cursor = {x};

        runs.visit((style, content) -> {
            int runColor = style.getColor() == null ? color : 0xFF000000 | style.getColor().getValue();
            if (shadow) {
                TextRenderUtil.drawString(extractor, aerial$scoreboardFont, content,
                        cursor[0] + 0.5f, top + 0.5f, aerial$SCOREBOARD_FONT_SIZE,
                        0xFF000000 | ((runColor & 0xFCFCFC) >> 2));
            }
            cursor[0] += TextRenderUtil.drawString(extractor, aerial$scoreboardFont, content,
                    cursor[0], top, aerial$SCOREBOARD_FONT_SIZE, runColor);
            return java.util.Optional.empty();
        }, Style.EMPTY);
    }

    private static AerialFont aerial$scoreboardFont;
    private static final float aerial$SCOREBOARD_FONT_SIZE = 8.0f;
    private static final float aerial$SCOREBOARD_FONT_NUDGE_Y = 0.5f;

    private static Component aerial$reparseLegacyFormatting(Component text) {
        MutableComponent rebuilt = Component.empty();
        boolean[] anyLegacyCode = {false};
        text.visit((style, content) -> {
            if (content.indexOf('§') < 0) {
                rebuilt.append(Component.literal(content).withStyle(style));
                return java.util.Optional.empty();
            }
            anyLegacyCode[0] = true;
            StringBuilder currentText = new StringBuilder();
            Style[] currentStyle = {style};
            net.minecraft.util.StringDecomposer.iterateFormatted(content, style, (index, runStyle, codepoint) -> {
                if (!runStyle.equals(currentStyle[0]) && currentText.length() > 0) {
                    rebuilt.append(Component.literal(currentText.toString()).withStyle(currentStyle[0]));
                    currentText.setLength(0);
                }
                currentStyle[0] = runStyle;
                currentText.appendCodePoint(codepoint);
                return true;
            });
            if (currentText.length() > 0) {
                rebuilt.append(Component.literal(currentText.toString()).withStyle(currentStyle[0]));
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return anyLegacyCode[0] ? rebuilt : text;
    }

    private static boolean aerial$isDarkGray(Style style) {
        return TextColor.DARK_GRAY.equals(style.getColor());
    }

    private static boolean aerial$hasDarkGray(Component text) {
        return text.visit((style, content) ->
                aerial$isDarkGray(style) && !content.isBlank()
                        ? java.util.Optional.of(Boolean.TRUE) : java.util.Optional.empty(),
                Style.EMPTY).isPresent();
    }

    private static String aerial$darkGrayText(Component text) {
        StringBuilder builder = new StringBuilder();
        text.visit((style, content) -> {
            if (aerial$isDarkGray(style)) {
                builder.append(content);
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return builder.toString().trim();
    }

    private static boolean aerial$isIdText(String candidate) {
        if (candidate.length() < 2) {
            return false;
        }
        boolean digit = false;
        boolean letter = false;
        for (int i = 0; i < candidate.length(); i++) {
            char c = candidate.charAt(i);
            if (Character.isWhitespace(c)) {
                return false;
            }
            digit |= Character.isDigit(c);
            letter |= Character.isLetter(c);
        }
        return digit && letter;
    }

    private static final String aerial$OBFUSCATED = "§khidden§r";

    private static Component aerial$obfuscateDarkGrayRuns(Component text) {
        MutableComponent rebuilt = Component.empty();
        boolean[] emitted = {false};
        text.visit((style, content) -> {
            if (!aerial$isDarkGray(style)) {
                rebuilt.append(Component.literal(content).withStyle(style));
                return java.util.Optional.empty();
            }
            if (!emitted[0]) {
                emitted[0] = true;
                rebuilt.append(Component.literal(aerial$OBFUSCATED).withStyle(style));
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        return rebuilt;
    }

    private static final String aerial$REPLACEMENT = "aerialclient.cc";

    private static final double aerial$FADE_STEP = 8.0;

    private static Style aerial$firstStyle(Component text) {
        return text.visit((style, content) -> content.isEmpty() ? java.util.Optional.<Style>empty() : java.util.Optional.of(style),
                Style.EMPTY).orElse(Style.EMPTY);
    }

    private static final java.util.regex.Pattern aerial$DOMAIN_PATTERN = java.util.regex.Pattern.compile(
            "\\b(?:[a-zA-Z0-9](?:[a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}\\b");

    private static Component aerial$replaceServerIp(Component text, java.util.regex.Pattern pattern) {
        String plain = text.getString();
        java.util.regex.Matcher matcher = pattern.matcher(plain);
        if (!matcher.find()) {
            return text;
        }

        Style baseStyle = aerial$firstStyle(text);
        MutableComponent rebuilt = Component.empty();
        int last = 0;
        do {
            if (matcher.start() > last) {
                rebuilt.append(Component.literal(plain.substring(last, matcher.start())).withStyle(baseStyle));
            }
            rebuilt.append(aerial$fadingReplacement(baseStyle));
            last = matcher.end();
        } while (matcher.find());
        if (last < plain.length()) {
            rebuilt.append(Component.literal(plain.substring(last)).withStyle(baseStyle));
        }
        return rebuilt;
    }

    private static Component aerial$fadingReplacement(Style baseStyle) {
        MutableComponent result = Component.empty();
        cc.aerial.client.theme.Theme theme = ThemeManager.getTheme();
        for (int i = 0; i < aerial$REPLACEMENT.length(); i++) {
            int rgb = theme.getAccentColor(0, i * aerial$FADE_STEP).getRGB() & 0xFFFFFF;
            result.append(Component.literal(String.valueOf(aerial$REPLACEMENT.charAt(i)))
                    .withStyle(baseStyle.withColor(TextColor.fromRgb(rgb))));
        }
        return result;
    }

    private Integer aerial$pendingHeaderX0, aerial$pendingHeaderY0, aerial$pendingHeaderX1, aerial$pendingHeaderY1;

    @Redirect(method = "displayScoreboardSidebar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void aerial$scoreboardRemoveBackground(GuiGraphicsExtractor extractor, int x0, int y0, int x1, int y1, int color) {
        if (aerial$pendingHeaderX0 == null) {
            aerial$pendingHeaderX0 = x0;
            aerial$pendingHeaderY0 = y0;
            aerial$pendingHeaderX1 = x1;
            aerial$pendingHeaderY1 = y1;

            return;
        }

        int unionX0 = Math.min(aerial$pendingHeaderX0, x0);
        int unionY0 = aerial$pendingHeaderY0;
        int unionX1 = Math.max(aerial$pendingHeaderX1, x1);
        int unionY1 = y1;

        AerialBlur.drawBlurredRound(extractor, BlurConsumer.SCOREBOARD, unionX0, unionY0, unionX1 - unionX0, unionY1 - unionY0, 0.01f);

        if (!ScoreboardModule.INSTANCE.isBackgroundRemoved()) {
            extractor.fill(unionX0, unionY0, unionX1, unionY1, color);
        }
        aerial$pendingHeaderX0 = null;
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"))
    private void aerial$scoreboardScaleStart(GuiGraphicsExtractor extractor, Objective objective, CallbackInfo ci) {
        float scale = ScoreboardModule.INSTANCE.getScale();
        float arraylistHeight = ScoreboardModule.INSTANCE.isAvoidingArraylist() ? ArraylistModule.INSTANCE.getTotalHeight() : 0f;

        arraylistHeight = Math.min(arraylistHeight, extractor.guiHeight() / 6.0f);

        extractor.pose().pushMatrix();
        if (arraylistHeight > 0f) {
            extractor.pose().translate(0f, arraylistHeight);
        }
        if (scale != 1.0F) {
            float rightEdge = extractor.guiWidth();
            extractor.pose().translate(rightEdge, 0);
            extractor.pose().scale(scale, scale);
            extractor.pose().translate(-rightEdge, 0);
        }
    }

    @Inject(method = "displayScoreboardSidebar", at = @At("TAIL"))
    private void aerial$scoreboardScaleEnd(GuiGraphicsExtractor extractor, Objective objective, CallbackInfo ci) {
        extractor.pose().popMatrix();
    }
}
