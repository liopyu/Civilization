package net.liopyu.civilization.ai.goal;

import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.HashSet;

import static net.liopyu.civilization.ai.util.AiUtil.*;

public class CutTreeGoal extends Goal {
    private final Adventurer mob;
    private final int maxNodes;
    private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    private final ArrayDeque<BlockPos> marked = new ArrayDeque<>();
    private final HashSet<BlockPos> seen = new HashSet<>();
    private boolean scanning;
    private MineBlockGoal miner;
    private boolean cleaningUpPillars;

    public CutTreeGoal(Adventurer mob, int maxNodes) {
        this.mob = mob; this.maxNodes = Math.max(1, maxNodes);
        setFlags(EnumSet.noneOf(Flag.class));
    }


    @Override
    public boolean canUse() {
        return !mob.level().isClientSide && mob.getActionMode() == ActionMode.CUTTING_TREE;
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getActionMode() == ActionMode.CUTTING_TREE;
    }

    private net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> familyTag;

    @Override
    public void start() {
        aiLogger("Start: CutTreeGoal");
        marked.clear();
        queue.clear();
        seen.clear();
        scanning = true;

        miner = null;
        for (var wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof MineBlockGoal m) {
                miner = m;
                break;
            }
        }
        if (miner == null) {
            mob.setActionMode(ActionMode.IDLE);
            return;
        }

        BlockPos log = findNearestLogAvoidingOthers(mob, 10, 6.0);
        familyTag = net.liopyu.civilization.ai.util.AiUtil.woodFamilyTag(mob.level().getBlockState(log));
        if (familyTag == null) {
            mob.setActionMode(ActionMode.IDLE); return;
        }
        final BlockPos base = findTreeBase(mob.level(), log);
        final int maxLateral = 3;

        if (log == null) {
            mob.setActionMode(ActionMode.IDLE); return;
        }


        mob.setMiningTargetPos(log);

        queue.add(log);
        seen.add(log);
        while (!queue.isEmpty() && marked.size() < maxNodes) {
            BlockPos p = queue.removeFirst();
            BlockState bs = mob.level().getBlockState(p);
            if (!net.liopyu.civilization.ai.util.AiUtil.isSameWoodFamily(bs, familyTag)) continue;

            marked.add(p.immutable());

            BlockPos[] neighbors = new BlockPos[]{
                    p.above(), p.below(),
                    p.north(), p.south(), p.east(), p.west()
            };

            for (BlockPos n : neighbors) {
                if (seen.contains(n)) continue;
                if (base != null) {
                    int dx = Math.abs(n.getX() - base.getX());
                    int dz = Math.abs(n.getZ() - base.getZ());
                    if (dx > maxLateral || dz > maxLateral) continue;
                }
                BlockState b2 = mob.level().getBlockState(n);
                if (net.liopyu.civilization.ai.util.AiUtil.isSameWoodFamily(b2, familyTag)) {
                    seen.add(n);
                    queue.add(n);
                }
            }


        }

        for (BlockPos p : marked) {
            miner.enqueue(p);
        }


        scanning = false;
        cleaningUpPillars = false;

    }

    private boolean isTreeCleared() {
        if (marked.isEmpty()) return false;
        for (BlockPos p : marked) {
            BlockState st = mob.level().getBlockState(p);
            if (isLog(st) && !net.liopyu.civilization.ai.util.AiUtil.isTemporaryPillar(mob, p)) return false;
        }
        return true;
    }

    @Override
    public void tick() {
        if (scanning) return;
        if (miner == null) {
            mob.setActionMode(ActionMode.IDLE); return;
        }

        if (!cleaningUpPillars) {
            if (isTreeCleared()) {
                java.util.List<net.minecraft.core.BlockPos> pillars = net.liopyu.civilization.ai.util.AiUtil.drainTemporaryPillarsLifo(mob);
                if (!pillars.isEmpty()) {
                    for (net.minecraft.core.BlockPos p : pillars) miner.enqueue(p);
                    cleaningUpPillars = true;
                    return;
                }
                mob.clearMiningTargetPos();
                net.minecraft.core.BlockPos next = net.liopyu.civilization.ai.util.AiUtil.findNearestLogAvoidingOthers(mob, 24, 6.0);
                if (next != null) {
                    mob.setActionMode(net.liopyu.civilization.ai.ActionMode.NAVIGATING_TO_NEAREST_TREE);
                } else {
                    mob.setActionMode(net.liopyu.civilization.ai.ActionMode.IDLE);
                }
                return;
            }
            return;
        }

        if (cleaningUpPillars) {
            if (miner.isIdle()) {
                cleaningUpPillars = false;
                mob.clearMiningTargetPos();
                net.minecraft.core.BlockPos next = net.liopyu.civilization.ai.util.AiUtil.findNearestLogAvoidingOthers(mob, 24, 6.0);
                if (next != null) {
                    mob.setActionMode(net.liopyu.civilization.ai.ActionMode.NAVIGATING_TO_NEAREST_TREE);
                } else {
                    mob.setActionMode(net.liopyu.civilization.ai.ActionMode.IDLE);
                }
            }
        }
    }

    @Override
    public void stop() {
        aiLogger("Stop: CutTreeGoal");
        marked.clear(); queue.clear(); seen.clear(); scanning = false;
        mob.clearMiningTargetPos();
    }


}
