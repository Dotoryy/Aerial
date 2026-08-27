package cc.aerial.client.features.impl.utility;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.utility.InventoryUtility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

public final class AutoChestModule extends Module {
    public static final AutoChestModule INSTANCE = new AutoChestModule();

    private static final Set<Item> RESOURCES = Set.of(Items.IRON_INGOT, Items.GOLD_INGOT, Items.DIAMOND, Items.EMERALD);

    private enum ChestInteractionMode {
        DEPOSIT, WITHDRAW, NONE
    }

    private final NumberProperty ticks = new NumberProperty("Ticks", 1, 0, 10, 1);
    private final BooleanProperty autoDeposit = new BooleanProperty("Auto Deposit", true);

    private ChestInteractionMode mode = ChestInteractionMode.NONE;
    private boolean hasSeenChest;
    private int tickCount;

    private AutoChestModule() {
        super("Auto Chest", "Dumps and retrieves resources in chests", ModuleCategory.UTILITY);
        addProperties(ticks, autoDeposit);
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ContainerScreen containerScreen)
                || !containerScreen.getTitle().getString().toLowerCase().contains("chest")) {
            resetState();
            return;
        }

        if (!this.hasSeenChest && this.autoDeposit.getValue()) {
            this.mode = ChestInteractionMode.DEPOSIT;
        }

        this.hasSeenChest = true;
        this.tickCount++;

        if (this.tickCount - 1 < this.ticks.getValue().intValue()) {
            return;
        }

        this.tickCount = 0;

        long window = mc.getWindow().handle();
        if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_MINUS) == GLFW.GLFW_PRESS) {
            this.mode = ChestInteractionMode.WITHDRAW;
        } else if (GLFW.glfwGetKey(window, GLFW.GLFW_KEY_EQUAL) == GLFW.GLFW_PRESS) {
            this.mode = ChestInteractionMode.DEPOSIT;
        }

        ChestMenu menu = containerScreen.getMenu();

        switch (this.mode) {
            case DEPOSIT -> {
                if (handleDeposit(mc, menu)) {
                    return;
                }
                this.mode = ChestInteractionMode.NONE;
            }
            case WITHDRAW -> {
                if (handleWithdraw(menu)) {
                    return;
                }
                this.mode = ChestInteractionMode.NONE;
            }
            case NONE -> {
            }
        }
    }

    private boolean handleDeposit(Minecraft mc, ChestMenu menu) {
        int chestSlotCount = menu.getContainer().getContainerSize();
        for (int i = chestSlotCount; i < menu.slots.size(); i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (RESOURCES.contains(stack.getItem())) {
                InventoryUtility.shiftClick(menu, i);
                return true;
            }
        }
        return false;
    }

    private boolean handleWithdraw(ChestMenu menu) {
        Container chestInventory = menu.getContainer();
        for (int i = 0; i < chestInventory.getContainerSize(); i++) {
            if (RESOURCES.contains(chestInventory.getItem(i).getItem())) {
                InventoryUtility.shiftClick(menu, i);
                return true;
            }
        }
        return false;
    }

    private void resetState() {
        this.mode = ChestInteractionMode.NONE;
        this.hasSeenChest = false;
        this.tickCount = 0;
    }
}
