package cc.aerial.client.features.impl.world;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.packet.ReceivePacketEvent;
import cc.aerial.client.event.impl.game.JoinWorldEvent;
import cc.aerial.client.event.impl.game.server.ServerDisconnectEvent;
import cc.aerial.client.event.impl.game.player.movement.PreMovementPacketEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.combat.killaura.KillauraModule;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.rotation.ServerRotation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.ClientboundBlockEventPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

public final class ChestAuraModule extends Module {
    public static final ChestAuraModule INSTANCE = new ChestAuraModule();

    private final NumberProperty range = new NumberProperty("Range", 4.0, 1.0, 6.0, 0.1);
    private final BooleanProperty throughWalls = new BooleanProperty("Through Walls", true);

    private final List<BlockPos> openedChests = new ArrayList<>();

    private BlockPos targetChest;
    private float targetYaw;
    private float targetPitch;
    private boolean rotating;

    private ChestAuraModule() {
        super("Chest Aura", "Opens nearby chests automatically", ModuleCategory.WORLD);
        addProperties(range, throughWalls);
    }

    @Override
    public String getSuffix() {
        return String.format("%.1f", range.getValue().doubleValue());
    }

    @Override
    protected void onEnable() {
        openedChests.clear();
    }

    @Override
    protected void onDisable() {
        targetChest = null;
        rotating = false;
    }

    @Subscribe
    public void onJoinWorld(JoinWorldEvent event) {
        openedChests.clear();
    }

    @Subscribe
    public void onServerDisconnect(ServerDisconnectEvent event) {
        openedChests.clear();
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
        if (event.getPacket() instanceof ClientboundBlockEventPacket packet && packet.getB1() == 1) {
            markOpened(packet.getPos());
        }
    }

    private void markOpened(BlockPos pos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        if (!openedChests.contains(pos)) {
            openedChests.add(pos);
        }
        Block block = mc.level.getBlockState(pos).getBlock();
        if (!(block instanceof ChestBlock)) {
            return;
        }
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos neighbor = pos.relative(facing);
            if (mc.level.getBlockState(neighbor).getBlock() == block && !openedChests.contains(neighbor)) {
                openedChests.add(neighbor);
            }
        }
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;

        targetChest = null;
        rotating = false;

        if (player == null || level == null) {
            return;
        }

        if (KillauraModule.INSTANCE.isEnabled()
                && KillauraModule.INSTANCE.getTargeting().getRotationTarget() != null) {
            return;
        }

        if (mc.gui.screen() != null) {
            return;
        }

        for (ChestBlockEntity chest : nearbyChests(level, player)) {
            if (chest.getOpenNess(1.0f) > 0.0f) {
                markOpened(chest.getBlockPos());
            }
        }

        BlockPos closest = findClosestChest(level, player);
        if (closest == null) {
            return;
        }

        double dx = closest.getX() + 0.5 - player.getX();
        double dy = closest.getY() + 0.5 - player.getY() - player.getEyeHeight();
        double dz = closest.getZ() + 0.5 - player.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);

        targetYaw = (float) (Math.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
        targetPitch = (float) -(Math.atan2(dy, horizontal) * 180.0 / Math.PI);
        targetChest = closest;
        rotating = true;

        ServerRotation.submit(targetYaw, 0);

        BlockHitResult hit = new BlockHitResult(
                new Vec3(closest.getX(), closest.getY(), closest.getZ()), Direction.UP, closest, false);
        if (mc.gameMode != null
                && mc.gameMode.useItemOn(player, InteractionHand.MAIN_HAND, hit).consumesAction()) {
            player.swing(InteractionHand.MAIN_HAND);
            markOpened(closest);
        }
    }

    @Subscribe
    public void onPreMovementPacket(PreMovementPacketEvent event) {
        if (!rotating || targetChest == null) {
            return;
        }
        event.setYaw(targetYaw);
        event.setPitch(targetPitch);
    }

    private BlockPos findClosestChest(ClientLevel level, LocalPlayer player) {
        double maxDistanceSq = range.getValue().doubleValue() * range.getValue().doubleValue();
        BlockPos closest = null;
        double closestDistanceSq = maxDistanceSq;

        for (ChestBlockEntity chest : nearbyChests(level, player)) {
            BlockPos pos = chest.getBlockPos();
            if (openedChests.contains(pos)) {
                continue;
            }
            double distanceSq = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            if (distanceSq > closestDistanceSq) {
                continue;
            }
            if (!throughWalls.getValue() && !hasLineOfSight(level, player, pos)) {
                continue;
            }
            closest = pos;
            closestDistanceSq = distanceSq;
        }
        return closest;
    }

    private List<ChestBlockEntity> nearbyChests(ClientLevel level, LocalPlayer player) {
        double reach = range.getValue().doubleValue();
        int minChunkX = SectionPos.blockToSectionCoord(Math.floor(player.getX() - reach));
        int maxChunkX = SectionPos.blockToSectionCoord(Math.floor(player.getX() + reach));
        int minChunkZ = SectionPos.blockToSectionCoord(Math.floor(player.getZ() - reach));
        int maxChunkZ = SectionPos.blockToSectionCoord(Math.floor(player.getZ() + reach));

        List<ChestBlockEntity> chests = new ArrayList<>();
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof ChestBlockEntity chest) {
                        chests.add(chest);
                    }
                }
            }
        }
        return chests;
    }

    private static boolean hasLineOfSight(ClientLevel level, LocalPlayer player, BlockPos pos) {
        Vec3 eye = player.getEyePosition();
        Vec3 center = new Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        HitResult hit = level.clip(new ClipContext(
                eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));

        return hit.getType() == HitResult.Type.MISS
                || (hit instanceof BlockHitResult blockHit && blockHit.getBlockPos().equals(pos));
    }
}
