package cc.aerial.client.utility;

import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.EnderpearlItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.component.Tool;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.core.component.DataComponents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class InventoryUtility {
    private InventoryUtility() {
    }

    public static boolean isArmor(ItemStack stack) {
        if (stack.getItem() == Items.PLAYER_HEAD || stack.getItem() == Items.PUMPKIN) {
            return false;
        }
        return stack.get(DataComponents.EQUIPPABLE) != null;
    }

    public static double getSwordValue(ItemStack stack) {
        if (!stack.is(net.minecraft.tags.ItemTags.SWORDS)) {
            return 0.0;
        }
        double score = PlayerUtility.getStackAttackDamage(stack);
        score *= calculateEnchantmentLevel(stack, Enchantments.SHARPNESS) + 1;
        score += calculateEnchantmentLevel(stack, Enchantments.FIRE_ASPECT);
        score -= durabilityRatio(stack) * 0.1;
        return score;
    }

    public static double getArmorValue(ItemStack stack) {
        if (!isArmor(stack)) {
            return 0.0;
        }
        double score = PlayerUtility.getArmorProtection(stack);
        score *= calculateEnchantmentLevel(stack, Enchantments.PROTECTION) + 1;
        score += calculateEnchantmentLevel(stack, Enchantments.THORNS);
        score += calculateEnchantmentLevel(stack, Enchantments.UNBREAKING) * 0.5;
        score += calculateEnchantmentLevel(stack, Enchantments.PROJECTILE_PROTECTION) * 0.25;
        score -= durabilityRatio(stack) * 0.1;
        return score;
    }

    public static double getToolValue(ItemStack stack) {
        Tool tool = stack.get(DataComponents.TOOL);
        if (tool == null) {
            return 0.0;
        }
        double score = tool.damagePerBlock();
        score *= calculateEnchantmentLevel(stack, Enchantments.EFFICIENCY) + 1;
        score += calculateEnchantmentLevel(stack, Enchantments.UNBREAKING);
        score -= durabilityRatio(stack) * 0.1;
        return score;
    }

    private static float durabilityRatio(ItemStack stack) {
        return stack.getMaxDamage() > 0 ? stack.getDamageValue() / (float) stack.getMaxDamage() : 0.0f;
    }

    public static boolean isGoodItem(ItemStack stack) {
        net.minecraft.world.item.Item item = stack.getItem();
        if (item instanceof BlockItem blockItem) {
            return isGoodBlock(blockItem.getBlock());
        }
        if (item == Items.PLAYER_HEAD || item == Items.PUMPKIN || item == Items.CARVED_PUMPKIN) {
            return false;
        }
        return item instanceof EnderpearlItem
                || item instanceof PotionItem
                || item instanceof ShieldItem
                || item instanceof FireChargeItem
                || stack.has(DataComponents.FOOD);
    }

    public static List<Slot> filterSlots(AbstractContainerMenu menu, Predicate<Slot> filter, boolean shuffle) {
        List<Slot> filtered = menu.slots.stream().filter(filter).collect(Collectors.toList());
        if (shuffle) {
            Collections.shuffle(filtered);
        }
        return filtered;
    }

    public static void drop(AbstractContainerMenu menu, int slot) {
        Player player = Minecraft.getInstance().player;
        Minecraft.getInstance().gameMode.handleContainerInput(menu.containerId, slot, 1, ContainerInput.THROW, player);
    }

    public static void shiftClick(AbstractContainerMenu menu, int slot) {
        Player player = Minecraft.getInstance().player;
        Minecraft.getInstance().gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.QUICK_MOVE, player);
    }

    public static void swap(AbstractContainerMenu menu, int originalSlot, int newHotbarSlot) {
        Player player = Minecraft.getInstance().player;
        Minecraft.getInstance().gameMode.handleContainerInput(menu.containerId, originalSlot, newHotbarSlot, ContainerInput.SWAP, player);
    }

    public static int calculateEnchantmentLevel(ItemStack stack, net.minecraft.resources.ResourceKey<Enchantment> enchantment) {
        net.minecraft.world.level.Level level = Minecraft.getInstance().level;
        if (level == null) {
            return 0;
        }
        Holder<Enchantment> holder = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(enchantment);
        return EnchantmentHelper.getItemEnchantmentLevel(holder, stack);
    }

    public static boolean isGoodBlock(Block block) {
        return !isBlockInteractable(block)
                && block.defaultBlockState().getShape(EmptyBlockGetter.INSTANCE, Minecraft.getInstance().player.blockPosition(),
                        CollisionContext.of(Minecraft.getInstance().player)) == Shapes.block()
                && !(block instanceof TntBlock)
                && !(block instanceof FallingBlock);
    }

    public static boolean isBlockInteractable(Block block) {
        return INTERACTABLE_BLOCKS.contains(block);
    }

    private static final List<Block> INTERACTABLE_BLOCKS = net.minecraft.core.registries.BuiltInRegistries.BLOCK.stream()
            .filter(block ->
                    block instanceof TrapDoorBlock ||
                            block instanceof SweetBerryBushBlock ||
                            block instanceof AbstractFurnaceBlock ||
                            block instanceof SignBlock ||
                            block instanceof AnvilBlock ||
                            block instanceof BarrelBlock ||
                            block instanceof BeaconBlock ||
                            block instanceof BedBlock ||
                            block instanceof BellBlock ||
                            block instanceof BrewingStandBlock ||
                            block instanceof ButtonBlock ||
                            block instanceof CakeBlock ||
                            block instanceof CandleCakeBlock ||
                            block instanceof CartographyTableBlock ||
                            block instanceof CaveVinesBlock ||
                            block instanceof ChestBlock ||
                            block instanceof ChiseledBookShelfBlock ||
                            block instanceof CommandBlock ||
                            block instanceof ComparatorBlock ||
                            block instanceof ComposterBlock ||
                            block instanceof CraftingTableBlock ||
                            block instanceof DaylightDetectorBlock ||
                            block instanceof DecoratedPotBlock ||
                            block instanceof DispenserBlock ||
                            block instanceof DoorBlock ||
                            block instanceof DragonEggBlock ||
                            block instanceof EnchantingTableBlock ||
                            block instanceof EnderChestBlock ||
                            block instanceof FenceBlock ||
                            block instanceof FenceGateBlock ||
                            block instanceof FlowerPotBlock ||
                            block instanceof GrindstoneBlock ||
                            block instanceof HopperBlock ||
                            block instanceof JigsawBlock ||
                            block instanceof JukeboxBlock ||
                            block instanceof LecternBlock ||
                            block instanceof LeverBlock ||
                            block instanceof LightBlock ||
                            block instanceof LoomBlock ||
                            block instanceof NoteBlock ||
                            block instanceof RedStoneWireBlock ||
                            block instanceof RepeaterBlock ||
                            block instanceof RespawnAnchorBlock ||
                            block instanceof ShulkerBoxBlock ||
                            block instanceof SmithingTableBlock ||
                            block instanceof StonecutterBlock ||
                            block instanceof FlowerBlock ||
                            block instanceof StructureBlock ||
                            block instanceof SlimeBlock ||
                            block instanceof WebBlock)
            .toList();
}
