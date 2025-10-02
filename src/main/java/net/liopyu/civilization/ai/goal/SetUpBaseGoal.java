package net.liopyu.civilization.ai.goal;

import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;

public class SetUpBaseGoal extends Goal {
    private final Adventurer mob;
    private final double speed;
    private final int searchRadius;

    private BlockPos target;

    public SetUpBaseGoal(Adventurer mob, double speed, int searchRadius) {
        this.mob = mob; this.speed = speed; this.searchRadius = Math.max(6, searchRadius);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private boolean isBusy() {
        ActionMode m = mob.getActionMode();
        return m == ActionMode.CUTTING_TREE || m == ActionMode.NAVIGATING_TO_NEAREST_TREE;
    }

    @Override
    public boolean canUse() {
        if (mob.level().isClientSide) return false;
        if (mob.hasHome()) return false;
        if (isBusy()) return false;
        return mob.getRandom().nextInt(20) == 0 && (target = findSpot()) != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (mob.hasHome()) return false;
        if (isBusy()) return false;
        return target != null && !mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        mob.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, speed);
    }

    @Override
    public void tick() {
        if (target == null) return;
        if (mob.blockPosition().closerThan(target, 2.0)) {
            if (mob.level().isEmptyBlock(target) && mob.level().getBlockState(target.below()).isSolid()) {
                mob.level().setBlock(target, Blocks.CHEST.defaultBlockState(), 3);
                BlockEntity be = mob.level().getBlockEntity(target);
                if (be instanceof ChestBlockEntity) {
                    be.getPersistentData().putUUID(mob.chestOwnerKey(), mob.chestOwnerId());
                    be.setChanged();
                    mob.setHomePos(target);
                }
            }
        }
    }

    @Override
    public void stop() {
        target = null;
    }

    private BlockPos findSpot() {
        BlockPos base = mob.blockPosition();
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                BlockPos p = base.offset(dx, 0, dz);
                BlockPos g = mob.level().getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
                if (mob.level().isEmptyBlock(g) && mob.level().getBlockState(g.below()).isSolid()) return g.immutable();
            }
        }
        return null;
    }
}
