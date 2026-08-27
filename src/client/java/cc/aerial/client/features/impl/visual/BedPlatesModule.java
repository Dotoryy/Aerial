package cc.aerial.client.features.impl.visual;

import cc.aerial.client.event.impl.render.Render2DEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.render.CameraRenderStateHelper;
import cc.aerial.client.render.ESPUtility;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BedPlatesModule extends Module {
    public static final BedPlatesModule INSTANCE = new BedPlatesModule();

    private static final long SCAN_INTERVAL_MS = 5000L;

    private static final Direction[] SURROUNDING_FACES = {
            Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private final BooleanProperty minimal = new BooleanProperty("Minimal", false);
    private final BooleanProperty showGradient = new BooleanProperty("Show Gradient", false);
    private final BooleanProperty showDistance = new BooleanProperty("Show Distance", true);
    private final BooleanProperty distanceScale = new BooleanProperty("Distance Scale", true);
    private final NumberProperty range = new NumberProperty("Range", 200, 20, 200, 10);
    private final NumberProperty refreshTicks = new NumberProperty("Refresh Ticks", 1, 1, 20, 1);

    private static AerialFont nameFont;
    private static AerialFont distanceLabelFont;
    private static AerialFont distanceFont;

    private final List<Entry> entries = new ArrayList<>();
    private final List<BlockPos> bedPositions = new CopyOnWriteArrayList<>();

    private int refreshCounter;
    private int lastTick = -1;
    private int blockCheckTicks;
    private double gradientProgress = 0.5;
    private volatile boolean scanning;
    private long lastScanTime;

    private BedPlatesModule() {
        super("Bed Plates", "Shows what every nearby bed is walled in with, and how far away it is.",
                ModuleCategory.VISUAL);
        addProperties(minimal, showGradient, showDistance, distanceScale, range, refreshTicks);
    }

    @Override
    protected void onEnable() {
        reset();
    }

    @Override
    protected void onDisable() {
        reset();
    }

    private void reset() {
        entries.clear();
        bedPositions.clear();
        refreshCounter = 0;
        lastTick = -1;
        blockCheckTicks = 0;
        scanning = false;
        lastScanTime = 0L;
    }

    private static void ensureFontsLoaded() {
        if (nameFont == null) {
            nameFont = AerialFont.createFromResource("ProductSansMedium.ttf");
            distanceLabelFont = AerialFont.createFromResource("ProductSansBold.ttf");
            distanceFont = AerialFont.createFromResource("ProductSansRegular.ttf");
        }
    }

    @Subscribe
    public void onRender2D(Render2DEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            reset();
            return;
        }
        ensureFontsLoaded();

        int tick = player.tickCount;
        if (tick != lastTick) {
            lastTick = tick;
            refreshCounter++;
            tryStartScan(player, level);
            if (refreshCounter >= (int) refreshTicks.getValue().doubleValue() || entries.isEmpty()) {
                refreshCounter = 0;
                updateEntries(player, level);
            }
        }

        renderPlates(event, minecraft, level);
    }

    private void tryStartScan(LocalPlayer player, ClientLevel level) {
        long now = System.currentTimeMillis();
        if (scanning || now - lastScanTime < SCAN_INTERVAL_MS) {
            return;
        }
        lastScanTime = now;
        scanning = true;

        int horizontal = (int) range.getValue().doubleValue();

        int vertical = Math.min(100, Math.max(4, horizontal / 2));
        double rangeSq = range.getValue().doubleValue() * range.getValue().doubleValue();
        BlockPos origin = player.blockPosition();
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();
        int minY = level.getMinY();
        int maxY = level.getMaxY();

        Thread thread = new Thread(() -> {
            try {
                List<BlockPos> found = new ArrayList<>();
                BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
                double offsetX = origin.getX() + 0.5 - px;
                double offsetZ = origin.getZ() + 0.5 - pz;

                for (int dx = -horizontal; dx <= horizontal; dx++) {
                    double relX = dx + offsetX;
                    double sqX = relX * relX;
                    for (int dz = -horizontal; dz <= horizontal; dz++) {
                        double relZ = dz + offsetZ;
                        double sqXZ = sqX + relZ * relZ;
                        if (sqXZ > rangeSq) {
                            continue;
                        }
                        for (int dy = -vertical; dy <= vertical; dy++) {
                            int worldY = origin.getY() + dy;
                            if (worldY < minY || worldY > maxY) {
                                continue;
                            }
                            cursor.set(origin.getX() + dx, worldY, origin.getZ() + dz);
                            BlockState state = level.getBlockState(cursor);
                            if (!(state.getBlock() instanceof BedBlock)
                                    || state.getValue(BedBlock.PART) != BedPart.HEAD) {
                                continue;
                            }
                            double relY = worldY + 0.5 - py;
                            if (sqXZ + relY * relY > rangeSq) {
                                continue;
                            }
                            BlockPos immutable = cursor.immutable();
                            if (!found.contains(immutable)) {
                                found.add(immutable);
                            }
                        }
                    }
                }

                bedPositions.clear();
                bedPositions.addAll(found);
            } catch (Throwable ignored) {
            } finally {
                scanning = false;
            }
        }, "Aerial-BedPlatesScan");
        thread.setDaemon(true);
        thread.start();
    }

    private void updateEntries(LocalPlayer player, ClientLevel level) {
        entries.clear();
        if (bedPositions.isEmpty()) {
            return;
        }
        double rangeSq = range.getValue().doubleValue() * range.getValue().doubleValue();
        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        for (BlockPos head : bedPositions) {
            BlockState state = level.getBlockState(head);
            if (!(state.getBlock() instanceof BedBlock) || state.getValue(BedBlock.PART) != BedPart.HEAD) {
                continue;
            }
            Direction facing = state.getValue(HorizontalDirectionalBlock.FACING);
            BlockPos foot = head.relative(facing.getOpposite());

            double cx = (head.getX() + foot.getX() + 1.0) / 2.0;
            double cy = head.getY() + 0.5;
            double cz = (head.getZ() + foot.getZ() + 1.0) / 2.0;
            double dx = cx - px;
            double dy = cy - py;
            double dz = cz - pz;
            double distanceSq = dx * dx + dy * dy + dz * dz;
            if (distanceSq > rangeSq) {
                continue;
            }

            PlateInfo info = buildInfo(player, level, head, foot, cx, cy, cz);
            if (info != null) {
                entries.add(new Entry(new Vec3(cx, cy, cz), distanceSq, info));
            }
        }
        entries.sort(Comparator.comparingDouble(Entry::distanceSq));
    }

    private PlateInfo buildInfo(LocalPlayer player, ClientLevel level,
                                BlockPos head, BlockPos foot,
                                double cx, double cy, double cz) {
        ItemStack softest = null;
        double softestHardness = Double.MAX_VALUE;
        double softestDistanceSq = 0.0;
        MapColor softestColor = null;
        int surrounding = 0;

        double px = player.getX();
        double py = player.getY();
        double pz = player.getZ();

        for (BlockPos half : new BlockPos[]{head, foot}) {
            for (Direction face : SURROUNDING_FACES) {
                BlockPos neighbour = half.relative(face);
                BlockState state = level.getBlockState(neighbour);
                if (state.getBlock() instanceof BedBlock || state.isAir() || !state.getFluidState().isEmpty()) {
                    continue;
                }
                if (!state.isSolid()) {
                    continue;
                }
                float hardness = state.getDestroySpeed(level, neighbour);

                if (hardness < 0.0f) {
                    continue;
                }
                surrounding++;
                if (hardness >= softestHardness) {
                    continue;
                }
                ItemStack stack = new ItemStack(state.getBlock().asItem());
                if (stack.isEmpty()) {
                    continue;
                }
                double bx = neighbour.getX() + 0.5;
                double by = neighbour.getY() + 0.5;
                double bz = neighbour.getZ() + 0.5;
                softest = stack;
                softestDistanceSq = (bx - px) * (bx - px) + (by - py) * (by - py) + (bz - pz) * (bz - pz);
                softestColor = state.getMapColor(level, neighbour);
                softestHardness = hardness;
            }
        }

        if (surrounding == 0) {
            double dx = cx - px;
            double dy = cy - py;
            double dz = cz - pz;
            return new PlateInfo(new ItemStack(Items.BED.red()),
                    dx * dx + dy * dy + dz * dz, null, false, true);
        }
        if (softest != null) {
            return new PlateInfo(softest, softestDistanceSq, softestColor, surrounding < 8, false);
        }
        return null;
    }

    private void renderPlates(Render2DEvent event, Minecraft minecraft, ClientLevel level) {
        if (entries.isEmpty() || minecraft.gui.screen() != null) {
            return;
        }
        gradientProgress = Math.sin(System.currentTimeMillis() / 600.0) * 0.5 + 0.5;
        blockCheckTicks++;
        boolean recheck = blockCheckTicks >= 20;
        if (recheck) {
            blockCheckTicks = 0;
        }

        CameraRenderState camera = CameraRenderStateHelper.get();
        if (camera == null) {
            return;
        }

        for (Entry entry : entries) {
            if (recheck) {
                BlockPos pos = BlockPos.containing(entry.position());
                entry.visible = level.getBlockState(pos).getBlock() instanceof BedBlock;
            }
            if (!entry.visible) {
                continue;
            }

            Vector4f projected = ESPUtility.project(entry.position(), camera, event.width(), event.height());
            if (projected == null) {
                continue;
            }
            drawPlate(event.extractor(), entry.info(), projected.x, projected.y);
        }
    }

    private void drawPlate(GuiGraphicsExtractor extractor, PlateInfo info, float screenX, float screenY) {
        ItemStack stack = info.stack();
        boolean isMinimal = minimal.getValue();
        if (isMinimal && (info.incomplete() || info.notProtected())) {
            stack = new ItemStack(Items.BED.red());
        }

        double distance = info.distance();

        float scale = 1.0f;
        if (distanceScale.getValue() && distance > 10.0) {
            scale = (float) Math.max(0.5, 1.0 - (distance - 10.0) / 80.0);
        }

        boolean withDistance = showDistance.getValue();
        float pad = 4.0f * scale;
        float icon = 16.0f * scale;
        float iconScale = 1.05f * scale;
        float headerHeight = 8.0f * scale;
        float nameSize = NAME_FONT_SIZE * scale;
        float distanceSize = DISTANCE_FONT_SIZE * scale;

        String distanceText = info.distanceText();
        String label = "distance: ";

        float width;
        float bodyHeight;
        if (isMinimal) {
            float distanceWidth = withDistance ? distanceFont.stringWidth(distanceText, distanceSize) : 0f;
            width = Math.max(icon + pad * 2.0f, distanceWidth + pad * 4.0f);
            bodyHeight = icon + pad;
        } else {
            float nameHeight = nameSize;
            float rowHeight = Math.max(icon, nameHeight);
            float nameWidth = nameFont.stringWidth(info.displayName(), nameSize);
            float distanceWidth = withDistance
                    ? distanceLabelFont.stringWidth(label, distanceSize)
                            + distanceFont.stringWidth(distanceText, distanceSize)
                    : 0f;
            width = Math.max(icon + pad * 3.0f + nameWidth, distanceWidth + pad * 4.0f);
            bodyHeight = rowHeight + pad;
        }

        float boxX = screenX - width / 2.0f;
        float boxY = screenY - bodyHeight - headerHeight - 8.0f;
        float round = Math.max(2f, RISE_ROUND - 1f);

        int bodyColor = tintTowardMapColor(RISE_PANEL, info.mapColor());
        int headerColor = withAlpha(rgb(24, 24, 27), 105 / 255f);

        RenderUtil.roundedRectAsym(extractor, boxX, boxY, width, headerHeight, round, false, headerColor, null);
        RenderUtil.roundedRectAsym(extractor, boxX, boxY + headerHeight, width, bodyHeight, round, true, bodyColor, null);

        if (showGradient.getValue()) {
            int accentA = themeAccent(gradientProgress);
            int accentB = themeAccent(1.0 - gradientProgress);
            RenderUtil.roundedRectGradient(extractor, boxX, boxY, width, 2.5f * scale, 1f,
                    accentA, accentB, false, null);
        }

        if (withDistance) {
            float textY = boxY + headerHeight / 2.0f - 1.2f * scale;
            if (isMinimal) {
                float textWidth = distanceFont.stringWidth(distanceText, distanceSize);
                TextRenderUtil.drawString(extractor, distanceFont, distanceText,
                        boxX + (width - textWidth) / 2.0f, textY, distanceSize, 0xFFFFFFFF);
            } else {
                float labelWidth = distanceLabelFont.stringWidth(label, distanceSize);
                float valueWidth = distanceFont.stringWidth(distanceText, distanceSize);
                float startX = boxX + (width - (labelWidth + valueWidth)) / 2.0f;
                TextRenderUtil.drawString(extractor, distanceLabelFont, label,
                        startX, textY, distanceSize, 0xFFAAAAAA);
                TextRenderUtil.drawString(extractor, distanceFont, distanceText,
                        startX + labelWidth, textY, distanceSize, 0xFFFFFFFF);
            }
        }

        float bodyY = boxY + headerHeight;
        if (isMinimal) {
            float size = 16.0f * iconScale;
            drawItem(extractor, stack, boxX + (width - size) / 2.0f, bodyY + (bodyHeight - size) / 2.0f, iconScale);
        } else {
            float size = 16.0f * iconScale;
            drawItem(extractor, stack, boxX + pad, bodyY + (bodyHeight - size) / 2.0f, iconScale);
            float nameX = boxX + pad * 2.0f + icon;
            float nameY = bodyY + bodyHeight / 2.0f - nameSize / 2.0f;
            TextRenderUtil.drawString(extractor, nameFont, info.displayName(),
                    nameX, nameY, nameSize, 0xFFE0E0E6);
        }
    }

    private static void drawItem(GuiGraphicsExtractor extractor, ItemStack stack, float x, float y, float scale) {
        extractor.pose().pushMatrix();
        extractor.pose().translate(x, y);
        extractor.pose().scale(scale, scale);
        extractor.item(stack, 0, 0);
        extractor.pose().popMatrix();
    }

    private static int tintTowardMapColor(int panel, MapColor mapColor) {
        if (mapColor == null) {
            return panel;
        }
        int target = mapColor.col;
        return mix(panel, (panel & 0xFF000000) | (target & 0x00FFFFFF), 0.35f);
    }

    private static int themeAccent(double t) {
        return InterfaceModule.INSTANCE.getTheme().getAccentColor(0, t * 50.0).getRGB() | 0xFF000000;
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int withAlpha(int argb, float alphaMul) {
        int a = Math.max(0, Math.min(255, (int) (((argb >>> 24) & 0xFF) * alphaMul)));
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private static final int RISE_PANEL = 0x6E000000;
    private static final float RISE_ROUND = 5f;
    private static final float NAME_FONT_SIZE = 7.5f;
    private static final float DISTANCE_FONT_SIZE = 5.5f;

    private static final class Entry {
        private final Vec3 position;
        private final double distanceSq;
        private final PlateInfo info;
        private boolean visible = true;

        Entry(Vec3 position, double distanceSq, PlateInfo info) {
            this.position = position;
            this.distanceSq = distanceSq;
            this.info = info;
        }

        Vec3 position() {
            return position;
        }

        double distanceSq() {
            return distanceSq;
        }

        PlateInfo info() {
            return info;
        }
    }

    private record PlateInfo(ItemStack stack, String displayName, String distanceText,
                             double distance, MapColor mapColor, boolean incomplete, boolean notProtected) {
        PlateInfo(ItemStack stack, double distanceSq, MapColor mapColor, boolean incomplete, boolean notProtected) {
            this(stack,
                    notProtected ? "Not Protected" : incomplete ? "Incomplete" : stack.getHoverName().getString(),
                    formatDistance(Math.sqrt(distanceSq)),
                    Math.sqrt(distanceSq), mapColor, incomplete, notProtected);
        }

        private static String formatDistance(double distance) {
            return Math.round(distance * 10.0) / 10.0 + "m";
        }
    }
}
