package cc.aerial.client.features.impl.utility;

import cc.aerial.client.event.impl.game.player.movement.PreMovementPacketEvent;
import cc.aerial.client.event.impl.game.player.teleport.PostTeleportEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.movement.FlightModule;
import cc.aerial.client.packet.blockage.BlockHolder;
import cc.aerial.client.packet.blockage.NetworkDirection;
import cc.aerial.client.utility.PlayerUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public final class AntiVoidModule extends Module {
    public static final AntiVoidModule INSTANCE = new AntiVoidModule();

    private final BlockHolder outboundHolder = new BlockHolder(NetworkDirection.OUTBOUND);

    private SavedGround savedGround;
    private boolean blinked, failed;
    private double startingY;

    private AntiVoidModule() {
        super("Anti Void", "Makes it impossible to fall into the void", ModuleCategory.UTILITY);
    }

    @Override
    public String getSuffix() {
        return "Blink";
    }

    @Override
    protected void onDisable() {
        outboundHolder.release();
        savedGround = null;
        blinked = false;
        failed = false;
    }

    @Subscribe
    public void onPreMovementPacket(PreMovementPacketEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        boolean shouldRun = !FlightModule.INSTANCE.isEnabled()
                && !player.getAbilities().mayfly
                && !player.getAbilities().flying;

        if (!shouldRun) {
            outboundHolder.release();
            savedGround = null;
            failed = true;
            return;
        }

        if (PlayerUtility.isOverVoid(player.level(), player.getBoundingBox())) {
            if (!failed) {
                if (player.getY() - startingY <= -6.0 && savedGround != null) {
                    savedGround.restore(player);

                    if (NoFallModule.INSTANCE.isEnabled()) {
                        NoFallModule.INSTANCE.syncFallDifference();
                    }

                    outboundHolder.block(packet -> packet instanceof ServerboundMovePlayerPacket ? null : packet);

                    startingY = player.getY();

                    outboundHolder.release();
                } else {
                    outboundHolder.block();
                }
            }

            blinked = true;
        } else {
            savedGround = new SavedGround(player.position(), player.getDeltaMovement());
            startingY = player.getY();
            failed = false;

            if (blinked) {
                outboundHolder.release();
                blinked = false;
            }
        }
    }

    @Subscribe
    public void onPostTeleport(PostTeleportEvent event) {
        if (savedGround != null) {
            outboundHolder.release();
            savedGround = null;
            failed = true;
        }
    }

    private record SavedGround(Vec3 position, Vec3 velocity) {
        void restore(LocalPlayer player) {
            player.setPos(position.x, position.y, position.z);
            player.setDeltaMovement(0.0, velocity.y, 0.0);
        }
    }
}
