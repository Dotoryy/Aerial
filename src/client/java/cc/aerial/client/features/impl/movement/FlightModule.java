package cc.aerial.client.features.impl.movement;

import cc.aerial.client.event.impl.game.input.MoveInputEvent;
import cc.aerial.client.event.impl.game.player.movement.PostMoveEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.ModeProperty;
import cc.aerial.client.event.impl.game.packet.SendPacketEvent;
import cc.aerial.client.event.impl.game.player.movement.PreMovementPacketEvent;
import cc.aerial.client.mixin.LocalPlayerInvoker;
import cc.aerial.client.property.BooleanProperty;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.utility.MoveUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

public final class FlightModule extends Module {
    public static final FlightModule INSTANCE = new FlightModule();

    public enum Mode {
        VANILLA("Vanilla"),
        FIREBALL("Fireball"),
        AIR_WALK("Air Walk"),
        BLOXD("Bloxd"),
        CUBE_CRAFT("CubeCraft");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final ModeProperty<Mode> mode = new ModeProperty<>("Mode", Mode.VANILLA);
    private final NumberProperty speed = new NumberProperty("Speed", 1.0, 0.1, 10.0, 0.1)
            .hideIf(() -> mode.getValue() != Mode.VANILLA && mode.getValue() != Mode.CUBE_CRAFT);

    private final BooleanProperty sendFlying = new BooleanProperty("Send Flying", false)
            .hideIf(() -> mode.getValue() != Mode.CUBE_CRAFT);

    private FlightModule() {
        super("Flight", "You grow wings in real life", ModuleCategory.MOVEMENT);
        addProperties(mode, speed, sendFlying);
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    @Override
    protected void onDisable() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        double maxSpeed = MoveUtility.getSwiftnessSpeed(0.221);
        MoveUtility.setSpeed(Math.min(MoveUtility.getSpeed(), maxSpeed));
        player.setDeltaMovement(player.getDeltaMovement().x, 0.0, player.getDeltaMovement().z);
    }

    @Subscribe
    public void onPostMove(PostMoveEvent event) {
        if (mode.getValue() != Mode.VANILLA) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        double speedValue = speed.getValue();
        double motionY = 0.0;
        if (mc.options.keyJump.isDown()) {
            motionY = speedValue;
        } else if (mc.options.keyShift.isDown()) {
            motionY = -speedValue;
        }

        MoveUtility.setSpeed(MoveUtility.isMoving() ? speedValue : 0);
        player.setDeltaMovement(player.getDeltaMovement().x, motionY, player.getDeltaMovement().z);
    }

    @Subscribe
    public void onMoveInput(MoveInputEvent event) {
        if (mode.getValue() == Mode.VANILLA || mode.getValue() == Mode.CUBE_CRAFT) {
            event.setSneak(false);
        }
    }

    @Subscribe
    public void onPostMoveCubeCraft(PostMoveEvent event) {
        if (mode.getValue() != Mode.CUBE_CRAFT) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }
        double value = speed.getValue();
        double motionY = -1.0E-10
                + (mc.options.keyJump.isDown() ? value : 0.0)
                - (mc.options.keyShift.isDown() ? value : 0.0);

        MoveUtility.setSpeed(MoveUtility.isMoving() ? value : 0.0);
        player.setDeltaMovement(player.getDeltaMovement().x, motionY, player.getDeltaMovement().z);
    }

    @Subscribe
    public void onPreMovementPacket(PreMovementPacketEvent event) {
        if (mode.getValue() != Mode.CUBE_CRAFT) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        LocalPlayerInvoker invoker = (LocalPlayerInvoker) player;
        double distance = player.position().distanceTo(new Vec3(
                invoker.aerial$getLastX(), invoker.aerial$getLastY(), invoker.aerial$getLastZ()));
        if (distance <= 10.0 - speed.getValue() - 0.15) {
            event.setCancelled();
        }
    }

    @Subscribe
    public void onSendPacket(SendPacketEvent event) {
        if (mode.getValue() != Mode.CUBE_CRAFT || sendFlying.getValue()) {
            return;
        }
        Packet<?> packet = event.getPacket();
        if (packet instanceof ServerboundMovePlayerPacket
                && !(packet instanceof ServerboundMovePlayerPacket.Pos)
                && !(packet instanceof ServerboundMovePlayerPacket.PosRot)) {
            event.setCancelled();
        }
    }
}
