package net.liopyu.civilization.ai.goal;

import com.mojang.logging.LogUtils;
import net.liopyu.civilization.ai.ActionMode;
import net.liopyu.civilization.ai.util.AiUtil;
import net.liopyu.civilization.entity.Adventurer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.EnumSet;

import static net.liopyu.civilization.ai.util.AiUtil.aiLogger;

public class MineBlockGoal extends Goal {
    private final Adventurer mob;
    private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
    private BlockPos current;
    private int breakingTicks;
    private int breakingTotal;

    private BlockPos lastProgressPos;
    private boolean showingProgress;

    private int swingCooldown;

    public MineBlockGoal(Adventurer mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        return !mob.level().isClientSide
                && (current != null || !queue.isEmpty())
                && mob.getActionMode() != ActionMode.IDLE;
    }

    @Override
    public boolean canContinueToUse() {
        return (current != null || !queue.isEmpty())
                && mob.getActionMode() != ActionMode.IDLE;
    }


    public void enqueue(BlockPos p) {
        if (p != null) queue.add(p.immutable());
    }

    @Override
    public void start() {
        super.start();
        aiLogger("Start: MineBlockGoal");
    }

    @Override
    public void stop() {
        aiLogger("Stop: MineBlockGoal");
        endProgress();
        swingCooldown = 0;
        current = null;
        breakingTicks = 0;
        breakingTotal = 0;
    }

    @Override
    public void tick() {
        if (current == null) {
            while (!queue.isEmpty() && current == null) {
                BlockPos next = queue.removeFirst();
                if (mob.getActionMode() == net.liopyu.civilization.ai.ActionMode.CUTTING_TREE
                        && net.liopyu.civilization.ai.util.AiUtil.isTemporaryPillar(mob, next)) {
                    continue;
                }
                if (mob.isValidWorkPos(next) && !mob.level().getBlockState(next).isAir()) current = next;
            }

            if (current == null) {
                endProgress();
                return;
            }
            mob.setMiningTargetPos(current);
        }

        if (!mob.isValidWorkPos(current)) {
            endProgress();
            current = null;
            breakingTicks = 0;
            return;
        }

        BlockState st = mob.level().getBlockState(current);
        if (mob.getActionMode() == net.liopyu.civilization.ai.ActionMode.CUTTING_TREE) {
            boolean isTempPillar = net.liopyu.civilization.ai.util.AiUtil.isTemporaryPillar(mob, current);
            boolean treeBlock = net.liopyu.civilization.ai.util.AiUtil.isLog(st) || net.liopyu.civilization.ai.util.AiUtil.isLeaves(st);
            if (!treeBlock && !isTempPillar) {
                endProgress();
                current = null;
                breakingTicks = 0;
                return;
            }
        }


        if (st.isAir()) {
            endProgress();
            current = null;
            breakingTicks = 0;
            mob.clearMiningTargetPos();
            return;
        }

        double r = mob.entityInteractionRange();
        double d2 = mob.distanceToSqr(current.getX() + 0.5, current.getY() + 0.5, current.getZ() + 0.5);
        if (d2 > r * r) {
            if (showingProgress) endProgress();
            int fx = net.minecraft.util.Mth.floor(mob.getX());
            int fz = net.minecraft.util.Mth.floor(mob.getZ());
            boolean within3x3 = Math.abs(fx - current.getX()) <= 1 && Math.abs(fz - current.getZ()) <= 1;

            if (current.getY() >= mob.blockPosition().getY() && within3x3) {
                net.minecraft.world.phys.AABB upBox = mob.getBoundingBox().move(0.0, 1.0, 0.0);
                boolean clearHeadroom = net.liopyu.civilization.ai.util.AiUtil.isAABBPassableForEntity(mob, upBox);
                if (!clearHeadroom) {
                    net.minecraft.core.BlockPos leaf = net.liopyu.civilization.ai.util.AiUtil.firstBlockingInAABB(
                            mob, upBox, net.liopyu.civilization.ai.util.AiUtil::isLeaves);
                    if (leaf != null) {
                        enqueueFront(current);
                        current = leaf;
                        breakingTicks = 0;
                        breakingTotal = 0;
                        mob.setMiningTargetPos(leaf);
                        return;
                    } else {
                        mob.setMiningTargetPos(current);
                        return;
                    }
                }
                net.liopyu.civilization.ai.util.AiUtil.pillarUpOneBlock(mob, 2);
                return;
            }
            mob.setMiningTargetPos(current);
            return;
        }


        if (!hasLineOfSight(current)) {
            java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> pred =
                    net.liopyu.civilization.ai.util.AiUtil.obstaclePredicateForMode(mob);
            BlockPos obstacle = net.liopyu.civilization.ai.util.AiUtil.firstBlockingAlong(mob, current, pred);
            if (obstacle != null) {
                if (showingProgress) endProgress();
                enqueueFront(current);
                current = obstacle;
                breakingTicks = 0;
                breakingTotal = 0;
                mob.setMiningTargetPos(obstacle);
                return;
            } else {
                if (showingProgress) endProgress();
                mob.setMiningTargetPos(current);
                return;
            }
        }


        if (breakingTotal <= 0) {
            breakingTicks = 0;
            breakingTotal = computeBreakTimeTicks(mob.getMainHandItem(), st, current);
            if (breakingTotal <= 0) breakingTotal = 1;
        }

        if (!showingProgress) beginProgress();

        mob.getLookControl().setLookAt(current.getX() + 0.5, current.getY() + 0.5, current.getZ() + 0.5);
        breakingTicks++;
        if (!mob.level().isClientSide) {
            if (swingCooldown <= 0) {
                mob.swing(InteractionHand.MAIN_HAND, true);
                swingCooldown = 2 + mob.getRandom().nextInt(4);
            } else {
                swingCooldown--;
            }
        }
        int stage = (int) Math.floor((breakingTicks / (double) breakingTotal) * 10.0);
        if (stage < 0) stage = 0;
        if (stage > 9) stage = 9;
        tickProgress(stage);

        if (breakingTicks >= breakingTotal) {
            AiUtil.harvestBlockToInternal(mob, current);
            endProgress();
            current = null;
            breakingTicks = 0;
            breakingTotal = 0;
        }
        // LogUtils.getLogger().info(queue.size() + "");
    }

    public boolean isIdle() {
        return current == null && queue.isEmpty();
    }

    private boolean hasLineOfSight(BlockPos pos) {
        net.minecraft.world.phys.Vec3 eye = mob.getEyePosition(1.0F);
        double px = pos.getX() + 0.5, py = pos.getY() + 0.5, pz = pos.getZ() + 0.5;
        double o = 0.3;
        net.minecraft.world.phys.Vec3[] targets = new net.minecraft.world.phys.Vec3[]{
                new net.minecraft.world.phys.Vec3(px, py, pz),
                new net.minecraft.world.phys.Vec3(px + o, py, pz),
                new net.minecraft.world.phys.Vec3(px - o, py, pz),
                new net.minecraft.world.phys.Vec3(px, py + o, pz),
                new net.minecraft.world.phys.Vec3(px, py, pz + o),
                new net.minecraft.world.phys.Vec3(px, py, pz - o)
        };
        java.util.function.Predicate<net.minecraft.world.level.block.state.BlockState> pred =
                net.liopyu.civilization.ai.util.AiUtil.obstaclePredicateForMode(mob);
        for (net.minecraft.world.phys.Vec3 end : targets) {
            net.minecraft.world.level.ClipContext ctx = new net.minecraft.world.level.ClipContext(
                    eye, end,
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    mob
            );
            net.minecraft.world.phys.HitResult hit = mob.level().clip(ctx);
            if (hit == null || hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS) return true;
            if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                net.minecraft.core.BlockPos hp = ((net.minecraft.world.phys.BlockHitResult) hit).getBlockPos();
                if (hp.equals(pos)) return true;
                net.minecraft.world.level.block.state.BlockState st = mob.level().getBlockState(hp);
                if (pred.test(st)) return false;
                double near = eye.distanceToSqr(net.minecraft.world.phys.Vec3.atCenterOf(hp));
                if (near < 1.2 * 1.2) continue;
            }
        }
        return false;
    }


    private int computeBreakTimeTicks(ItemStack tool, BlockState state, BlockPos pos) {
        float hardness = state.getDestroySpeed(mob.level(), pos);
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

    private void beginProgress() {
        if (!(mob.level() instanceof net.minecraft.server.level.ServerLevel sl)) return;
        if (current == null) return;
        sl.destroyBlockProgress(mob.getId(), current, 0);
        lastProgressPos = current.immutable();
        showingProgress = true;
        swingCooldown = 0;

    }


    private void tickProgress(int stage) {
        if (!(mob.level() instanceof ServerLevel sl)) return;
        if (current == null) return;
        int clamped = Math.max(0, Math.min(9, stage));
        sl.destroyBlockProgress(mob.getId(), current, clamped);
        showingProgress = true;
    }

    private void endProgress() {
        if (!(mob.level() instanceof ServerLevel sl)) return;
        if (lastProgressPos != null && showingProgress) {
            sl.destroyBlockProgress(mob.getId(), lastProgressPos, -1);
        }
        lastProgressPos = null;
        showingProgress = false;
        this.breakingTicks = 0;
    }

    public void enqueueFront(BlockPos p) {
        if (p != null) queue.addFirst(p.immutable());
    }

}
