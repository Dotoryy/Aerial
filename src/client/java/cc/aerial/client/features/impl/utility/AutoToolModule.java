package cc.aerial.client.features.impl.utility;

import cc.aerial.client.event.impl.game.input.MouseHandleInputEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.mouse.MouseHelper;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.utility.HypixelServer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class AutoToolModule extends Module {
    public static final AutoToolModule INSTANCE = new AutoToolModule();

    private final BooleanProperty onlySneaking = new BooleanProperty("Only while sneaking", false);
    private final BooleanProperty switchPreviousSlot = new BooleanProperty("Switch previous slot", false);
    private final BooleanProperty silent = new BooleanProperty("Spoof", false);

    private int previousSlot = -1;

    private ItemStack silentDisplayStack = ItemStack.EMPTY;

    private AutoToolModule() {
        super("Auto Tool", "Automatically switches to the best tool in your hotbar.", ModuleCategory.UTILITY);
        addProperties(onlySneaking, switchPreviousSlot, silent);
    }

    @Override
    public String getSuffix() {
        return silent.getValue() ? "Spoof" : "Normal";
    }

    public ItemStack getSilentDisplayStack() {
        return silentDisplayStack;
    }

    @Override
    protected void onDisable() {
        if (Minecraft.getInstance().player != null) {
            restorePreviousSlot(Minecraft.getInstance().player);
        }
        silentDisplayStack = ItemStack.EMPTY;
    }

    @Subscribe
    public void onMouseHandleInput(MouseHandleInputEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }

        if (onlySneaking.getValue() && !player.isShiftKeyDown()) {
            restorePreviousSlot(player);
            return;
        }

        if (!(mc.hitResult instanceof BlockHitResult blockHitResult) || blockHitResult.getType() != HitResult.Type.BLOCK
                || !MouseHelper.getLeftButton().isDown() || MouseHelper.getRightButton().isDown()
                || player.isUsingItem()) {
            restorePreviousSlot(player);
            return;
        }

        GameType gameType = mc.gameMode.getPlayerMode();
        if (gameType == GameType.CREATIVE || gameType == GameType.SPECTATOR) {
            restorePreviousSlot(player);
            return;
        }

        BlockPos pos = blockHitResult.getBlockPos();
        BlockState blockState = level.getBlockState(pos);
        float hardness = blockState.getDestroySpeed(level, pos);
        if (hardness == 0.0f) {
            restorePreviousSlot(player);
            return;
        }

        Inventory inventory = player.getInventory();
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.getDestroySpeed(blockState) > 1.0f) {
                slot = i;
                break;
            }
        }
        if (slot == -1) {
            restorePreviousSlot(player);
            return;
        }

        if (inventory.getSelectedSlot() != slot) {
            if ((switchPreviousSlot.getValue() || silent.getValue()) && previousSlot == -1) {
                previousSlot = inventory.getSelectedSlot();
            }
            if (silent.getValue() && silentDisplayStack.isEmpty()) {
                silentDisplayStack = inventory.getItem(inventory.getSelectedSlot()).copy();
            }
            inventory.setSelectedSlot(slot);
        }
    }

    private void restorePreviousSlot(LocalPlayer player) {
        silentDisplayStack = ItemStack.EMPTY;
        if (previousSlot != -1) {
            player.getInventory().setSelectedSlot(previousSlot);
            previousSlot = -1;
        }
    }
}
