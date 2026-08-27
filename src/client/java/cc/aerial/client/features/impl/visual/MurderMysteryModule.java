package cc.aerial.client.features.impl.visual;

import cc.aerial.client.event.impl.game.JoinWorldEvent;
import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.combat.AntiBotModule;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.render.CameraRenderStateHelper;
import cc.aerial.client.render.ESPUtility;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.notification.NotificationManager;
import cc.aerial.client.notification.NotificationType;
import cc.aerial.client.utility.HypixelServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Scoreboard;

import java.util.HashSet;
import java.util.Set;

public final class MurderMysteryModule extends Module {
    public static final MurderMysteryModule INSTANCE = new MurderMysteryModule();

    private static final int COLOR_MURDERER = 0xFFFF3333;
    private static final int COLOR_BOW = 0xFFFFA500;
    private static final int COLOR_INNOCENT = 0xFF33FF33;
    private static final int COLOR_GOLD = 0xFFFAF089;

    private static final Set<Item> MURDER_WEAPONS = Set.of(
            Items.IRON_SWORD, Items.STONE_SWORD, Items.IRON_SHOVEL, Items.STICK, Items.WOODEN_AXE,
            Items.WOODEN_SWORD, Items.STONE_SHOVEL, Items.BLAZE_ROD, Items.DIAMOND_SHOVEL,
            Items.QUARTZ, Items.PUMPKIN_PIE, Items.GOLDEN_PICKAXE, Items.APPLE, Items.NAME_TAG,
            Items.CARROT_ON_A_STICK, Items.BONE, Items.CARROT, Items.GOLDEN_CARROT, Items.COOKIE,
            Items.DIAMOND_AXE, Items.PRISMARINE_SHARD, Items.COOKED_BEEF, Items.NETHER_BRICK,
            Items.COOKED_CHICKEN, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.DIAMOND_HOE,
            Items.SHEARS, Items.COD, Items.SPONGE, Items.DEAD_BUSH, Items.BOOK, Items.SUNFLOWER,
            Items.GLISTERING_MELON_SLICE);

    private final BooleanProperty alert = new BooleanProperty("Alert murderer", true);
    private final BooleanProperty highlightMurderer = new BooleanProperty("Highlight murderer", true);
    private final BooleanProperty highlightBow = new BooleanProperty("Highlight bow", true);
    private final BooleanProperty highlightInnocent = new BooleanProperty("Highlight innocent", true);
    private final BooleanProperty highlightDead = new BooleanProperty("Highlight dead", true);
    private final BooleanProperty goldEsp = new BooleanProperty("Gold ESP", true);

    private final Set<Player> murderers = new HashSet<>();
    private final Set<Player> archers = new HashSet<>();

    private MurderMysteryModule() {
        super("Murder Mystery", "Identifies the murderer on Hypixel", ModuleCategory.VISUAL);
        addProperties(alert, highlightMurderer, highlightBow, highlightInnocent, highlightDead, goldEsp);
    }

    @Override
    protected void onDisable() {
        clear();
    }

    private void clear() {
        murderers.clear();
        archers.clear();
    }

    @Subscribe
    public void onJoinWorld(JoinWorldEvent event) {
        clear();
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer self = mc.player;
        ClientLevel level = mc.level;
        if (self == null || level == null) {
            return;
        }
        if (!isMurderMystery(self)) {
            clear();
            return;
        }
        CameraRenderState camera = CameraRenderStateHelper.get();
        if (camera == null) {
            return;
        }

        for (Player other : level.players()) {
            if (other == self || other.isInvisible()) {
                continue;
            }

            if (AntiBotModule.isBot(other) && !highlightDead.getValue()) {
                continue;
            }
            classify(self, other);

            int color;
            if (murderers.contains(other) && highlightMurderer.getValue()) {
                color = COLOR_MURDERER;
            } else if (archers.contains(other) && highlightBow.getValue()) {
                color = COLOR_BOW;
            } else if (highlightInnocent.getValue()) {
                color = COLOR_INNOCENT;
            } else {
                continue;
            }

            if (!highlightDead.getValue() && boundingBoxVolume(other) <= 0.009) {
                continue;
            }
            drawFrame(event, camera, other.getBoundingBox(), color, 1.0f);
        }

        if (goldEsp.getValue()) {
            renderGold(event, camera, level);
        }
    }

    private void classify(LocalPlayer self, Player other) {
        ItemStack held = other.getMainHandItem();
        if (held.isEmpty()) {
            return;
        }
        if (MURDER_WEAPONS.contains(held.getItem())) {
            if (murderers.add(other) && alert.getValue()) {
                NotificationManager.INSTANCE.builder(NotificationType.WARNING)
                        .title("Murderer found")
                        .description(other.getName().getString() + " (" + (int) self.distanceTo(other) + "m)")
                        .duration(4000)
                        .buildAndPublish();
                self.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 1.0f);
            }
        } else if (held.is(Items.BOW) && highlightBow.getValue()) {
            archers.add(other);
        }
    }

    private void renderGold(Render2DEvent event, CameraRenderState camera, ClientLevel level) {
        Vec3 cameraPosition = camera.pos;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity item) || entity.tickCount < 3) {
                continue;
            }
            ItemStack stack = item.getItem();
            if (stack.isEmpty() || !stack.is(Items.GOLD_INGOT)) {
                continue;
            }
            Vec3 position = entity.getPosition(event.partialTick());
            double half = Math.min(Math.max(0.2, 0.01 * position.distanceTo(cameraPosition)), 0.4);
            AABB box = new AABB(
                    position.x - half, position.y, position.z - half,
                    position.x + half, position.y + half * 2.0, position.z + half);
            drawFrame(event, camera, box, COLOR_GOLD, 1.0f);
        }
    }

    private static void drawFrame(Render2DEvent event, CameraRenderState camera, AABB box,
                                  int color, float thickness) {
        ESPUtility.ScreenBox screen = ESPUtility.project(box, camera, event.width(), event.height());
        if (screen == null) {
            return;
        }
        float x0 = screen.x();
        float y0 = screen.y();
        float x1 = screen.x() + screen.width();
        float y1 = screen.y() + screen.height();
        RenderUtil.sharpRect(event.extractor(), x0, y0, x1, y0 + thickness, color);
        RenderUtil.sharpRect(event.extractor(), x0, y1 - thickness, x1, y1, color);
        RenderUtil.sharpRect(event.extractor(), x0, y0, x0 + thickness, y1, color);
        RenderUtil.sharpRect(event.extractor(), x1 - thickness, y0, x1, y1, color);
    }

    private static double boundingBoxVolume(Entity entity) {
        AABB box = entity.getBoundingBox();
        return (box.maxX - box.minX) * (box.maxY - box.minY) * (box.maxZ - box.minZ);
    }

    private static boolean isMurderMystery(LocalPlayer self) {
        if (!HypixelServer.isCurrent()) {
            return false;
        }
        Scoreboard scoreboard = self.level().getScoreboard();
        Objective sidebar = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR);
        if (sidebar == null) {
            return false;
        }
        String title = sidebar.getDisplayName().getString().toUpperCase();
        if (!title.contains("MURDER") && !title.contains("MYSTERY")) {
            return false;
        }
        for (PlayerScoreEntry entry : scoreboard.listPlayerScores(sidebar)) {
            String line = entry.ownerName().getString();
            if (line.contains("Role:") || line.contains("Innocents Left:")) {
                return true;
            }
        }
        return false;
    }
}
