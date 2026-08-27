package cc.aerial.client.features.impl.utility;

import cc.aerial.client.features.Module;
import cc.aerial.client.features.ModuleCategory;
import cc.aerial.client.mixin.BlockItemInvoker;
import cc.aerial.client.property.BooleanProperty;
import cc.aerial.client.property.NumberProperty;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.JukeboxBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class FastPlaceModule extends Module {
    public static final FastPlaceModule INSTANCE = new FastPlaceModule();

    private final NumberProperty delay = new NumberProperty("Delay", 1.0, 0.0, 3.0, 0.1);
    private final BooleanProperty blocksOnly = new BooleanProperty("Blocks Only", true);
    private final BooleanProperty placeFix = new BooleanProperty("Place Fix", true);
    private final BooleanProperty skipObsidian = new BooleanProperty("Skip Obsidian", true);
    private final BooleanProperty skipInteractable = new BooleanProperty("Skip Interactable", true);

    private long delayMs;

    private FastPlaceModule() {
        super("Fast Place", "Removes the vanilla delay between block placements", ModuleCategory.UTILITY);
        addProperties(delay, blocksOnly, placeFix, skipObsidian, skipInteractable);
    }

    @Override
    protected void onDisable() {
        delayMs = 0L;
    }

    public void armDelay() {
        delayMs += (long) (50.0 * delay.getValue());
    }

    public void tickDelay() {
        if (delayMs > 0L) {
            delayMs -= 50L;
        }
    }

    public boolean isDelayElapsed() {
        return delayMs <= 0L;
    }

    public boolean canPlace() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        ItemStack stack = player.getMainHandItem();
        if (!stack.isEmpty()) {
            if (stack.getItem() instanceof FishingRodItem) {
                return false;
            }
            if (stack.getItem() instanceof BlockItem blockItem) {
                Block block = blockItem.getBlock();
                if (skipObsidian.getValue() && block == Blocks.OBSIDIAN) {
                    return false;
                }
                if (skipInteractable.getValue() && isInteractable(block)) {
                    return false;
                }
                if (!placeFix.getValue()) {
                    return true;
                }
                HitResult hitResult = Minecraft.getInstance().hitResult;
                if (!(hitResult instanceof BlockHitResult blockHit) || blockHit.getType() != HitResult.Type.BLOCK) {
                    return false;
                }
                BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, blockHit);
                return ((BlockItemInvoker) blockItem).aerial$getPlacementState(context) != null;
            }
        }
        return !blocksOnly.getValue();
    }

    private static boolean isInteractable(Block block) {
        if (block instanceof BaseEntityBlock) {
            return true;
        }
        if (block instanceof CraftingTableBlock) {
            return true;
        }
        if (block instanceof AnvilBlock) {
            return true;
        }
        if (block instanceof BedBlock) {
            return true;
        }
        if (block instanceof DoorBlock && block != Blocks.IRON_DOOR) {
            return true;
        }
        if (block instanceof TrapDoorBlock) {
            return true;
        }
        if (block instanceof FenceGateBlock) {
            return true;
        }
        if (block instanceof FenceBlock) {
            return true;
        }
        if (block instanceof ButtonBlock) {
            return true;
        }
        if (block instanceof LeverBlock) {
            return true;
        }
        return block instanceof JukeboxBlock;
    }
}
