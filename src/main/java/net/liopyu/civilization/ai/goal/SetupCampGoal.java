package net.liopyu.civilization.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.liopyu.civilization.entity.Adventurer;

import java.util.EnumSet;

public class SetupCampGoal extends Goal {
    private final Adventurer mob;
    private final double speed;
    private final int searchRadius;

    private BlockPos target;
    private Path path;

    public SetupCampGoal(Adventurer mob, double speed, int searchRadius) {
        this.mob = mob;
        this.speed = speed;
        this.searchRadius = Math.max(6, searchRadius);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.level().isClientSide) return false;
        // Example: only consider sometimes and during evening
        if (mob.getRandom().nextInt(80) != 0) return false;
        long time = mob.level().getDayTime() % 24000L;
        if (time < 11000 && time > 13000) return false; // loose dusk-ish window

        target = findCampSpot();
        if (target == null) return false;
        path = mob.getNavigation().createPath(target, 0);
        return path != null;
    }

    @Override
    public boolean canContinueToUse() {
        return target != null && !mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        mob.getNavigation().moveTo(path, speed);
    }

    @Override
    public void stop() {
        target = null;
        path = null;
    }

    @Override
    public void tick() {
        if (target == null) return;
        if (mob.blockPosition().closerThan(target, 2.0)) {
            // Place a campfire if possible
            if (mob.level().isEmptyBlock(target) && mob.level().getBlockState(target.below()).isSolid()) {
                mob.level().setBlock(target, Blocks.CAMPFIRE.defaultBlockState(), 3);
            }
            stop();
        }
    }

    private BlockPos findCampSpot() {
        BlockPos base = mob.blockPosition();
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                BlockPos p = base.offset(dx, 0, dz);
                BlockPos ground = mob.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
                BlockState below = mob.level().getBlockState(ground.below());
                if (below.isSolid() && mob.level().isEmptyBlock(ground)) {
                    return ground.immutable();
                }
            }
        }
        return null;
    }
}
