package net.liopyu.civilization.ai.brain;

import com.google.common.collect.ImmutableMap;
import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;

public class NavigateToNearestTreeBehavior extends Behavior<Adventurer> {
    private final double speed;
    private BlockPos seedLog;
    private BlockPos baseLog;
    private BlockPos standPos;
    private double lastD2;
    private int noProgress;
    private int stuck;
    private static final double STOP_RADIUS = 1.0;

    private NavigateToNearestTreeBehavior(double s) {
        super(ImmutableMap.<MemoryModuleType<?>, MemoryStatus>of(), 20, 20);
        this.speed = s;
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Adventurer a, long time) {
        return true;
    }

    public static NavigateToNearestTreeBehavior create(double s) {
        return new NavigateToNearestTreeBehavior(s);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Adventurer a) {
        return a.getActionMode() == ActionMode.NAVIGATING_TO_NEAREST_TREE;
    }

    @Override
    protected void start(ServerLevel level, Adventurer a, long time) {
        seedLog = findNearest(a, 16);
        if (seedLog == null) {
            a.setActionMode(ActionMode.IDLE);
            net.liopyu.civilization.ai.brain.AdventurerAi.updateActivity(a);
            return;
        }
        baseLog = findBase(level, seedLog);
        if (baseLog == null) {
            a.setActionMode(ActionMode.IDLE);
            net.liopyu.civilization.ai.brain.AdventurerAi.updateActivity(a);
            return;
        }
        standPos = findStand(a, baseLog);
        if (standPos == null) {
            a.setActionMode(ActionMode.IDLE);
            net.liopyu.civilization.ai.brain.AdventurerAi.updateActivity(a);
            return;
        }
        a.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, speed);
        lastD2 = Double.MAX_VALUE;
        noProgress = 0;
        stuck = 0;
    }


    @Override
    protected void tick(ServerLevel level, Adventurer a, long time) {
        if (standPos == null || baseLog == null) {
            a.setActionMode(ActionMode.IDLE);
            net.liopyu.civilization.ai.brain.AdventurerAi.updateActivity(a);
            return;
        }
        double d2 = a.distanceToSqr(baseLog.getX() + 0.5, baseLog.getY() + 0.5, baseLog.getZ() + 0.5);
        if (d2 >= lastD2 - 0.01) noProgress++;
        else noProgress = 0;
        lastD2 = d2;
        a.getLookControl().setLookAt(baseLog.getX() + 0.5, baseLog.getY() + 0.5, baseLog.getZ() + 0.5);
        if (dist2(a, baseLog) <= a.entityInteractionRange() * a.entityInteractionRange()) {
            a.getNavigation().stop();
            a.setMiningTargetPos(baseLog);
            a.setActionMode(ActionMode.CUTTING_TREE);
            net.liopyu.civilization.ai.brain.AdventurerAi.updateActivity(a);
            return;
        }

        if (dist2(a, standPos) <= STOP_RADIUS * STOP_RADIUS) a.getNavigation().stop();
        else if (a.getNavigation().isDone())
            a.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, speed);
        if (noProgress > 80) {
            BlockPos alt = findAltStand(a, baseLog);
            if (alt != null && !alt.equals(standPos)) {
                standPos = alt;
                a.getNavigation().moveTo(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5, speed);
                noProgress = 0;
                lastD2 = Double.MAX_VALUE;
                return;
            }
            a.setActionMode(ActionMode.IDLE);
            net.liopyu.civilization.ai.brain.AdventurerAi.updateActivity(a);
        }
        if (++stuck > 200) {
            a.getNavigation().stop();
            a.setActionMode(ActionMode.IDLE);
            net.liopyu.civilization.ai.brain.AdventurerAi.updateActivity(a);
        }
    }


    private double dist2(Adventurer e, BlockPos p) {
        return e.distanceToSqr(p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5);
    }

    private BlockPos findNearest(Adventurer a, int r) {
        BlockPos base = a.blockPosition(); BlockPos best = null; double bestD = Double.MAX_VALUE;
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos p = base.offset(dx, dy, dz);
                    if (a.level().getBlockState(p).is(net.minecraft.tags.BlockTags.LOGS)) {
                        double d = base.distSqr(p);
                        if (d < bestD) {
                            bestD = d; best = p.immutable();
                        }
                    }
                }
        return best;
    }

    private BlockPos findBase(ServerLevel l, BlockPos any) {
        BlockPos p = any; while (l.getBlockState(p.below()).is(net.minecraft.tags.BlockTags.LOGS)) p = p.below();
        return p.immutable();
    }

    private BlockPos findStand(Adventurer a, BlockPos trunk) {
        BlockPos best = null; double bestScore = Double.POSITIVE_INFINITY;
        int[][] ring1 = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        int[][] ring2 = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {2, 1}, {2, -1}, {-2, 1}, {-2, -1}, {1, 2}, {-1, 2}, {1, -2}, {-1, -2}, {2, 2}, {2, -2}, {-2, 2}, {-2, -2}};
        int[][][] rings = {ring1, ring2};
        for (int[][] ring : rings) {
            for (int[] d : ring) {
                BlockPos feet = trunk.offset(d[0], 0, d[1]);
                if (!a.level().isLoaded(feet)) continue;
                if (!a.level().getBlockState(feet).isAir()) continue;
                if (!a.level().getBlockState(feet.below()).isSolid()) continue;
                double s = trunk.distSqr(feet) * 2.0 + a.distanceToSqr(feet.getX() + 0.5, feet.getY() + 0.1, feet.getZ() + 0.5);
                if (s < bestScore) {
                    bestScore = s; best = feet.immutable();
                }
            }
            if (best != null) break;
        }
        return best;
    }

    private BlockPos findAltStand(Adventurer a, BlockPos trunkBase) {
        BlockPos best = null; double bestScore = Double.POSITIVE_INFINITY;
        for (int r = 2; r <= 4; r++)
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) continue;
                    BlockPos feet = trunkBase.offset(dx, 0, dz);
                    if (!a.level().isLoaded(feet)) continue;
                    if (!a.level().getBlockState(feet).isAir()) continue;
                    if (!a.level().getBlockState(feet.below()).isSolid()) continue;
                    double s = trunkBase.distSqr(feet) * 1.5 + a.distanceToSqr(feet.getX() + 0.5, feet.getY() + 0.1, feet.getZ() + 0.5);
                    if (s < bestScore) {
                        bestScore = s; best = feet.immutable();
                    }
                }
        return best;
    }
}
