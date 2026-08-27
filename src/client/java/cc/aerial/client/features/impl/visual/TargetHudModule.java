package cc.aerial.client.features.impl.visual;

import cc.aerial.client.event.impl.game.JoinWorldEvent;
import cc.aerial.client.event.impl.game.player.interaction.AttackEvent;
import cc.aerial.client.event.impl.game.server.ServerDisconnectEvent;
import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.render.BlurConsumer;
import cc.aerial.client.render.AerialBloomFilter;
import cc.aerial.client.render.AerialBlur;
import cc.aerial.client.render.particle.HudParticle;
import cc.aerial.client.render.particle.HudParticles;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.BitmapFont;
import cc.aerial.client.render.font.GlyphQuad;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.screen.animation.Animation;
import cc.aerial.client.screen.animation.Easing;
import cc.aerial.client.theme.Theme;
import cc.aerial.client.utility.HudDrag;
import cc.aerial.client.utility.InventoryUtility;
import cc.aerial.client.utility.PlayerUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.text.DecimalFormat;
import java.util.function.Consumer;
import java.util.function.IntFunction;

public final class TargetHudModule extends Module {
    public static final TargetHudModule INSTANCE = new TargetHudModule();

    private static final Minecraft mc = Minecraft.getInstance();
    private static final DecimalFormat HEALTH_DF = new DecimalFormat("0.#");
    private static final long ATTACK_GRACE_MS = 1200L;
    private static final int PULSE_PIPS = 12;
    private static final String HEART_GLYPH = String.valueOf((char) 0xE87D);

    private static final int RISE_PANEL = rgba(0, 0, 0, 110);

    private static final int RISE_BLOOM = rgba(0, 0, 0, 190);

    private static final float RISE_ROUND = 5f;

    private static final float MODERN_FONT_SIZE = 11f;

    private static final float GODLY_FONT_SIZE = 9f;

    private static final float BMS_FONT_SIZE = 11f;

    private static final String RISE_NAME_LABEL = "Name:";

    enum TargetHudStyle {
        AERIAL("Aerial"),
        ADJUST("Adjust"),
        PULSE("Pulse"),
        SIMPLE("Simple"),
        CAPSULE("Capsule"),
        MODERN("Modern"),
        GODLY("Godly"),
        CREIDA("Creida"),
        BMS("BMS"),
        NOVOLINE("Novoline"),
        OLD_NOVOLINE("Old Novoline"),
        AKRIEN("Akrien");

        private final String label;

        TargetHudStyle(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    enum RiseBackground { GLASS, TINT, SOLID }

    enum TargetHudScale {
        X1_0("1.0x", 1.0f), X1_25("1.25x", 1.25f), X1_5("1.5x", 1.5f), X2_0("2.0x", 2.0f);

        private final String label;
        private final float multiplier;

        TargetHudScale(String label, float multiplier) {
            this.label = label;
            this.multiplier = multiplier;
        }

        float multiplier() {
            return multiplier;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    enum AdjustBackgroundMode { OUTLINE, SPLIT, BAR }

    enum PulseHealthMode { PIPS, BAR }

    private final ModeProperty<TargetHudStyle> style = new ModeProperty<>("Style", TargetHudStyle.AERIAL);

    private final BooleanProperty riseParticles = new BooleanProperty("Particles", true)
            .hideIf(() -> style.getValue() != TargetHudStyle.MODERN && style.getValue() != TargetHudStyle.CREIDA);

    private final ModeProperty<RiseBackground> riseBackground = new ModeProperty<>("Background Mode", RiseBackground.GLASS)
            .hideIf(() -> style.getValue() != TargetHudStyle.MODERN && style.getValue() != TargetHudStyle.CREIDA);
    private final ModeProperty<TargetHudScale> scale = new ModeProperty<TargetHudScale>("Scale", TargetHudScale.X1_0)
            .hideIf(() -> style.getValue() != TargetHudStyle.ADJUST);
    private final ModeProperty<AdjustBackgroundMode> backgroundMode = new ModeProperty<AdjustBackgroundMode>("Background Mode", AdjustBackgroundMode.OUTLINE)
            .hideIf(() -> style.getValue() != TargetHudStyle.ADJUST);
    private final BooleanProperty healthRatioColor = new BooleanProperty("Health Ratio Color", true);
    private final ModeProperty<PulseHealthMode> pulseHealthMode = new ModeProperty<PulseHealthMode>("Pulse Health Mode", PulseHealthMode.PIPS)
            .hideIf(() -> style.getValue() != TargetHudStyle.PULSE);

    private TargetHudModule() {
        super("Target HUD", "Show info about your current combat target", ModuleCategory.VISUAL);
        addProperties(style, scale, backgroundMode, riseBackground, riseParticles, healthRatioColor, pulseHealthMode, xPos, yPos);
    }

    private static AerialFont sfFont;
    private static AerialFont sfBoldFont;
    private static AerialFont heartFont;

    private static BitmapFont novolineFont;

    private static AerialFont riseLightFont;
    private static AerialFont riseMediumFont;

    private static AerialFont riseRegularFont;

    private static void ensureFontsLoaded() {
        if (sfFont == null) {
            sfFont = AerialFont.createFromResource("SF.ttf");
            sfBoldFont = AerialFont.createFromResource("SFBOLD.ttf");
            heartFont = AerialFont.createIconFromResource("OpalMaterialIconsRegular.ttf", (char) 0xE87D);
            novolineFont = BitmapFont.fromResource("novoline_vanilla.png");
        }
        if (riseLightFont == null) {
            riseLightFont = AerialFont.createFromResource("ProductSansLight.ttf");
            riseMediumFont = AerialFont.createFromResource("ProductSansMedium.ttf");
            riseRegularFont = AerialFont.createFromResource("ProductSansRegular.ttf");
        }
    }

    private LivingEntity renderTarget;
    private LivingEntity lastAttackedEntity;
    private long lastAttackTimeMs;

    private final NumberProperty xPos = new NumberProperty("X", 20.0, -10000.0, 10000.0, 0.01).hideIf(() -> true);
    private final NumberProperty yPos = new NumberProperty("Y", 20.0, -10000.0, 10000.0, 0.01).hideIf(() -> true);

    private float x = 20f, y = 20f;
    private float currentWidth = 100f, currentHeight = 30f;

    private static final DecimalFormat AKRIEN_DF = new DecimalFormat("0.0");
    private static final float AKRIEN_HEIGHT = 39.5f;

    private static final float AKRIEN_MIN_WIDTH = 115f;

    private static final float AKRIEN_NAME_PAD = 52f;

    private static final float AKRIEN_NAME_SIZE = 9f;
    private static final float AKRIEN_STAT_SIZE = 6.5f;

    private static final float AKRIEN_HEAD = 26f;

    private static final float AKRIEN_BAR_BORDER = 0.74f;
    private static final int AKRIEN_BAR_EASE_MS = 18;

    private static final float AKRIEN_BAR_INSET_LEFT = 1.5f;

    private static final float AKRIEN_BAR_INSET_RIGHT = 1.5f;

    private AerialFont akrienBold;
    private AerialFont akrienRegular;
    private final ContinualAnimation akrienHealthAnim = new ContinualAnimation();

    private boolean dragging;
    private float dragOffsetX, dragOffsetY;

    @Subscribe
    public void onAttack(AttackEvent event) {
        if (event.getTarget() instanceof LivingEntity living) {
            this.lastAttackedEntity = living;
            this.lastAttackTimeMs = System.currentTimeMillis();
        }
    }

    @Subscribe
    public void onJoinWorld(JoinWorldEvent event) {
        aerialReleaseTargets();
    }

    @Subscribe
    public void onServerDisconnect(ServerDisconnectEvent event) {
        aerialReleaseTargets();
    }

    private void aerialReleaseTargets() {
        this.lastAttackedEntity = null;
        this.renderTarget = null;
        this.fadingTarget = null;
        this.winningCacheTargetId = -1;
        this.winningCacheTick = -1;
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        AerialBloomFilter.begin(BlurConsumer.TARGET_HUD);
        try {
            onRender2DBody(event);
        } finally {
            AerialBloomFilter.end();
        }
    }

    private void onRender2DBody(Render2DEvent event) {
        ensureFontsLoaded();
        if (!dragging) {
            x = xPos.getValue().floatValue();
            y = yPos.getValue().floatValue();
        }
        LocalPlayer self = mc.player;
        if (self == null) {
            return;
        }

        boolean inChat = mc.gui.screen() instanceof ChatScreen;
        boolean inOtherScreen = mc.gui.screen() != null && !inChat;
        if (inOtherScreen) {
            return;
        }

        boolean recentlyAttacked = lastAttackedEntity != null && lastAttackedEntity.isAlive()
                && System.currentTimeMillis() - lastAttackTimeMs < ATTACK_GRACE_MS;

        LivingEntity newTarget;
        if (inChat) {
            newTarget = self;
        } else if (recentlyAttacked && lastAttackedEntity instanceof Player) {
            newTarget = lastAttackedEntity;
        } else {
            newTarget = null;
        }

        if (style.getValue() == TargetHudStyle.AERIAL) {
            renderWithFade(newTarget, target -> drawAerial(event, self, target));
        } else if (style.getValue() == TargetHudStyle.OLD_NOVOLINE) {
            renderWithFade(newTarget, target -> drawOldNovoline(event, self, target));
        } else if (style.getValue() == TargetHudStyle.CREIDA) {
            renderWithFade(newTarget, target -> drawCreida(event, self, target));
        } else if (style.getValue() == TargetHudStyle.BMS) {
            renderWithFade(newTarget, target -> drawBms(event, self, target));
        } else if (style.getValue() == TargetHudStyle.NOVOLINE) {
            renderWithFade(newTarget, target -> drawNovoline(event, self, target));
        } else if (style.getValue() == TargetHudStyle.GODLY) {
            renderWithFade(newTarget, target -> drawGodly(event, self, target));
        } else if (style.getValue() == TargetHudStyle.MODERN) {
            renderWithFade(newTarget, target -> drawModern(event, self, target));
        } else if (style.getValue() == TargetHudStyle.PULSE) {
            renderWithFade(newTarget, target -> drawPulse(event, self, target));
        } else if (style.getValue() == TargetHudStyle.SIMPLE) {
            renderWithFade(newTarget, target -> drawSimple(event, self, target));
        } else if (style.getValue() == TargetHudStyle.CAPSULE) {
            renderWithFade(newTarget, target -> drawCapsule(event, self, target));
        } else if (style.getValue() == TargetHudStyle.AKRIEN) {
            renderWithFade(newTarget, target -> drawAkrien(event, self, target));
        } else {
            renderWithFade(newTarget, target -> drawAdjust(event, self, target));
        }
    }

    private boolean isLeaving(LivingEntity target) {
        if (mc.gui.screen() instanceof ChatScreen) {
            return false;
        }
        return !target.isAlive()
                || System.currentTimeMillis() - lastAttackTimeMs >= ATTACK_GRACE_MS;
    }

    private void handleDragging(float boxW, float boxH) {
        if (!(mc.gui.screen() instanceof ChatScreen)) {
            dragging = false;
            return;
        }
        float mouseX = (float) mc.mouseHandler.getScaledXPos(mc.getWindow());
        float mouseY = (float) mc.mouseHandler.getScaledYPos(mc.getWindow());

        boolean leftPressed = org.lwjgl.glfw.GLFW.glfwGetMouseButton(mc.getWindow().handle(),
                org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;

        if (leftPressed) {
            if (!dragging && isHovered(mouseX, mouseY, x, y, boxW, boxH)) {
                dragging = true;
                dragOffsetX = mouseX - x;
                dragOffsetY = mouseY - y;
            }
            if (dragging) {
                float screenW = mc.getWindow().getGuiScaledWidth();
                float screenH = mc.getWindow().getGuiScaledHeight();
                x = HudDrag.clamp(mouseX - dragOffsetX, boxW, screenW);
                y = HudDrag.clamp(mouseY - dragOffsetY, boxH, screenH);
                xPos.setValue((double) x);
                yPos.setValue((double) y);
            }
        } else {
            dragging = false;
        }
    }

    private static boolean isHovered(float mouseX, float mouseY, float bx, float by, float bw, float bh) {
        return mouseX >= bx && mouseY >= by && mouseX < bx + bw && mouseY < by + bh;
    }

    private void drawDragOutline(GuiGraphicsExtractor extractor) {
        int color = withAlpha(themeAccent1(), 0.8f);
        float t = 1.0f;
        RenderUtil.flatRect(extractor, x - 0.5f, y - 0.5f, currentWidth + 1f, t, color);
        RenderUtil.flatRect(extractor, x - 0.5f, y + currentHeight - 0.5f, currentWidth + 1f, t, color);
        RenderUtil.flatRect(extractor, x - 0.5f, y - 0.5f, t, currentHeight + 1f, color);
        RenderUtil.flatRect(extractor, x + currentWidth - 0.5f, y - 0.5f, t, currentHeight + 1f, color);
    }

    private static int rgba(int r, int g, int b, int a) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int rgb(int r, int g, int b) {
        return rgba(r, g, b, 255);
    }

    private static int withAlpha(int argb, float alphaMul) {
        int a = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 0xFF) * alphaMul)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int darker(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        int r = Math.max(0, (int) (((argb >> 16) & 0xFF) * factor));
        int g = Math.max(0, (int) (((argb >> 8) & 0xFF) * factor));
        int b = Math.max(0, (int) ((argb & 0xFF) * factor));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int ra = (int) (aa + (ba - aa) * t), rr = (int) (ar + (br - ar) * t);
        int rg = (int) (ag + (bg - ag) * t), rb = (int) (ab + (bb - ab) * t);
        return (ra << 24) | (rr << 16) | (rg << 8) | rb;
    }

    private static int themeAccent1() {
        return InterfaceModule.INSTANCE.getTheme().getAccentColor(0, 0).getRGB() | 0xFF000000;
    }

    private static int themeAccent2() {
        return InterfaceModule.INSTANCE.getTheme().getAccentColor(0, 50).getRGB() | 0xFF000000;
    }

    private void drawHeadSquare(GuiGraphicsExtractor extractor, Player player, float x, float y, float size) {
        if (!(player instanceof AbstractClientPlayer clientPlayer)) {
            return;
        }
        Identifier skin = clientPlayer.getSkin().body().texturePath();
        float pixelScale = size / 8f;
        extractor.pose().pushMatrix();
        extractor.pose().translate(x, y);
        extractor.pose().scale(pixelScale, pixelScale);
        extractor.blit(RenderPipelines.GUI_TEXTURED, skin, 0, 0, 8f, 8f, 8, 8, 64, 64);
        extractor.blit(RenderPipelines.GUI_TEXTURED, skin, 0, 0, 40f, 8f, 8, 8, 64, 64);
        extractor.pose().popMatrix();
    }

    private void drawItem(GuiGraphicsExtractor extractor, ItemStack stack, float x, float y, float itemScale) {
        extractor.pose().pushMatrix();
        extractor.pose().translate(x, y);
        extractor.pose().scale(itemScale, itemScale);
        extractor.item(stack, 0, 0);
        extractor.pose().popMatrix();
    }

    private int winningCacheTick = -1;
    private int winningCacheTargetId = -1;
    private float winningCacheValue;

    private float cachedCalculateWinning(LivingEntity target, LocalPlayer self) {
        int tick = self.tickCount;
        if (tick == winningCacheTick && target.getId() == winningCacheTargetId) {
            return winningCacheValue;
        }
        winningCacheTick = tick;
        winningCacheTargetId = target.getId();
        winningCacheValue = calculateWinning(target, self);
        return winningCacheValue;
    }

    private static float calculateWinning(LivingEntity target, LocalPlayer self) {
        if (!(target instanceof Player)) {
            return 0f;
        }
        float playerHealth = self.getHealth();
        float targetHealth = target.getHealth();
        int guard = 0;
        while (playerHealth > 0f && targetHealth > 0f && guard++ < 200) {
            targetHealth -= damage(self.getMainHandItem(), self, target);
            if (targetHealth <= 0f) {
                break;
            }
            playerHealth -= damage(target.getMainHandItem(), target, self);
        }
        return playerHealth - targetHealth;
    }

    private static float damage(ItemStack stack, LivingEntity attacker, LivingEntity target) {
        float baseDamage = 1.0f;
        if (!stack.isEmpty()) {
            int sharpness = InventoryUtility.calculateEnchantmentLevel(stack, Enchantments.SHARPNESS);
            baseDamage = 1.0f + (float) PlayerUtility.getStackAttackDamage(stack);
            baseDamage += sharpness * 1.25f;
        }
        if (attacker.hasEffect(MobEffects.STRENGTH)) {
            baseDamage += (attacker.getEffect(MobEffects.STRENGTH).getAmplifier() + 1) * 3.0f;
        }

        float totalDamage = baseDamage;
        int totalProtection = 0;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack piece = target.getItemBySlot(slot);
            if (!piece.isEmpty()) {
                totalProtection += InventoryUtility.calculateEnchantmentLevel(piece, Enchantments.PROTECTION);
            }
        }
        double armorValue = target.getAttributeValue(Attributes.ARMOR);
        totalDamage *= (1f - (float) armorValue * 0.04f);
        totalDamage *= (1f - totalProtection * 0.04f);
        if (target.hasEffect(MobEffects.RESISTANCE)) {
            totalDamage *= (1f - (target.getEffect(MobEffects.RESISTANCE).getAmplifier() + 1) * 0.2f);
        }
        return Math.max(0f, totalDamage);
    }

    private static final EquipmentSlot[] ARMOR_SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private static final int ADJUST_BACKGROUND = rgba(20, 20, 20, 115);

    private final Animation adjustBarAnim = new Animation(Easing.LINEAR, 150);
    private final Animation adjustGhostAnim = new Animation(Easing.LINEAR, 450);
    private String adjustLastTargetName;

    private float getScale() {
        return scale.getValue().multiplier();
    }

    private int adjustBarColor(LivingEntity target) {
        if (!healthRatioColor.getValue()) {
            return themeAccent1();
        }
        float ratio = clamp01(target.getHealth() / target.getMaxHealth());
        int red = rgb(250, 42, 68);
        int yellow = rgb(221, 244, 2);
        int green = rgb(48, 246, 6);
        if (ratio < 0.5f) {
            return mix(red, yellow, ratio / 0.5f);
        }
        return mix(yellow, green, (ratio - 0.5f) / 0.5f);
    }

    private static float fontOffsetX(float rawX) {
        return rawX - 1f;
    }

    private static float fontOffsetY(float rawY) {
        return rawY - 3f;
    }

    private void drawScaledText(GuiGraphicsExtractor extractor, AerialFont font, String text,
                                 float rawX, float rawY, float size, int color, float s) {
        if (s >= 1.25f) {
            float drawX = x + (rawX - x) * s;
            float drawY = y + (rawY - y) * s;
            extractor.pose().popMatrix();
            TextRenderUtil.drawString(extractor, font, text, fontOffsetX(drawX), fontOffsetY(drawY), size, color);
            extractor.pose().pushMatrix();
            extractor.pose().translate(x, y);
            extractor.pose().scale(s, s);
            extractor.pose().translate(-x, -y);
        } else {
            TextRenderUtil.drawString(extractor, font, text, fontOffsetX(rawX), fontOffsetY(rawY), size, color);
        }
    }

    private static float adjustNameFontSize(float s) {
        if (s >= 2.0f) return 14.5f;
        if (s >= 1.5f) return 12.5f;
        if (s >= 1.25f) return 10f;
        return ADJUST_NAME_FONT_SIZE;
    }

    private static final float ADJUST_NAME_FONT_SIZE = 8f;

    private static final float BAR_HEIGHT = 3f;

    private static final float ADJUST_ITEM_ORIGIN = 12f;

    private static final float ADJUST_ITEM_STEP = 11f;

    private static final float ADJUST_ITEM_BOX = 12f;

    private static float adjustDamageFontSize(float s) {
        if (s >= 2.0f) return 15f;
        if (s >= 1.5f) return 12f;
        if (s >= 1.25f) return 10f;
        return 8f;
    }

    private static final String ADJUST_WINNING_TEXT = "Winning";
    private static final String ADJUST_LOSING_TEXT = "Losing";

    private static String adjustOutcomeText(LocalPlayer self, LivingEntity target) {
        return self.getHealth() - target.getHealth() >= 0f ? ADJUST_WINNING_TEXT : ADJUST_LOSING_TEXT;
    }

    private void drawAdjust(Render2DEvent event, LocalPlayer self, LivingEntity target) {
        if (!(target instanceof Player player)) {
            return;
        }
        GuiGraphicsExtractor extractor = event.extractor();
        float s = getScale();

        int barColor = adjustBarColor(target);
        int ghostColor = darker(barColor, 0.7f);

        String targetName = StreamerModule.INSTANCE.filter(player.getName().getString());
        String damageText = adjustOutcomeText(self, target);

        float nameWidth = sfFont.stringWidth(targetName, ADJUST_NAME_FONT_SIZE);
        float damageTextWidth = sfFont.stringWidth(damageText, 8f);

        int equipmentCount = player.getMainHandItem().isEmpty() ? 0 : 1;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            if (!player.getItemBySlot(slot).isEmpty()) {
                equipmentCount++;
            }
        }
        float nameRowWidth = 24f + nameWidth + 4f + damageTextWidth + 4f;
        float equipmentRowWidth = ADJUST_ITEM_ORIGIN + equipmentCount * ADJUST_ITEM_STEP + ADJUST_ITEM_BOX
                + 4f + damageTextWidth + 4f;
        currentWidth = Math.max(100f, Math.max(nameRowWidth, equipmentRowWidth));
        currentHeight = 30f;

        handleDragging(currentWidth * s, currentHeight * s);

        float barFullWidth = currentWidth - 4f;
        float healthFrac = target.getHealth() / target.getMaxHealth();
        float targetBarWidth = barFullWidth * healthFrac;

        if (!targetName.equals(adjustLastTargetName)) {
            adjustBarAnim.setValue(targetBarWidth);
            adjustGhostAnim.setValue(targetBarWidth);
            adjustLastTargetName = targetName;
        } else {
            adjustBarAnim.run(targetBarWidth);
            adjustGhostAnim.run(targetBarWidth);
        }
        float barWidth = adjustBarAnim.getValue();
        float ghostBarWidth = adjustGhostAnim.getValue();

        extractor.pose().pushMatrix();
        extractor.pose().translate(x, y);
        extractor.pose().scale(s, s);
        extractor.pose().translate(-x, -y);

        float alpha = this.targetAlpha;
        AerialBlur.drawGlassFlat(extractor, BlurConsumer.TARGET_HUD, x, y, currentWidth,
                currentHeight, ADJUST_BACKGROUND, alpha, null);

        float space = 24.5f;

        float barX = Math.round(x + 2f);
        float barY = Math.round(y + space);

        if (!healthRatioColor.getValue()) {
            Theme theme = InterfaceModule.INSTANCE.getTheme();
            int left = withAlpha(theme.getAccentColor(0, 0).getRGB() | 0xFF000000, alpha);
            int right = withAlpha(theme.getAccentColor(0, 14).getRGB() | 0xFF000000, alpha);
            int ghostLeft = darker(left, 0.7f);
            int ghostRight = darker(right, 0.7f);
            RenderUtil.sharpRectGradient(extractor, barX, barY, ghostBarWidth, BAR_HEIGHT, ghostLeft, ghostRight, null);
            RenderUtil.sharpRectGradient(extractor, barX, barY, barWidth, BAR_HEIGHT, left, right, null);
        } else {
            RenderUtil.sharpRect(extractor, barX, barY,
                    barX + ghostBarWidth, barY + BAR_HEIGHT, withAlpha(ghostColor, alpha));
            RenderUtil.sharpRect(extractor, barX, barY,
                    barX + barWidth, barY + BAR_HEIGHT, withAlpha(barColor, alpha));
        }

        drawHeadSquare(extractor, player, x + 2f, y + 2f, 20f);

        drawScaledText(extractor, sfFont, targetName, x + 25.5f, y + 3.5f, adjustNameFontSize(s), withAlpha(0xFFFFFFFF, alpha), s);

        float itemX = x + ADJUST_ITEM_ORIGIN;
        float itemY = y + 10f;
        ItemStack held = player.getMainHandItem();
        if (!held.isEmpty()) {
            itemX += ADJUST_ITEM_STEP;
            drawItem(extractor, held, itemX + 1f, itemY + 1f, 0.65f);
        }
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack piece = player.getItemBySlot(slot);
            if (!piece.isEmpty()) {
                itemX += ADJUST_ITEM_STEP;
                drawItem(extractor, piece, itemX, itemY, 0.75f);
            }
        }

        float healthDiff = self.getHealth() - target.getHealth();
        int damageColor = healthDiff >= 0f ? rgb(64, 224, 96) : rgb(255, 80, 80);
        drawScaledText(extractor, sfFont, damageText, x + currentWidth - damageTextWidth - 0.5f, y + 16.5f,
                adjustDamageFontSize(s), withAlpha(damageColor, alpha), s);

        if (dragging) {
            drawDragOutline(extractor);
        }

        extractor.pose().popMatrix();
    }

    private final Animation fadeAnim = new Animation(Easing.EASE_OUT_EXPO, 200);
    private float targetAlpha = 0f;
    private LivingEntity fadingTarget;

    private final Animation pulseHealthAnim = new Animation(Easing.EASE_OUT_EXPO, 700);
    private float pulseHealthAnimFrac = 1f;
    private float pulseLastTrueHealthFrac = 1f;

    private int lastSeenHurtTime;

    private void renderWithFade(LivingEntity newTarget, Consumer<LivingEntity> drawFn) {
        this.renderTarget = newTarget;
        boolean hasTarget = newTarget != null;

        if (hasTarget) {
            this.fadingTarget = null;
        } else if (this.fadingTarget == null) {
            this.fadingTarget = this.lastAttackedEntity;
        }

        fadeAnim.setDuration((long) (200.0 / InterfaceModule.INSTANCE.getFadeSpeed()));
        fadeAnim.run(hasTarget ? 1f : 0f);
        this.targetAlpha = fadeAnim.getValue();

        LivingEntity drawTarget = hasTarget ? newTarget : this.fadingTarget;
        if (drawTarget == null || this.targetAlpha <= 0.01f) {
            if (!hasTarget) {
                this.fadingTarget = null;
            }
            return;
        }
        drawFn.accept(drawTarget);
    }

    private void drawPulsePips(GuiGraphicsExtractor extractor, float px, float py, float totalW, float pipH,
                                float frac, IntFunction<Integer> colorFor) {
        float pipGap = 1.8f;
        float pipW = (totalW - pipGap * (PULSE_PIPS - 1)) / PULSE_PIPS;
        if (pipW <= 0.1f) {
            return;
        }
        float radius = Math.min(pipW, pipH) / 2f;
        float exact = frac * PULSE_PIPS;
        for (int i = 0; i < PULSE_PIPS; i++) {
            float fill = Math.max(0f, Math.min(1f, exact - i));
            if (fill <= 0.004f) {
                continue;
            }
            int c = colorFor.apply(i);
            int drawn = fill >= 1f ? c : withAlpha(c, fill);
            RenderUtil.roundedRect(extractor, px + i * (pipW + pipGap), py, pipW, pipH, radius, drawn);
        }
    }

    private void spawnPulseSparks(LivingEntity target, float padding, float stripeW, float headSize, int sparkColor) {
        int hurtTime = target.hurtTime;
        boolean freshHit = hurtTime > lastSeenHurtTime;
        lastSeenHurtTime = hurtTime;
        if (!freshHit) {
            return;
        }

        int count = 4 + (int) (Math.random() * 5);
        for (int i = 0; i < count; i++) {
            float sx = x + padding + stripeW / 2f;
            float sy = y + padding + (float) (Math.random() * headSize);
            double angle = Math.toRadians(150 + Math.random() * 60);
            float speed = 40f + (float) (Math.random() * 70f);
            float vx = (float) (Math.cos(angle) * speed);
            float vy = (float) (Math.sin(angle) * speed) - 30f;
            float size = 2.5f + (float) (Math.random() * 2f);
            PulseSparkManager.add(new PulseSpark(sx, sy, vx, vy, size, new java.awt.Color(sparkColor, true)));
        }
    }

    private void drawPulse(Render2DEvent event, LocalPlayer self, LivingEntity target) {
        GuiGraphicsExtractor extractor = event.extractor();
        boolean isPlayer = target instanceof Player;
        String targetName = StreamerModule.INSTANCE.filter(target.getName().getString());

        float padding = 5f;
        float stripeW = 2.5f;
        float gapAfterStripe = 5f;
        float headSize = 25f;
        float gapAfterHead = 7f;
        float equipRowH = 3f;
        float equipSlotSize = 9f;

        float winningValue = isPlayer ? cachedCalculateWinning(target, self) : 0f;
        String winLoseLabel = winningValue > 0 ? "Winning " : "Losing ";
        int winLoseColor = winningValue > 0 ? rgb(90, 230, 145) : rgb(245, 95, 95);
        float winLoseLabelW = isPlayer ? sfFont.stringWidth(winLoseLabel, 7f) : 0f;

        float absorption = isPlayer ? target.getAbsorptionAmount() : 0f;
        float hp = target.getHealth() + absorption;
        String hpText = HEALTH_DF.format(hp);
        String distText = HEALTH_DF.format(self.distanceTo(target)) + "m";
        float heartGap = 3f;
        float heartW = heartFont.stringWidth(HEART_GLYPH, 8f);

        float nameWidth = sfBoldFont.stringWidth(targetName, 8f);
        float hpNumW = sfBoldFont.stringWidth(hpText.length() > 2 ? hpText : "88", 8f);
        float hpBlockW = Math.max(hpNumW + heartGap + heartW, sfFont.stringWidth(distText, 7f));
        float contentW = Math.max(74f, winLoseLabelW + nameWidth);
        currentWidth = padding + stripeW + gapAfterStripe + headSize + gapAfterHead
                + contentW + 8f + hpBlockW + padding;
        currentHeight = padding * 2f + headSize + equipRowH;

        handleDragging(currentWidth, currentHeight);

        double maxHealth = target.getMaxHealth();
        float trueHealthFrac = clamp01((float) (hp / maxHealth));
        float excessFrac = maxHealth > 0 ? clamp01((float) ((hp - maxHealth) / maxHealth)) : 0f;

        if (Math.abs(trueHealthFrac - pulseLastTrueHealthFrac) > 0.0001f) {
            pulseHealthAnim.setValue(pulseHealthAnimFrac);
            pulseLastTrueHealthFrac = trueHealthFrac;
        }
        pulseHealthAnim.run(trueHealthFrac);
        pulseHealthAnimFrac = pulseHealthAnim.getValue();

        float alpha = this.targetAlpha;
        float hurt = target.hurtTime <= 0 ? 0f : clamp01(target.hurtTime / 10f);
        float round = 6f;

        AerialBlur.drawGlass(extractor, BlurConsumer.TARGET_HUD, x, y, currentWidth, currentHeight,
                round, rgba(38, 38, 42, 120), alpha, null);

        int stripeColor = mix(themeAccent1(), 0xFFFFFFFF, hurt * 0.75f);
        float stripeH = currentHeight - padding * 2f;
        RenderUtil.roundedRect(extractor, x + padding, y + padding, stripeW, stripeH, stripeW / 2f, withAlpha(stripeColor, alpha));

        spawnPulseSparks(target, padding, stripeW, headSize, stripeColor);
        PulseSparkManager.render(extractor);

        float headGrow = (headSize - 20f) / 2f;
        float headX = x + padding + stripeW + gapAfterStripe;
        float headY = y + padding + 2f;
        float textX = headX + headSize + gapAfterHead;
        float pipH = 3.5f;
        float pipY = y + currentHeight - padding - pipH - 1f;

        boolean ratioColor = healthRatioColor.getValue();
        int lowColor = rgb(250, 62, 78);
        int midColor = rgb(247, 191, 42);
        int fullColor = rgb(64, 224, 122);
        int ratioBase = trueHealthFrac < 0.33f ? lowColor : trueHealthFrac < 0.55f ? midColor : fullColor;

        if (pulseHealthMode.getValue() == PulseHealthMode.BAR) {
            RenderUtil.roundedRect(extractor, textX, pipY, contentW, pipH, pipH / 2f, withAlpha(rgba(255, 255, 255, 28), alpha));

            float ghostW = contentW * pulseHealthAnimFrac;
            if (ghostW > 0.5f) {
                int ghostBase = ratioColor ? ratioBase : mix(themeAccent1(), themeAccent2(), 0.5f);
                RenderUtil.roundedRect(extractor, textX, pipY, ghostW, pipH, pipH / 2f,
                        withAlpha(rgba(redOf(ghostBase) / 3, greenOf(ghostBase) / 3, blueOf(ghostBase) / 3, 235), alpha));
            }

            float liveW = contentW * trueHealthFrac;
            if (liveW > 0.5f) {
                if (ratioColor) {
                    RenderUtil.roundedRect(extractor, textX, pipY, liveW, pipH, pipH / 2f, withAlpha(mix(ratioBase, 0xFFFFFFFF, hurt * 0.45f), alpha));
                } else {
                    int flatColor = mix(themeAccent1(), themeAccent2(), 0.5f);
                    RenderUtil.roundedRect(extractor, textX, pipY, liveW, pipH, pipH / 2f, withAlpha(mix(flatColor, 0xFFFFFFFF, hurt * 0.45f), alpha));
                }
                if (excessFrac > 0.001f) {
                    float excessW = Math.max(0.5f, contentW * excessFrac);
                    RenderUtil.roundedRect(extractor, textX, pipY, excessW, pipH, pipH / 2f, withAlpha(rgb(250, 204, 21), alpha));
                }
            }
        } else {
            IntFunction<Integer> trackColor = i -> withAlpha(rgba(255, 255, 255, 28), alpha);
            drawPulsePips(extractor, textX, pipY, contentW, pipH, 1f, trackColor);

            IntFunction<Integer> ghostColorFn = i -> {
                int base = ratioColor ? ratioBase : mix(themeAccent1(), themeAccent2(), PULSE_PIPS <= 1 ? 0f : (float) i / (PULSE_PIPS - 1));
                return withAlpha(rgba(redOf(base) / 3, greenOf(base) / 3, blueOf(base) / 3, 235), alpha);
            };
            drawPulsePips(extractor, textX, pipY, contentW, pipH, pulseHealthAnimFrac, ghostColorFn);

            int excessPips = excessFrac > 0.001f ? Math.round(excessFrac * PULSE_PIPS) : 0;
            IntFunction<Integer> liveColorFn = i -> {
                if (i < excessPips) {
                    return withAlpha(rgb(250, 204, 21), alpha);
                }
                int base = ratioColor ? ratioBase : mix(themeAccent1(), themeAccent2(), PULSE_PIPS <= 1 ? 0f : (float) i / (PULSE_PIPS - 1));
                return withAlpha(mix(base, 0xFFFFFFFF, hurt * 0.45f), alpha);
            };
            drawPulsePips(extractor, textX, pipY, contentW, pipH, trueHealthFrac, liveColorFn);
        }

        if (isPlayer) {
            TextRenderUtil.drawString(extractor, sfFont, winLoseLabel, fontOffsetX(textX), fontOffsetY(y + padding + 1.5f + headGrow), 7f, withAlpha(winLoseColor, alpha));
        }
        TextRenderUtil.drawString(extractor, sfBoldFont, targetName, fontOffsetX(textX + winLoseLabelW), fontOffsetY(y + padding + 0.5f + headGrow), 8f, withAlpha(0xFFFFFFFF, alpha));

        float hpTextW = sfBoldFont.stringWidth(hpText, 8f);
        float heartX = x + currentWidth - padding - heartW;
        float hpX = heartX - heartGap - hpTextW;
        TextRenderUtil.drawString(extractor, sfBoldFont, hpText, fontOffsetX(hpX), fontOffsetY(y + padding + 0.5f + headGrow), 8f,
                withAlpha(mix(0xFFFFFFFF, themeAccent1(), 0.35f), alpha));
        TextRenderUtil.drawString(extractor, heartFont, HEART_GLYPH, fontOffsetX(heartX), fontOffsetY(y + padding + 1.5f + headGrow), 8f, withAlpha(rgb(235, 55, 65), alpha));

        float distW = sfFont.stringWidth(distText, 7f);
        TextRenderUtil.drawString(extractor, sfFont, distText, fontOffsetX(x + currentWidth - padding - distW), fontOffsetY(pipY - 4.5f), 7f, withAlpha(rgba(255, 255, 255, 130), alpha));

        if (isPlayer) {
            drawHeadSquare(extractor, (Player) target, headX, headY, headSize);
        }

        if (isPlayer) {
            Player playerTarget = (Player) target;
            ItemStack[] equipment = {
                    playerTarget.getItemBySlot(EquipmentSlot.HEAD),
                    playerTarget.getItemBySlot(EquipmentSlot.CHEST),
                    playerTarget.getItemBySlot(EquipmentSlot.LEGS),
                    playerTarget.getItemBySlot(EquipmentSlot.FEET),
                    playerTarget.getMainHandItem(),
            };
            float slotGap = 2f;
            float slotScale = 0.5f;
            float itemOffset = (equipSlotSize - 16f * slotScale) / 2f;
            float nameTextTop = y + padding + 0.5f + headGrow;
            float nameTextBottom = nameTextTop + 10f;
            float midY = (nameTextBottom + pipY) / 2f;
            float equipY = midY - equipSlotSize / 2f - 2f;
            float slotX = textX;
            for (ItemStack stack : equipment) {
                RenderUtil.roundedRect(extractor, slotX, equipY, equipSlotSize, equipSlotSize, 2f, withAlpha(rgba(0, 0, 0, 70), alpha));
                if (!stack.isEmpty()) {
                    drawItem(extractor, stack, slotX + itemOffset, equipY + itemOffset, slotScale);
                }
                slotX += equipSlotSize + slotGap;
            }
        }

        if (dragging) {
            drawDragOutline(extractor);
        }
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static int redOf(int argb) {
        return (argb >> 16) & 0xFF;
    }

    private static int greenOf(int argb) {
        return (argb >> 8) & 0xFF;
    }

    private static int blueOf(int argb) {
        return argb & 0xFF;
    }

    private final Animation simpleHealthAnim = new Animation(Easing.EASE_OUT_EXPO, 250);

    private static int ratioColor(float frac) {
        if (frac < 0.33f) return rgb(250, 62, 78);
        if (frac < 0.55f) return rgb(247, 191, 42);
        return rgb(64, 224, 122);
    }

    private final Animation modernScaleAnim = new Animation(Easing.EASE_OUT_ELASTIC, 500);
    private final Animation modernHealthAnim = new Animation(Easing.EASE_OUT_QUINT, 250);

    private void drawModern(Render2DEvent event, LocalPlayer self, LivingEntity target) {
        if (!(target instanceof AbstractClientPlayer clientPlayer)) {
            return;
        }
        GuiGraphicsExtractor extractor = event.extractor();

        boolean leaving = isLeaving(target);
        modernScaleAnim.setDuration(leaving ? 400L : 850L);
        modernScaleAnim.setEasing(leaving ? Easing.EASE_IN_BACK : Easing.EASE_OUT_ELASTIC);
        modernScaleAnim.run(leaving ? 0f : 1f);
        float scale = modernScaleAnim.getValue();
        if (scale <= 0f) {
            return;
        }

        String name = target.getName().getString();
        String outcome = cachedCalculateWinning(target, self) > 0f ? "Winning:" : "Losing:";

        float nameWidth = riseMediumFont.stringWidth(name, MODERN_FONT_SIZE);
        float outcomeWidth = riseLightFont.stringWidth(outcome, MODERN_FONT_SIZE);

        float maxHealth = Math.max(1f, target.getMaxHealth());
        float health = Math.min(target.isAlive() ? Math.round(target.getHealth() * 10f) / 10f : 0f, maxHealth);
        String healthText = String.valueOf(health);
        float healthTextWidth = riseMediumFont.stringWidth(healthText, MODERN_FONT_SIZE);

        float barWidth = Math.max(outcomeWidth + nameWidth + 35f - healthTextWidth, 65f);
        modernHealthAnim.run(health / maxHealth * barWidth);
        float barFill = modernHealthAnim.getValue();

        float hurt = target.hurtTime == 0 ? 0f
                : (target.hurtTime - event.partialTick()) * 0.5f;
        float hurtHalf = hurt / 2f;

        float cardWidth = 48f + barWidth + 4f + healthTextWidth + 8f;
        float cardHeight = 48f;
        currentWidth = cardWidth;
        currentHeight = cardHeight;
        handleDragging(cardWidth, cardHeight);

        float alpha = this.targetAlpha;

        int top = RISE_PANEL;
        int bottom = RISE_PANEL;
        int textPrimary = themeAccent1();
        int textSecondary = themeAccent2();
        if (riseBackground.getValue() == RiseBackground.TINT) {
            int a1 = InterfaceModule.INSTANCE.getTheme().getAccentColor(x, y).getRGB();
            int a2 = InterfaceModule.INSTANCE.getTheme().getAccentColor(x, y + cardHeight).getRGB();
            top = rgba(((a1 >> 16) & 0xFF) / 5, ((a1 >> 8) & 0xFF) / 5, (a1 & 0xFF) / 5, 128);
            bottom = rgba(((a2 >> 16) & 0xFF) / 5, ((a2 >> 8) & 0xFF) / 5, (a2 & 0xFF) / 5, 128);
        } else if (riseBackground.getValue() == RiseBackground.SOLID) {
            top = withAlpha(themeAccent1(), 128 / 255f);
            bottom = withAlpha(themeAccent2(), 128 / 255f);
            textPrimary = 0xFFFFFFFF;
            textSecondary = rgb(164, 164, 164);
        }

        extractor.pose().pushMatrix();
        extractor.pose().translate((x + cardWidth / 2f) * (1f - scale), (y + cardHeight / 2f) * (1f - scale));
        extractor.pose().scale(scale, scale);

        AerialBlur.drawGlassGradient(extractor, BlurConsumer.TARGET_HUD, x, y, cardWidth - 1f,
                cardHeight, 16f, top, bottom, true, alpha, null);

        float textX = x + 8f + 32f + 7f;
        TextRenderUtil.drawString(extractor, riseLightFont, outcome,
                fontOffsetX(textX), fontOffsetY(y + 8f + 4f + 2f),
                MODERN_FONT_SIZE, withAlpha(0xFFFFFFFF, alpha));
        TextRenderUtil.drawString(extractor, riseMediumFont, name,
                fontOffsetX(textX + outcomeWidth + 3f), fontOffsetY(y + 8f + 4f + 2.5f),
                MODERN_FONT_SIZE, withAlpha(textPrimary, alpha));

        float barX = textX;
        float barY = y + 8f + 32f - 4f - 7f;

        int trackTop = withAlpha(RISE_PANEL, alpha / 1.7f);
        RenderUtil.roundedRectGradient(extractor, barX, barY, barWidth, 6f, 3f,
                trackTop, withAlpha(RISE_PANEL, alpha), true, null);
        RenderUtil.roundedRectGradient(extractor, barX, barY, barFill, 6f, 3f,
                withAlpha(textSecondary, alpha), withAlpha(textPrimary, alpha), true, null);
        TextRenderUtil.drawString(extractor, riseMediumFont, healthText,
                fontOffsetX(barX + barWidth + 4f), fontOffsetY(y + 8f + 32f - 4f - 8f),
                MODERN_FONT_SIZE, withAlpha(textPrimary, alpha));

        float headX = x + 8f + hurtHalf;
        float headY = y + 8f + hurtHalf;
        float headSize = 32f - hurt;
        RenderUtil.dropShadow(extractor, 3, headX, headY, headSize, headSize, 20.0, RISE_ROUND * 2f, null);

        int headTint = withAlpha(mix(0xFFFFFFFF, 0xFFFF0000, Math.min(1f, hurt / 9f)), alpha);
        RenderUtil.roundedHead(extractor, clientPlayer.getSkin().body().texturePath(),
                headX, headY, headSize, RISE_ROUND * 2f, headTint, null);

        RenderUtil.roundedOutline(extractor, headX - 0.5f, headY - 0.5f, headSize + 1f, headSize + 1f,
                RISE_ROUND * 2f, 0.5f, withAlpha(rgba(0, 0, 0, 40), alpha), null);

        spawnModernParticles(hurt);
        HudParticles.drawGlow(extractor);
        HudParticles.draw(extractor);

        extractor.pose().popMatrix();

        if (dragging) {
            drawDragOutline(extractor);
        }
    }

    private void spawnModernParticles(float hurt) {
        if (!riseParticles.getValue() || hurt <= 0f) {
            return;
        }
        for (int i = 0; i < hurt * Math.random() / 2.0; i++) {
            HudParticles.spawn(new HudParticle(
                    x + 20f, y + 20f,
                    (float) (Math.random() - 0.5) * 1.7f,
                    (float) (Math.random() - 0.5) * 1.7f));
        }
    }

    private final Animation godlyScaleAnim = new Animation(Easing.EASE_OUT_ELASTIC, 500);
    private final Animation godlyHealthAnim = new Animation(Easing.EASE_OUT_QUINT, 250);

    private static float heartTop(float numberY, float heartSize) {
        float numberCenter = numberY + inkCenter(riseRegularFont, "H", GODLY_FONT_SIZE);
        return numberCenter - inkCenter(heartFont, HEART_GLYPH, heartSize);
    }

    private static float inkCenter(AerialFont face, String glyph, float size) {
        GlyphQuad[] quads = face.layout(glyph, 0.0f, 0.0f, size);
        if (quads.length == 0) {
            return size * 0.5f;
        }
        return (quads[0].y0 + quads[0].y1) * 0.5f;
    }

    private void drawGodly(Render2DEvent event, LocalPlayer self, LivingEntity target) {
        if (!(target instanceof AbstractClientPlayer clientPlayer)) {
            return;
        }
        GuiGraphicsExtractor extractor = event.extractor();

        boolean leaving = isLeaving(target);
        godlyScaleAnim.setDuration(leaving ? 400L : 850L);
        godlyScaleAnim.setEasing(leaving ? Easing.EASE_IN_BACK : Easing.EASE_OUT_ELASTIC);
        godlyScaleAnim.run(leaving ? 0f : 1f);
        float scale = godlyScaleAnim.getValue();
        if (scale <= 0f) {
            return;
        }

        String name = target.getName().getString();
        float nameWidth = riseRegularFont.stringWidth(name, GODLY_FONT_SIZE);

        float maxHealth = Math.max(1f, target.getMaxHealth());
        float rawHealth = target.isAlive() ? target.getHealth() : 0f;
        float health = Math.min(Math.round(rawHealth * 10f) / 10f, maxHealth);

        float barWidth = Math.max(nameWidth + 15f, 70f);
        godlyHealthAnim.run(health / maxHealth * barWidth);
        float barFill = godlyHealthAnim.getValue();

        float hurt = target.hurtTime == 0 ? 0f : (target.hurtTime - event.partialTick()) * 0.5f;
        float hurtHalf = hurt / 2f;

        float cardWidth = 44f + barWidth + 4f + 6f;
        float cardHeight = 44f;
        currentWidth = cardWidth;
        currentHeight = cardHeight;
        handleDragging(cardWidth, cardHeight);

        float alpha = this.targetAlpha;
        int accent1 = themeAccent1();
        int accent2 = themeAccent2();

        float panelWidth = cardWidth - 3.5f;
        float panelHeight = cardHeight - 4f;

        extractor.pose().pushMatrix();
        extractor.pose().translate((x + cardWidth / 2f) * (1f - scale), (y + cardHeight / 2f) * (1f - scale));
        extractor.pose().scale(scale, scale);

        AerialBlur.drawGlass(extractor, BlurConsumer.TARGET_HUD, x, y, panelWidth, panelHeight,
                8f, RISE_PANEL, alpha, null);

        float textX = x + 6f + 32f + 7f - 2.5f;
        TextRenderUtil.drawString(extractor, riseRegularFont, name,
                fontOffsetX(textX), fontOffsetY(y + 6f + 4f - 2f),
                GODLY_FONT_SIZE, withAlpha(accent1, alpha));

        String healthText = String.valueOf(Math.round(rawHealth));
        float heartSize = GODLY_FONT_SIZE;
        float heartX = x + 32f + 11f;
        float numberY = fontOffsetY(y + 6f + 4f + 8f);
        TextRenderUtil.drawString(extractor, heartFont, HEART_GLYPH,
                heartX, heartTop(numberY, heartSize), heartSize, withAlpha(rgb(170, 0, 0), alpha));
        TextRenderUtil.drawString(extractor, riseRegularFont, healthText,
                fontOffsetX(heartX + heartFont.stringWidth(HEART_GLYPH, heartSize) + 2f),
                numberY,
                GODLY_FONT_SIZE, withAlpha(0xFFFFFFFF, alpha));

        float barX = textX;
        float barY = y + 6f + 32f - 4f - 6f;
        RenderUtil.roundedRectGradient(extractor, barX, barY, barWidth, 6f, 3f,
                withAlpha(RISE_PANEL, alpha / 1.7f), withAlpha(RISE_PANEL, alpha), true, null);

        RenderUtil.roundedRectGradient(extractor, barX, barY, barFill, 6f, 3f,
                withAlpha(accent2, alpha), withAlpha(accent1, alpha), false, null);

        float headX = x + 6f + hurtHalf - 2f;
        float headY = y + 6f + hurtHalf - 2f;
        float headSize = 32f - hurt;
        RenderUtil.dropShadow(extractor, 3, headX, headY, headSize, headSize, 20.0, RISE_ROUND * 2f, null);

        int headTint = withAlpha(mix(0xFFFFFFFF, 0xFFFF0000, Math.min(1f, hurt / 9f)), alpha);
        RenderUtil.roundedHead(extractor, clientPlayer.getSkin().body().texturePath(),
                headX, headY, headSize, RISE_ROUND * 2f, headTint, null);
        RenderUtil.roundedOutline(extractor, headX - 0.5f, headY - 0.5f, headSize + 1f, headSize + 1f,
                RISE_ROUND * 2f, 0.5f, withAlpha(rgba(0, 0, 0, 40), alpha), null);

        HudParticles.drawGlow(extractor);
        HudParticles.draw(extractor);

        extractor.pose().popMatrix();

        if (dragging) {
            drawDragOutline(extractor);
        }
    }

    private final Animation novolineWidthAnim = new Animation(Easing.EASE_OUT_EXPO, 250);
    private final Animation novolineHealthAnim = new Animation(Easing.EASE_OUT_EXPO, 250);
    private LivingEntity novolineLastTarget;
    private float novolineLastWidth;

    private void drawNovoline(Render2DEvent event, LocalPlayer self, LivingEntity target) {
        GuiGraphicsExtractor extractor = event.extractor();
        net.minecraft.client.gui.Font vanilla = mc.font;

        boolean leaving = isLeaving(target);
        if (leaving) {
            novolineHealthAnim.reset();
            novolineWidthAnim.reset();
            novolineLastTarget = null;
            return;
        }

        String name = target.getName().getString();
        float nameWidth = vanilla.width(name);

        float maxHealth = Math.max(1f, target.getMaxHealth());
        float health = Math.min(Math.round(target.getHealth() * 10f) / 10f, maxHealth);
        float percent = health / maxHealth * 100f;

        float wantedWidth = 74f + nameWidth;
        if (novolineLastTarget != target) {
            novolineWidthAnim.reset();
            novolineWidthAnim.setValue(novolineLastWidth);
            novolineLastTarget = target;
        }
        novolineWidthAnim.run(wantedWidth);
        float cardWidth = novolineWidthAnim.getValue();
        float cardHeight = 42f;
        currentWidth = cardWidth;
        currentHeight = cardHeight;
        handleDragging(cardWidth, cardHeight);

        float alpha = this.targetAlpha;

        RenderUtil.flatRect(extractor, x, y, cardWidth, cardHeight, withAlpha(rgba(40, 40, 40, 255), alpha));
        extractor.text(vanilla, name, (int) (x + 44f), (int) (y + 10f), withAlpha(0xFFFFFFFF, alpha));

        float barWidth = 26f + nameWidth;
        RenderUtil.flatRect(extractor, x + 44f, y + 22f, barWidth, 11f, withAlpha(rgba(21, 21, 21, 150), alpha));

        float instantFill = barWidth * (health / maxHealth);
        novolineHealthAnim.run(instantFill);
        float trailFill = novolineHealthAnim.getValue();

        RenderUtil.flatRect(extractor, x + 44f, y + 22f, trailFill, 11f,
                withAlpha(darker(themeAccent2(), 0.5f), alpha));
        RenderUtil.flatRect(extractor, x + 44f, y + 22f, instantFill, 11f,
                withAlpha(themeAccent1(), alpha));

        String percentText = String.format("%.1f%%", percent);
        float percentWidth = vanilla.width(percentText);
        extractor.text(vanilla, percentText,
                (int) (x + 44f + barWidth / 2f - percentWidth / 2f), (int) (y + 24.5f),
                withAlpha(0xFFFFFFFF, alpha));

        drawHeadSquare(extractor, target instanceof Player player ? player : null, x + 1f, y + 1f, 40f);

        novolineLastWidth = cardWidth;

        if (dragging) {
            drawDragOutline(extractor);
        }
    }

    private final Animation oldNovolineBarAnim = new Animation(Easing.LINEAR, 150);
    private final Animation oldNovolineGhostAnim = new Animation(Easing.LINEAR, 450);
    private LivingEntity oldNovolineLastTarget;

    private static final float OLD_NOVOLINE_HEIGHT = 43f;

    private static final float OLD_NOVOLINE_MIN_BAR = 112f;

    private static final float OLD_NOVOLINE_NAME_PADDING = 12f;

    private static final float OLD_NOVOLINE_TEXT_GAP = 5f;
    private static final float OLD_NOVOLINE_RIGHT_MARGIN = 6f;

    private static final float OLD_NOVOLINE_BORDER = 0.95f;
    private static final float OLD_NOVOLINE_BAR_X = 45f;

    private static final float OLD_NOVOLINE_BAR_H = 7.8f;
    private static final float OLD_NOVOLINE_BAR_Y = 22.65f - OLD_NOVOLINE_BAR_H / 2f;

    private static final float OLD_NOVOLINE_PORTRAIT = 40f;

    private static final float OLD_NOVOLINE_FONT_SCALE = 1f;

    private static final float OLD_NOVOLINE_MODEL_MARGIN = 5f;

    private static final float OLD_NOVOLINE_MODEL_OFFSET = 0f;

    private static final DecimalFormat OLD_NOVOLINE_HEALTH_DF = new DecimalFormat("0.0");

    private void drawOldNovoline(Render2DEvent event, LocalPlayer self, LivingEntity target) {
        GuiGraphicsExtractor extractor = event.extractor();

        float health = target.getHealth() / 2f;
        float maxHealth = Math.max(0.5f, target.getMaxHealth() / 2f);
        float fraction = Math.min(health / maxHealth, 1f);

        String targetName = StreamerModule.INSTANCE.filter(target.getName().getString());
        float nameWidth = novolineFont.width(targetName, OLD_NOVOLINE_FONT_SCALE);
        float barTrackWidth = Math.max(OLD_NOVOLINE_MIN_BAR, nameWidth + OLD_NOVOLINE_NAME_PADDING);
        float cardWidth = OLD_NOVOLINE_PORTRAIT + OLD_NOVOLINE_TEXT_GAP
                + barTrackWidth + OLD_NOVOLINE_RIGHT_MARGIN;

        float wantedWidth = barTrackWidth * fraction;
        if (oldNovolineLastTarget != target) {
            oldNovolineLastTarget = target;
            oldNovolineBarAnim.setValue(wantedWidth);
            oldNovolineGhostAnim.setValue(wantedWidth);
        } else {
            oldNovolineBarAnim.run(wantedWidth);
            oldNovolineGhostAnim.run(wantedWidth);
        }
        float liveWidth = oldNovolineBarAnim.getValue();
        float ghostWidth = oldNovolineGhostAnim.getValue();

        currentWidth = cardWidth;
        currentHeight = OLD_NOVOLINE_HEIGHT;
        handleDragging(cardWidth, OLD_NOVOLINE_HEIGHT);

        float alpha = this.targetAlpha;

        RenderUtil.flatRect(extractor, x, y, OLD_NOVOLINE_PORTRAIT, OLD_NOVOLINE_HEIGHT,
                withAlpha(rgba(59, 59, 59, 255), alpha));
        RenderUtil.flatRect(extractor, x + OLD_NOVOLINE_PORTRAIT, y,
                cardWidth - OLD_NOVOLINE_PORTRAIT, OLD_NOVOLINE_HEIGHT,
                withAlpha(rgba(78, 78, 78, 255), alpha));

        int border = withAlpha(rgba(65, 65, 65, 255), alpha);
        RenderUtil.flatRect(extractor, x, y, cardWidth, OLD_NOVOLINE_BORDER, border);
        RenderUtil.flatRect(extractor, x, y + OLD_NOVOLINE_HEIGHT - OLD_NOVOLINE_BORDER,
                cardWidth, OLD_NOVOLINE_BORDER, border);
        RenderUtil.flatRect(extractor, x, y, OLD_NOVOLINE_BORDER, OLD_NOVOLINE_HEIGHT, border);
        RenderUtil.flatRect(extractor, x + cardWidth - OLD_NOVOLINE_BORDER, y,
                OLD_NOVOLINE_BORDER, OLD_NOVOLINE_HEIGHT, border);

        drawOldNovolinePortrait(extractor, target, event.partialTick());

        novolineFont.drawWithShadow(extractor, targetName,
                (float) Math.round(x + OLD_NOVOLINE_BAR_X), (float) Math.round(y + 7f),
                OLD_NOVOLINE_FONT_SCALE, withAlpha(0xFFFFFFFF, alpha));

        float barX = Math.round(x + OLD_NOVOLINE_BAR_X);
        float barY = Math.round(y + OLD_NOVOLINE_BAR_Y);
        RenderUtil.flatRect(extractor, barX, barY, barTrackWidth, OLD_NOVOLINE_BAR_H,
                withAlpha(rgba(40, 40, 40, 255), alpha));

        boolean ratioColor = healthRatioColor.getValue();
        int barLeft = ratioColor ? adjustBarColor(target) : themeAccent1();
        int barRight = ratioColor ? barLeft : themeAccent2();

        int ghostLeft = darker(barLeft, 0.7f);
        int ghostRight = darker(barRight, 0.7f);

        drawOldNovolineBar(extractor, barX, barY, barTrackWidth, ghostWidth,
                withAlpha(ghostLeft, alpha), withAlpha(ghostRight, alpha));
        drawOldNovolineBar(extractor, barX, barY, barTrackWidth, liveWidth,
                withAlpha(barLeft, alpha), withAlpha(barRight, alpha));

        String healthText = OLD_NOVOLINE_HEALTH_DF.format(health);
        float figureX = (float) Math.round(x + OLD_NOVOLINE_BAR_X);
        float figureY = (float) Math.round(y + 31f);
        novolineFont.drawWithShadow(extractor, healthText, figureX, figureY,
                OLD_NOVOLINE_FONT_SCALE, withAlpha(0xFFFFFFFF, alpha));

        float heartX = figureX + novolineFont.width(healthText, OLD_NOVOLINE_FONT_SCALE) + 3f;
        float heartSize = 11f;
        float lineInkCenter = novolineFont.inkCenter(healthText, OLD_NOVOLINE_FONT_SCALE);

        TextRenderUtil.drawString(extractor, heartFont, HEART_GLYPH,
                heartX, figureY + lineInkCenter - inkCenter(heartFont, HEART_GLYPH, heartSize),
                heartSize, withAlpha(rgb(255, 72, 82), alpha));

        if (dragging) {
            drawDragOutline(extractor);
        }
    }

    private void drawOldNovolinePortrait(GuiGraphicsExtractor extractor, LivingEntity target, float partialTick) {
        EntityRenderer<? super LivingEntity, ?> renderer =
                mc.getEntityRenderDispatcher().getRenderer(target);
        EntityRenderState state = renderer.createRenderState(target, partialTick);
        state.shadowPieces.clear();
        state.outlineColor = 0;

        if (state instanceof LivingEntityRenderState living) {
            living.bodyRot = 180f;
            living.yRot = 0f;
            living.xRot = 0f;
            living.boundingBoxWidth /= living.scale;
            living.boundingBoxHeight /= living.scale;
            living.scale = 1f;
        }

        float bodyHeight = Math.max(0.1f, state.boundingBoxHeight);
        float scale = (OLD_NOVOLINE_HEIGHT - OLD_NOVOLINE_MODEL_MARGIN * 2f) / bodyHeight;

        int boxX0 = Math.round(x);
        int boxY0 = Math.round(y);
        int boxX1 = Math.round(x + OLD_NOVOLINE_PORTRAIT);
        int boxY1 = Math.round(y + OLD_NOVOLINE_HEIGHT);

        Quaternionf cameraOrientation = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf pose = new Quaternionf();
        Vector3f translation = new Vector3f(0f, bodyHeight / 2f + OLD_NOVOLINE_MODEL_OFFSET, 0f);

        extractor.entity(state, scale, translation, cameraOrientation, pose,
                boxX0, boxY0, boxX1, boxY1);
    }

    private static void drawOldNovolineBar(GuiGraphicsExtractor extractor, float barX, float barY,
                                           float trackWidth, float fill,
                                           int colorLeft, int colorRight) {
        if (fill <= 0.05f) {
            return;
        }
        float clamped = Math.min(fill, trackWidth);
        int edge = mix(colorLeft, colorRight, trackWidth <= 0f ? 1f : clamped / trackWidth);
        RenderUtil.sharpRectGradient(extractor, barX, barY, clamped, OLD_NOVOLINE_BAR_H,
                colorLeft, edge, null);
    }

    private final Animation bmsScaleAnim = new Animation(Easing.EASE_OUT_ELASTIC, 500);
    private final Animation bmsHealthAnim = new Animation(Easing.EASE_OUT_QUINT, 250);

    private void drawBms(Render2DEvent event, LocalPlayer self, LivingEntity target) {
        if (!(target instanceof AbstractClientPlayer clientPlayer)) {
            return;
        }
        GuiGraphicsExtractor extractor = event.extractor();

        boolean leaving = isLeaving(target);
        bmsScaleAnim.setDuration(leaving ? 400L : 850L);
        bmsScaleAnim.setEasing(leaving ? Easing.EASE_IN_BACK : Easing.EASE_OUT_ELASTIC);
        bmsScaleAnim.run(leaving ? 0f : 1f);
        float scale = bmsScaleAnim.getValue();
        if (scale <= 0f) {
            return;
        }

        String name = target.getName().getString();
        float maxHealth = Math.max(1f, target.getMaxHealth());
        float health = Math.min(Math.round(target.getHealth() * 10f) / 10f, maxHealth);

        float padding = 4f;
        float barOffsetX = 4f;
        float barOffsetY = 4f;
        float head = 32f;
        float barWidth = 100f;

        bmsHealthAnim.run(health / maxHealth * barWidth);
        float barFill = bmsHealthAnim.getValue();
        long percent = Math.round(health / maxHealth * 100f);

        float cardWidth = padding + head + padding + barWidth + barOffsetY + padding;
        float cardHeight = head + padding * 2f;
        currentWidth = cardWidth;
        currentHeight = cardHeight;
        handleDragging(cardWidth, cardHeight);

        float alpha = this.targetAlpha;
        int accent1 = themeAccent1();

        extractor.pose().pushMatrix();
        extractor.pose().translate((x + cardWidth / 2f) * (1f - scale), (y + cardHeight / 2f) * (1f - scale));
        extractor.pose().scale(scale, scale);

        AerialBlur.drawGlass(extractor, BlurConsumer.TARGET_HUD, x, y, cardWidth - 4f, cardHeight,
                6f, RISE_PANEL, alpha, null);

        TextRenderUtil.drawString(extractor, riseLightFont, name,
                fontOffsetX(x - 28f + head + barOffsetX
                        + riseLightFont.stringWidth(RISE_NAME_LABEL, BMS_FONT_SIZE) + 3f),
                fontOffsetY(y + padding + barOffsetY),
                BMS_FONT_SIZE, withAlpha(0xFFFFFFFF, alpha));

        float headX = x + padding;
        float headY = y + padding;
        RenderUtil.dropShadow(extractor, 3, headX, headY, head, head, 20.0, 5f, null);
        RenderUtil.roundedHead(extractor, clientPlayer.getSkin().body().texturePath(),
                headX, headY, head, 3f, withAlpha(0xFFFFFFFF, alpha), null);

        float barX = x + padding + head + barOffsetX;
        float barY = y + padding + head - barOffsetY - 10f;

        RenderUtil.roundedRect(extractor, barX, barY, barWidth, 12f, 2f,
                withAlpha(rgb(44, 44, 44), alpha), null);
        RenderUtil.roundedRect(extractor, barX, barY, barFill, 12f, 2f,
                withAlpha(accent1, alpha * (100f / 255f)), null);

        String percentText = percent + "%";
        float percentWidth = riseLightFont.stringWidth(percentText, BMS_FONT_SIZE);
        TextRenderUtil.drawString(extractor, riseLightFont, percentText,
                fontOffsetX(barX + barWidth + barOffsetY - 50f - percentWidth / 2f),
                fontOffsetY(y + padding + head - barOffsetY - 8f),
                BMS_FONT_SIZE, withAlpha(0xFFFFFFFF, alpha));

        extractor.pose().popMatrix();

        if (dragging) {
            drawDragOutline(extractor);
        }
    }

    private final Animation creidaShowAnim = new Animation(Easing.EASE_OUT_ELASTIC, 500);
    private final Animation creidaHealthAnim = new Animation(Easing.EASE_OUT_QUINT, 250);
    private final Animation creidaHurtAnim = new Animation(Easing.EASE_IN_OUT_CUBIC, 300);

    private void drawCreida(Render2DEvent event, LocalPlayer self, LivingEntity target) {
        if (!(target instanceof AbstractClientPlayer clientPlayer)) {
            return;
        }
        GuiGraphicsExtractor extractor = event.extractor();

        boolean leaving = isLeaving(target);
        creidaShowAnim.setDuration(leaving ? 400L : 850L);
        creidaShowAnim.setEasing(leaving ? Easing.EASE_IN_BACK : Easing.EASE_OUT_ELASTIC);
        creidaShowAnim.run(leaving ? 0f : 1f);
        float scale = creidaShowAnim.getValue();
        if (scale <= 0f) {
            return;
        }

        String name = target.getName().getString();
        float nameWidth = riseMediumFont.stringWidth(name, BMS_FONT_SIZE);

        float maxHealth = Math.max(1f, target.getMaxHealth());
        float health = Math.min(Math.round(target.getHealth() * 10f) / 10f, maxHealth);
        String healthText = String.valueOf(health);
        float healthTextWidth = riseMediumFont.stringWidth(healthText, BMS_FONT_SIZE);

        float barWidth = Math.max(nameWidth + 35f - healthTextWidth, 75f);
        creidaHealthAnim.run(health / maxHealth * barWidth);
        float barFill = creidaHealthAnim.getValue();

        float hurt = target.hurtTime == 0 ? 0f : (target.hurtTime - event.partialTick()) * 0.5f;
        creidaHurtAnim.run(hurt / 2f);
        float hurtEased = creidaHurtAnim.getValue();

        float head = 32f;
        float cardWidth = 52f + barWidth + 4f + healthTextWidth + 10f;
        float cardHeight = 42f;
        currentWidth = cardWidth;
        currentHeight = cardHeight;
        handleDragging(cardWidth, cardHeight);

        float alpha = this.targetAlpha;

        int top = RISE_PANEL;
        int bottom = RISE_PANEL;
        int textPrimary = themeAccent1();
        int textSecondary = themeAccent2();
        if (riseBackground.getValue() == RiseBackground.TINT) {
            int a1 = InterfaceModule.INSTANCE.getTheme().getAccentColor(x, y).getRGB();
            int a2 = InterfaceModule.INSTANCE.getTheme().getAccentColor(x, y + cardHeight).getRGB();
            top = rgba(((a1 >> 16) & 0xFF) / 5, ((a1 >> 8) & 0xFF) / 5, (a1 & 0xFF) / 5, 128);
            bottom = rgba(((a2 >> 16) & 0xFF) / 5, ((a2 >> 8) & 0xFF) / 5, (a2 & 0xFF) / 5, 128);
        } else if (riseBackground.getValue() == RiseBackground.SOLID) {
            top = withAlpha(themeAccent1(), 128 / 255f);
            bottom = withAlpha(themeAccent2(), 128 / 255f);
            textPrimary = 0xFFFFFFFF;
            textSecondary = rgb(164, 164, 164);
        }

        extractor.pose().pushMatrix();
        extractor.pose().translate((x + cardWidth / 2f) * (1f - scale), (y + cardHeight / 2f) * (1f - scale));
        extractor.pose().scale(scale, scale);

        float panelX = x + 3f;
        float panelY = y + 5f;
        AerialBlur.drawGlassGradient(extractor, BlurConsumer.TARGET_HUD, panelX, panelY,
                cardWidth - 1f, cardHeight, 11f, top, bottom, true, alpha, null);

        float textX = x + 10f + head + 6f;
        float labelWidth = riseLightFont.stringWidth(RISE_NAME_LABEL, BMS_FONT_SIZE);
        TextRenderUtil.drawString(extractor, riseLightFont, RISE_NAME_LABEL,
                fontOffsetX(textX), fontOffsetY(y + 10f + 4f + 2f),
                BMS_FONT_SIZE, withAlpha(0xFFFFFFFF, alpha));
        TextRenderUtil.drawString(extractor, riseMediumFont, name,
                fontOffsetX(textX + labelWidth + 3f), fontOffsetY(y + 10f + 4f + 2.5f),
                BMS_FONT_SIZE, withAlpha(textPrimary, alpha));

        float barX = textX;
        float barY = y + 10f + head - 4f - 7f;
        RenderUtil.roundedRectGradient(extractor, barX, barY, barWidth, 6.5f, 3.5f,
                withAlpha(RISE_PANEL, alpha / 1.7f), withAlpha(RISE_PANEL, alpha), true, null);
        RenderUtil.roundedRectGradient(extractor, barX, barY, barFill, 6.5f, 3.5f,
                withAlpha(textSecondary, alpha), withAlpha(textPrimary, alpha), false, null);
        TextRenderUtil.drawString(extractor, riseMediumFont, healthText,
                fontOffsetX(barX + barWidth + 4f), fontOffsetY(y + 10f + head - 4f - 8f),
                BMS_FONT_SIZE, withAlpha(textPrimary, alpha));

        RenderUtil.dropShadow(extractor, 3, x + 10f + hurtEased, y + 10f + hurtEased,
                head - hurt, head - hurt, 20.0, RISE_ROUND * 2f, null);
        float headOffset = (hurtEased == 0f ? 1f : hurtEased) / 2f;
        float headX = x + 10f + headOffset;
        float headY = y + 10f + headOffset;
        float headSize = head - hurt / 2f;

        int headTint = withAlpha(mix(0xFFFFFFFF, 0xFFFF0000, Math.min(1f, hurt / 9f)), alpha);
        RenderUtil.roundedHead(extractor, clientPlayer.getSkin().body().texturePath(),
                headX, headY, headSize, RISE_ROUND * 2f, headTint, null);

        if (riseParticles.getValue()) {
            spawnModernParticles(hurt);
        }
        HudParticles.drawGlow(extractor);
        HudParticles.draw(extractor);

        extractor.pose().popMatrix();

        if (dragging) {
            drawDragOutline(extractor);
        }
    }

    private void drawSimple(Render2DEvent event, LocalPlayer self, LivingEntity target) {
        GuiGraphicsExtractor extractor = event.extractor();
        boolean isPlayer = target instanceof Player;
        String targetName = StreamerModule.INSTANCE.filter(target.getName().getString());

        final float edge = 8f;
        final float face = 30f;
        final float indent = 4f;
        final float barH = 5f;
        final float nameSize = 9f;
        final float smallSize = 7f;

        float maxHealth = target.getMaxHealth();
        float health = Math.min(target.getHealth(), maxHealth);
        String healthStr = HEALTH_DF.format(health) + " HP";
        String distStr = (int) self.distanceTo(target) + "m";

        float nameWidth = sfBoldFont.stringWidth(targetName, nameSize);
        float healthTextW = sfFont.stringWidth(healthStr, smallSize);
        float barWidth = Math.max(nameWidth, 70f);

        currentWidth = edge + face + edge + barWidth + indent + healthTextW + edge;
        currentHeight = face + edge * 2f;

        handleDragging(currentWidth, currentHeight);

        float alpha = this.targetAlpha;
        float frac = maxHealth <= 0f ? 0f : clamp01(health / maxHealth);

        simpleHealthAnim.run(frac * barWidth);
        float filled = simpleHealthAnim.getValue();

        int accent = healthRatioColor.getValue()
                ? ratioColor(frac)
                : mix(themeAccent1(), themeAccent2(), 0.5f);

        AerialBlur.drawGlass(extractor, BlurConsumer.TARGET_HUD, x, y, currentWidth, currentHeight,
                8f, rgba(20, 20, 24, 205), alpha, null);

        RenderUtil.roundedRect(extractor, x + 2.5f, y + 4f, 2f, currentHeight - 8f, 1f,
                withAlpha(themeAccent1(), alpha));

        float headX = x + edge;
        float headY = y + edge;
        if (isPlayer) {
            drawHeadSquare(extractor, (Player) target, headX, headY, face);
        } else {
            RenderUtil.roundedRect(extractor, headX, headY, face, face, 4f,
                    withAlpha(rgba(255, 255, 255, 22), alpha));
        }
        drawThinOutline(extractor, headX - 0.5f, headY - 0.5f, face + 1f, face + 1f, 0.5f,
                withAlpha(rgba(0, 0, 0, 60), alpha));

        float textX = x + edge + face + edge;

        TextRenderUtil.drawString(extractor, sfBoldFont, targetName, textX, y + edge + 1f,
                nameSize, withAlpha(0xFFFFFFFF, alpha));
        TextRenderUtil.drawString(extractor, sfFont, distStr, textX, y + edge + nameSize + 3f,
                smallSize, withAlpha(rgba(255, 255, 255, 170), alpha));

        float barY = y + currentHeight - edge - barH;
        RenderUtil.roundedRect(extractor, textX, barY, barWidth, barH, barH / 2f,
                withAlpha(rgba(0, 0, 0, 140), alpha));
        if (filled > 0.5f) {
            RenderUtil.roundedRect(extractor, textX, barY, Math.max(2f, filled), barH, barH / 2f,
                    withAlpha(accent, alpha));
        }

        TextRenderUtil.drawString(extractor, sfFont, healthStr, textX + barWidth + indent, barY - 1.5f,
                smallSize, withAlpha(0xFFFFFFFF, alpha));

        if (dragging) {
            drawDragOutline(extractor);
        }
    }

    private static final float CAP_HEIGHT = 18.0f;
    private static final float CAP_RADIUS = 4.5f;
    private static final float CAP_PADDING = 5.0f;
    private static final float CAP_RIGHT_EXTRA = 3.5f;
    private static final float CAP_ICON = 12.25f;
    private static final float CAP_DIVIDER_GAP = 5.0f;
    private static final float CAP_DIVIDER_WIDTH = 0.5f;
    private static final float CAP_DIVIDER_HEIGHT = 11.0f;
    private static final int CAP_DIVIDER_COLOR = 0x40808080;

    private static final float CAP_NAME_GAP = 6.5f;
    private static final float CAP_FIELD_GAP = 3.5f;
    private static final float CAP_UNIT_GAP = 1.5f;
    private static final float CAP_NAME_SIZE = 8.0f;
    private static final float CAP_STAT_SIZE = 6.5f;
    private static final float CAP_UNIT_SIZE = 5.5f;
    private static final int CAP_BACKGROUND = 0x80090909;
    private static final int CAP_MUTED = 0xFF808080;

    private static final float CAP_HEALTH_LINE = 1.0f;

    private static AerialFont capFont;

    private static float capValueInkCenter = -1.0f;
    private static float capValueInkBottom, capStatInkBottom, capUnitInkBottom;

    private final Animation capsuleHealthAnim = new Animation(Easing.EASE_OUT_EXPO, 250);

    private static void capMeasureInk() {
        if (capValueInkCenter >= 0.0f) {
            return;
        }
        GlyphQuad[] value = capFont.layout("0", 0.0f, 0.0f, CAP_NAME_SIZE);
        GlyphQuad[] stat = capFont.layout("0", 0.0f, 0.0f, CAP_STAT_SIZE);
        GlyphQuad[] unit = capFont.layout("0", 0.0f, 0.0f, CAP_UNIT_SIZE);
        capValueInkCenter = value.length == 0 ? CAP_NAME_SIZE * 0.5f : (value[0].y0 + value[0].y1) * 0.5f;
        capValueInkBottom = value.length == 0 ? CAP_NAME_SIZE : value[0].y1;
        capStatInkBottom = stat.length == 0 ? CAP_STAT_SIZE : stat[0].y1;
        capUnitInkBottom = unit.length == 0 ? CAP_UNIT_SIZE : unit[0].y1;
    }

    private static float capNameTop(float centerY) {
        capMeasureInk();
        return centerY - capValueInkCenter;
    }

    private static float capBaseline(float centerY) {
        capMeasureInk();
        return centerY - capValueInkCenter + capValueInkBottom;
    }

    private static float capStatTop(float baseline) {
        capMeasureInk();
        return baseline - capStatInkBottom;
    }

    private static float capUnitTop(float baseline) {
        capMeasureInk();
        return baseline - capUnitInkBottom;
    }

    private void drawAkrien(Render2DEvent event, LocalPlayer self, LivingEntity target) {
        GuiGraphicsExtractor extractor = event.extractor();
        if (akrienBold == null) {
            akrienBold = AerialFont.createFromResource("RubikBold.ttf");
            akrienRegular = AerialFont.createFromResource("Rubik.ttf");
        }

        String targetName = StreamerModule.INSTANCE.filter(target.getName().getString());

        currentWidth = Math.max(AKRIEN_MIN_WIDTH,
                akrienBold.stringWidth(targetName, AKRIEN_NAME_SIZE) + AKRIEN_NAME_PAD);
        currentHeight = AKRIEN_HEIGHT;

        handleDragging(currentWidth, currentHeight);

        float ax = Math.round(x);
        float ay = Math.round(y);

        float alpha = this.targetAlpha;
        int bg = withAlpha(rgba(0, 0, 0, 102), alpha);
        int textColor = withAlpha(0xFFFFFFFF, alpha);

        RenderUtil.sharpRect(extractor, ax, ay, ax + currentWidth, ay + currentHeight, bg);

        float barLeft = ax + AKRIEN_BAR_INSET_LEFT;
        float barRight = ax + currentWidth - AKRIEN_BAR_INSET_RIGHT;
        float barSpan = barRight - barLeft;
        RenderUtil.sharpRect(extractor, barLeft, ay + 31f, barRight, ay + 33.5f, bg);
        RenderUtil.sharpRect(extractor, barLeft, ay + 34.5f, barRight, ay + 37f, bg);

        float maxHealth = target.getMaxHealth();
        float absorption = target.getAbsorptionAmount();
        float healthFraction = maxHealth + absorption <= 0.0f ? 0.0f
                : Math.max(0.0f, Math.min(1.0f,
                        (target.getHealth() + absorption) / (maxHealth + absorption)));

        float endWidth = Math.max(0.0f, barSpan * healthFraction);
        akrienHealthAnim.animate(endWidth, AKRIEN_BAR_EASE_MS);
        float shown = akrienHealthAnim.getOutput();
        if (shown > 0.0f) {
            drawGradientRectBordered(extractor, barLeft, ay + 31f, barLeft + shown, ay + 33.5f,
                    AKRIEN_BAR_BORDER, withAlpha(0xFF009C41, alpha), withAlpha(0xFF8EFFC1, alpha), bg);
        }

        double armour = target.getArmorValue() / 20.0;
        if (armour > 0.0) {
            drawGradientRectBordered(extractor, barLeft, ay + 34.5f,
                    barLeft + (float) (barSpan * armour), ay + 37f,
                    AKRIEN_BAR_BORDER, withAlpha(0xFF0067B0, alpha), withAlpha(0xFF39D5FF, alpha), bg);
        }

        if (target instanceof Player player) {
            drawHeadSquare(extractor, player, ax + 3f, ay + 3f, AKRIEN_HEAD);
        } else {
            RenderUtil.sharpRect(extractor, ax + 3f, ay + 3f, ax + 28f, ay + 28f, bg);
            float markWidth = akrienBold.stringWidth("?", AKRIEN_NAME_SIZE);
            TextRenderUtil.drawStringWithShadow(extractor, akrienBold, "?",
                    ax + 3f + (25f - markWidth) * 0.5f, ay + 3f + (25f - AKRIEN_NAME_SIZE) * 0.5f,
                    AKRIEN_NAME_SIZE, textColor);
        }

        drawAkrienLine(extractor, akrienBold, targetName, ax + 31f, ay + 5f, AKRIEN_NAME_SIZE, textColor);
        drawAkrienLine(extractor, akrienRegular, "Health: " + AKRIEN_DF.format(target.getHealth()),
                ax + 31f, ay + 15f, AKRIEN_STAT_SIZE, textColor);
        drawAkrienLine(extractor, akrienRegular,
                "Distance: " + AKRIEN_DF.format(self.distanceTo(target)) + "m",
                ax + 31f, ay + 22f, AKRIEN_STAT_SIZE, textColor);
    }

    private static void drawAkrienLine(GuiGraphicsExtractor extractor, AerialFont font, String text,
                                       float x, float y, float size, int color) {
        TextRenderUtil.drawString(extractor, font, text, x, y - akrienInkTop(font, size), size, color);
    }

    private static float akrienInkTop(AerialFont font, float size) {
        GlyphQuad[] quads = font.layout("H", 0.0f, 0.0f, size);
        return quads.length == 0 ? 0.0f : quads[0].y0;
    }

    private static void drawGradientRectBordered(GuiGraphicsExtractor extractor,
                                                 float left, float top, float right, float bottom,
                                                 float width, int startColor, int endColor,
                                                 int borderColor) {
        if (right - left <= width * 2.0f || bottom - top <= width * 2.0f) {
            RenderUtil.sharpRect(extractor, left, top, right, bottom, borderColor);
            return;
        }
        RenderUtil.sharpRectGradient(extractor, left + width, top + width,
                (right - width) - (left + width), (bottom - width) - (top + width),
                startColor, endColor, null);
        RenderUtil.sharpRect(extractor, left + width, top, right - width, top + width, borderColor);
        RenderUtil.sharpRect(extractor, left, top, left + width, bottom, borderColor);
        RenderUtil.sharpRect(extractor, right - width, top, right, bottom, borderColor);
        RenderUtil.sharpRect(extractor, left + width, bottom - width, right - width, bottom, borderColor);
    }

    private static final class ContinualAnimation {
        private float output;
        private float endpoint;
        private long start;
        private long duration;
        private float from;

        void animate(float destination, int millis) {
            output = current();
            if (Math.abs(endpoint - destination) > 1.0E-4f) {
                from = output;
                endpoint = destination;
                start = System.currentTimeMillis();
                duration = Math.max(1L, millis);
            }
        }

        float getOutput() {
            output = current();
            return output;
        }

        private float current() {
            if (duration <= 0L) {
                return endpoint;
            }
            float t = (System.currentTimeMillis() - start) / (float) duration;
            if (t >= 1.0f) {
                return endpoint;
            }

            float eased = t * t * (3.0f - 2.0f * t);
            return from + (endpoint - from) * eased;
        }
    }

    private void drawCapsule(Render2DEvent event, LocalPlayer self, LivingEntity target) {
        GuiGraphicsExtractor extractor = event.extractor();
        if (capFont == null) {
            capFont = AerialFont.createFromResource("OpalProductSansMedium.ttf");
        }

        String targetName = StreamerModule.INSTANCE.filter(target.getName().getString());
        float maxHealth = target.getMaxHealth();
        float health = Math.min(target.getHealth(), maxHealth);
        String healthText = HEALTH_DF.format(health);
        String distanceText = String.valueOf((int) self.distanceTo(target));

        currentWidth = CAP_PADDING + CAP_ICON + CAP_DIVIDER_GAP + CAP_DIVIDER_WIDTH + CAP_NAME_GAP
                + capFont.stringWidth(targetName, CAP_NAME_SIZE) + CAP_FIELD_GAP
                + capMeasureStat(healthText, "hp") + capMeasureStat(distanceText, "m")
                + CAP_PADDING + CAP_RIGHT_EXTRA;
        currentHeight = CAP_HEIGHT;

        handleDragging(currentWidth, currentHeight);

        float alpha = this.targetAlpha;
        float centerY = y + CAP_HEIGHT * 0.5f;
        int accentLeft = withAlpha(themeAccent2(), alpha);
        int accentRight = withAlpha(themeAccent1(), alpha);

        AerialBlur.drawGlass(extractor, BlurConsumer.TARGET_HUD, x, y, currentWidth,
                currentHeight, CAP_RADIUS, CAP_BACKGROUND, alpha, null);

        float cursorX = x + CAP_PADDING;
        if (target instanceof Player player) {
            drawHeadSquare(extractor, player, cursorX, centerY - CAP_ICON * 0.5f, CAP_ICON);
        } else {
            RenderUtil.roundedRect(extractor, cursorX, centerY - CAP_ICON * 0.5f, CAP_ICON, CAP_ICON,
                    2.5f, withAlpha(rgba(255, 255, 255, 22), alpha));
        }
        cursorX += CAP_ICON + CAP_DIVIDER_GAP;

        RenderUtil.flatRect(extractor, cursorX, centerY - CAP_DIVIDER_HEIGHT * 0.5f,
                CAP_DIVIDER_WIDTH, CAP_DIVIDER_HEIGHT, withAlpha(CAP_DIVIDER_COLOR, alpha));
        cursorX += CAP_DIVIDER_WIDTH + CAP_NAME_GAP;

        cursorX += TextRenderUtil.drawGradientString(extractor, capFont, targetName,
                cursorX, capNameTop(centerY), CAP_NAME_SIZE, accentLeft, accentRight);
        cursorX += CAP_FIELD_GAP;

        cursorX = capDrawStat(extractor, cursorX, centerY, healthText, "hp", alpha);
        capDrawStat(extractor, cursorX, centerY, distanceText, "m", alpha);

        float fraction = maxHealth <= 0.0f ? 0.0f : clamp01(health / maxHealth);
        float lineWidth = currentWidth - CAP_RADIUS * 2.0f;
        capsuleHealthAnim.run(fraction * lineWidth);
        float filled = capsuleHealthAnim.getValue();
        int lineColor = healthRatioColor.getValue()
                ? ratioColor(fraction)
                : mix(themeAccent1(), themeAccent2(), 0.5f);

        float lineY = y + CAP_HEIGHT - CAP_HEALTH_LINE - 1.5f;
        RenderUtil.roundedRect(extractor, x + CAP_RADIUS, lineY, lineWidth, CAP_HEALTH_LINE,
                CAP_HEALTH_LINE * 0.5f, withAlpha(rgba(0, 0, 0, 120), alpha));
        if (filled > 0.5f) {
            RenderUtil.roundedRect(extractor, x + CAP_RADIUS, lineY, filled, CAP_HEALTH_LINE,
                    CAP_HEALTH_LINE * 0.5f, withAlpha(lineColor, alpha));
        }

        if (dragging) {
            drawDragOutline(extractor);
        }
    }

    private float capDrawStat(GuiGraphicsExtractor extractor, float cursorX, float centerY,
                              String value, String unit, float alpha) {
        float baseline = capBaseline(centerY);
        cursorX += TextRenderUtil.drawString(extractor, capFont, value,
                cursorX, capStatTop(baseline), CAP_STAT_SIZE, withAlpha(0xFFFFFFFF, alpha));
        cursorX += CAP_UNIT_GAP;
        cursorX += TextRenderUtil.drawString(extractor, capFont, unit,
                cursorX, capUnitTop(baseline), CAP_UNIT_SIZE, withAlpha(CAP_MUTED, alpha));
        return cursorX + CAP_FIELD_GAP;
    }

    private static float capMeasureStat(String value, String unit) {
        return capFont.stringWidth(value, CAP_STAT_SIZE) + CAP_UNIT_GAP
                + capFont.stringWidth(unit, CAP_UNIT_SIZE) + CAP_FIELD_GAP;
    }

    private void drawThinOutline(GuiGraphicsExtractor extractor, float ox, float oy, float w, float h, float t, int color) {
        RenderUtil.sharpRect(extractor, ox, oy, ox + w, oy + t, color);
        RenderUtil.sharpRect(extractor, ox, oy + h - t, ox + w, oy + h, color);
        RenderUtil.sharpRect(extractor, ox, oy, ox + t, oy + h, color);
        RenderUtil.sharpRect(extractor, ox + w - t, oy, ox + w, oy + h, color);
    }

    private static final float AER_HEIGHT = 34.0f;
    private static final float AER_MIN_WIDTH = 124.0f;
    private static final float AER_RADIUS = 6.0f;
    private static final float AER_PAD = 5.0f;
    private static final float AER_HEAD = 24.0f;
    private static final float AER_HEAD_RADIUS = 4.0f;
    private static final float AER_HEAD_GAP = 7.0f;
    private static final float AER_RIGHT_PAD = 8.0f;

    private static final float AER_NAME_SIZE = 8.5f;
    private static final float AER_VALUE_SIZE = 7.5f;
    private static final float AER_SMALL_SIZE = 6.5f;

    private static final float AER_NAME_TOP = 5.5f;
    private static final float AER_TRACK_TOP = 17.5f;
    private static final float AER_TRACK_HEIGHT = 3.5f;
    private static final float AER_READING_TOP = 23.5f;

    private static final String AER_ICON_WIN = String.valueOf((char) 0xE86C);

    private static final String AER_ICON_LOSE = String.valueOf((char) 0xE5C9);

    private static final String AER_ICON_CLOSE = String.valueOf((char) 0xE002);
    private static final float AER_ICON_SIZE = 10.0f;
    private static final float AER_ICON_GAP = 6.0f;

    private static final float AER_OUTCOME_MARGIN = 3.0f;
    private static final int AER_WIN_COLOR = 0xFF4ADE80;
    private static final int AER_LOSE_COLOR = 0xFFF4635A;
    private static final int AER_CLOSE_COLOR = 0xFFF5C451;

    private static final int AER_HEART_COLOR = 0xFFEB3741;

    private static final float AER_HEART_SIZE = 7.5f;

    private static final float AER_HEART_DROP = 0.75f;

    private static final int AER_BACKGROUND = 0x80090909;
    private static final int AER_TRACK = 0x66000000;
    private static final int AER_MUTED = 0xFF8B8D96;
    private static final int AER_HURT_RING = 0xFFFF5A5A;

    private static AerialFont aerialFont;
    private static AerialFont aerialBoldFont;

    private static AerialFont aerialIconFont;

    private final Animation aerialFillAnim = new Animation(Easing.EASE_OUT_EXPO, 180);

    private final Animation aerialGhostAnim = new Animation(Easing.EASE_OUT_EXPO, 650);

    private void drawAerial(Render2DEvent event, LocalPlayer self, LivingEntity target) {
        GuiGraphicsExtractor extractor = event.extractor();
        if (aerialFont == null) {
            aerialFont = AerialFont.createFromResource("OpalProductSansMedium.ttf");
            aerialBoldFont = AerialFont.createFromResource("OpalProductSansBold.ttf");
            aerialIconFont = AerialFont.createIconFromResource("OpalMaterialIconsRegular.ttf",
                    (char) 0xE86C, (char) 0xE5C9, (char) 0xE002);
        }

        String name = StreamerModule.INSTANCE.filter(target.getName().getString());
        float maxHealth = Math.max(1.0f, target.getMaxHealth());
        float health = Math.max(0.0f, Math.min(target.getHealth(), maxHealth));
        float fraction = clamp01(health / maxHealth);

        String healthText = HEALTH_DF.format(health);
        String distanceText = (int) self.distanceTo(target) + "m";

        boolean rated = target instanceof Player;
        float margin = rated ? cachedCalculateWinning(target, self) : 0.0f;
        String outcomeIcon;
        int outcomeColor;
        if (margin > AER_OUTCOME_MARGIN) {
            outcomeIcon = AER_ICON_WIN;
            outcomeColor = AER_WIN_COLOR;
        } else if (margin < -AER_OUTCOME_MARGIN) {
            outcomeIcon = AER_ICON_LOSE;
            outcomeColor = AER_LOSE_COLOR;
        } else {
            outcomeIcon = AER_ICON_CLOSE;
            outcomeColor = AER_CLOSE_COLOR;
        }
        float iconWidth = rated ? aerialIconFont.stringWidth(outcomeIcon, AER_ICON_SIZE) : 0.0f;

        float textLeft = AER_PAD + AER_HEAD + AER_HEAD_GAP;

        float readingWidth = aerialFont.stringWidth(healthText, AER_VALUE_SIZE) + 3.0f
                + heartFont.stringWidth(HEART_GLYPH, AER_HEART_SIZE) + 10.0f
                + aerialFont.stringWidth(distanceText, AER_SMALL_SIZE);
        float nameRowWidth = aerialBoldFont.stringWidth(name, AER_NAME_SIZE)
                + (rated ? AER_ICON_GAP + iconWidth : 0.0f);
        float contentWidth = Math.max(nameRowWidth, readingWidth);
        currentWidth = Math.max(AER_MIN_WIDTH, textLeft + contentWidth + AER_RIGHT_PAD);
        currentHeight = AER_HEIGHT;

        handleDragging(currentWidth, currentHeight);

        float alpha = this.targetAlpha;
        int accentLeft = withAlpha(themeAccent2(), alpha);
        int accentRight = withAlpha(themeAccent1(), alpha);

        AerialBlur.drawGlass(extractor, BlurConsumer.TARGET_HUD, x, y, currentWidth, currentHeight,
                AER_RADIUS, AER_BACKGROUND, alpha, null);

        float headX = x + AER_PAD;
        float headY = y + (AER_HEIGHT - AER_HEAD) * 0.5f;
        if (target instanceof Player player) {
            drawHeadSquare(extractor, player, headX, headY, AER_HEAD);
        } else {
            RenderUtil.roundedRect(extractor, headX, headY, AER_HEAD, AER_HEAD, AER_HEAD_RADIUS,
                    withAlpha(rgba(255, 255, 255, 22), alpha));
        }

        float hurt = clamp01(target.hurtTime / 10.0f);
        int ring = mix(withAlpha(rgba(255, 255, 255, 38), alpha), withAlpha(AER_HURT_RING, alpha), hurt);
        drawThinOutline(extractor, headX - 0.5f, headY - 0.5f, AER_HEAD + 1.0f, AER_HEAD + 1.0f, 0.5f, ring);

        TextRenderUtil.drawGradientString(extractor, aerialBoldFont, name,
                x + textLeft, y + AER_NAME_TOP, AER_NAME_SIZE, accentLeft, accentRight);

        float trackX = x + textLeft;
        float trackWidth = currentWidth - textLeft - AER_RIGHT_PAD;
        float trackY = y + AER_TRACK_TOP;
        float radius = AER_TRACK_HEIGHT * 0.5f;

        if (rated) {
            TextRenderUtil.drawString(extractor, aerialIconFont, outcomeIcon,
                    trackX + trackWidth - iconWidth,
                    y + AER_NAME_TOP + (AER_NAME_SIZE - AER_ICON_SIZE) * 0.5f,
                    AER_ICON_SIZE, withAlpha(outcomeColor, alpha));
        }

        aerialFillAnim.run(fraction * trackWidth);
        aerialGhostAnim.run(fraction * trackWidth);
        float filled = aerialFillAnim.getValue();
        float ghost = aerialGhostAnim.getValue();

        int fillColor = healthRatioColor.getValue() ? ratioColor(fraction) : themeAccent1();

        RenderUtil.roundedRect(extractor, trackX, trackY, trackWidth, AER_TRACK_HEIGHT, radius,
                withAlpha(AER_TRACK, alpha));

        if (ghost > filled + 0.5f) {
            RenderUtil.roundedRect(extractor, trackX, trackY, ghost, AER_TRACK_HEIGHT, radius,
                    withAlpha(mix(fillColor, rgb(255, 255, 255), 0.35f), alpha * 0.4f));
        }
        if (filled > 0.5f) {
            RenderUtil.roundedRect(extractor, trackX, trackY, filled, AER_TRACK_HEIGHT, radius,
                    withAlpha(fillColor, alpha));
        }

        float rowY = y + AER_READING_TOP - 0.5f;
        float cursor = trackX;
        cursor += TextRenderUtil.drawString(extractor, aerialFont, healthText,
                cursor, rowY, AER_VALUE_SIZE, withAlpha(0xFFFFFFFF, alpha));
        cursor += 3.0f;

        float heartY = rowY + (AER_VALUE_SIZE - AER_HEART_SIZE) * 0.5f + AER_HEART_DROP;
        TextRenderUtil.drawString(extractor, heartFont, HEART_GLYPH,
                cursor, heartY, AER_HEART_SIZE, withAlpha(AER_HEART_COLOR, alpha));

        float distanceWidth = aerialFont.stringWidth(distanceText, AER_SMALL_SIZE);
        TextRenderUtil.drawString(extractor, aerialFont, distanceText,
                trackX + trackWidth - distanceWidth, rowY, AER_SMALL_SIZE,
                withAlpha(AER_MUTED, alpha));

        if (dragging) {
            drawDragOutline(extractor);
        }
    }
}
