package net.liopyu.civilization.ai.goal;

import com.mojang.logging.LogUtils;
import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

import java.util.EnumSet;

import static net.liopyu.civilization.ai.util.AiUtil.aiLogger;

public class NavigateToNearestTreeGoal extends Goal {
    private static final double STOP_RADIUS = 1.0;
    private static final int MAX_STUCK_TICKS = 200;

    private final Adventurer mob;
    private final double speed;

    private BlockPos seedLog;
    private BlockPos baseLog;
    private BlockPos standPos;
    private int stuckTicks;
    private int searchTicks;
    private int noProgressTicks;
    private double lastD2;
    private int reselectAttempts;

    public NavigateToNearestTreeGoal(Adventurer mob, double speed) {
        this.mob = mob; this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return !mob.level().isClientSide && mob.getActionMode() == ActionMode.NAVIGATING_TO_NEAREST_TREE;
    }

    @Override
    public boolean canContinueToUse() {
        if (mob.getActionMode() != ActionMode.NAVIGATING_TO_NEAREST_TREE) return false;
        if (standPos == null || baseLog == null) return false;
        if (distToCenterSq(mob, standPos) <= STOP_RADIUS * STOP_RADIUS) return false;
        if (distToCenterSq(mob, baseLog) <= mob.entityInteractionRange() * mob.entityInteractionRange()) return false;
        return !mob.getNavigation().isDone();
    }

    @Override
    public void start() {
        aiLogger("Start: NavigateToNearestTreeGoal");
        seedLog = findNearestLogFlat(mob, 16);
        if (seedLog == null) {
            mob.setActionMode(ActionMode.IDLE); return;
        }
        baseLog = findTreeBaseFlat(mob, seedLog);
        if (baseLog == null) {
            mob.setActionMode(ActionMode.IDLE); return;
        }
        standPos = findAdjacentGroundTight(mob, baseLog);
        if (standPos == null) {
            mob.setActionMode(ActionMode.IDLE); return;
        }
        mob.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, speed);
        stuckTicks = 0;
        searchTicks = 0;
        noProgressTicks = 0;
        lastD2 = Double.MAX_VALUE;
        reselectAttempts = 0;
    }

    @Override
    public void stop() {

        aiLogger("Stop: NavigateToNearestTreeGoal");
        seedLog = null;
        baseLog = null;
        standPos = null;
        stuckTicks = 0;
    }

    private BlockPos findAlternateStandAround(Adventurer mob, BlockPos trunkBase) {
        BlockPos best = null; double bestScore = Double.POSITIVE_INFINITY;
        for (int r = 2; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    BlockPos feet = trunkBase.offset(dx, 0, dz);
                    if (!mob.level().isLoaded(feet)) continue;
                    if (!mob.level().getBlockState(feet).isAir()) continue;
                    if (!mob.level().getBlockState(feet.below()).isSolid()) continue;
                    double score = trunkBase.distSqr(feet) * 1.5
                            + mob.distanceToSqr(feet.getX() + 0.5, feet.getY() + 0.1, feet.getZ() + 0.5);
                    if (score < bestScore) {
                        bestScore = score; best = feet.immutable();
                    }
                }
            }
            if (best != null) break;
        }
        return best;
    }

    @Override
    public void tick() {
        if (standPos == null || baseLog == null) {
            mob.setActionMode(ActionMode.IDLE); return;
        }

        double d2 = mob.distanceToSqr(baseLog.getX() + 0.5, baseLog.getY() + 0.5, baseLog.getZ() + 0.5);
        if (d2 >= lastD2 - 0.01) {
            noProgressTicks++;
        } else {
            noProgressTicks = 0;
        }
        lastD2 = d2;
        searchTicks++;

        mob.getLookControl().setLookAt(baseLog.getX() + 0.5, baseLog.getY() + 0.5, baseLog.getZ() + 0.5);

        if (distToCenterSq(mob, baseLog) <= mob.entityInteractionRange() * mob.entityInteractionRange()) {
            mob.getNavigation().stop();
            mob.setActionMode(ActionMode.CUTTING_TREE);
            return;
        }

        if (distToCenterSq(mob, standPos) <= STOP_RADIUS * STOP_RADIUS) {
            mob.getNavigation().stop();
        } else if (mob.getNavigation().isDone() && mob.getActionMode() == ActionMode.NAVIGATING_TO_NEAREST_TREE) {
            mob.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, speed);
        }

        if (searchTicks >= 200 || noProgressTicks > 80) {
            BlockState baseState = mob.level().getBlockState(baseLog);
            boolean baseValid = baseState.is(net.minecraft.tags.BlockTags.LOGS);
            if (!baseValid && reselectAttempts < 3) {
                BlockPos newSeed = findNearestLogFlat(mob, 16);
                if (newSeed != null && !newSeed.equals(seedLog)) {
                    seedLog = newSeed;
                    baseLog = findTreeBaseFlat(mob, seedLog);
                    if (baseLog != null) {
                        standPos = findAdjacentGroundTight(mob, baseLog);
                        if (standPos != null) {
                            mob.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, speed);
                            searchTicks = 0;
                            noProgressTicks = 0;
                            lastD2 = Double.MAX_VALUE;
                            reselectAttempts++;
                            return;
                        }
                    }
                }
            }

            BlockPos alt = findAlternateStandAround(mob, baseLog);
            if (alt != null && !alt.equals(standPos)) {
                standPos = alt;
                mob.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, speed);
                searchTicks = 0;
                noProgressTicks = 0;
                lastD2 = Double.MAX_VALUE;
                return;
            }

            net.minecraft.world.phys.Vec3 nudge = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPos(mob, 6, 3);
            if (nudge != null) {
                mob.getNavigation().moveTo(nudge.x, nudge.y, nudge.z, speed);
                searchTicks = 0;
                noProgressTicks = 0;
                lastD2 = Double.MAX_VALUE;
                return;
            }

            mob.setActionMode(ActionMode.IDLE);
        }

        if (++stuckTicks > MAX_STUCK_TICKS) {
            mob.getNavigation().stop();
            mob.setActionMode(ActionMode.IDLE);
        }
    }


    private double distToCenterSq(Adventurer e, BlockPos p) {
        return e.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
    }

    private BlockPos findNearestLogFlat(Adventurer mob, int r) {
        BlockPos base = mob.blockPosition();
        BlockPos best = null; double bestD = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    BlockState st = mob.level().getBlockState(p);
                    if (st.is(net.minecraft.tags.BlockTags.LOGS)) {
                        double d = base.distSqr(p);
                        if (d < bestD) {
                            bestD = d; best = p.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    private BlockPos findTreeBaseFlat(Adventurer mob, BlockPos anyLog) {
        BlockPos p = anyLog;
        while (mob.level().getBlockState(p.below()).is(net.minecraft.tags.BlockTags.LOGS)) p = p.below();
        return p.immutable();
    }

    private BlockPos findAdjacentGroundTight(Adventurer mob, BlockPos trunkBase) {
        if (!(mob.level() instanceof ServerLevel level)) return null;
        BlockPos best = null; double bestScore = Double.POSITIVE_INFINITY;

        int[][] ring1 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        int[][] ring2 = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 1}, {2, -1}, {-2, 1}, {-2, -1}, {1, 2}, {-1, 2}, {1, -2}, {-1, -2}, {2, 2}, {2, -2}, {-2, 2}, {-2, -2}};

        int[][][] rings = {ring1, ring2};

        for (int[][] ring : rings) {
            for (int[] d : ring) {
                BlockPos feet = trunkBase.offset(d[0], 0, d[1]);
                if (!level.isLoaded(feet)) continue;
                if (!level.getBlockState(feet).isAir()) continue;
                if (!level.getBlockState(feet.below()).isSolid()) continue;
                double score = trunkBase.distSqr(feet) * 2.0
                        + mob.distanceToSqr(feet.getX() + 0.5, feet.getY() + 0.1, feet.getZ() + 0.5);
                if (score < bestScore) {
                    bestScore = score; best = feet.immutable();
                }
            }
            if (best != null) break;
        }
        return best;
    }
}
