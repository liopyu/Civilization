package net.liopyu.civilization.ai.util;

import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class BlockUtil {
    private BlockUtil() {
    }

    public static boolean isAirish(BlockState st) {
        return st.isAir() || st.canBeReplaced();
    }

    public static boolean isDiggable(BlockState st) {
        return st.is(Blocks.DIRT) || st.is(Blocks.GRASS_BLOCK) || st.is(Blocks.SHORT_GRASS) ||
                st.is(Blocks.TALL_GRASS) || st.is(Blocks.GRAVEL) || st.is(Blocks.SAND) ||
                st.is(Blocks.DIRT_PATH) || st.is(Blocks.ROOTED_DIRT) || st.is(Blocks.MYCELIUM) ||
                st.is(Blocks.PODZOL) || st.is(Blocks.MOSS_BLOCK) || st.is(Blocks.SNOW) ||
                st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.MOSS_CARPET) || st.is(Blocks.FERN) ||
                st.is(Blocks.LARGE_FERN) || st.is(Blocks.AIR) || st.canBeReplaced();
    }

    public static boolean isMineableStone(BlockState st) {
        return st.is(Blocks.STONE) || st.is(Blocks.DEEPSLATE) || st.is(Blocks.COBBLESTONE);
    }

    public static boolean isMineableStone(Adventurer mob, BlockPos p) {
        return isMineableStone(mob.level().getBlockState(p));
    }

    public boolean isLog(BlockPos p, Mob mob) {
        return mob.level().getBlockState(p).is(BlockTags.LOGS);
    }


    public static boolean isLog(BlockState st) {
        return st.is(BlockTags.LOGS);
    }

    public static boolean isLeaves(BlockState st) {
        return st.is(BlockTags.LEAVES);
    }


    /**
     * Raycast eye→block center; true only if first hit is the target or MISS.
     */
    public static boolean canSeeBlock(Adventurer mob, BlockPos target) {
        Vec3 from = new Vec3(mob.getX(), mob.getEyeY(), mob.getZ());
        Vec3 to = new Vec3(target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);
        var hit = mob.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
        if (hit.getType() == BlockHitResult.Type.MISS) return true;
        return hit.getType() == BlockHitResult.Type.BLOCK && hit.getBlockPos().equals(target);
    }
}
