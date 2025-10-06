package net.liopyu.civilization.ai.brain;

import com.google.common.collect.ImmutableMap;
import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.ai.util.AiUtil;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.behavior.Behavior;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;

public class MineBlockBehavior extends Behavior<Adventurer> {
    private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    private BlockPos current;
    private int breakingTicks;
    private int breakingTotal;
    private BlockPos lastProgressPos;
    private boolean showing;
    private int swing;

    private MineBlockBehavior() {
        super(ImmutableMap.<MemoryModuleType<?>, MemoryStatus>of(), 20, 20);
    }

    public static MineBlockBehavior create() {
        return new MineBlockBehavior();
    }

    public void enqueue(BlockPos p) {
        if (p != null) queue.add(p.immutable());
    }

    public void enqueueFront(BlockPos p) {
        if (p != null) queue.addFirst(p.immutable());
    }

    public boolean isIdle() {
        return current == null && queue.isEmpty();
    }

    @Override
    protected boolean checkExtraStartConditions(ServerLevel level, Adventurer a) {
        if (a.getActionMode() == ActionMode.IDLE) return false;
        if (current != null || !queue.isEmpty()) return true;
        return a.getBrain().getMemory(AdventurerMemories.MINING_TARGET.get()).isPresent();
    }


    @Override
    protected void stop(ServerLevel level, Adventurer a, long time) {
        endProgress(level, a);
        current = null;
        breakingTicks = 0;
        breakingTotal = 0;
        a.getBrain().eraseMemory(AdventurerMemories.MINING_TARGET.get());
        a.clearMiningTargetPos();
    }

    @Override
    protected boolean canStillUse(ServerLevel level, Adventurer a, long time) {
        if (a.getActionMode() == ActionMode.IDLE) return false;
        if (current != null || !queue.isEmpty()) return true;
        return a.getBrain().getMemory(AdventurerMemories.MINING_TARGET.get()).isPresent();
    }

    @Override
    protected void tick(ServerLevel level, Adventurer a, long time) {
        var brain = a.getBrain();

        // ===== adopt current from MINING_TARGET memory if needed =====
        if (current == null) {
            // 1) Use brain memory if present
            var mem = brain.getMemory(AdventurerMemories.MINING_TARGET.get());
            if (mem.isPresent()) {
                BlockPos candidate = (BlockPos) mem.get();
                if (a.isValidWorkPos(candidate) && !level.getBlockState(candidate).isAir()) {
                    current = candidate.immutable();
                } else {
                    brain.eraseMemory(AdventurerMemories.MINING_TARGET.get());
                }
            }

            // 2) Otherwise, drain our local queue (e.g., tunneling obstacles)
            while (current == null && !queue.isEmpty()) {
                BlockPos next = queue.removeFirst();
                if (a.getActionMode() == ActionMode.CUTTING_TREE && AiUtil.isTemporaryPillar(a, next)) continue;
                if (a.isValidWorkPos(next) && !level.getBlockState(next).isAir()) current = next;
            }

            if (current == null) {
                brain.eraseMemory(AdventurerMemories.MINING_TARGET.get());
                endProgress(level, a);
                return;
            }

            // keep synced
            brain.setMemory(AdventurerMemories.MINING_TARGET.get(), current);
            a.setMiningTargetPos(current);
        }

        // ===== existing logic unchanged below =====
        if (!a.isValidWorkPos(current)) {
            endProgress(level, a);
            current = null;
            breakingTicks = 0;
            if (queue.isEmpty()) {
                brain.eraseMemory(AdventurerMemories.MINING_TARGET.get());
                a.clearMiningTargetPos();
            }
            return;
        }

        BlockState st = level.getBlockState(current);

        if (a.getActionMode() == ActionMode.CUTTING_TREE) {
            boolean isTemp = AiUtil.isTemporaryPillar(a, current);
            boolean tree = AiUtil.isLog(st) || AiUtil.isLeaves(st);
            if (!tree && !isTemp) {
                endProgress(level, a);
                current = null;
                breakingTicks = 0;
                if (queue.isEmpty()) {
                    brain.eraseMemory(AdventurerMemories.MINING_TARGET.get());
                    a.clearMiningTargetPos();
                }
                return;
            }
        }

        if (st.isAir()) {
            endProgress(level, a);
            current = null;
            breakingTicks = 0;
            brain.eraseMemory(AdventurerMemories.MINING_TARGET.get());
            a.clearMiningTargetPos();
            return;
        }

        double r = a.entityInteractionRange();
        double d2 = a.distanceToSqr(current.getX() + 0.5, current.getY() + 0.5, current.getZ() + 0.5);

        if (d2 > r * r) {
            if (showing) endProgress(level, a);

            int fx = net.minecraft.util.Mth.floor(a.getX());
            int fz = net.minecraft.util.Mth.floor(a.getZ());
            boolean within3x3 = Math.abs(fx - current.getX()) <= 1 && Math.abs(fz - current.getZ()) <= 1;

            if (current.getY() >= a.blockPosition().getY() && within3x3) {
                var upBox = a.getBoundingBox().move(0.0, 1.0, 0.0);
                boolean clear = AiUtil.isAABBPassableForEntity(a, upBox);
                if (!clear) {
                    var leaf = AiUtil.firstBlockingInAABB(a, upBox, AiUtil::isLeaves);
                    if (leaf != null) {
                        enqueueFront(current);
                        current = leaf;
                        breakingTicks = 0;
                        breakingTotal = 0;

                        brain.setMemory(AdventurerMemories.MINING_TARGET.get(), current);
                        a.setMiningTargetPos(leaf);
                        return;
                    } else {
                        a.setMiningTargetPos(current);
                        return;
                    }
                }
                AiUtil.pillarUpOneBlock(a, 2);
                return;
            }

            a.setMiningTargetPos(current);
            return;
        }

        if (!hasLineOfSight(level, a, current)) {
            var pred = AiUtil.obstaclePredicateForMode(a);
            BlockPos obstacle = AiUtil.firstBlockingAlong(a, current, pred);
            if (obstacle != null) {
                if (showing) endProgress(level, a);
                enqueueFront(current);
                current = obstacle;
                breakingTicks = 0;
                breakingTotal = 0;

                brain.setMemory(AdventurerMemories.MINING_TARGET.get(), current);
                a.setMiningTargetPos(obstacle);
                return;
            } else {
                if (showing) endProgress(level, a);
                a.setMiningTargetPos(current);
                return;
            }
        }

        if (breakingTotal <= 0) {
            breakingTicks = 0;
            breakingTotal = computeBreakTicks(a.getMainHandItem(), st, current, level);
            if (breakingTotal <= 0) breakingTotal = 1;
        }

        if (!showing) beginProgress(level, a);

        a.getLookControl().setLookAt(current.getX() + 0.5, current.getY() + 0.5, current.getZ() + 0.5);
        breakingTicks++;

        if (swing <= 0) {
            a.swing(InteractionHand.MAIN_HAND, true);
            swing = 2 + a.getRandom().nextInt(4);
        } else swing--;

        int stage = (int) Math.floor((breakingTicks / (double) breakingTotal) * 10.0);
        if (stage < 0) stage = 0;
        if (stage > 9) stage = 9;
        tickProgress(level, a, stage);

        if (breakingTicks >= breakingTotal) {
            AiUtil.harvestBlockToInternal(a, current);
            endProgress(level, a);

            current = null;
            breakingTicks = 0;
            breakingTotal = 0;

            if (queue.isEmpty()) {
                brain.eraseMemory(AdventurerMemories.MINING_TARGET.get());
                a.clearMiningTargetPos();
            }
        }
    }


    private boolean hasLineOfSight(ServerLevel level, Adventurer a, BlockPos pos) {
        var eye = a.getEyePosition(1.0F);
        double px = pos.getX() + 0.5, py = pos.getY() + 0.5, pz = pos.getZ() + 0.5;
        double o = 0.3;
        var targets = new net.minecraft.world.phys.Vec3[]{
                new net.minecraft.world.phys.Vec3(px, py, pz),
                new net.minecraft.world.phys.Vec3(px + o, py, pz),
                new net.minecraft.world.phys.Vec3(px - o, py, pz),
                new net.minecraft.world.phys.Vec3(px, py + o, pz),
                new net.minecraft.world.phys.Vec3(px, py, pz + o),
                new net.minecraft.world.phys.Vec3(px, py, pz - o)
        };
        var pred = AiUtil.obstaclePredicateForMode(a);
        for (var end : targets) {
            var ctx = new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, a);
            var hit = level.clip(ctx);
            if (hit == null || hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return true;
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                var hp = ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos();
                if (hp.equals(pos)) return true;
                var st = level.getBlockState(hp);
                if (pred.test(st)) return false;
                double near = eye.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(hp));
                if (near < 1.2 * 1.2) continue;
            }
        }
        return false;
    }

    private int computeBreakTicks(net.minecraft.world.item.ItemStack tool, BlockState state, BlockPos pos, ServerLevel level) {
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return 0;
        float speed = 1.0F;
        if (!tool.isEmpty()) {
            float s = tool.getDestroySpeed(state);
            if (s > 1.0F) speed = s;
        }
        if (speed <= 0.0F) speed = 0.5F;
        float t = Math.max(1.0F, 20.0F * hardness / speed);
        if (t > 200F) t = 200F;
        return (int) Math.ceil(t);
    }

    private void beginProgress(ServerLevel sl, Adventurer a) {
        if (current == null) return;
        sl.destroyBlockProgress(a.getId(), current, 0);
        lastProgressPos = current.immutable();
        showing = true;
        swing = 0;
    }

    private void tickProgress(ServerLevel sl, Adventurer a, int stage) {
        if (current == null) return;
        int clamped = Math.max(0, Math.min(9, stage));
        sl.destroyBlockProgress(a.getId(), current, clamped);
        showing = true;
    }

    private void endProgress(ServerLevel sl, Adventurer a) {
        if (lastProgressPos != null && showing) sl.destroyBlockProgress(a.getId(), lastProgressPos, -1);
        lastProgressPos = null;
        showing = false;
        breakingTicks = 0;
    }
}
