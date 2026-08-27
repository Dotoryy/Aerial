package cc.aerial.client.utility;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarvedPumpkinBlock;
import net.minecraft.world.level.block.CraftingTableBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.PumpkinBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.SlimeBlock;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;

import java.util.IdentityHashMap;
import java.util.Map;

public final class ScaffoldBlockFilter {
    private static final Map<Item, Boolean> CACHE = new IdentityHashMap<>();

    private ScaffoldBlockFilter() {
    }

    public static boolean isPlaceable(ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem)) {
            return false;
        }
        return CACHE.computeIfAbsent(stack.getItem(), item -> compute((BlockItem) item));
    }

    private static boolean compute(BlockItem item) {
        Block block = item.getBlock();

        if (block instanceof FallingBlock
                || block instanceof EntityBlock
                || block instanceof CraftingTableBlock
                || block instanceof NoteBlock
                || block instanceof RespawnAnchorBlock
                || block instanceof TntBlock
                || block instanceof SlimeBlock
                || block instanceof PumpkinBlock
                || block instanceof CarvedPumpkinBlock) {
            return false;
        }

        BlockState state = block.defaultBlockState();
        return Block.isShapeFullBlock(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    }
}
