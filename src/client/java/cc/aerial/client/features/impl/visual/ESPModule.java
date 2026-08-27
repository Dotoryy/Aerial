package cc.aerial.client.features.impl.visual;

import cc.aerial.client.event.impl.game.chat.ChatReceivedEvent;
import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.combat.AntiBotModule;
import cc.aerial.client.property.MultipleBooleanProperty;
import cc.aerial.client.render.CameraRenderStateHelper;
import cc.aerial.client.render.ESPUtility;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.target.TargetFlags;
import cc.aerial.client.target.TargetProperty;
import cc.aerial.client.utility.HypixelServer;
import cc.aerial.client.utility.PlayerUtility;
import net.hypixel.data.type.GameType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;

import static net.minecraft.world.item.enchantment.Enchantments.*;

public final class ESPModule extends Module {
    public static final ESPModule INSTANCE = new ESPModule();

    private final ESPSettings settings = new ESPSettings(this);

    private static AerialFont nameTagFont;
    private static AerialFont iconFont;
    private static final float NAMETAG_FONT_SIZE = 5.0F;
    private static final DecimalFormat HEALTH_DF = new DecimalFormat("0.#");

    private static final String ICON_STRENGTH = "";
    private static final String ICON_SNEAKING = "";
    private static final String ICON_INVISIBLE = "";
    private static final String ICON_BLOCKING = "";
    private static final String ICON_DISTANCE = "";
    private static final String ICON_HEART = "";

    private final Map<String, Long> strengthExpiry = new HashMap<>();

    private ESPModule() {
        super("ESP", "Extra sensory perception", ModuleCategory.VISUAL);
    }

    private static void ensureFontsLoaded() {
        if (nameTagFont == null) {
            nameTagFont = AerialFont.createFromResource("OpalProductSansBold.ttf");
            iconFont = AerialFont.createIconFromResource("OpalMaterialIconsRegular.ttf",
                    ICON_STRENGTH.charAt(0), ICON_SNEAKING.charAt(0), ICON_INVISIBLE.charAt(0),
                    ICON_BLOCKING.charAt(0), ICON_DISTANCE.charAt(0), ICON_HEART.charAt(0));
        }
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        ensureFontsLoaded();

        CameraRenderState camera = CameraRenderStateHelper.get();
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        ClientLevel level = mc.level;
        if (camera == null || self == null || level == null) {
            return;
        }

        TargetProperty targetProperty = settings.getTargetProperty();
        int flags = targetProperty.getTargetFlags();

        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof LivingEntity living) || living.isDeadOrDying()) {
                continue;
            }

            boolean isLocal = living == self;
            if (isLocal && (!targetProperty.isLocalPlayer() || mc.options.getCameraType().isFirstPerson())) {
                continue;
            }
            if (!isMatchingFlags(self, living, flags)) {
                continue;
            }

            AABB interpolatedBox = interpolatedBoundingBox(living, event.partialTick());
            ESPUtility.ScreenBox box = ESPUtility.project(interpolatedBox, camera, event.width(), event.height());
            if (box == null) {
                continue;
            }

            renderBoxIn2D(event.extractor(), self, living, box);
        }
    }

    private static AABB interpolatedBoundingBox(LivingEntity entity, float partialTick) {
        Vec3 pos = entity.getPosition(partialTick);
        AABB current = entity.getBoundingBox();
        double halfWidth = (current.maxX - current.minX) / 2.0;
        double height = current.maxY - current.minY;
        return new AABB(pos.x - halfWidth, pos.y, pos.z - halfWidth,
                pos.x + halfWidth, pos.y + height, pos.z + halfWidth);
    }

    private boolean isMatchingFlags(LocalPlayer self, LivingEntity entity, int flags) {
        if (entity instanceof ArmorStand || entity instanceof Villager) {
            return false;
        }
        if (entity instanceof Player otherPlayer) {
            if (AntiBotModule.isBot(otherPlayer)) {
                return false;
            }
            if ((flags & TargetFlags.FRIENDLY) == 0 && PlayerUtility.areOnSameTeam(self, otherPlayer)) {
                return false;
            }
            return (flags & TargetFlags.PLAYERS) != 0;
        }
        if (entity instanceof Monster) {
            return (flags & TargetFlags.HOSTILE) != 0;
        }
        return (flags & TargetFlags.PASSIVE) != 0;
    }

    private void renderBoxIn2D(GuiGraphicsExtractor extractor, LocalPlayer self, LivingEntity entity, ESPUtility.ScreenBox box) {
        int teamColor = 0xFF000000 | (entity.getTeamColor() & 0x00FFFFFF);

        if (settings.getBox()) {
            renderBox(extractor, box, teamColor, settings.getBoxStroke());
        }
        if (settings.getHealthBar()) {
            renderHealthBar(extractor, box, settings.getHealthBarStroke(), entity.getHealth() / entity.getMaxHealth());
        }
        if (settings.areNameTagsEnabled()) {
            renderNameTag(extractor, self, entity, box);
        }
    }

    private void renderBox(GuiGraphicsExtractor extractor, ESPUtility.ScreenBox box, int color, boolean stroke) {
        float minDim = Math.min(box.width(), box.height());
        float thickness = Math.min(0.5F, Math.max(0.15F, minDim * 0.12F));
        if (stroke) {
            float strokeThickness = Math.min(thickness * 3.0F, Math.max(thickness, minDim * 0.35F));
            drawBoxFrame(extractor, box, strokeThickness, 0xFF000000);
        }
        drawBoxFrame(extractor, box, thickness, color);
    }

    private void drawBoxFrame(GuiGraphicsExtractor extractor, ESPUtility.ScreenBox box, float lineThickness, int color) {
        float x0 = box.x();
        float y0 = box.y();
        float x1 = box.x() + box.width();
        float y1 = box.y() + box.height();
        float t = lineThickness;

        RenderUtil.sharpRect(extractor, x0, y0, x1, y0 + t, color);
        RenderUtil.sharpRect(extractor, x0, y1 - t, x1, y1, color);
        RenderUtil.sharpRect(extractor, x0, y0, x0 + t, y1, color);
        RenderUtil.sharpRect(extractor, x1 - t, y0, x1, y1, color);
    }

    private void renderHealthBar(GuiGraphicsExtractor extractor, ESPUtility.ScreenBox box, boolean stroke, float healthFraction) {
        float barWidth = Math.min(2.0F, Math.max(0.5F, box.height() * 0.18F));
        float strokeThickness = Math.min(0.5F, Math.max(0.15F, box.height() * 0.06F));
        float clamped = Math.max(0.0F, Math.min(1.0F, healthFraction));

        float y0 = box.y();
        float y1 = box.y() + box.height();
        float barX0 = box.x() - barWidth - 2.0F;
        float barX1 = barX0 + barWidth;
        float filledHeight = (y1 - y0) * clamped;

        if (stroke) {
            float st = strokeThickness;
            RenderUtil.sharpRect(extractor, barX0 - st, y0 - st, barX1 + st, y1 + st, 0xFF000000);
        }
        RenderUtil.sharpRect(extractor, barX0, y0, barX1, y1, 0x88000000);
        RenderUtil.sharpRect(extractor, barX0, y1 - filledHeight, barX1, y1, 0xFF55FF55);
    }

    private void renderNameTag(GuiGraphicsExtractor extractor, LocalPlayer self, LivingEntity entity, ESPUtility.ScreenBox box) {
        MultipleBooleanProperty indicators = settings.getNameTagIndicators();
        MultipleBooleanProperty elements = settings.getNameTagElements();

        List<NameTagElement> elementList = new ArrayList<>();

        String entityName = entity.getName().getString();

        if (indicators.getProperty("Strength").getValue() && isStrengthed(entityName)) {
            elementList.add(new NameTagElement(new NameTagIcon(ICON_STRENGTH, 0.25F), 0xFFFF0000));
        }
        if (indicators.getProperty("Sneaking").getValue() && entity.isCrouching()) {
            elementList.add(new NameTagElement(new NameTagIcon(ICON_SNEAKING), 0xFFFF5555));
        }
        if (indicators.getProperty("Invisible").getValue() && entity.isInvisible()) {
            elementList.add(new NameTagElement(new NameTagIcon(ICON_INVISIBLE, 0.3F), 0xFFAAAAAA));
        }
        if (indicators.getProperty("Blocking").getValue() && entity instanceof Player player && isBlockingIndicator(self, player)) {
            elementList.add(new NameTagElement(new NameTagIcon(ICON_BLOCKING, 0.15F), 0xFF41AF7D));
        }

        if (elements.getProperty("Distance").getValue() && entity != self) {
            NameTagIcon distanceIcon = new NameTagIcon(ICON_DISTANCE, NameTagIconPosition.RIGHT);
            elementList.add(new NameTagElement(distanceIcon, String.valueOf((int) Math.floor(entity.distanceTo(self))), 0xFFAAAAAA));
        }
        if (elements.getProperty("Name").getValue()) {
            elementList.add(new NameTagElement(StreamerModule.INSTANCE.filter(entityName), 0xFFFFFFFF));
        }
        if (elements.getProperty("Health").getValue()) {
            NameTagIcon redHeart = new NameTagIcon(ICON_HEART, NameTagIconPosition.RIGHT);
            elementList.add(new NameTagElement(redHeart, HEALTH_DF.format(entity.getHealth()), 0xFFFF5555));
            if (entity.getAbsorptionAmount() > 0) {
                NameTagIcon normalHeart = new NameTagIcon(ICON_HEART, NameTagIconPosition.RIGHT);
                elementList.add(new NameTagElement(normalHeart, HEALTH_DF.format(entity.getAbsorptionAmount()), 0xFFFFC247));
            }
        }

        float startX = calculateStartingX(elementList, box);
        float y = box.y() - 4.5F;
        renderNameTagElements(extractor, elementList, startX, y);

        if (elements.getProperty("Equipment").getValue()) {
            renderEquipment(extractor, entity, box, !elementList.isEmpty());
        }
    }

    private boolean isBlockingIndicator(LocalPlayer self, Player player) {
        if (player == self && cc.aerial.client.features.impl.movement.NoSlowModule.INSTANCE.isFakeBlockingState()) {
            return true;
        }
        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.is(net.minecraft.tags.ItemTags.SWORDS)
                && mainHand.getUseAnimation() == net.minecraft.world.item.ItemUseAnimation.BLOCK
                && player.getTicksUsingItem() > 0) {
            return true;
        }
        return mainHand.is(net.minecraft.tags.ItemTags.SWORDS)
                && player.getUseItem().getItem() instanceof net.minecraft.world.item.ShieldItem
                && player.getTicksUsingItem() > 0;
    }

    private boolean isStrengthed(String name) {
        Long expiry = strengthExpiry.get(name);
        if (expiry == null) {
            return false;
        }
        if (expiry <= System.currentTimeMillis()) {
            strengthExpiry.remove(name);
            return false;
        }
        return true;
    }

    @Subscribe
    public void onChatReceived(ChatReceivedEvent event) {
        String message = event.getText().getString();
        Matcher matcher = HypixelServer.KILL_MESSAGE_PATTERN.matcher(message);
        if (!matcher.find() || !HypixelServer.isCurrent()) {
            return;
        }

        HypixelServer.ModAPI.Location location = HypixelServer.ModAPI.get().getCurrentLocation();
        if (location == null || location.serverType() != GameType.SKYWARS || location.mode() == null
                || location.mode().startsWith("mini")) {
            return;
        }

        String killer = matcher.group("killer");
        long durationMillis = (location.mode().startsWith("solo") ? 5 : 2) * 1000L;
        strengthExpiry.put(killer, System.currentTimeMillis() + durationMillis);
    }

    private float calculateStartingX(List<NameTagElement> elements, ESPUtility.ScreenBox box) {
        float totalWidth = 0;
        for (int i = 0; i < elements.size(); i++) {
            NameTagElement element = elements.get(i);
            if (element.text() != null) {
                totalWidth += nameTagFont.stringWidth(element.text(), NAMETAG_FONT_SIZE);
            }
            if (element.icon() != null) {
                totalWidth += iconFont.stringWidth(element.icon().unicode(), NAMETAG_FONT_SIZE);
            }
            if (i < elements.size() - 1) {
                totalWidth += 5;
            }
        }
        return box.x() + box.width() / 2 - totalWidth / 2;
    }

    private void renderNameTagElements(GuiGraphicsExtractor extractor, List<NameTagElement> elements, float startX, float y) {
        float currentX = startX;

        for (NameTagElement element : elements) {
            boolean hasText = element.text() != null;
            boolean hasIcon = element.icon() != null;
            NameTagIcon icon = element.icon();

            float textWidth = hasText ? nameTagFont.stringWidth(element.text(), NAMETAG_FONT_SIZE) : 0;
            float iconWidth = hasIcon ? iconFont.stringWidth(icon.unicode(), NAMETAG_FONT_SIZE) : 0;

            float bgPadding = 2;
            float bgRadius = 2;
            RenderUtil.roundedRect(extractor, currentX - bgPadding, y - bgPadding,
                    textWidth + iconWidth + bgPadding * 2, NAMETAG_FONT_SIZE + bgPadding * 2,
                    bgRadius, 0x80000000);

            float textY = y - 0.2f;
            float textX = currentX;

            if (hasIcon && icon.position() == NameTagIconPosition.LEFT) {
                TextRenderUtil.drawString(extractor, iconFont, icon.unicode(), currentX + icon.horizontalOffset(), textY + 1, NAMETAG_FONT_SIZE, element.color());
                textX += iconWidth;
            }
            if (hasText) {
                TextRenderUtil.drawString(extractor, nameTagFont, element.text(), textX, textY, NAMETAG_FONT_SIZE, element.color());
            }
            if (hasIcon && icon.position() == NameTagIconPosition.RIGHT) {
                TextRenderUtil.drawString(extractor, iconFont, icon.unicode(), textX + textWidth + icon.horizontalOffset(), textY + 1, NAMETAG_FONT_SIZE, element.color());
            }

            currentX += textWidth + iconWidth + 5;
        }
    }

    private void renderEquipment(GuiGraphicsExtractor extractor, LivingEntity entity, ESPUtility.ScreenBox box, boolean hasNametagElements) {
        List<ItemStack> equipment = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) {
                continue;
            }
            ItemStack stack = entity.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                equipment.add(stack);
            }
        }
        ItemStack mainHand = entity.getMainHandItem();
        if (!mainHand.isEmpty()) {
            equipment.add(mainHand);
        }
        if (equipment.isEmpty()) {
            return;
        }

        float scale = 0.65F;
        float baseY = box.y() - (hasNametagElements ? 23.5F : 14F);

        for (int i = 0; i < equipment.size(); i++) {
            ItemStack stack = equipment.get(i);
            float stackX = box.x() + box.width() / 2 - (equipment.size() * scale * 8) + ((equipment.size() - i - 1) * scale * 16);

            extractor.pose().pushMatrix();
            extractor.pose().translate(stackX, baseY);
            extractor.pose().scale(scale, scale);
            extractor.item(stack, 0, 0);
            extractor.pose().popMatrix();

            int enchantIndex = 0;
            for (var entry : stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
                Optional<ResourceKey<Enchantment>> key = entry.getKey().unwrapKey();
                if (key.isEmpty()) {
                    continue;
                }
                String shortName = ENCHANTMENT_NAMES.get(key.get());
                if (shortName == null) {
                    continue;
                }
                extractor.pose().pushMatrix();
                extractor.pose().translate(stackX, baseY);
                extractor.text(Minecraft.getInstance().font, shortName + entry.getIntValue(), 2, 7 + (-8 * enchantIndex), 0xFFFFFFFF, true);
                extractor.pose().popMatrix();
                enchantIndex++;
            }
        }
    }

    private static final Map<ResourceKey<Enchantment>, String> ENCHANTMENT_NAMES = new HashMap<>() {{
        put(PROTECTION, "Pr");
        put(FIRE_PROTECTION, "Fp");
        put(FEATHER_FALLING, "Ff");
        put(BLAST_PROTECTION, "Bp");
        put(PROJECTILE_PROTECTION, "Pp");
        put(RESPIRATION, "Re");
        put(AQUA_AFFINITY, "Aa");
        put(THORNS, "Th");
        put(DEPTH_STRIDER, "Ds");
        put(FROST_WALKER, "Fw");
        put(BINDING_CURSE, "Bc");
        put(SOUL_SPEED, "Ss");
        put(SWIFT_SNEAK, "Sn");
        put(SHARPNESS, "Sh");
        put(SMITE, "Sm");
        put(BANE_OF_ARTHROPODS, "BoA");
        put(KNOCKBACK, "Kb");
        put(FIRE_ASPECT, "Fa");
        put(LOOTING, "Lo");
        put(SWEEPING_EDGE, "Sw");
        put(EFFICIENCY, "Ef");
        put(SILK_TOUCH, "St");
        put(UNBREAKING, "Un");
        put(FORTUNE, "Fo");
        put(POWER, "Po");
        put(PUNCH, "Pu");
        put(FLAME, "Fl");
        put(INFINITY, "In");
        put(LUCK_OF_THE_SEA, "Lu");
        put(LURE, "Lr");
        put(LOYALTY, "Ly");
        put(IMPALING, "Ip");
        put(RIPTIDE, "Ri");
        put(CHANNELING, "Ch");
        put(MULTISHOT, "Mu");
        put(QUICK_CHARGE, "Qc");
        put(PIERCING, "Pi");
        put(MENDING, "Me");
        put(VANISHING_CURSE, "Vc");
    }};
}
