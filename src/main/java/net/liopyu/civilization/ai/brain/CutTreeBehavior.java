package net.liopyu.civilization.ai.brain;

import com.google.common.collect.ImmutableMap;
import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.ai.util.AiUtil;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Iterator;

public class CutTreeBehavior extends Behavior<Adventurer> {
    private final int maxNodes;
    private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    private final ArrayDeque<BlockPos> marked = new ArrayDeque<>();
    private final HashSet<BlockPos> seen = new HashSet<>();
    private boolean scanning;
    private boolean cleanup;
    private net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> family;
    private Iterator<BlockPos> workIter;

    private CutTreeBehavior(int max) {
        super(ImmutableMap.<MemoryModuleType<?>, MemoryStatus>of(AdventurerMemories.MINING_TARGET.get(), MemoryStatus.REGISTERED), 200, 200);
        this.maxNodes = Math.max(1, max);
    }

    public static CutTreeBehavior create(int maxNodes) {
        return new CutTreeBehavior(maxNodes);
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Adventurer a) {
        return a.getActionMode() == ActionMode.CUTTING_TREE;
    }

    @Override
    protected void start(ServerLevel level, Adventurer a, long time) {
        marked.clear(); queue.clear(); seen.clear(); scanning = true; cleanup = false; workIter = null;

        BlockPos log = AiUtil.findNearestLogAvoidingOthers(a, 10, 6.0);
        if (log == null) {
            a.setActionMode(ActionMode.IDLE);
            net.liopyu.civilization.ai.brain.AdventurerAi.updateActivity(a);
            scanning = false;
            return;
        }

        family = AiUtil.woodFamilyTag(level.getBlockState(log));
        if (family == null) {
            a.setActionMode(ActionMode.IDLE);
            net.liopyu.civilization.ai.brain.AdventurerAi.updateActivity(a);
            scanning = false;
            return;
        }

        BlockPos base = AiUtil.findTreeBase(level, log);
        a.setMiningTargetPos(log);

        queue.add(log);
        seen.add(log);
        while (!queue.isEmpty() && marked.size() < maxNodes) {
            BlockPos p = queue.removeFirst();
            BlockState bs = level.getBlockState(p);
            if (!AiUtil.isSameWoodFamily(bs, family)) continue;

            marked.add(p.immutable());

            BlockPos[] neighbors = new BlockPos[]{p.above(), p.below(), p.north(), p.south(), p.east(), p.west()};
            for (BlockPos n : neighbors) {
                if (seen.contains(n)) continue;
                if (base != null) {
                    int dx = Math.abs(n.getX() - base.getX());
                    int dz = Math.abs(n.getZ() - base.getZ());
                    if (dx > 3 || dz > 3) continue;
                }
                BlockState b2 = level.getBlockState(n);
                if (AiUtil.isSameWoodFamily(b2, family)) {
                    seen.add(n);
                    queue.add(n);
                }
            }
        }

        workIter = marked.iterator();
        if (workIter.hasNext()) {
            BlockPos first = workIter.next();
            a.getBrain().setMemory(AdventurerMemories.MINING_TARGET.get(), first);
        }

        scanning = false;
        net.liopyu.civilization.ai.brain.AdventurerAi.updateActivity(a);
    }

    @Override
    protected void tick(ServerLevel level, Adventurer a, long time) {
        if (scanning) return;

        boolean hasTarget = a.getBrain().getMemory(AdventurerMemories.MINING_TARGET.get()).isPresent();
        if (!hasTarget) {
            if (workIter != null && workIter.hasNext()) {
                BlockPos next = workIter.next();
                if (level.isLoaded(next) && !level.getBlockState(next).isAir()) {
                    a.getBrain().setMemory(AdventurerMemories.MINING_TARGET.get(), next);
                    a.setMiningTargetPos(next);
                    return;
                }
            }

            if (treeCleared(level, a)) {
                var pillars = AiUtil.drainTemporaryPillarsLifo(a);
                if (!pillars.isEmpty()) {
                    BlockPos p = pillars.get(0);
                    a.getBrain().setMemory(AdventurerMemories.MINING_TARGET.get(), p);
                    a.setMiningTargetPos(p);
                    cleanup = true;
                    return;
                }
                a.clearMiningTargetPos();
                BlockPos next = AiUtil.findNearestLogAvoidingOthers(a, 24, 6.0);
                if (next != null) a.setActionMode(ActionMode.NAVIGATING_TO_NEAREST_TREE);
                else a.setActionMode(ActionMode.IDLE);
                net.liopyu.civilization.ai.brain.AdventurerAi.updateActivity(a);
            }
        } else {
            a.setMiningTargetPos((BlockPos) a.getBrain().getMemory(AdventurerMemories.MINING_TARGET.get()).get());
        }

        if (cleanup) {
            boolean empty = a.getBrain().getMemory(AdventurerMemories.MINING_TARGET.get()).isEmpty();
            if (empty) {
                cleanup = false;
                a.clearMiningTargetPos();
                BlockPos next = AiUtil.findNearestLogAvoidingOthers(a, 24, 6.0);
                if (next != null) a.setActionMode(ActionMode.NAVIGATING_TO_NEAREST_TREE);
                else a.setActionMode(ActionMode.IDLE);
                net.liopyu.civilization.ai.brain.AdventurerAi.updateActivity(a);
            }
        }
    }

    @Override
    protected void stop(ServerLevel level, Adventurer a, long time) {
        marked.clear();
        queue.clear();
        seen.clear();
        scanning = false;
        cleanup = false;
        workIter = null;
        a.clearMiningTargetPos();
        a.getBrain().eraseMemory(AdventurerMemories.MINING_TARGET.get());
        net.liopyu.civilization.ai.brain.AdventurerAi.updateActivity(a);
    }


    private boolean treeCleared(ServerLevel level, Adventurer a) {
        if (marked.isEmpty()) return false;
        for (BlockPos p : marked) {
            BlockState st = level.getBlockState(p);
            if (AiUtil.isLog(st) && !AiUtil.isTemporaryPillar(a, p)) return false;
        }
        return true;
    }
}
