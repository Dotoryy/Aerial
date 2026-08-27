package cc.aerial.client.features.impl.utility;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.impl.game.packet.ReceivePacketEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.combat.killaura.KillauraModule;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.BoundedNumberProperty;
import cc.aerial.client.property.NumberProperty;
import cc.aerial.client.utility.HypixelServer;
import cc.aerial.client.utility.InventoryUtility;
import net.hypixel.data.type.GameType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BucketItem;
import net.minecraft.tags.ItemTags;

import java.util.Comparator;

public final class InventoryManagerModule extends Module {
    public static final InventoryManagerModule INSTANCE = new InventoryManagerModule();

    private final BoundedNumberProperty delay = new BoundedNumberProperty("Delay", 50, 100, 0, 400, 5);
    private final BooleanProperty arrangeSword = new BooleanProperty("Sword", true);
    private final BooleanProperty arrangePickaxe = new BooleanProperty("Pickaxe", true);
    private final BooleanProperty arrangeAxe = new BooleanProperty("Axe", true);
    private final BooleanProperty arrangeBlocks = new BooleanProperty("Blocks", true);
    private final NumberProperty swordSlot = new NumberProperty("Sword Slot", 0, 0, 8, 1);
    private final NumberProperty pickaxeSlot = new NumberProperty("Pickaxe Slot", 1, 0, 8, 1);
    private final NumberProperty axeSlot = new NumberProperty("Axe Slot", 2, 0, 8, 1);
    private final NumberProperty blockSlot = new NumberProperty("Block Slot", 3, 0, 8, 1);

    private long lastMoveTime;

    private InventoryManagerModule() {
        super("Inventory Manager", "Manages your inventory", ModuleCategory.UTILITY);
        addProperties(delay, arrangeSword, arrangePickaxe, arrangeAxe, arrangeBlocks, swordSlot, pickaxeSlot, axeSlot, blockSlot);
    }

    public boolean canMove(long customDelay) {
        if (customDelay == 0) {
            return true;
        }
        return System.currentTimeMillis() - lastMoveTime >= customDelay;
    }

    public void resetTimer() {
        lastMoveTime = System.currentTimeMillis();
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            return;
        }

        if (!(mc.gui.screen() instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen) && !InvMoveModule.INSTANCE.isEnabled()) {
            return;
        }

        if (KillauraModule.INSTANCE.getTargeting().getTarget() != null) {
            return;
        }

        if (HypixelServer.isCurrent()) {
            HypixelServer.ModAPI.Location location = HypixelServer.ModAPI.get().getCurrentLocation();
            if (location == null || location.isLobby()) {
                return;
            }
            if (location.serverType() != GameType.SURVIVAL_GAMES && location.serverType() != GameType.SKYWARS) {
                return;
            }
        }

        AbstractContainerMenu menu = player.containerMenu;
        if (!(menu instanceof InventoryMenu)) {
            return;
        }

        Slot bestSword = getBestByTag(menu, ItemTags.SWORDS, InventoryUtility::getSwordValue);
        Slot preferredSwordSlot = menu.getSlot(swordSlot.getValue().intValue() + 36);

        Slot bestPickaxe = getBestByTag(menu, ItemTags.PICKAXES, InventoryUtility::getToolValue);
        Slot preferredPickaxeSlot = menu.getSlot(pickaxeSlot.getValue().intValue() + 36);

        Slot bestAxe = getBestAxe(menu);
        Slot preferredAxeSlot = menu.getSlot(axeSlot.getValue().intValue() + 36);

        Slot mostBlocks = getMostBlocks(menu);
        Slot preferredBlockSlot = menu.getSlot(blockSlot.getValue().intValue() + 36);

        for (Slot validSlot : InventoryUtility.filterSlots(menu, s -> !s.getItem().isEmpty(), true)) {
            if (!canMove((long) delay.getRandomValue()) || InventoryUtility.isGoodItem(validSlot.getItem())) {
                continue;
            }
            if (validSlot.getItem().get(DataComponents.EQUIPPABLE) != null) {
                continue;
            }

            if (arrangeSword.getValue()) {
                arrangeBest(menu, preferredSwordSlot, bestSword, InventoryUtility::getSwordValue);
            }
            if (arrangePickaxe.getValue()) {
                arrangeBest(menu, preferredPickaxeSlot, bestPickaxe, InventoryUtility::getToolValue);
            }
            if (arrangeAxe.getValue()) {
                arrangeBest(menu, preferredAxeSlot, bestAxe, InventoryUtility::getToolValue);
            }
            if (arrangeBlocks.getValue()) {
                arrangeMostBlocks(menu, preferredBlockSlot, mostBlocks);
            }

            if (validSlot.index == preferredSwordSlot.index && validSlot.getItem().is(ItemTags.SWORDS)) {
                continue;
            }
            if (validSlot.index == preferredPickaxeSlot.index && validSlot.getItem().is(ItemTags.PICKAXES)) {
                continue;
            }
            if (validSlot.index == preferredAxeSlot.index && validSlot.getItem().getItem() instanceof AxeItem) {
                continue;
            }
            if (validSlot.getItem().getItem() instanceof BucketItem) {
                continue;
            }

            if (validSlot.getItem().getHoverName().getStyle().isEmpty()) {
                InventoryUtility.drop(menu, validSlot.index);
                resetTimer();
            }
        }
    }

    @Subscribe
    public void onReceivePacket(ReceivePacketEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.getPacket() instanceof ClientboundContainerSetSlotPacket slotUpdate
                && !slotUpdate.getItem().isEmpty()
                && player != null
                && slotUpdate.getContainerId() == player.containerMenu.containerId) {
            resetTimer();
        }
    }

    private Slot getBestByTag(AbstractContainerMenu menu, net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tag, java.util.function.ToDoubleFunction<ItemStack> value) {
        return InventoryUtility.filterSlots(menu, slot -> slot.getItem().is(tag), false)
                .stream()
                .max(Comparator.comparingDouble(slot -> value.applyAsDouble(slot.getItem())))
                .orElse(null);
    }

    private Slot getBestAxe(AbstractContainerMenu menu) {
        return InventoryUtility.filterSlots(menu, slot -> slot.getItem().getItem() instanceof AxeItem, false)
                .stream()
                .max(Comparator.comparingDouble(slot -> InventoryUtility.getToolValue(slot.getItem())))
                .orElse(null);
    }

    private Slot getMostBlocks(AbstractContainerMenu menu) {
        return InventoryUtility.filterSlots(menu, slot ->
                        slot.getItem().getItem() instanceof BlockItem blockItem
                                && slot.getItem().getCount() > 0
                                && InventoryUtility.isGoodBlock(blockItem.getBlock()), false)
                .stream()
                .max(Comparator.comparingInt(slot -> slot.getItem().getCount()))
                .orElse(null);
    }

    private void arrangeBest(AbstractContainerMenu menu, Slot preferredSlot, Slot bestSlot, java.util.function.ToDoubleFunction<ItemStack> value) {
        if (bestSlot == null || bestSlot.index == preferredSlot.index) {
            return;
        }
        double bestValue = value.applyAsDouble(bestSlot.getItem());
        double preferredValue = value.applyAsDouble(preferredSlot.getItem());
        if (bestValue > preferredValue) {
            InventoryUtility.swap(menu, bestSlot.index, preferredSlot.index - 36);
            resetTimer();
        }
    }

    private void arrangeMostBlocks(AbstractContainerMenu menu, Slot preferredSlot, Slot mostSlot) {
        if (mostSlot == null || mostSlot.index == preferredSlot.index) {
            return;
        }
        if (mostSlot.getItem().getCount() > preferredSlot.getItem().getCount()) {
            InventoryUtility.swap(menu, mostSlot.index, preferredSlot.index - 36);
            resetTimer();
        }
    }
}
