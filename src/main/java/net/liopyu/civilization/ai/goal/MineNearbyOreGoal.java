package net.liopyu.civilization.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.liopyu.civilization.entity.Adventurer;

import java.util.EnumSet;

public class MineNearbyOreGoal extends Goal {
    private final Adventurer mob;
    private final double speed;
    private final int searchRadius;
    private final int verticalRange;

    private BlockPos target;
    private Path path;
    private int mineTicks;

    public MineNearbyOreGoal(Adventurer mob, double speed, int searchRadius, int verticalRange) {
        this.mob = mob;
        this.speed = speed;
        this.searchRadius = Math.max(4, searchRadius);
        this.verticalRange = Math.max(1, verticalRange);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (mob.level().isClientSide) return false;
        // Light throttle
        if (mob.getRandom().nextInt(20) != 0) return false;

        target = findOre();
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
        mineTicks = 0;
    }

    @Override
    public void stop() {
        target = null;
        path = null;
        mineTicks = 0;
    }

    @Override
    public void tick() {
        if (target == null) return;

        double dist2 = mob.distanceToSqr(
                target.getX() + 0.5, target.getY() + 0.5, target.getZ() + 0.5);

        if (dist2 < 2.5) { // close enough: “mine”
            if (++mineTicks > 20) {
                if (mob.level().getBlockState(target).is(BlockTags.COAL_ORES)) {
                    mob.level().destroyBlock(target, true, mob); // TODO: custom loot, tool checks, fatigue
                }
                stop();
            }
        } else if (mob.getNavigation().isDone()) {
            // Repather if needed
            path = mob.getNavigation().createPath(target, 0);
            if (path != null) mob.getNavigation().moveTo(path, speed);
        }
    }

    private BlockPos findOre() {
        BlockPos base = mob.blockPosition();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int dy = -verticalRange; dy <= verticalRange; dy++) {
            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    if (!mob.isValidWorkPos(p)) continue;
                    BlockState st = mob.level().getBlockState(p);
                    if (!st.is(BlockTags.COAL_ORES)) continue;

                    double d2 = p.distSqr(base);
                    if (d2 < bestDist) {
                        bestDist = d2;
                        best = p.immutable();
                    }
                }
            }
        }
        return best;
    }
}
