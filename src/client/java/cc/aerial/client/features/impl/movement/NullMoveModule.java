package cc.aerial.client.features.impl.movement;

import cc.aerial.client.event.impl.game.input.MoveInputEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

public final class NullMoveModule extends Module {
    public static final NullMoveModule INSTANCE = new NullMoveModule();

    private boolean previousForward;
    private boolean previousBack;
    private boolean previousLeft;
    private boolean previousRight;
    private int lastForwardSign;
    private int lastSidewaysSign;

    private NullMoveModule() {
        super("Null Move", "Makes the newer of two opposing keys win", ModuleCategory.MOVEMENT);
    }

    @Override
    protected void onDisable() {
        previousForward = false;
        previousBack = false;
        previousLeft = false;
        previousRight = false;
        lastForwardSign = 0;
        lastSidewaysSign = 0;
    }

    @Subscribe(priority = 5)
    public void onMoveInput(MoveInputEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.screen() != null) {
            return;
        }
        Options options = mc.options;
        boolean forward = options.keyUp.isDown();
        boolean back = options.keyDown.isDown();
        boolean left = options.keyLeft.isDown();
        boolean right = options.keyRight.isDown();

        if (forward && !previousForward) {
            lastForwardSign = 1;
        }
        if (back && !previousBack) {
            lastForwardSign = -1;
        }
        if (left && !previousLeft) {
            lastSidewaysSign = 1;
        }
        if (right && !previousRight) {
            lastSidewaysSign = -1;
        }

        if (forward && back) {
            event.setForward(lastForwardSign >= 0 ? 1.0f : -1.0f);
        }
        if (left && right) {
            event.setSideways(lastSidewaysSign >= 0 ? 1.0f : -1.0f);
        }

        previousForward = forward;
        previousBack = back;
        previousLeft = left;
        previousRight = right;
    }
}
