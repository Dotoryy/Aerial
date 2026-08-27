package cc.aerial.client.features.impl.movement;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.player.movement.PostMoveEvent;
import cc.aerial.client.event.impl.game.player.movement.PreMovementPacketEvent;
import cc.aerial.client.event.impl.game.player.teleport.PostTeleportEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.utility.MoveUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public final class PhaseModule extends Module {
    public static final PhaseModule INSTANCE = new PhaseModule();

    private final BooleanProperty autoDisable = new BooleanProperty("Auto Disable", true);

    private boolean collision, phased, shouldForward;
    private int ticksSinceTeleport = -1;

    private PhaseModule() {
        super("Phase", "Allows you to walk through walls", ModuleCategory.MOVEMENT);
        addProperties(autoDisable);
    }

    @Override
    protected void onEnable() {
        this.phased = false;
        this.shouldForward = false;
        this.ticksSinceTeleport = -1;
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        this.collision = player != null && player.horizontalCollision;
        if (this.ticksSinceTeleport >= 0) {
            this.ticksSinceTeleport++;
        }
    }

    @Subscribe
    public void onPostTeleport(PostTeleportEvent event) {
        this.shouldForward = true;
        this.ticksSinceTeleport = 0;
    }

    @Subscribe
    public void onPreMovementPacket(PreMovementPacketEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        double yaw = MoveUtility.getDirectionRadians();

        if (!this.phased) {
            if (this.collision) {
                double amount = 0.005;
                player.setPos(player.getX() - Mth.sin((float) yaw) * amount, player.getY(), player.getZ() + Mth.cos((float) yaw) * amount);
                this.phased = true;
            }
        } else if (player.level().noCollision(player) && this.shouldForward) {
            if (this.autoDisable.getValue()) {
                this.setEnabled(false);
            } else {
                this.phased = false;
            }
        }

        if (this.phased && this.ticksSinceTeleport == 3) {
            double amount = 0.8;
            player.setPos(player.getX() - Mth.sin((float) yaw) * amount, player.getY(), player.getZ() + Mth.cos((float) yaw) * amount);
        }
    }

    @Subscribe
    public void onPostMove(PostMoveEvent event) {
        if (this.shouldForward) {
            MoveUtility.setSpeed(0);
        }
    }
}
