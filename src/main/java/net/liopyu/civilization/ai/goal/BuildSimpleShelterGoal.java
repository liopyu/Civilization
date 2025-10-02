package net.liopyu.civilization.ai.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.Blocks;
import net.liopyu.civilization.entity.Adventurer;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class BuildSimpleShelterGoal extends Goal {
    private final Adventurer mob;
    private final double speed;
    private final int searchRadius;

    private BlockPos origin;
    private int buildIndex;
    private List<BlockPos> plan;

    public BuildSimpleShelterGoal(Adventurer mob, double speed, int searchRadius) {
        this.mob = mob;
        this.speed = speed;
        this.searchRadius = Math.max(8, searchRadius);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (mob.level().isClientSide) return false;
        if (mob.getRandom().nextInt(160) != 0) return false;
        origin = findBuildSpot();
        if (origin == null) return false;

        plan = createPlan(origin);
        buildIndex = 0;
        return !plan.isEmpty();
    }

    @Override
    public boolean canContinueToUse() {
        return plan != null && buildIndex < plan.size();
    }

    @Override
    public void start() {
        moveToNext();
    }

    @Override
    public void tick() {
        if (plan == null || buildIndex >= plan.size()) return;
        BlockPos target = plan.get(buildIndex);
        if (!mob.blockPosition().closerThan(target, 2.0)) return;

        if (mob.level().isEmptyBlock(target)) {
            mob.level().setBlock(target, Blocks.OAK_PLANKS.defaultBlockState(), 3);
        }
        buildIndex++;
        moveToNext();
    }

    @Override
    public void stop() {
        plan = null;
        origin = null;
    }

    private void moveToNext() {
        if (plan != null && buildIndex < plan.size()) {
            BlockPos p = plan.get(buildIndex);
            mob.getNavigation().moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, speed);
        }
    }

    private BlockPos findBuildSpot() {
        BlockPos base = mob.blockPosition();
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                BlockPos p = base.offset(dx, 0, dz);
                BlockPos g = mob.level().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);
                if (mob.level().isEmptyBlock(g)) return g.immutable();
            }
        }
        return null;
    }

    // Very simple 3x3 roof at y+2 with four posts at corners.
    private List<BlockPos> createPlan(BlockPos o) {
        List<BlockPos> list = new ArrayList<>();
        // posts
        list.add(o);
        list.add(o.offset(2, 0, 0));
        list.add(o.offset(0, 0, 2));
        list.add(o.offset(2, 0, 2));
        list.add(o.above());
        list.add(o.offset(2, 1, 0));
        list.add(o.offset(0, 1, 2));
        list.add(o.offset(2, 1, 2));
        // roof 3x3 at y + 2
        BlockPos roof = o.above(2).offset(0, 0, 0);
        for (int x = 0; x < 3; x++)
            for (int z = 0; z < 3; z++)
                list.add(roof.offset(x, 0, z));
        return list;
    }
}
