package cc.aerial.client.features.impl.combat;

import cc.aerial.client.event.impl.game.player.interaction.AttackEvent;
import cc.aerial.client.event.impl.game.player.movement.PreMovementPacketEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.utility.GroundTickTracker;
import cc.aerial.client.utility.Stopwatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.LivingEntity;

public final class CriticalsModule extends Module {
    public static final CriticalsModule INSTANCE = new CriticalsModule();

    private static final double[] EDIT_OFFSETS = {5.0E-4, 1.0E-4};

    private static final double[] PACKET_OFFSETS = {0.42, 0.0};

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.PACKET);
    private final NumberProperty delay = new NumberProperty("Delay", 500, 0, 1000, 1)
            .hideIf(() -> mode.getValue() == Mode.NO_GROUND);

    private final Stopwatch stopwatch = new Stopwatch();

    private boolean attacked;
    private int ticks;

    private CriticalsModule() {
        super("Criticals", "Makes the server treat your hits as critical", ModuleCategory.COMBAT);
        addProperties(mode, delay);
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    @Override
    protected void onDisable() {
        this.attacked = false;
        this.ticks = 0;
    }

    @Subscribe
    public void onAttack(AttackEvent event) {
        if (!(event.getTarget() instanceof LivingEntity living)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        switch (mode.getValue()) {
            case EDIT -> {
                if (player.onGround() && !player.onClimbable()
                        && this.stopwatch.hasTimeElapsed(delay.getValue().longValue())) {
                    player.crit(living);
                    this.stopwatch.reset();
                    this.attacked = true;
                }
            }
            case PACKET -> {
                if (this.stopwatch.hasTimeElapsed(delay.getValue().longValue())
                        && GroundTickTracker.getGroundTicks() > 0) {
                    sendHop(player);
                    player.crit(living);
                    this.stopwatch.reset();
                }
            }
            case NO_GROUND -> {
            }
        }
    }

    @Subscribe
    public void onPreMovementPacket(PreMovementPacketEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        switch (mode.getValue()) {
            case NO_GROUND -> event.setOnGround(false);
            case EDIT -> {
                if (!player.onGround() || !this.attacked) {
                    this.attacked = false;
                    this.ticks = 0;
                    return;
                }
                this.ticks++;
                if (this.ticks == 1) {
                    event.setY(event.getY() + EDIT_OFFSETS[0]);
                } else if (this.ticks == 2) {
                    event.setY(event.getY() + EDIT_OFFSETS[1]);
                    this.attacked = false;
                }
                event.setOnGround(false);
            }
            case PACKET -> {
            }
        }
    }

    private void sendHop(LocalPlayer player) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return;
        }
        for (double offset : PACKET_OFFSETS) {
            connection.send(new ServerboundMovePlayerPacket.Pos(
                    player.getX(), player.getY() + offset, player.getZ(),
                    false, player.horizontalCollision));
        }
    }

    public enum Mode {
        EDIT("Edit"),
        NO_GROUND("No Ground"),
        PACKET("Packet");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
