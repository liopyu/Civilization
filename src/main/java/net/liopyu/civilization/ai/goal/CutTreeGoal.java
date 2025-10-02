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

    public CutTreeGoal(Adventurer mob, int maxNodes) {
        this.mob = mob; this.maxNodes = Math.max(1, maxNodes);
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return !mob.level().isClientSide && mob.getActionMode() == ActionMode.CUTTING_TREE;
    }

    @Override
    public boolean canContinueToUse() {
        return mob.getActionMode() == ActionMode.CUTTING_TREE;
    }

    @Override
    public void start() {
        marked.clear(); queue.clear(); seen.clear(); scanning = true;
        for (var g : mob.goalSelector.getAvailableGoals()) {
            if (g.getGoal() instanceof MineBlockGoal m) {
                miner = m; break;
            }
        }
        if (miner == null) {
            mob.setActionMode(ActionMode.IDLE); return;
        }

        BlockPos log = net.liopyu.civilization.ai.util.AiUtil.findNearestLogAvoidingOthers(mob, 10, 6.0);
        if (log == null) {
            mob.setActionMode(ActionMode.IDLE); return;
        }

        queue.add(log); seen.add(log);
        while (!queue.isEmpty() && marked.size() < maxNodes) {
            BlockPos p = queue.removeFirst();
            BlockState bs = mob.level().getBlockState(p);
            if (!(isLog(bs) || isLeaves(bs))) continue;
            marked.add(p.immutable());
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = p.offset(dx, dy, dz);
                        if (!seen.contains(n)) {
                            BlockState b2 = mob.level().getBlockState(n);
                            if (isLog(b2)) {
                                seen.add(n); queue.add(n);
                            }
                        }
                    }
        }
        while (!marked.isEmpty()) miner.enqueue(marked.removeFirst());
        scanning = false;
    }


    @Override
    public void tick() {
        if (scanning) return;
        if (miner == null) {
            mob.setActionMode(ActionMode.IDLE); return;
        }
        if (isTreeCleared()) {
            mob.setActionMode(ActionMode.IDLE); return;
        }
    }

    private boolean isTreeCleared() {
        for (BlockPos p : marked) return false;
        return true;
    }

    @Override
    public void stop() {
        marked.clear(); queue.clear(); seen.clear(); scanning = false;
    }
}
