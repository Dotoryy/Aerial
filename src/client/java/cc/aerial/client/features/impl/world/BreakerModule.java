package cc.aerial.client.features.impl.world;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.hud.BreakProgressBar;
import cc.aerial.client.features.impl.hud.DynamicIsland;
import cc.aerial.client.features.impl.hud.IslandTrigger;
import cc.aerial.client.features.impl.visual.InterfaceModule;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.mixin.MultiPlayerGameModeAccessor;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.render.RenderUtil;
import cc.aerial.client.render.TextRenderUtil;
import cc.aerial.client.render.font.AerialFont;
import cc.aerial.client.rotation.RaycastUtility;
import cc.aerial.client.rotation.RotationHelper;
import cc.aerial.client.rotation.RotationUtility;
import cc.aerial.client.rotation.model.impl.InstantRotationModel;
import cc.aerial.client.screen.animation.Animation;
import cc.aerial.client.screen.animation.Easing;
import cc.aerial.client.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class BreakerModule extends Module implements IslandTrigger {
    public static final BreakerModule INSTANCE = new BreakerModule();

    private final NumberProperty range = new NumberProperty("Range", 4.5, 0.5, 6.0, 0.5);
    private final BooleanProperty breakSurroundings = new BooleanProperty("Break Surroundings", true);

    private final BooleanProperty attackWhileBreaking = new BooleanProperty("Attack While Breaking", true);

    private final BooleanProperty waitForKillaura = new BooleanProperty("Wait For KillAura", false);

    private static final Direction[] DIRECTIONS = {Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    private static final float ISLAND_WIDTH = 140.0f;
    private static final float ISLAND_HEIGHT = 25.0f;
    private static final float SWATCH_SIZE = 17.0f;
    private static final float BAR_WIDTH = 85.0f;

    private static final float BAR_HEIGHT = 3.0f;
    private static final float PADDING = 5.5f;

    private record TargetResult(BlockPos pos, double resistance, int slot) {
    }

    private BlockPos currentTarget;

    private double currentResistance;

    private int bestSlot = -1;
    private boolean breaking;
    private Animation progressAnimation;

    private BreakerModule() {
        super("Breaker", "Breaks relevant blocks for mini-games", ModuleCategory.WORLD);
        addProperties(range, breakSurroundings, attackWhileBreaking, waitForKillaura);
    }

    public boolean isSuppressingKillauraAttack() {
        return isEnabled() && breaking && !attackWhileBreaking.getValue();
    }

    @Override
    protected void onDisable() {
        stop();
    }

    private void stop() {
        if (breaking) {
            Minecraft.getInstance().gameMode.stopDestroyBlock();
        }
        breaking = false;
        currentTarget = null;
        bestSlot = -1;
        progressAnimation = null;
        DynamicIsland.removeTrigger(this);
        BreakProgressBar.reset();
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        Level level = Minecraft.getInstance().level;
        if (player == null || level == null) {
            stop();
            return;
        }

        if (waitForKillaura.getValue()
                && cc.aerial.client.features.impl.combat.killaura.KillauraModule.INSTANCE.hasTarget()) {
            stop();
            return;
        }

        TargetResult newTarget = findTarget(player, level);

        boolean shouldSwitch;
        if (currentTarget == null) {
            shouldSwitch = true;
        } else {
            BlockState currentState = level.getBlockState(currentTarget);
            if (currentState.isAir() || !currentState.getFluidState().isEmpty()) {
                shouldSwitch = true;
            } else if (eyeDistance(player, currentTarget) > range.getValue()) {
                shouldSwitch = true;
            } else if (newTarget == null) {
                shouldSwitch = false;
            } else {
                float breakingProgress = breaking
                        ? ((MultiPlayerGameModeAccessor) Minecraft.getInstance().gameMode).aerial$getDestroyProgress()
                        : 0.0f;
                double remainingResistance = currentResistance * (1.0 - breakingProgress);
                shouldSwitch = remainingResistance >= newTarget.resistance();
            }
        }

        if (shouldSwitch) {
            if (newTarget == null) {
                stop();
                return;
            }
            if (!newTarget.pos().equals(currentTarget)) {
                stop();
            }
            currentTarget = newTarget.pos();
            currentResistance = newTarget.resistance();
            bestSlot = newTarget.slot();
        }

        Vec2 rotation = RotationUtility.getRotationFromPosition(Vec3.atCenterOf(currentTarget));
        RotationHelper.getHandler().rotate(rotation, InstantRotationModel.INSTANCE, this);

        BlockHitResult hit = RaycastUtility.rayTraceBlock(rotation.x, rotation.y, range.getValue());
        if (hit == null) {
            DynamicIsland.removeTrigger(this);
            return;
        }

        if (bestSlot != -1 && player.getInventory().getSelectedSlot() != bestSlot) {
            player.getInventory().setSelectedSlot(bestSlot);
        }

        var gameMode = Minecraft.getInstance().gameMode;
        if (!breaking) {
            gameMode.startDestroyBlock(currentTarget, hit.getDirection());
            breaking = true;
        } else {
            gameMode.continueDestroyBlock(currentTarget, hit.getDirection());
        }
        player.swing(InteractionHand.MAIN_HAND);

        DynamicIsland.addTrigger(this);
    }

    private static double eyeDistance(LocalPlayer player, BlockPos pos) {
        return player.getEyePosition().distanceTo(Vec3.atCenterOf(pos));
    }

    private TargetResult findTarget(LocalPlayer player, Level level) {
        Vec3 eyePos = player.getEyePosition();
        float r = range.getValue().floatValue();

        int fromX = (int) Math.floor(eyePos.x - r - 1), toX = (int) Math.ceil(eyePos.x + r + 1);
        int fromY = (int) Math.floor(eyePos.y - r - 1), toY = (int) Math.ceil(eyePos.y + r + 1);
        int fromZ = (int) Math.floor(eyePos.z - r - 1), toZ = (int) Math.ceil(eyePos.z + r + 1);

        BlockPos closestBed = null;
        double closestDistance = Double.MAX_VALUE;

        for (int x = fromX; x <= toX; x++) {
            for (int y = fromY; y <= toY; y++) {
                for (int z = fromZ; z <= toZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!(level.getBlockState(pos).getBlock() instanceof BedBlock)) {
                        continue;
                    }
                    double distance = eyePos.distanceTo(Vec3.atCenterOf(pos));
                    if (distance <= r && distance < closestDistance) {
                        closestDistance = distance;
                        closestBed = pos;
                    }
                }
            }
        }

        if (closestBed == null) {
            return null;
        }
        if (!breakSurroundings.getValue()) {
            return new TargetResult(closestBed, 0.01, -1);
        }

        List<BlockPos> adjacent = new ArrayList<>();
        for (Direction direction : DIRECTIONS) {
            adjacent.add(closestBed.relative(direction));
        }
        BlockState bedState = level.getBlockState(closestBed);
        BlockPos otherHalf = closestBed.relative(BedBlock.getConnectedDirection(bedState));
        for (Direction direction : DIRECTIONS) {
            adjacent.add(otherHalf.relative(direction));
        }
        adjacent.sort(java.util.Comparator.comparingDouble(pos -> eyePos.distanceToSqr(Vec3.atCenterOf(pos))));

        for (BlockPos pos : adjacent) {
            if (eyePos.distanceTo(Vec3.atCenterOf(pos)) > r) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) {
                return new TargetResult(closestBed, 0.01, -1);
            }
        }

        BlockPos weakest = null;
        double weakestResistance = Double.MAX_VALUE;
        int weakestSlot = -1;
        for (BlockPos pos : adjacent) {
            double distance = eyePos.distanceTo(Vec3.atCenterOf(pos));
            if (distance > r) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BedBlock || state.isAir()) {
                continue;
            }

            int selectedSlot = player.getInventory().getSelectedSlot();
            int bestSlotForCandidate = selectedSlot;
            float fastestSpeed = player.getInventory().getItem(selectedSlot).getDestroySpeed(state);
            for (int i = 0; i < 9; i++) {
                if (i == selectedSlot) {
                    continue;
                }
                float speed = player.getInventory().getItem(i).getDestroySpeed(state);
                if (speed > fastestSpeed) {
                    fastestSpeed = speed;
                    bestSlotForCandidate = i;
                }
            }

            double resistance = Math.max(0.01, state.getDestroySpeed(level, pos)) / Math.max(0.01, fastestSpeed);
            if (resistance < weakestResistance) {
                weakestResistance = resistance;
                weakest = pos;
                weakestSlot = bestSlotForCandidate;
            }
        }

        return weakest == null ? null : new TargetResult(weakest, weakestResistance, weakestSlot);
    }

    @Override
    public float getIslandWidth() {
        return ISLAND_WIDTH;
    }

    @Override
    public float getIslandHeight() {
        return ISLAND_HEIGHT;
    }

    @Override
    public int getIslandPriority() {
        return 3;
    }

    @Override
    public void renderIsland(GuiGraphicsExtractor extractor, float x, float y, float width, float height, float progress) {
        if (currentTarget == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) {
            return;
        }

        Theme theme = InterfaceModule.INSTANCE.getTheme();
        int accent = theme.getAccentColor(0, 0).getRGB() | 0xFF000000;

        float swatchX = x + PADDING;
        float swatchY = y + (height - SWATCH_SIZE) * 0.5f;
        RenderUtil.roundedRect(extractor, swatchX, swatchY, SWATCH_SIZE, SWATCH_SIZE, SWATCH_SIZE * 0.5f, withAlpha(accent, 0.5f));

        ItemStack targetItem = level.getBlockState(currentTarget).getBlock().asItem().getDefaultInstance();
        if (!targetItem.isEmpty()) {
            float itemScale = 0.75f;
            extractor.pose().pushMatrix();
            extractor.pose().translate(swatchX + SWATCH_SIZE * 0.5f - 8f * itemScale, swatchY + SWATCH_SIZE * 0.5f - 8f * itemScale);
            extractor.pose().scale(itemScale, itemScale);
            extractor.item(targetItem, 0, 0);
            extractor.pose().popMatrix();
        }

        float barX = swatchX + SWATCH_SIZE + 5.0f;
        float barY = y + (height - BAR_HEIGHT) * 0.5f + 0.5f;

        float breakProgress = Math.min(1.0f, ((MultiPlayerGameModeAccessor) mc.gameMode).aerial$getDestroyProgress());

        if (progressAnimation == null) {
            progressAnimation = new Animation(Easing.EASE_OUT_EXPO, 200);
            progressAnimation.setValue(breakProgress * BAR_WIDTH);
        } else {
            progressAnimation.run(breakProgress * BAR_WIDTH);
        }

        RenderUtil.roundedRect(extractor, barX, barY, BAR_WIDTH, BAR_HEIGHT, BAR_HEIGHT * 0.5f, 0x40FFFFFF);
        if (breakProgress > 0.0f) {
            RenderUtil.roundedRect(extractor, barX, barY, progressAnimation.getValue(), BAR_HEIGHT, BAR_HEIGHT * 0.5f, accent);
        }

        ensureFontLoaded();
        String percentText = (int) (breakProgress * 100) + "%";
        TextRenderUtil.drawString(extractor, font, percentText,
                barX + BAR_WIDTH + 6.0f, y + (height - 7f) * 0.5f, 7f, withAlpha(0xFFFFFFFF, progress));
    }

    private static AerialFont font;

    private static void ensureFontLoaded() {
        if (font == null) {
            font = AerialFont.createFromResource("OpalProductSansMedium.ttf");
        }
    }

    private static int withAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    public BlockPos getCurrentTarget() {
        return currentTarget;
    }

    public boolean isBreaking() {
        return breaking;
    }
}
