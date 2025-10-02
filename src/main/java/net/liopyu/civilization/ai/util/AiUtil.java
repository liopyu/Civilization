package net.liopyu.civilization.ai.util;

import net.liopyu.civilization.entity.Adventurer;
import net.liopyu.civilization.mixin.BlockBehaviourAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

public final class AiUtil {
    private AiUtil() {
    }

    public static boolean isLog(BlockState s) {
        return s != null && s.is(BlockTags.LOGS);
    }

    public static boolean isLeaves(BlockState s) {
        return s != null && s.is(BlockTags.LEAVES);
    }

    public static net.minecraft.core.BlockPos findNearestLogAvoidingOthers(net.liopyu.civilization.entity.Adventurer mob, int r, double avoidRadius) {
        net.minecraft.core.BlockPos base = mob.blockPosition();
        net.minecraft.core.BlockPos best = null; double bestD = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    net.minecraft.core.BlockPos p = base.offset(dx, dy, dz);
                    net.minecraft.world.level.block.state.BlockState st = mob.level().getBlockState(p);
                    if (!st.is(net.minecraft.tags.BlockTags.LOGS)) continue;
                    net.minecraft.core.BlockPos treeBase = findTreeBase(mob.level(), p);
                    java.util.List<net.liopyu.civilization.entity.Adventurer> others =
                            mob.level().getEntitiesOfClass(
                                    net.liopyu.civilization.entity.Adventurer.class,
                                    new net.minecraft.world.phys.AABB(treeBase).inflate(avoidRadius),
                                    a -> a != mob && a.getActionMode() == net.liopyu.civilization.ai.ActionMode.CUTTING_TREE
                            );
                    if (!others.isEmpty()) continue;
                    double d = p.distSqr(base);
                    if (d < bestD) {
                        bestD = d; best = p.immutable();
                    }
                }
        return best;
    }

    public static BlockPos findNearestLog(Adventurer mob, int r) {
        BlockPos base = mob.blockPosition();
        BlockPos best = null; double bestD = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    BlockState st = mob.level().getBlockState(p);
                    if (st.is(BlockTags.LOGS)) {
                        double d = p.distSqr(base);
                        if (d < bestD) {
                            bestD = d; best = p.immutable();
                        }
                    }
                }
        return best;
    }

    public static boolean tryStoreMinedBlock(Adventurer mob, BlockState st) {
        var item = st.getBlock().asItem();
        if (item == Items.AIR) return false;
        return mob.addItemToInternal(new ItemStack(item, 1));
    }

    public static boolean harvestBlockToInternal(Adventurer mob, BlockPos pos) {
        var level = mob.level();
        var st = level.getBlockState(pos);
        if (st.isAir()) return false;

        var tool = mob.getMainHandItem();
        if (!tool.isEmpty()) tool.getItem().mineBlock(tool, level, st, pos, mob);

        if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) return false;

        net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
        LootParams.Builder lp =
                new LootParams.Builder(sl)
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.ORIGIN,
                                net.minecraft.world.phys.Vec3.atCenterOf(pos))
                        .withParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_STATE, st)
                        .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY, be)
                        .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.THIS_ENTITY, mob)
                        .withOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.TOOL, tool);
        var drops = ((BlockBehaviourAccessor) st.getBlock()).invokeGetDrops(st, lp);


        boolean any = false;
        for (net.minecraft.world.item.ItemStack drop : drops) {
            if (!drop.isEmpty()) {
                if (mob.addItemToInternal(drop.copy())) any = true;
            }
        }

        level.removeBlock(pos, false);
        return any;
    }

    public static BlockPos findTreeBase(Level level, BlockPos anyLog) {
        BlockPos p = anyLog;
        while (isLog(level.getBlockState(p.below()))) p = p.below();
        return p.immutable();
    }

    public static BlockPos findStandNear(Level level, BlockPos target, int radius) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos feet = target.offset(dx, dy, dz);
                    if (!level.isLoaded(feet)) continue;
                    if (!level.isEmptyBlock(feet)) continue;
                    if (!level.getBlockState(feet.below()).isSolid()) continue;
                    return feet.immutable();
                }
            }
        }
        return null;
    }
}
