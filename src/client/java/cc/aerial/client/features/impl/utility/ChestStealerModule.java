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
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.HashMap;
import java.util.Map;

public final class ChestStealerModule extends Module {
    public static final ChestStealerModule INSTANCE = new ChestStealerModule();

    private final BooleanProperty smart = new BooleanProperty("Smart", true);
    private final BooleanProperty highlight = new BooleanProperty("Highlight Items", true).hideIf(() -> !smart.getValue());
    private final NumberProperty delay = new NumberProperty("Delay", 100, 0, 400, 5);

    private long lastMoveTime;

    private ChestStealerModule() {
        super("Chest Stealer", "Steals only useful or upgraded items from chests", ModuleCategory.UTILITY);
        addProperties(smart, highlight, delay);
    }

    private boolean canMove() {
        long customDelay = delay.getValue().longValue();
        return customDelay == 0 || System.currentTimeMillis() - lastMoveTime >= customDelay;
    }

    @Subscribe
    public void onPreGameTick(PreGameTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.gui.screen() instanceof ContainerScreen containerScreen)) {
            return;
        }

        ChestMenu menu = containerScreen.getMenu();
        Container chest = menu.getContainer();

        if (!containerScreen.getTitle().getString().toLowerCase().contains("chest")) {
            return;
        }
        if (chest.isEmpty() || isInventoryFull(mc)) {
            containerScreen.onClose();
            return;
        }

        Map<EquipmentSlot, ItemStack> bestChestArmor = getBestChestArmor(chest);
        ItemStack bestChestSword = getBestChestItem(chest, ItemTags.SWORDS, InventoryUtility::getSwordValue);
        ItemStack bestChestPickaxe = getBestChestItem(chest, ItemTags.PICKAXES, InventoryUtility::getToolValue);
        ItemStack bestChestAxe = getBestChestAxe(chest);

        boolean tookItem = false;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (canMove() && (shouldTake(mc, stack, bestChestArmor, bestChestSword, bestChestPickaxe, bestChestAxe) || !smart.getValue())) {
                InventoryUtility.shiftClick(menu, i);
                lastMoveTime = System.currentTimeMillis();
                tookItem = true;
                break;
            }
        }

        if (smart.getValue() && !tookItem) {
            boolean hasValuableLeft = false;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                ItemStack stack = chest.getItem(i);
                if (!stack.isEmpty() && shouldTake(mc, stack, bestChestArmor, bestChestSword, bestChestPickaxe, bestChestAxe)) {
                    hasValuableLeft = true;
                    break;
                }
            }
            if (!hasValuableLeft) {
                containerScreen.onClose();
            }
        }
    }

    private boolean isInventoryFull(Minecraft mc) {
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getItem(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private boolean shouldTake(Minecraft mc, ItemStack stack, Map<EquipmentSlot, ItemStack> bestChestArmor,
                                ItemStack bestChestSword, ItemStack bestChestPickaxe, ItemStack bestChestAxe) {
        if (InventoryUtility.isGoodItem(stack)) {
            return true;
        }

        if (stack.is(ItemTags.SWORDS)) {
            double value = InventoryUtility.getSwordValue(stack);
            double current = InventoryUtility.getSwordValue(getBestHotbarItem(mc, ItemTags.SWORDS, InventoryUtility::getSwordValue));
            return stack == bestChestSword && value > current;
        }

        if (stack.is(ItemTags.PICKAXES)) {
            double value = InventoryUtility.getToolValue(stack);
            double current = InventoryUtility.getToolValue(getBestHotbarItem(mc, ItemTags.PICKAXES, InventoryUtility::getToolValue));
            return stack == bestChestPickaxe && value > current;
        }

        if (stack.is(ItemTags.AXES)) {
            double value = InventoryUtility.getToolValue(stack);
            double current = InventoryUtility.getToolValue(getBestHotbarAxe(mc));
            return stack == bestChestAxe && value > current;
        }

        if (!InventoryUtility.isArmor(stack)) {
            return false;
        }

        Equippable equip = stack.get(DataComponents.EQUIPPABLE);
        if (equip == null) {
            return false;
        }

        EquipmentSlot slot = equip.slot();
        ItemStack currentEquipped = mc.player.getItemBySlot(slot);
        ItemStack bestInChest = bestChestArmor.getOrDefault(slot, ItemStack.EMPTY);
        if (stack != bestInChest) {
            return false;
        }

        return InventoryUtility.getArmorValue(stack) > InventoryUtility.getArmorValue(currentEquipped);
    }

    private Map<EquipmentSlot, ItemStack> getBestChestArmor(Container chest) {
        Map<EquipmentSlot, ItemStack> best = new HashMap<>();
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (!InventoryUtility.isArmor(stack)) {
                continue;
            }
            Equippable equip = stack.get(DataComponents.EQUIPPABLE);
            if (equip == null) {
                continue;
            }
            best.merge(equip.slot(), stack, (existing, replacement) ->
                    InventoryUtility.getArmorValue(replacement) > InventoryUtility.getArmorValue(existing) ? replacement : existing);
        }
        return best;
    }

    private ItemStack getBestChestItem(Container chest, TagKey<Item> tag, java.util.function.ToDoubleFunction<ItemStack> value) {
        ItemStack best = ItemStack.EMPTY;
        double bestValue = -1;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.is(tag) && value.applyAsDouble(stack) > bestValue) {
                best = stack;
                bestValue = value.applyAsDouble(stack);
            }
        }
        return best;
    }

    private ItemStack getBestChestAxe(Container chest) {
        ItemStack best = ItemStack.EMPTY;
        double bestValue = -1;
        for (int i = 0; i < chest.getContainerSize(); i++) {
            ItemStack stack = chest.getItem(i);
            if (stack.getItem() instanceof AxeItem && InventoryUtility.getToolValue(stack) > bestValue) {
                best = stack;
                bestValue = InventoryUtility.getToolValue(stack);
            }
        }
        return best;
    }

    private ItemStack getBestHotbarItem(Minecraft mc, TagKey<Item> tag, java.util.function.ToDoubleFunction<ItemStack> value) {
        ItemStack best = ItemStack.EMPTY;
        double bestValue = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.is(tag) && value.applyAsDouble(stack) > bestValue) {
                best = stack;
                bestValue = value.applyAsDouble(stack);
            }
        }
        return best;
    }

    private ItemStack getBestHotbarAxe(Minecraft mc) {
        ItemStack best = ItemStack.EMPTY;
        double bestValue = -1;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.getItem() instanceof AxeItem && InventoryUtility.getToolValue(stack) > bestValue) {
                best = stack;
                bestValue = InventoryUtility.getToolValue(stack);
            }
        }
        return best;
    }
}
