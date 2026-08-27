package cc.aerial.client.features.impl.world;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.utility.KeyMappingUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.player.LocalPlayer;

public final class AntiAfkModule extends Module {
    public static final AntiAfkModule INSTANCE = new AntiAfkModule();

    private static final int IDLE_TICKS = 20 * 10;

    private static final int RELEASE_INTERVAL = 5;
    private static final int STRAFE_INTERVAL = 20;

    private static final int STRAFE_ALTERNATE = 40;
    private static final int JUMP_INTERVAL = 100;

    private int idleTicks;

    private AntiAfkModule() {
        super("Anti AFK", "Moves for you so idle timers never fire", ModuleCategory.WORLD);
    }

    @Override
    protected void onDisable() {
        idleTicks = 0;
        releaseAll();
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        Options options = mc.options;
        if (options.keyJump.isDown() || options.keyRight.isDown() || options.keyUp.isDown()
                || options.keyLeft.isDown() || options.keyDown.isDown()) {
            idleTicks = 0;
        }
        idleTicks++;
        if (idleTicks < IDLE_TICKS) {
            return;
        }

        int age = player.tickCount;

        if (age % RELEASE_INTERVAL == 0) {
            releaseAll();
        }
        if (age % STRAFE_INTERVAL == 0) {
            KeyMappingUtility.press(age % STRAFE_ALTERNATE == 0 ? options.keyRight : options.keyLeft);
        }
        if (age % JUMP_INTERVAL == 0) {
            KeyMappingUtility.press(options.keyJump);
        }
    }

    private void releaseAll() {
        Options options = Minecraft.getInstance().options;
        KeyMappingUtility.release(options.keyRight);
        KeyMappingUtility.release(options.keyLeft);
        KeyMappingUtility.release(options.keyJump);
    }
}
