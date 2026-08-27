package cc.aerial.client.scaffold;

import cc.aerial.client.mixin.MultiPlayerGameModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

public final class SlotSpoof {
    private static boolean active;
    private static int slot = -1;

    private SlotSpoof() {
    }

    public static void set(int targetSlot) {
        if (targetSlot < 0 || targetSlot > 8) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.gameMode == null) {
            return;
        }
        active = true;
        slot = targetSlot;
        ((MultiPlayerGameModeAccessor) minecraft.gameMode).aerial$ensureHasSentCarriedItem();
    }

    public static void reset() {
        if (!active) {
            return;
        }
        active = false;
        slot = -1;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gameMode != null) {
            ((MultiPlayerGameModeAccessor) minecraft.gameMode).aerial$ensureHasSentCarriedItem();
        }
    }

    public static boolean isActive() {
        return active;
    }

    public static int getSlot() {
        return slot;
    }

    public static ItemStack getStack() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return ItemStack.EMPTY;
        }
        int index = active ? slot : player.getInventory().getSelectedSlot();
        if (index < 0 || index > 8) {
            return ItemStack.EMPTY;
        }
        return player.getInventory().getItem(index);
    }
}
