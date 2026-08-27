package cc.aerial.client.features.impl.movement;

import cc.aerial.client.event.impl.game.player.movement.JumpEvent;
import cc.aerial.client.event.impl.game.player.movement.PreMovementPacketEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.ModeProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

public final class JesusModule extends Module {
    public static final JesusModule INSTANCE = new JesusModule();

    public enum Mode {
        VANILLA("Vanilla"),
        NCP("NCP");

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
    private final BooleanProperty allowJump = new BooleanProperty("Allow Jump", true);

    private JesusModule() {
        super("Jesus", "Walk on liquid surfaces", ModuleCategory.MOVEMENT);
        addProperties(mode, allowJump);
    }

    @Override
    public String getSuffix() {
        return mode.getValue().toString();
    }

    public boolean isSolidifyingLiquids() {
        return isEnabled() && !Minecraft.getInstance().options.keyShift.isDown();
    }

    @Subscribe
    public void onJump(JumpEvent event) {
        if (!allowJump.getValue() && isStandingOnSolidifiedLiquid()) {
            event.setCancelled();
        }
    }

    @Subscribe(priority = -5)
    public void onMovementPacket(PreMovementPacketEvent event) {
        if (mode.getValue() != Mode.NCP || !isEnabled()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || player.tickCount % 2 != 0 || !isStandingOnSolidifiedLiquid()) {
            return;
        }
        event.setY(event.getY() - 0.015625);
    }

    private boolean isStandingOnSolidifiedLiquid() {
        if (!isSolidifyingLiquids()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null || !player.onGround()) {
            return false;
        }
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.1, player.getZ());
        return !mc.level.getFluidState(below).isEmpty();
    }
}
