package cc.aerial.client.features.impl.utility;

import cc.aerial.client.event.impl.game.PreGameTickEvent;
import cc.aerial.client.event.subscriber.Subscribe;
import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.features.impl.combat.killaura.KillauraModule;
import cc.aerial.client.property.BoundedNumberProperty;
import cc.aerial.client.utility.HypixelServer;
import cc.aerial.client.utility.InventoryUtility;
import net.hypixel.data.type.GameType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.Equippable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public final class AutoArmorModule extends Module {
    public static final AutoArmorModule INSTANCE = new AutoArmorModule();

    private final BoundedNumberProperty delay = new BoundedNumberProperty("Delay", 50, 100, 0, 400, 5);

    private AutoArmorModule() {
        super("Auto Armor", "Automatically equips the best armor possible", ModuleCategory.UTILITY);
        addProperties(delay);
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
            if (location != null && (location.isLobby()
                    || !(location.serverType() == GameType.SKYWARS || location.serverType() == GameType.SURVIVAL_GAMES))) {
                return;
            }
        }

        AbstractContainerMenu menu = player.containerMenu;
        if (!(menu instanceof InventoryMenu)) {
            return;
        }

        List<Slot> bestArmor = getBestArmor(menu);

        for (Slot slot : InventoryUtility.filterSlots(menu, s -> !s.getItem().isEmpty() && InventoryUtility.isArmor(s.getItem()), true)) {
            ItemStack stack = slot.getItem();
            if (bestArmor.stream().noneMatch(armor -> armor.getItem() == stack)) {
                if (!InventoryManagerModule.INSTANCE.canMove((long) delay.getRandomValue())) {
                    return;
                }
                InventoryUtility.drop(menu, slot.index);
                InventoryManagerModule.INSTANCE.resetTimer();
            }
        }

        for (Slot armorSlot : bestArmor) {
            List<ItemStack> equipped = getArmorStacks(player);
            Collections.shuffle(equipped);
            if (equipped.stream().noneMatch(armor -> armor == armorSlot.getItem())) {
                if (!InventoryManagerModule.INSTANCE.canMove((long) delay.getRandomValue())) {
                    return;
                }
                InventoryUtility.shiftClick(menu, armorSlot.index);
                InventoryManagerModule.INSTANCE.resetTimer();
            }
        }
    }

    private List<Slot> getBestArmor(AbstractContainerMenu menu) {
        return Arrays.stream(EquipmentSlot.values())
                .map(equipmentSlot -> InventoryUtility.filterSlots(menu, slot -> {
                            if (slot.getItem().isEmpty() || !InventoryUtility.isArmor(slot.getItem())) {
                                return false;
                            }
                            Equippable equippable = slot.getItem().get(DataComponents.EQUIPPABLE);
                            return equippable != null && equippable.slot() == equipmentSlot;
                        }, false)
                        .stream()
                        .max(Comparator.comparingDouble(slot -> InventoryUtility.getArmorValue(slot.getItem())))
                        .orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private List<ItemStack> getArmorStacks(LocalPlayer player) {
        List<ItemStack> stacks = new ArrayList<>();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack equipped = player.getItemBySlot(slot);
            if (!equipped.isEmpty() && InventoryUtility.isArmor(equipped)) {
                stacks.add(equipped);
            }
        }
        return stacks;
    }
}
